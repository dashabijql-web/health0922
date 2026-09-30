package com.xzkj.hv2.positioning;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 收件箱、备份、失败目录的实际位置。
 * <p>
 * 配置了就用配置的；没配置时找 health-v2 目录下的 runtime/。
 * 后端可能从 health-v2/、health-v2/backend/ 或仓库根目录启动（命令行脚本、IDEA），
 * 所以按这三种工作目录依次找，都找不到就启动失败，提示设置环境变量。
 */
@Component
public class PositioningPaths {

    /** 目录结构 jxry/<煤矿编码>/（docs/02 第二节） */
    public static final String TOPIC = "jxry";

    private final Path inbox;
    private final Path backup;
    private final Path error;

    public PositioningPaths(PositioningProperties props) {
        Path runtime = null;
        if (isBlank(props.inboxDir()) || isBlank(props.backupDir()) || isBlank(props.errorDir())) {
            runtime = findRuntimeDir();
        }
        this.inbox = resolve(props.inboxDir(), runtime, "inbox");
        this.backup = resolve(props.backupDir(), runtime, "backup");
        this.error = resolve(props.errorDir(), runtime, "error");
    }

    public Path inbox() {
        return inbox;
    }

    public Path backup() {
        return backup;
    }

    public Path error() {
        return error;
    }

    private static Path resolve(String configured, Path runtime, String name) {
        Path p = isBlank(configured) ? runtime.resolve(name) : Path.of(configured);
        return p.toAbsolutePath().normalize();
    }

    private static Path findRuntimeDir() {
        Path cwd = Path.of("").toAbsolutePath();
        List<Path> candidates = List.of(cwd, cwd.resolve(".."), cwd.resolve("health-v2"));
        for (Path root : candidates) {
            if (Files.isRegularFile(root.resolve("backend/pom.xml")) && Files.isDirectory(root.resolve("docs"))) {
                return root.resolve("runtime").normalize();
            }
        }
        throw new IllegalStateException("找不到 health-v2 目录，请设置 POSITIONING_INBOX_DIR、"
                + "POSITIONING_BACKUP_DIR、POSITIONING_ERROR_DIR（当前工作目录 " + cwd + "）");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
