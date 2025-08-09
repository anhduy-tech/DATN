package com.lapxpert.backend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

// Redisson imports for distributed locking
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

import java.time.Duration;



/**
 * Redis configuration for Pub/Sub messaging and distributed locking
 * Provides Redis setup for WebSocket real-time messaging and Redisson distributed locks
 * No longer used for caching - focuses on messaging infrastructure
 */
@Configuration
@Slf4j
public class RedisConfig {

    // Inject centralized ObjectMapper from CommonBeansConfig
    @Autowired
    private ObjectMapper centralObjectMapper;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    // Production Connection Pool Configuration
    @Value("${spring.data.redis.lettuce.pool.max-active:20}")
    private int maxActive;

    @Value("${spring.data.redis.lettuce.pool.max-idle:10}")
    private int maxIdle;

    @Value("${spring.data.redis.lettuce.pool.min-idle:2}")
    private int minIdle;

    @Value("${spring.data.redis.lettuce.pool.max-wait:5000}")
    private long maxWaitMillis;

    @Value("${spring.data.redis.timeout:10000}")
    private long timeoutMillis;

    @Value("${spring.data.redis.lettuce.shutdown-timeout:5000}")
    private long shutdownTimeoutMillis;



    // ==================== CONNECTION FACTORY ====================

    /**
     * Production-optimized Redis connection factory with Lettuce client
     * Provides timeout configuration and resource management
     * Configured for standalone Redis deployment with production settings
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        log.info("Configuring production Redis connection to {}:{} (database: {})", redisHost, redisPort, redisDatabase);

        try {
            // Redis server configuration
            RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
            serverConfig.setHostName(redisHost);
            serverConfig.setPort(redisPort);
            serverConfig.setDatabase(redisDatabase);

            // Set password if provided
            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                serverConfig.setPassword(redisPassword);
                log.info("Redis password authentication enabled");
            } else {
                log.info("Redis password authentication disabled");
            }

            // Production client configuration with optimized timeouts
            LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofMillis(timeoutMillis))
                    .shutdownTimeout(Duration.ofMillis(shutdownTimeoutMillis))
                    .clientResources(clientResources())
                    .build();

            LettuceConnectionFactory factory = new LettuceConnectionFactory(serverConfig, clientConfig);

            // Production settings
            factory.setValidateConnection(true);
            factory.setShareNativeConnection(false); // Avoid read-only issues
            factory.setEagerInitialization(true); // Catch connection issues early

            factory.afterPropertiesSet();

            log.info("Production Redis connection factory configured successfully with timeout={}ms, shutdownTimeout={}ms",
                    timeoutMillis, shutdownTimeoutMillis);
            return factory;

        } catch (Exception e) {
            log.error("Failed to configure Redis connection factory: {}", e.getMessage(), e);
            throw new RuntimeException("Redis configuration failed", e);
        }
    }

    /**
     * Production-optimized client resources for Lettuce
     * Manages I/O threads and connection resources efficiently
     */
    @Bean(destroyMethod = "shutdown")
    public ClientResources clientResources() {
        return DefaultClientResources.builder()
                .ioThreadPoolSize(4) // Optimize for production load
                .computationThreadPoolSize(4)
                .build();
    }

    // ==================== REDISSON CLIENT ====================

    /**
     * Redisson client for distributed locking
     * Configured to use the same Redis instance for messaging and locking
     * Provides distributed locks for inventory race condition prevention
     */
    @Bean
    public RedissonClient redissonClient() {
        log.info("Configuring Redisson client for distributed locking");

        try {
            Config config = new Config();

            // Configure single server setup to match existing Redis configuration
            SingleServerConfig singleServerConfig = config.useSingleServer()
                    .setAddress("redis://" + redisHost + ":" + redisPort)
                    .setDatabase(redisDatabase)
                    .setConnectionMinimumIdleSize(2)
                    .setConnectionPoolSize(10)
                    .setConnectTimeout((int) timeoutMillis)
                    .setTimeout((int) timeoutMillis)
                    .setRetryAttempts(3)
                    .setRetryInterval(1500);

            // Set password if provided
            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                singleServerConfig.setPassword(redisPassword);
                log.info("Redisson password authentication enabled");
            } else {
                log.info("Redisson password authentication disabled");
            }

            // Configure lock watchdog timeout for automatic lock renewal
            config.setLockWatchdogTimeout(30000); // 30 seconds

            RedissonClient redissonClient = Redisson.create(config);

            log.info("Redisson client configured successfully for distributed locking");
            return redissonClient;

        } catch (Exception e) {
            log.error("Failed to configure Redisson client: {}", e.getMessage(), e);
            throw new RuntimeException("Redisson configuration failed", e);
        }
    }

    // ==================== REDIS TEMPLATE ====================

    /**
     * Primary RedisTemplate for String-Object operations
     * Used for Redis Pub/Sub messaging and distributed operations
     * Configured for reliable read/write operations
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("Configuring primary RedisTemplate<String, Object>");

        try {
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactory);

            // Configure serializers
            StringRedisSerializer stringSerializer = new StringRedisSerializer();
            Jackson2JsonRedisSerializer<Object> jsonSerializer = createJsonSerializer();

            // Key serialization
            template.setKeySerializer(stringSerializer);
            template.setHashKeySerializer(stringSerializer);

            // Value serialization
            template.setValueSerializer(jsonSerializer);
            template.setHashValueSerializer(jsonSerializer);

            // Enable transaction support for write operations
            template.setEnableTransactionSupport(false);

            template.afterPropertiesSet();

            log.info("RedisTemplate<String, Object> configured successfully");
            return template;

        } catch (Exception e) {
            log.error("Failed to configure RedisTemplate: {}", e.getMessage(), e);
            throw new RuntimeException("RedisTemplate configuration failed", e);
        }
    }





    // ==================== HELPER METHODS ====================

    /**
     * Test Redis connection and write capability
     * This method can be called manually to verify Redis connectivity
     */
    public boolean testRedisConnection() {
        try {
            RedisConnectionFactory factory = redisConnectionFactory();
            RedisTemplate<String, Object> template = redisTemplate(factory);

            // Test basic connectivity
            String testKey = "lapxpert:health:test";
            String testValue = "connection-test-" + System.currentTimeMillis();

            // Test write operation
            template.opsForValue().set(testKey, testValue, Duration.ofSeconds(10));
            log.info("Redis write test successful");

            // Test read operation
            String retrievedValue = (String) template.opsForValue().get(testKey);
            if (testValue.equals(retrievedValue)) {
                log.info("Redis read test successful");

                // Clean up test key
                template.delete(testKey);
                log.info("Redis connection test completed successfully");
                return true;
            } else {
                log.warn("Redis read test failed: expected '{}', got '{}'", testValue, retrievedValue);
                return false;
            }

        } catch (Exception e) {
            log.error("Redis connection test failed: {}", e.getMessage());
            log.warn("Redis Pub/Sub messaging and distributed locking may be affected");
            return false;
        }
    }

    /**
     * Create simplified Jackson JSON serializer using centralized ObjectMapper.
     * Uses the ObjectMapper from CommonBeansConfig to ensure consistent JSON processing
     * across Redis messaging and other application components.
     */
    private Jackson2JsonRedisSerializer<Object> createJsonSerializer() {
        log.debug("Creating Redis JSON serializer using centralized ObjectMapper");

        // Use centralized ObjectMapper from CommonBeansConfig instead of creating new instance
        // This ensures consistent JSON serialization across Redis messaging and other components
        return new Jackson2JsonRedisSerializer<>(centralObjectMapper, Object.class);
    }
}
