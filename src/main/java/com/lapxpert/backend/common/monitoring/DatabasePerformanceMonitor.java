package com.lapxpert.backend.common.monitoring;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Database performance monitoring service for tracking query execution times
 * and connection pool metrics. Provides baseline metrics for cache removal impact assessment.
 */
@Component
@Slf4j
public class DatabasePerformanceMonitor {

    private final DataSource dataSource;
    
    // Performance metrics
    private final ConcurrentHashMap<String, AtomicLong> queryExecutionTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> queryExecutionCounts = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> baselineTimestamp = new AtomicReference<>();
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong slowQueryCount = new AtomicLong(0);
    
    // Thresholds
    private static final long SLOW_QUERY_THRESHOLD_MS = 1000; // 1 second
    private static final long VERY_SLOW_QUERY_THRESHOLD_MS = 5000; // 5 seconds

    @Autowired
    public DatabasePerformanceMonitor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Initialize performance baseline metrics
     */
    public void initializeBaseline() {
        baselineTimestamp.set(Instant.now());
        log.info("Database performance baseline initialized at {}", baselineTimestamp.get());
        
        // Log initial connection pool status
        logConnectionPoolStatus();
    }

    /**
     * Record query execution time for performance tracking
     */
    public void recordQueryExecution(String queryType, long executionTimeMs) {
        // Update execution time metrics
        queryExecutionTimes.computeIfAbsent(queryType, k -> new AtomicLong(0))
                          .addAndGet(executionTimeMs);
        queryExecutionCounts.computeIfAbsent(queryType, k -> new AtomicLong(0))
                           .incrementAndGet();
        
        // Update global counters
        totalQueries.incrementAndGet();
        
        // Track slow queries
        if (executionTimeMs > SLOW_QUERY_THRESHOLD_MS) {
            slowQueryCount.incrementAndGet();
            
            if (executionTimeMs > VERY_SLOW_QUERY_THRESHOLD_MS) {
                log.warn("Very slow query detected - Type: {}, Execution time: {}ms", 
                        queryType, executionTimeMs);
            } else {
                log.debug("Slow query detected - Type: {}, Execution time: {}ms", 
                         queryType, executionTimeMs);
            }
        }
    }

    /**
     * Get performance baseline data for comparison
     */
    public PerformanceBaseline getPerformanceBaseline() {
        return new PerformanceBaseline(
            baselineTimestamp.get(),
            totalQueries.get(),
            slowQueryCount.get(),
            calculateAverageQueryTime(),
            getConnectionPoolMetrics()
        );
    }

    /**
     * Alert on slow queries exceeding threshold
     */
    public void alertOnSlowQueries(long thresholdMs) {
        queryExecutionTimes.forEach((queryType, totalTime) -> {
            AtomicLong count = queryExecutionCounts.get(queryType);
            if (count != null && count.get() > 0) {
                long avgTime = totalTime.get() / count.get();
                if (avgTime > thresholdMs) {
                    log.warn("Query type '{}' exceeds threshold: avg {}ms > {}ms (total executions: {})",
                            queryType, avgTime, thresholdMs, count.get());
                }
            }
        });
    }

    /**
     * Scheduled performance monitoring - runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void performanceHealthCheck() {
        try {
            log.debug("Starting database performance health check");
            
            // Log connection pool status
            logConnectionPoolStatus();
            
            // Check for slow queries
            alertOnSlowQueries(SLOW_QUERY_THRESHOLD_MS);
            
            // Log performance summary
            logPerformanceSummary();
            
        } catch (Exception e) {
            log.error("Database performance health check failed", e);
        }
    }

    /**
     * Calculate average query execution time
     */
    private double calculateAverageQueryTime() {
        long totalTime = queryExecutionTimes.values().stream()
                                           .mapToLong(AtomicLong::get)
                                           .sum();
        long totalCount = totalQueries.get();
        return totalCount > 0 ? (double) totalTime / totalCount : 0.0;
    }

    /**
     * Get HikariCP connection pool metrics
     */
    private ConnectionPoolMetrics getConnectionPoolMetrics() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDS = (HikariDataSource) dataSource;
            return new ConnectionPoolMetrics(
                hikariDS.getHikariPoolMXBean().getActiveConnections(),
                hikariDS.getHikariPoolMXBean().getIdleConnections(),
                hikariDS.getHikariPoolMXBean().getTotalConnections(),
                hikariDS.getHikariPoolMXBean().getThreadsAwaitingConnection()
            );
        }
        return new ConnectionPoolMetrics(0, 0, 0, 0);
    }

    /**
     * Log connection pool status
     */
    private void logConnectionPoolStatus() {
        ConnectionPoolMetrics metrics = getConnectionPoolMetrics();
        log.info("Connection Pool Status - Active: {}, Idle: {}, Total: {}, Awaiting: {}",
                metrics.activeConnections(), metrics.idleConnections(), 
                metrics.totalConnections(), metrics.threadsAwaitingConnection());
    }

    /**
     * Log performance summary
     */
    private void logPerformanceSummary() {
        long total = totalQueries.get();
        long slow = slowQueryCount.get();
        double avgTime = calculateAverageQueryTime();
        
        log.info("Performance Summary - Total queries: {}, Slow queries: {}, " +
                "Slow query rate: {:.2f}%, Average execution time: {:.2f}ms",
                total, slow, total > 0 ? (double) slow / total * 100 : 0.0, avgTime);
    }

    /**
     * Performance baseline data structure
     */
    public record PerformanceBaseline(
        Instant timestamp,
        long totalQueries,
        long slowQueries,
        double averageQueryTime,
        ConnectionPoolMetrics connectionPoolMetrics
    ) {}

    /**
     * Connection pool metrics data structure
     */
    public record ConnectionPoolMetrics(
        int activeConnections,
        int idleConnections,
        int totalConnections,
        int threadsAwaitingConnection
    ) {}
}
