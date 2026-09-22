package com.xzkj.health.config.datasource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Component
public class HealthAsyncQueryExecutor {

    private final Executor executor;

    public HealthAsyncQueryExecutor(@Qualifier("healthQueryTaskExecutor") Executor executor) {
        this.executor = executor;
    }

    public <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }
}
