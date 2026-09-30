package com.xzkj.hv2.auth;

import java.util.HashMap;
import java.util.Map;

/** 测试用：内存里的失败计数器（Redis 版本的过期逻辑由 RedisLoginFailureCounterTest 验证）。 */
class InMemoryFailureCounter implements LoginFailureCounter {

    final Map<String, Long> counts = new HashMap<>();

    @Override
    public long current(String username) {
        return counts.getOrDefault(username, 0L);
    }

    @Override
    public long increment(String username) {
        return counts.merge(username, 1L, Long::sum);
    }

    @Override
    public void clear(String username) {
        counts.remove(username);
    }
}
