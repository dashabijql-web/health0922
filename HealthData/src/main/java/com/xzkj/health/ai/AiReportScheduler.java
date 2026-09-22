package com.xzkj.health.ai;

import com.xzkj.health.mapper.AiHealthReportMapper;
import com.xzkj.health.model.AiHealthReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * AI 健康报告定时推送调度器
 *
 * 每周一凌晨 2:00 自动为各部门生成健康周报，保存到报告缓存表并写入日志。
 * 外部通知通道未配置时明确记录并跳过，不伪造发送结果。
 */
@Slf4j
@Component
public class AiReportScheduler {

    @Autowired
    private AiReportMapper aiReportMapper;

    @Autowired
    private AiReportService aiReportService;

    @Autowired
    private AiHealthReportMapper aiHealthReportMapper;

    /**
     * 每周一凌晨 02:00 自动生成各部门健康周报
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 2 * * MON")
    public void generateWeeklyDeptReports() {
        log.info("==== [AI报告定时任务] 开始生成各部门健康周报 ====");
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        try {
            List<String> depts = aiReportMapper.selectDepartmentNames();

            if (depts.isEmpty()) {
                log.warn("[AI报告定时任务] 未查询到任何部门，跳过");
                return;
            }

            log.info("[AI报告定时任务] 共 {} 个部门，开始逐一生成...", depts.size());

            for (String deptName : depts) {
                try {
                    log.info("[AI报告定时任务] 正在生成部门「{}」的健康报告...", deptName);
                    String report = aiReportService.generateDepartmentReport(deptName);
                    // 记录报告摘要（前200字）
                    String summary = report.length() > 200 ? report.substring(0, 200) + "..." : report;
                    log.info("[AI报告定时任务] 部门「{}」报告生成成功（{}字）\n摘要: {}",
                        deptName, report.length(), summary);

                    persistDepartmentReport(deptName, report);
                    log.info("[AI报告定时任务] 部门「{}」报告已保存到 ai_health_report", deptName);
                    log.info("[AI报告定时任务] 外部通知通道未配置，跳过企业微信/钉钉推送");

                    // 避免频繁调用 DeepSeek API，每个部门间隔 10 秒
                    Thread.sleep(10_000);
                } catch (Exception e) {
                    log.error("[AI报告定时任务] 部门「{}」报告生成失败: {}", deptName, e.getMessage());
                }
            }

            log.info("==== [AI报告定时任务] 全部 {} 个部门报告生成完毕（{}）====", depts.size(), today);
        } catch (Exception e) {
            log.error("[AI报告定时任务] 任务执行失败", e);
        }
    }

    /**
     * 每天 08:00 生成当日高风险员工预警摘要；外部通知通道未配置时仅记录日志。
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void generateDailyHighRiskSummary() {
        log.info("==== [AI报告定时任务] 生成今日高风险预警摘要 ====");
        try {
            List<AiReportHighRiskSummaryRow> highRisk = aiReportMapper.selectDailyHighRiskSummaries();

            if (highRisk.isEmpty()) {
                log.info("[AI报告定时任务] 今日无高风险未处理预警 ✅");
            } else {
                log.warn("[AI报告定时任务] 今日高风险未处理预警 {} 条 ⚠️：{}", highRisk.size(), highRisk);
                log.info("[AI报告定时任务] 外部通知通道未配置，跳过管理员推送");
            }
        } catch (Exception e) {
            log.error("[AI报告定时任务] 高风险摘要生成失败", e);
        }
    }

    private void persistDepartmentReport(String deptName, String report) {
        String cacheKey = "DEPT_" + deptName;
        aiHealthReportMapper.deleteByEmpCode(cacheKey);
        AiHealthReport entity = new AiHealthReport();
        entity.setEmpCode(cacheKey);
        entity.setEmpName(deptName);
        entity.setReportContent(report);
        entity.setGenerateTime(LocalDateTime.now());
        entity.setExpiresAt(LocalDateTime.now().plusDays(7));
        aiHealthReportMapper.insert(entity);
    }
}
