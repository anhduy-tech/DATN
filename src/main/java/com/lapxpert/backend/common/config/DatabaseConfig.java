package com.lapxpert.backend.common.config;

import com.lapxpert.backend.common.monitoring.DatabasePerformanceMonitor;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Database configuration with HikariCP optimization and performance monitoring.
 * Optimized for cache removal scenario with increased direct database access.
 */
@Configuration
@Slf4j
public class DatabaseConfig implements ApplicationRunner {

    private final DataSource dataSource;

    @Autowired
    private DatabasePerformanceMonitor performanceMonitor;

    public DatabaseConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (dataSource != null) {
            try {
                // Test connection and log pool configuration
                dataSource.getConnection().close();
                logHikariConfiguration();

                // Initialize performance baseline
                performanceMonitor.initializeBaseline();

                log.info("Database configuration initialized successfully");
            } catch (SQLException e) {
                log.error("Database connection test failed", e);
                throw e;
            }
        }
    }

    /**
     * Log HikariCP configuration for monitoring purposes
     */
    private void logHikariConfiguration() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDS = (HikariDataSource) dataSource;
            log.info("HikariCP Configuration:");
            log.info("  Maximum Pool Size: {}", hikariDS.getMaximumPoolSize());
            log.info("  Minimum Idle: {}", hikariDS.getMinimumIdle());
            log.info("  Connection Timeout: {}ms", hikariDS.getConnectionTimeout());
            log.info("  Validation Timeout: {}ms", hikariDS.getValidationTimeout());
            log.info("  Idle Timeout: {}ms", hikariDS.getIdleTimeout());
            log.info("  Max Lifetime: {}ms", hikariDS.getMaxLifetime());
            log.info("  Leak Detection Threshold: {}ms", hikariDS.getLeakDetectionThreshold());
        }
    }
}
