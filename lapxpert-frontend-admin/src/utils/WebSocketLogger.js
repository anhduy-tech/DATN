/**
 * Centralized WebSocket Logger Utility
 *
 * Provides intelligent logging with environment-based conditional logging,
 * three-tier log level system (Critical/Debug/Trace), and throttling mechanisms
 * for high-frequency events. Integrates with existing LapXpert error handling
 * patterns and preserves Vietnamese business terminology.
 *
 * Features:
 * - Environment detection using import.meta.env.DEV pattern
 * - Critical/Debug/Trace log level hierarchy
 * - Intelligent throttling for high-frequency events
 * - Log grouping for related operations
 * - Vietnamese business terminology preservation
 * - Memory-conscious throttle counter management
 */

/**
 * Log levels with corresponding console methods and environment rules
 */
const LogLevel = {
  CRITICAL: {
    name: 'critical',
    emoji: '🚨',
    method: 'error',
    alwaysLog: true, // Always log regardless of environment
    color: '#ff4444'
  },
  DEBUG: {
    name: 'debug',
    emoji: '🔧',
    method: 'log',
    alwaysLog: false, // Only log in development
    color: '#4444ff'
  },
  TRACE: {
    name: 'trace',
    emoji: '📊',
    method: 'debug',
    alwaysLog: false, // Only log in development with throttling
    color: '#888888'
  }
}

/**
 * Default throttling configuration for different event types
 * Based on audit findings from useRealTimeOrderManagement.js, useVoucherMonitoring.js, and OrderCreate.vue
 */
const DefaultThrottleConfig = {
  // High-frequency events (sample 1 in 100) - from audit: 52 high-frequency events identified
  health_monitoring: { rate: 100, counter: 0, lastLogged: 0 },
  heartbeat_check: { rate: 100, counter: 0, lastLogged: 0 },
  server_health_status: { rate: 100, counter: 0, lastLogged: 0 },
  connection_quality_update: { rate: 50, counter: 0, lastLogged: 0 },

  // Message processing throttling (from audit: message processing ~200 logs per hour)
  message_processing: { rate: 50, counter: 0, lastLogged: 0 },
  voucher_message_filtering: { rate: 30, counter: 0, lastLogged: 0 },
  message_type_processing: { rate: 20, counter: 0, lastLogged: 0 },
  subscription_confirmation: { rate: 20, counter: 0, lastLogged: 0 },

  // Medium-frequency events (sample 1 in 10)
  connection_status: { rate: 10, counter: 0, lastLogged: 0 },
  voucher_filtering: { rate: 10, counter: 0, lastLogged: 0 },
  price_update: { rate: 10, counter: 0, lastLogged: 0 },
  network_status_update: { rate: 15, counter: 0, lastLogged: 0 },

  // Customer and order operations (from audit: OrderCreate.vue high-frequency events)
  customer_search: { rate: 10, counter: 0, lastLogged: 0 },
  address_validation: { rate: 10, counter: 0, lastLogged: 0 },
  voucher_validation: { rate: 8, counter: 0, lastLogged: 0 },
  state_synchronization: { rate: 15, counter: 0, lastLogged: 0 },

  // Low-frequency events (sample 1 in 5)
  user_interaction: { rate: 5, counter: 0, lastLogged: 0 },
  form_validation: { rate: 5, counter: 0, lastLogged: 0 },
  websocket_integration_callback: { rate: 5, counter: 0, lastLogged: 0 },

  // Performance monitoring
  performance_monitoring: { rate: 50, counter: 0, lastLogged: 0 },

  // Default throttling for unspecified events
  default: { rate: 10, counter: 0, lastLogged: 0 }
}

/**
 * Centralized WebSocket Logger Class
 */
class WebSocketLogger {
  constructor(componentName, options = {}) {
    this.componentName = componentName
    this.enabledLevels = options.enabledLevels || ['critical', 'debug', 'trace']
    this.throttleConfig = { ...DefaultThrottleConfig, ...options.throttleConfig }
    this.groupStack = []

    // Memory management: cleanup throttle counters periodically
    this.setupCleanupTimer()
  }

  /**
   * Log critical events (always logged regardless of environment)
   * Used for: connection failures, authentication errors, order processing failures
   */
  critical(message, data = null, context = '') {
    if (!this.shouldLog('critical')) return

    const level = LogLevel.CRITICAL
    const formattedMessage = this.formatMessage(level, message, context)

    console[level.method](formattedMessage, data || '')

    // Also log to error tracking if available
    if (window.errorTracker) {
      window.errorTracker.logError(formattedMessage, data)
    }
  }

  /**
   * Log debug events (development only)
   * Used for: connection lifecycle, message processing, business logic flow
   */
  debug(message, data = null, context = '') {
    if (!import.meta.env.DEV || !this.shouldLog('debug')) return

    const level = LogLevel.DEBUG
    const formattedMessage = this.formatMessage(level, message, context)

    console[level.method](formattedMessage, data || '')
  }

  /**
   * Log trace events with throttling (development only, throttled)
   * Used for: health monitoring, detailed processing steps, performance metrics
   */
  trace(message, data = null, context = '', throttleKey = 'default') {
    if (!import.meta.env.DEV || !this.shouldLog('trace')) return
    if (!this.shouldThrottle(throttleKey)) return

    const level = LogLevel.TRACE
    const formattedMessage = this.formatMessage(level, message, context)

    console[level.method](formattedMessage, data || '')
  }

  /**
   * Specialized logging methods for high-frequency events identified in audit
   */

  /**
   * Log health monitoring with aggressive throttling (1 in 100)
   */
  logHealthMonitoring(message, data = null, context = 'health') {
    this.trace(message, data, context, 'health_monitoring')
  }

  /**
   * Log heartbeat checks with aggressive throttling (1 in 100)
   */
  logHeartbeat(message, data = null, context = 'heartbeat') {
    this.trace(message, data, context, 'heartbeat_check')
  }

  /**
   * Log message processing with moderate throttling (1 in 50)
   */
  logMessageProcessing(message, data = null, context = 'processing') {
    this.trace(message, data, context, 'message_processing')
  }

  /**
   * Log voucher message filtering with throttling (1 in 30)
   */
  logVoucherFiltering(message, data = null, context = 'voucher') {
    this.trace(message, data, context, 'voucher_message_filtering')
  }

  /**
   * Log customer search with throttling (1 in 10)
   */
  logCustomerSearch(message, data = null, context = 'customer') {
    this.trace(message, data, context, 'customer_search')
  }

  /**
   * Log address validation with throttling (1 in 10)
   */
  logAddressValidation(message, data = null, context = 'address') {
    this.trace(message, data, context, 'address_validation')
  }

  /**
   * Log connection quality updates with burst detection
   */
  logConnectionQuality(message, data = null, context = 'connection') {
    if (!import.meta.env.DEV || !this.shouldLog('trace')) return
    if (!this.shouldThrottleWithBurstDetection('connection_quality_update', 20)) return

    const level = LogLevel.TRACE
    const formattedMessage = this.formatMessage(level, message, context)
    console[level.method](formattedMessage, data || '')
  }

  /**
   * Log network status updates with aggregation
   */
  logNetworkStatus(message, data = null, context = 'network') {
    if (!import.meta.env.DEV || !this.shouldLog('trace')) return

    const statusKey = data?.status || 'unknown'
    if (!this.shouldThrottleWithAggregation('network_status_update', statusKey)) return

    const level = LogLevel.TRACE
    const formattedMessage = this.formatMessage(level, message, context)
    console[level.method](formattedMessage, data || '')
  }

  /**
   * Start a log group for related operations
   */
  group(title, level = 'debug') {
    if (!this.shouldLogLevel(level)) return

    const emoji = LogLevel[level.toUpperCase()]?.emoji || '📁'
    const groupTitle = `${emoji} [${this.componentName}] ${title}`

    console.group(groupTitle)
    this.groupStack.push(groupTitle)
  }

  /**
   * End the current log group
   */
  groupEnd() {
    if (this.groupStack.length > 0) {
      console.groupEnd()
      this.groupStack.pop()
    }
  }

  /**
   * Log with automatic grouping for related operations
   */
  groupLog(groupTitle, logs, level = 'debug') {
    this.group(groupTitle, level)

    logs.forEach(({ message, data, context }) => {
      this[level](message, data, context)
    })

    this.groupEnd()
  }

  /**
   * Check if logging is enabled for a specific level
   */
  shouldLog(level) {
    return this.enabledLevels.includes(level)
  }

  /**
   * Check if logging should occur based on environment and level
   */
  shouldLogLevel(level) {
    const logLevel = LogLevel[level.toUpperCase()]
    if (!logLevel) return false

    // Critical logs always show
    if (logLevel.alwaysLog) return true

    // Other logs only in development
    return import.meta.env.DEV && this.shouldLog(level)
  }

  /**
   * Enhanced throttling mechanism for high-frequency events
   * Implements sampling-based approach with time-based fallback
   */
  shouldThrottle(throttleKey) {
    const config = this.throttleConfig[throttleKey] || this.throttleConfig.default
    if (!config) return true // Log if no throttle config

    const now = Date.now()
    config.counter++

    // Primary throttling: Log every Nth occurrence based on rate
    if (config.counter >= config.rate) {
      config.counter = 0
      config.lastLogged = now
      return true
    }

    // Time-based fallback: Ensure at least one log every 5 minutes for high-frequency events
    const timeSinceLastLog = now - (config.lastLogged || 0)
    const fiveMinutes = 5 * 60 * 1000

    if (timeSinceLastLog > fiveMinutes && config.rate > 50) {
      config.counter = 0
      config.lastLogged = now
      return true
    }

    return false
  }

  /**
   * Advanced throttling with burst detection and adaptive rates
   */
  shouldThrottleWithBurstDetection(throttleKey, burstThreshold = 10) {
    const config = this.throttleConfig[throttleKey] || this.throttleConfig.default
    if (!config) return true

    const now = Date.now()
    config.counter++

    // Initialize burst tracking if not present
    if (!config.burstWindow) {
      config.burstWindow = { start: now, count: 0 }
    }

    // Reset burst window every minute
    if (now - config.burstWindow.start > 60000) {
      config.burstWindow = { start: now, count: 0 }
    }

    config.burstWindow.count++

    // Detect burst: if more than burstThreshold events in 1 minute, increase throttling
    const isBurst = config.burstWindow.count > burstThreshold
    const effectiveRate = isBurst ? config.rate * 2 : config.rate

    // Apply throttling with adaptive rate
    if (config.counter >= effectiveRate) {
      config.counter = 0
      config.lastLogged = now
      return true
    }

    return false
  }

  /**
   * Throttle with message aggregation for similar events
   */
  shouldThrottleWithAggregation(throttleKey, messageKey = '') {
    const config = this.throttleConfig[throttleKey] || this.throttleConfig.default
    if (!config) return true

    // Initialize aggregation tracking
    if (!config.aggregation) {
      config.aggregation = new Map()
    }

    const now = Date.now()
    const aggregationEntry = config.aggregation.get(messageKey) || { count: 0, firstSeen: now, lastSeen: now }

    aggregationEntry.count++
    aggregationEntry.lastSeen = now
    config.aggregation.set(messageKey, aggregationEntry)

    // Clean up old aggregation entries (older than 5 minutes)
    const fiveMinutesAgo = now - 5 * 60 * 1000
    for (const [key, entry] of config.aggregation.entries()) {
      if (entry.lastSeen < fiveMinutesAgo) {
        config.aggregation.delete(key)
      }
    }

    // Apply standard throttling
    return this.shouldThrottle(throttleKey)
  }

  /**
   * Format log message with component context and Vietnamese terminology preservation
   */
  formatMessage(level, message, context) {
    const timestamp = new Date().toLocaleTimeString('vi-VN')
    const contextStr = context ? ` (${context})` : ''

    return `${level.emoji} [${this.componentName}${contextStr}] ${message}`
  }

  /**
   * Enhanced cleanup mechanism for throttle counters and aggregation data
   */
  setupCleanupTimer() {
    // Reset throttle counters every 5 minutes to prevent overflow
    setInterval(() => {
      Object.keys(this.throttleConfig).forEach(key => {
        const config = this.throttleConfig[key]

        // Reset basic counter
        config.counter = 0

        // Clean up burst detection data older than 10 minutes
        if (config.burstWindow) {
          const tenMinutesAgo = Date.now() - 10 * 60 * 1000
          if (config.burstWindow.start < tenMinutesAgo) {
            delete config.burstWindow
          }
        }

        // Clean up aggregation data older than 10 minutes
        if (config.aggregation) {
          const tenMinutesAgo = Date.now() - 10 * 60 * 1000
          for (const [aggregationKey, entry] of config.aggregation.entries()) {
            if (entry.lastSeen < tenMinutesAgo) {
              config.aggregation.delete(aggregationKey)
            }
          }

          // Remove empty aggregation maps
          if (config.aggregation.size === 0) {
            delete config.aggregation
          }
        }
      })
    }, 5 * 60 * 1000) // 5 minutes
  }

  /**
   * Get throttling statistics for monitoring and debugging
   */
  getThrottlingStats() {
    const stats = {}

    Object.keys(this.throttleConfig).forEach(key => {
      const config = this.throttleConfig[key]
      stats[key] = {
        rate: config.rate,
        counter: config.counter,
        lastLogged: config.lastLogged || 0,
        burstDetected: config.burstWindow ? config.burstWindow.count > 10 : false,
        aggregatedMessages: config.aggregation ? config.aggregation.size : 0
      }
    })

    return stats
  }

  /**
   * Reset throttling for a specific key (useful for testing)
   */
  resetThrottling(throttleKey) {
    const config = this.throttleConfig[throttleKey]
    if (config) {
      config.counter = 0
      config.lastLogged = 0
      delete config.burstWindow
      delete config.aggregation
    }
  }

  /**
   * Temporarily disable throttling for debugging
   */
  disableThrottling(duration = 60000) { // 1 minute default
    const originalConfigs = {}

    // Store original rates and set to 1 (no throttling)
    Object.keys(this.throttleConfig).forEach(key => {
      originalConfigs[key] = this.throttleConfig[key].rate
      this.throttleConfig[key].rate = 1
    })

    // Restore original rates after duration
    setTimeout(() => {
      Object.keys(originalConfigs).forEach(key => {
        if (this.throttleConfig[key]) {
          this.throttleConfig[key].rate = originalConfigs[key]
        }
      })
    }, duration)

    return originalConfigs
  }

  /**
   * Create a logger instance for WebSocket operations with Vietnamese terminology
   */
  static createWebSocketLogger(componentName, options = {}) {
    return new WebSocketLogger(componentName, {
      enabledLevels: ['critical', 'debug', 'trace'],
      throttleConfig: DefaultThrottleConfig,
      ...options
    })
  }

  /**
   * Create a logger instance for voucher operations with Vietnamese business terminology
   */
  static createVoucherLogger(componentName, options = {}) {
    return new WebSocketLogger(componentName, {
      enabledLevels: ['critical', 'debug', 'trace'],
      throttleConfig: {
        ...DefaultThrottleConfig,
        voucher_processing: { rate: 5, counter: 0 },
        phieu_giam_gia_update: { rate: 1, counter: 0 }, // Always log voucher updates
        integration_callback: { rate: 1, counter: 0 }   // Always log integration callbacks
      },
      ...options
    })
  }

  /**
   * Create a logger instance for order operations with Vietnamese business terminology
   */
  static createOrderLogger(componentName, options = {}) {
    return new WebSocketLogger(componentName, {
      enabledLevels: ['critical', 'debug', 'trace'],
      throttleConfig: {
        ...DefaultThrottleConfig,
        order_processing: { rate: 1, counter: 0 },      // Always log order processing
        khach_hang_search: { rate: 10, counter: 0 },    // Throttle customer search
        dia_chi_validation: { rate: 10, counter: 0 },   // Throttle address validation
        websocket_integration: { rate: 5, counter: 0 }  // Moderate WebSocket integration logging
      },
      ...options
    })
  }
}

/**
 * Utility functions for common WebSocket logging patterns
 */
export const WebSocketLoggerUtils = {
  /**
   * Log WebSocket connection lifecycle with Vietnamese terminology
   */
  logConnectionLifecycle(logger, event, details = {}) {
    const vietnameseEvents = {
      connecting: 'Đang kết nối WebSocket',
      connected: 'Kết nối WebSocket thành công',
      disconnected: 'Mất kết nối WebSocket',
      reconnecting: 'Đang thử kết nối lại',
      error: 'Lỗi kết nối WebSocket'
    }

    const message = vietnameseEvents[event] || event

    if (event === 'error') {
      logger.critical(message, details, 'connection')
    } else {
      logger.debug(message, details, 'connection')
    }
  },

  /**
   * Log voucher processing with Vietnamese business terminology
   */
  logVoucherProcessing(logger, action, voucherData = {}) {
    const vietnameseActions = {
      'PHIEU_GIAM_GIA_EXPIRED': 'Phiếu giảm giá đã hết hạn',
      'PHIEU_GIAM_GIA_NEW': 'Phiếu giảm giá mới được tạo',
      'PHIEU_GIAM_GIA_UPDATED': 'Phiếu giảm giá đã được cập nhật',
      'PHIEU_GIAM_GIA_ALTERNATIVES': 'Gợi ý phiếu giảm giá thay thế',
      'PHIEU_GIAM_GIA_BETTER_SUGGESTION': 'Gợi ý phiếu giảm giá tốt hơn'
    }

    const message = vietnameseActions[action] || action
    logger.debug(message, voucherData, 'voucher')
  },

  /**
   * Log order processing with Vietnamese business terminology
   */
  logOrderProcessing(logger, action, orderData = {}) {
    const vietnameseActions = {
      customer_selected: 'Đã chọn khách hàng',
      address_validated: 'Địa chỉ đã được xác thực',
      order_created: 'Đơn hàng đã được tạo',
      payment_processed: 'Thanh toán đã được xử lý'
    }

    const message = vietnameseActions[action] || action
    logger.debug(message, orderData, 'order')
  },

  /**
   * Log performance metrics with throttling
   */
  logPerformance(logger, metric, value, unit = 'ms') {
    logger.trace(`Performance: ${metric} = ${value}${unit}`, null, 'performance', 'performance_monitoring')
  },

  /**
   * Enhanced logging utilities for high-frequency events identified in audit
   */

  /**
   * Log WebSocket health status with aggressive throttling
   */
  logHealthStatus(logger, status, details = {}) {
    logger.logHealthMonitoring(`Health status: ${status}`, details)
  },

  /**
   * Log heartbeat with aggressive throttling
   */
  logHeartbeat(logger, latency = null) {
    const data = latency ? { latency: `${latency}ms` } : null
    logger.logHeartbeat('Heartbeat received', data)
  },

  /**
   * Log message processing with moderate throttling
   */
  logMessageProcessed(logger, messageType, messageId = null) {
    const data = messageId ? { messageType, messageId } : { messageType }
    logger.logMessageProcessing('Message processed', data)
  },

  /**
   * Log voucher message filtering with throttling
   */
  logVoucherMessageFiltering(logger, totalMessages, filteredCount) {
    logger.logVoucherFiltering('Voucher messages filtered', {
      total: totalMessages,
      filtered: filteredCount,
      ratio: `${filteredCount}/${totalMessages}`
    })
  },

  /**
   * Log customer search operations with throttling
   */
  logCustomerSearchOperation(logger, query, resultCount) {
    logger.logCustomerSearch('Customer search performed', {
      query: query?.substring(0, 20) + (query?.length > 20 ? '...' : ''), // Truncate for privacy
      resultCount
    })
  },

  /**
   * Log address validation with throttling
   */
  logAddressValidationOperation(logger, validationType, isValid) {
    logger.logAddressValidation('Address validation performed', {
      type: validationType,
      valid: isValid
    })
  },

  /**
   * Log connection quality updates with burst detection
   */
  logConnectionQualityUpdate(logger, quality, metrics = {}) {
    logger.logConnectionQuality('Connection quality updated', {
      quality,
      ...metrics
    })
  },

  /**
   * Log network status changes with aggregation
   */
  logNetworkStatusChange(logger, oldStatus, newStatus, reason = '') {
    logger.logNetworkStatus('Network status changed', {
      from: oldStatus,
      to: newStatus,
      reason,
      timestamp: new Date().toISOString()
    })
  }
}

/**
 * Integration with existing LapXpert error handling patterns
 */
export const ErrorHandlingIntegration = {
  /**
   * Map WebSocket logger levels to useErrorHandling severity levels
   */
  mapToErrorSeverity(logLevel) {
    const mapping = {
      critical: 'CRITICAL',
      debug: 'INFO',
      trace: 'INFO'
    }
    return mapping[logLevel] || 'INFO'
  },

  /**
   * Create logger that integrates with useErrorHandling composable
   */
  createIntegratedLogger(componentName, errorHandler) {
    const logger = new WebSocketLogger(componentName)

    // Override critical method to also use error handler
    const originalCritical = logger.critical.bind(logger)
    logger.critical = (message, data, context) => {
      originalCritical(message, data, context)

      // Also log to error handler if available
      if (errorHandler && errorHandler.recordError) {
        errorHandler.recordError(
          new Error(message),
          ErrorHandlingIntegration.mapToErrorSeverity('critical'),
          'WEBSOCKET',
          { data, context, component: componentName }
        )
      }
    }

    return logger
  }
}

export default WebSocketLogger
