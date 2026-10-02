package com.xzkj.hv2ops;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * 建表迁移（docs/04 第七节）：脚本打包在 jar 里，和后端 backend/src/main/resources/db/migration 是同一份。
 * 不允许 clean（清空），连错库也删不了东西。只能用建表账号（HEALTH_V2）执行，应用账号没有建表权限。
 */
final class Migrate {

    private Migrate() {
    }

    static int run(String goal, Db db) {
        Flyway flyway = Flyway.configure()
                .dataSource(db.url(), db.user(), db.password())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .load();
        try {
            switch (goal) {
                case "migrate" -> {
                    MigrateResult r = flyway.migrate();
                    System.out.println("执行了 " + r.migrationsExecuted + " 个脚本，当前版本 "
                            + (r.targetSchemaVersion == null ? "（无变化）" : r.targetSchemaVersion));
                    System.out.println("迁移后记得重新执行授权脚本 tools/deploy/oracle/grant-app.sql（docs/11）");
                }
                case "validate" -> {
                    flyway.validate();
                    System.out.println("校验通过：库里执行过的脚本和 jar 里的一致");
                }
                default -> printInfo(flyway);
            }
            return 0;
        } catch (FlywayException e) {
            throw new OpsException(e.getMessage());
        }
    }

    private static void printInfo(Flyway flyway) {
        System.out.printf("%-8s %-28s %-10s %s%n", "版本", "说明", "状态", "执行时间");
        for (MigrationInfo i : flyway.info().all()) {
            System.out.printf("%-8s %-28s %-10s %s%n", i.getVersion(), i.getDescription(), i.getState().getDisplayName(),
                    i.getInstalledOn() == null ? "" : i.getInstalledOn());
        }
    }
}
