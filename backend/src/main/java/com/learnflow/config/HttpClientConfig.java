package com.learnflow.config;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 客户端相关配置。
 *
 * 创建共享连接池；每次调用的读取与整体预算由 AgentHttpClient 按场景覆盖。
 */
@Configuration
@EnableConfigurationProperties(LearnFlowAiAgentProperties.class)
public class HttpClientConfig {

    @Bean
    public OkHttpClient agentOkHttpClient(LearnFlowAiAgentProperties properties) {
        String internalToken = properties.getInternalToken();
        String environment = properties.getEnvironment();
        if ("production".equalsIgnoreCase(environment)
                && (internalToken.startsWith("dev-only-") || internalToken.getBytes(StandardCharsets.UTF_8).length < 32)) {
            throw new IllegalStateException("Production requires a strong injected Agent internal token");
        }
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(properties.getMaxConnections());
        dispatcher.setMaxRequestsPerHost(properties.getMaxConnections());
        return new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(new ConnectionPool(
                        properties.getMaxIdleConnections(),
                        properties.getKeepAlive().toMillis(),
                        TimeUnit.MILLISECONDS
                ))
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getDefaultReadTimeout())
                .writeTimeout(properties.getWriteTimeout())
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("Authorization", "Bearer " + internalToken)
                        .header("X-LearnFlow-Service", "learnflow-backend")
                        .build()))
                .build();
    }
}


