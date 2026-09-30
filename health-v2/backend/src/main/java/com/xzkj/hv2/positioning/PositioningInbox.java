package com.xzkj.hv2.positioning;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 扫描收件箱，逐个处理定位文件（docs/02 第二节）：
 * <pre>
 * 1. 文件名合法、是普通文件、是本矿的？  否 → 移到失败目录
 * 2. 属于直接删除的类型？               是 → 删除，记一行日志
 * 3. 文件完整（末尾有 ||）？            否 → 本轮跳过；连续 10 分钟仍不完整才移到失败目录
 * 4. 处理过（文件名 + 摘要都相同）？     是 → 记 DUPLICATE，移到备份目录
 * 5. 解析并写库（一个事务）
 * 6. 成功 → 移到备份目录；失败 → 回滚，移到失败目录
 * </pre>
 * 文件先复制到备份目录再提交事务，提交成功后才删收件箱里的原文件；
 * 备份目录写不进时文件留在收件箱、不入库，下一轮重试。
 * 数据库连不上时也留在收件箱，等下一轮，不当成文件的错。
 */
@Component
public class PositioningInbox {

    private static final Logger log = LoggerFactory.getLogger(PositioningInbox.class);

    private final PositioningPaths paths;
    private final PositioningProperties props;
    private final PositioningIngestService ingest;
    private final PositioningMapper mapper;
    private final Clock clock;
    private final MeterRegistry meters;

    public PositioningInbox(PositioningPaths paths, PositioningProperties props, PositioningIngestService ingest,
                            PositioningMapper mapper, Clock clock, MeterRegistry meters) {
        this.paths = paths;
        this.props = props;
        this.ingest = ingest;
        this.mapper = mapper;
        this.clock = clock;
        this.meters = meters;
    }

    /** 扫描一轮。返回本轮每个文件的处理结果（跳过的不算），供测试和日志使用。 */
    public synchronized List<Outcome> scanOnce() {
        List<Outcome> outcomes = new ArrayList<>();
        Path topic = paths.inbox().resolve(PositioningPaths.TOPIC);
        try {
            Files.createDirectories(topic.resolve(props.mineCode()));
            String misplaced = "文件应放在 " + PositioningPaths.TOPIC + "/" + props.mineCode() + "/ 目录下";
            for (Path file : listFiles(paths.inbox())) {
                addIfHandled(outcomes, failWithoutReading(file, "", misplaced));
            }
            for (Path file : listFiles(topic)) {
                addIfHandled(outcomes, failWithoutReading(file, PositioningPaths.TOPIC, misplaced));
            }
            for (Path mineDir : listDirs(topic)) {
                String dirName = mineDir.getFileName().toString();
                for (Path file : sortedByUploadTime(listFiles(mineDir))) {
                    addIfHandled(outcomes, processOne(file, dirName));
                }
            }
        } catch (DatabaseUnavailable e) {
            log.warn("数据库暂时不可用，本轮停止处理，剩下的文件留在收件箱等下一轮：{}", e.getMessage());
        } catch (IOException e) {
            log.error("扫描收件箱失败：{}", topic, e);
        }
        return outcomes;
    }

    /** 本轮跳过或出错留在收件箱的文件返回 null，不计入结果 */
    private static void addIfHandled(List<Outcome> outcomes, Outcome o) {
        if (o != null) {
            outcomes.add(o);
        }
    }

    private Outcome processOne(Path file, String dirName) {
        String fileName = file.getFileName().toString();
        String relDir = PositioningPaths.TOPIC + "/" + dirName;
        try {
            // ---- 第 1 步：文件名、普通文件、本矿 ----
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                return failWithoutReading(file, relDir, "不是普通文件（如符号链接），不读取");
            }
            PositioningFileName name = PositioningFileName.parse(fileName);
            if (name == null) {
                return failWithoutReading(file, relDir, "文件名不合法，应为 <煤矿编码12位>_<类型>_<上传时间14位>.txt");
            }
            if (!name.mineCode().equals(props.mineCode())) {
                return failWithoutReading(file, relDir, "不是本矿（" + props.mineCode() + "）的文件");
            }
            if (!dirName.equals(name.mineCode())) {
                return failWithoutReading(file, relDir, "所在目录与文件名里的煤矿编码不一致");
            }

            // ---- 第 2 步：直接删除的类型 ----
            if (name.discarded()) {
                Files.delete(file);
                log.info("定位文件 {} 属于不使用的类型 {}，已删除", fileName, name.type());
                count("DELETED");
                return new Outcome(fileName, "DELETED");
            }

            // ---- 第 3 步：大小、完整性 ----
            long size = Files.size(file);
            LocalDateTime receivedAt = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(file).toInstant(), clock.getZone()).withNano(0);
            if (size > props.maxFileBytes()) {
                return fail(file, relDir, name, null, size, receivedAt,
                        "文件大小 " + size + " 字节，超过上限 " + props.maxFileBytes(), List.of(), null);
            }
            byte[] content = Files.readAllBytes(file);
            if (!PositioningParser.isComplete(content)) {
                Instant modified = Files.getLastModifiedTime(file).toInstant();
                Duration waited = Duration.between(modified, clock.instant());
                if (waited.compareTo(props.incompleteTimeout()) < 0) {
                    log.debug("定位文件 {} 还不完整，本轮跳过", fileName);
                    return null;
                }
                return fail(file, relDir, name, sha256(content), size, receivedAt,
                        "文件末尾没有 ||，超过 " + props.incompleteTimeout().toMinutes() + " 分钟仍不完整",
                        List.of(), null);
            }
            String sha = sha256(content);
            var facts = new FactsBuilder(fileName, name.mineCode(), sha, size, receivedAt);

            // ---- 未识别的类型：不解析，只备份 ----
            PositioningFileType type = name.knownType();
            if (type == null) {
                String backup = moveToBackup(file, name);
                ingest.record(facts.build(backup).toEntry(name.type(), null, IngestStatus.UNKNOWN, null, null,
                        "未识别的文件类型 " + name.type()), List.of());
                log.warn("定位文件 {} 是未识别的类型 {}，已备份，不解析", fileName, name.type());
                return counted(fileName, IngestStatus.UNKNOWN);
            }

            // ---- 第 4 步：重复 ----
            if (mapper.countProcessed(fileName, sha) > 0) {
                String backup = moveToBackup(file, name);
                ingest.record(facts.build(backup).toEntry(type.name(), null, IngestStatus.DUPLICATE, null, null,
                        "同名同内容的文件已处理过"), List.of());
                log.info("定位文件 {} 已处理过，记为重复并备份", fileName);
                return counted(fileName, IngestStatus.DUPLICATE);
            }

            // ---- 第 5 步：解析 ----
            ParsedFile parsed;
            try {
                parsed = PositioningParser.parse(type, name.mineCode(), content);
            } catch (PositioningFormatException e) {
                return fail(file, relDir, name, sha, size, receivedAt, e.getMessage(), List.of(), null);
            }
            LocalDateTime latestAllowed = LocalDateTime.now(clock).plusMinutes(props.futureToleranceMinutes());
            if (parsed.headerTime().isAfter(latestAllowed)) {
                log.error("定位文件 {} 的文件头时间 {} 比服务器现在晚超过 {} 分钟，拒收",
                        fileName, parsed.headerTime(), props.futureToleranceMinutes());
                return fail(file, relDir, name, sha, size, receivedAt, "文件头时间 " + parsed.headerTime()
                        + " 比服务器现在晚超过 " + props.futureToleranceMinutes() + " 分钟", List.of(),
                        parsed.headerTime());
            }
            int errors = parsed.errorCount();
            if (parsed.recordCount() > 0 && errors > parsed.recordCount() * props.maxErrorRatio()) {
                return fail(file, relDir, name, sha, size, receivedAt, parsed.recordCount() + " 条记录中 "
                        + errors + " 条出错，超过 " + Math.round(props.maxErrorRatio() * 100)
                        + "%，格式可能整体变了", parsed.issues(), parsed.headerTime());
            }

            // ---- 第 6 步：先复制到备份，再写库，成功后删原文件 ----
            Path backup = reserve(paths.backup().resolve(relDir).resolve(name.uploadDate()), fileName);
            try {
                Files.createDirectories(backup.getParent());
                Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException e) {
                log.error("定位文件 {} 复制到备份目录失败，留在收件箱下一轮重试", fileName, e);
                return null;
            }
            IngestStatus status;
            try {
                status = ingest.ingest(parsed, facts.build(relative("backup", paths.backup(), backup)));
            } catch (RuntimeException e) {
                Files.deleteIfExists(backup);
                if (isDatabaseUnavailable(e)) {
                    throw new DatabaseUnavailable(e);
                }
                log.error("定位文件 {} 写库失败，已回滚", fileName, e);
                return fail(file, relDir, name, sha, size, receivedAt, "写库失败：" + e.getClass().getSimpleName(),
                        List.of(), parsed.headerTime());
            }
            deleteOriginal(file);
            log.info("定位文件 {} 处理完成：{}，{} 条记录，{} 条有误", fileName, status, parsed.recordCount(), errors);
            return counted(fileName, status);
        } catch (DatabaseUnavailable e) {
            throw e;
        } catch (RuntimeException e) {
            if (isDatabaseUnavailable(e)) {
                throw new DatabaseUnavailable(e);
            }
            log.error("处理定位文件 {} 时出错，留在收件箱下一轮重试", fileName, e);
            return null;
        } catch (IOException e) {
            log.error("处理定位文件 {} 时读写文件出错，留在收件箱下一轮重试", fileName, e);
            return null;
        }
    }

    // ---- 失败目录 ----

    /** 文件名不合法、不是本矿、符号链接等：不读内容，直接移到失败目录。 */
    private Outcome failWithoutReading(Path file, String relDir, String reason) {
        String fileName = file.getFileName().toString();
        PositioningFileName name = PositioningFileName.parse(fileName);
        try {
            String errorPath = moveToError(file, relDir);
            ingest.record(new IngestFileEntry(fileName, name == null ? null : name.type(),
                    name == null ? null : name.mineCode(), null, null, null, IngestStatus.FAILED,
                    null, null, reason, null, errorPath), List.of());
        } catch (IOException e) {
            log.error("定位文件 {} 移到失败目录失败", fileName, e);
            return null;
        } catch (RuntimeException e) {
            if (isDatabaseUnavailable(e)) {
                throw new DatabaseUnavailable(e);
            }
            throw e;
        }
        log.error("定位文件 {} 已移到失败目录：{}", fileName, reason);
        return counted(fileName, IngestStatus.FAILED);
    }

    private Outcome fail(Path file, String relDir, PositioningFileName name, String sha, long size,
                         LocalDateTime receivedAt, String reason, List<RecordIssue> issues,
                         LocalDateTime headerTime) throws IOException {
        String fileName = file.getFileName().toString();
        String errorPath = moveToError(file, relDir);
        log.error("定位文件 {} 处理失败，已移到失败目录：{}", fileName, reason);
        ingest.record(new IngestFileEntry(fileName, name.type(), name.mineCode(), headerTime, sha, size,
                IngestStatus.FAILED, null, (int) issues.stream()
                        .filter(i -> i.kind() == RecordIssue.Kind.ERROR).count(),
                reason, receivedAt, errorPath), issues);
        return counted(fileName, IngestStatus.FAILED);
    }

    private String moveToError(Path file, String relDir) throws IOException {
        Path dir = relDir.isEmpty() ? paths.error() : paths.error().resolve(relDir);
        Files.createDirectories(dir);
        Path target = reserve(dir, file.getFileName().toString());
        // 符号链接移动的是链接本身，不会读取或移动它指向的文件
        Files.move(file, target);
        return relative("error", paths.error(), target);
    }

    // ---- 备份目录 ----

    private String moveToBackup(Path file, PositioningFileName name) throws IOException {
        Path dir = paths.backup().resolve(PositioningPaths.TOPIC).resolve(name.mineCode()).resolve(name.uploadDate());
        Files.createDirectories(dir);
        Path target = reserve(dir, name.fileName());
        Files.move(file, target);
        return relative("backup", paths.backup(), target);
    }

    /** 目标目录里已有同名文件时，依次试 .dup1、.dup2……，不覆盖原来的。 */
    private static Path reserve(Path dir, String fileName) {
        Path target = dir.resolve(fileName);
        for (int i = 1; Files.exists(target, LinkOption.NOFOLLOW_LINKS); i++) {
            target = dir.resolve(fileName + ".dup" + i);
        }
        return target;
    }

    private static String relative(String root, Path base, Path target) {
        return root + "/" + base.relativize(target).toString().replace('\\', '/');
    }

    private static void deleteOriginal(Path file) {
        try {
            Files.delete(file);
        } catch (IOException e) {
            // 已入库、已备份；原文件下一轮会被当成重复文件移走
            log.error("定位文件 {} 已入库，但删除收件箱里的原文件失败", file.getFileName(), e);
        }
    }

    // ---- 工具 ----

    private static List<Path> listFiles(Path dir) throws IOException {
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                // 隐藏文件（如 macOS 的 .DS_Store、上传工具的临时文件）不处理
                if (!p.getFileName().toString().startsWith(".")
                        && !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    result.add(p);
                }
            }
        }
        return result;
    }

    /** 真正的子目录；指向别处的目录链接不进去 */
    private static List<Path> listDirs(Path dir) throws IOException {
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    result.add(p);
                }
            }
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    /** 先处理上传时间早的，减少不必要的 STALE；文件名不合法的排在最后 */
    private static List<Path> sortedByUploadTime(List<Path> files) {
        return files.stream().sorted(Comparator.comparing((Path p) -> {
            PositioningFileName n = PositioningFileName.parse(p.getFileName().toString());
            return n == null ? "~" : n.uploadTime();
        }).thenComparing(p -> p.getFileName().toString())).toList();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isDatabaseUnavailable(Throwable e) {
        return e instanceof TransientDataAccessException
                || e instanceof DataAccessResourceFailureException
                || e instanceof RecoverableDataAccessException
                || e instanceof CannotCreateTransactionException;
    }

    private Outcome counted(String fileName, IngestStatus status) {
        count(status.name());
        return new Outcome(fileName, status.name());
    }

    private void count(String status) {
        meters.counter("hv2.positioning.files", "status", status).increment();
    }

    /** 本轮处理了一个文件。status 是 IngestStatus 的名字，或 DELETED（直接删除的类型）。 */
    public record Outcome(String fileName, String status) {
    }

    private record FactsBuilder(String fileName, String mineCode, String sha, long size, LocalDateTime receivedAt) {
        PositioningIngestService.FileFacts build(String backupPath) {
            return new PositioningIngestService.FileFacts(fileName, mineCode, sha, size, receivedAt, backupPath);
        }
    }

    private static final class DatabaseUnavailable extends RuntimeException {
        DatabaseUnavailable(Throwable cause) {
            super(cause.getMessage(), cause, false, false);
        }
    }
}
