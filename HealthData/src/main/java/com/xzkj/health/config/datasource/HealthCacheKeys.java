package com.xzkj.health.config.datasource;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class HealthCacheKeys {

    private static final String CACHE_NAMESPACE = "old";

    private HealthCacheKeys() {
    }

    public static String currentSource() {
        return CACHE_NAMESPACE;
    }

    public static String key(Object... parts) {
        String suffix = Arrays.stream(parts)
                .map(String::valueOf)
                .collect(Collectors.joining("|"));
        return currentSource() + "|" + suffix;
    }
}
