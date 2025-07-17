package com.lapxpert.backend.chatbox;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient configuration cho AI Chat Service
 * Cấu hình WebClient chuyên dụng với timeout 180 giây cho AI processing operations
 * Sử dụng ReactorClientHttpConnector với HttpClient để có control tốt hơn về timeout
 */
@Configuration
@Slf4j
public class AiChatWebClientConfig {

    @Autowired
    private AiChatConfig aiChatConfig;

    /**
     * WebClient bean specifically configured for AI Chat service
     * Uses ReactorClientHttpConnector with HttpClient for enhanced timeout control
     * Configured with extended timeouts to support 1-2 minute AI processing operations
     */
    @Bean("aiChatWebClient")
    public WebClient aiChatWebClient() {
        log.info("Đang cấu hình WebClient cho AI Chat service với timeout: {}s", 
                aiChatConfig.getRequest().getTimeoutSeconds());

        // Configure HttpClient with comprehensive timeout settings
        HttpClient httpClient = HttpClient.create()
                // Response timeout - overall request timeout
                .responseTimeout(Duration.ofSeconds(aiChatConfig.getRequest().getTimeoutSeconds()))
                // Connection timeout - time to establish connection
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                       aiChatConfig.getRequest().getConnectTimeoutSeconds() * 1000)
                // Configure read and write timeouts
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(
                            aiChatConfig.getRequest().getSocketTimeoutSeconds(), TimeUnit.SECONDS));
                    connection.addHandlerLast(new WriteTimeoutHandler(
                            aiChatConfig.getRequest().getSocketTimeoutSeconds(), TimeUnit.SECONDS));
                });

        // Create WebClient with configured HttpClient
        WebClient webClient = WebClient.builder()
                .baseUrl(aiChatConfig.getService().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .build();

        log.info("WebClient cho AI Chat service đã được cấu hình thành công với timeout: {}s", 
                aiChatConfig.getRequest().getTimeoutSeconds());
        log.info("Base URL: {}, Connect timeout: {}s, Socket timeout: {}s", 
                aiChatConfig.getService().getBaseUrl(),
                aiChatConfig.getRequest().getConnectTimeoutSeconds(),
                aiChatConfig.getRequest().getSocketTimeoutSeconds());

        return webClient;
    }
}
