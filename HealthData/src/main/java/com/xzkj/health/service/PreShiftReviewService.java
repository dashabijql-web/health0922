package com.xzkj.health.service;

import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.config.CommandCenterOperationalProperties;
import com.xzkj.health.dto.commandcenter.PreShiftReviewCandidateRow;
import com.xzkj.health.dto.commandcenter.PreShiftReviewRow;
import com.xzkj.health.dto.commandcenter.PreShiftReviewSummaryRow;
import com.xzkj.health.dto.commandcenter.PreShiftReviewSummaryView;
import com.xzkj.health.dto.commandcenter.PreShiftReviewView;
import com.xzkj.health.mapper.PreShiftReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PreShiftReviewService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> LIST_STATUSES = Set.of("ALL", "PENDING", "IN_REVIEW", "COMPLETED", "OVERDUE");

    private final PreShiftReviewMapper reviewMapper;
    private final CommandCenterOperationalProperties properties;
    private volatile boolean schemaInitialized;

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public PreShiftReviewSummaryView getTodaySummary() {
        ensureSchema();
        PreShiftReviewSummaryRow row = reviewMapper.getTodaySummary();
        return new PreShiftReviewSummaryView(
                intValue(row == null ? null : row.getAwaitingReview()),
                intValue(row == null ? null : row.getRetestOverdue()));
    }

    @Transactional(rollbackFor = Exception.class)
    public List<PreShiftReviewView> getTodayReviews(String status) {
        syncTodayCandidates();
        String normalized = normalizeListStatus(status);
        return reviewMapper.getTodayReviews(normalized).stream().map(this::toView).toList();
    }

    private void syncTodayCandidates() {
        ensureSchema();
        int reviewMinutes = Math.max(1, properties.getPreShiftReviewMinutes());
        List<PreShiftReviewCandidateRow> candidates = reviewMapper.getTodayCandidates();
        Map<String, PreShiftReviewRow> current = reviewMapper.getTodayReviews("ALL").stream()
                .collect(Collectors.toMap(
                        review -> Objects.requireNonNull(review).getEmpCode(), Function.identity()));
        for (PreShiftReviewCandidateRow candidate : candidates) {
            if (candidate.getEmpCode() == null || candidate.getSourceRecordTime() == null) {
                continue;
            }
            boolean qualified = intValue(candidate.getQualified()) == 1;
            PreShiftReviewRow existing = current.get(candidate.getEmpCode());
            if (existing == null) {
                if (!qualified) {
                    try {
                        reviewMapper.insertPendingReview(
                                candidate.getEmpCode(), candidate.getSourceRecordTime(), reviewMinutes);
                    } catch (DataIntegrityViolationException ignored) {
                        // Concurrent dashboard refresh already inserted today's row.
                    }
                }
                continue;
            }
            if (existing.getSourceRecordTime() == null
                    || candidate.getSourceRecordTime().isAfter(existing.getSourceRecordTime())) {
                reviewMapper.updateFromLatestMeasurement(
                        candidate.getEmpCode(), candidate.getSourceRecordTime(), qualified, reviewMinutes);
            }
        }
    }

    private void ensureSchema() {
        if (schemaInitialized) {
            return;
        }
        synchronized (this) {
            if (schemaInitialized) {
                return;
            }
            if (reviewMapper.countSchemaTables() != 1) {
                throw new BusinessException(503,
                        "班前复检表未迁移，请先执行 sql/command_center_operational_status.sql");
            }
            schemaInitialized = true;
        }
    }

    private PreShiftReviewView toView(PreShiftReviewRow row) {
        return new PreShiftReviewView(
                row.getReviewDate() == null ? null : DATE.format(row.getReviewDate()),
                row.getEmpCode(),
                format(row.getSourceRecordTime()),
                format(row.getReviewDeadline()),
                row.getReviewStatus(),
                row.getReviewResult(),
                row.getReviewOwner(),
                format(row.getReviewedAt()),
                Boolean.TRUE.equals(row.getOverdue()),
                row.getRemark());
    }

    private String normalizeListStatus(String value) {
        String normalized = value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase(Locale.ROOT);
        if (!LIST_STATUSES.contains(normalized)) {
            throw new BusinessException(400, "复检状态仅支持 ALL、PENDING、IN_REVIEW、COMPLETED 或 OVERDUE");
        }
        return normalized;
    }

    private String format(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    private int intValue(Number value) {
        return value == null ? 0 : value.intValue();
    }
}
