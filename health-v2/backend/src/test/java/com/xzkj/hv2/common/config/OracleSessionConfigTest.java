package com.xzkj.hv2.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OracleSessionConfigTest {

    @Test
    void withoutSchemaOnlySetsTimeZone() {
        assertThat(OracleSessionConfig.initSql("")).isEqualTo("ALTER SESSION SET TIME_ZONE = 'Asia/Shanghai'");
        assertThat(OracleSessionConfig.initSql(null)).isEqualTo("ALTER SESSION SET TIME_ZONE = 'Asia/Shanghai'");
    }

    @Test
    void withSchemaAlsoSwitchesCurrentSchema() {
        assertThat(OracleSessionConfig.initSql(" health_v2 ")).isEqualTo(
                "BEGIN EXECUTE IMMEDIATE q'[ALTER SESSION SET TIME_ZONE = 'Asia/Shanghai']'; "
                        + "EXECUTE IMMEDIATE 'ALTER SESSION SET CURRENT_SCHEMA = HEALTH_V2'; END;");
    }

    @Test
    void rejectsAnythingThatIsNotAPlainName() {
        assertThatThrownBy(() -> OracleSessionConfig.initSql("HEALTH_V2; DROP TABLE X"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OracleSessionConfig.initSql("1ABC")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OracleSessionConfig.initSql("A'B")).isInstanceOf(IllegalStateException.class);
    }
}
