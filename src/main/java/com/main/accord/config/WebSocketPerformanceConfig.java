package com.main.accord.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class WebSocketPerformanceConfig {

    @Bean(name = "taskExecutor")
    @Primary  // This makes it the default
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "clientInboundChannelExecutor")
    public TaskExecutor clientInboundChannelExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("client-inbound-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "clientOutboundChannelExecutor")
    public TaskExecutor clientOutboundChannelExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("client-outbound-");
        executor.initialize();
        return executor;
    }
}