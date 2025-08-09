import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useRealTimeOrderManagement } from './useRealTimeOrderManagement.js'
import { resolveStateConflict, RESOLUTION_STRATEGIES } from '../utils/StateConflictResolver.js'

/**
 * useRealTimeSync - Real-Time State Synchronization Composable
 * Provides cross-component state synchronization with event-driven updates,
 * state validation, and seamless integration with WebSocket infrastructure
 * Follows LapXpert patterns and Vietnamese business terminology
 */

/**
 * Create real-time sync composable
 * @param {Object} options - Configuration options
 * @param {String} options.entityName - Vietnamese entity name (e.g., 'hoaDon', 'sanPham')
 * @param {String} options.storeKey - Unique store identifier
 * @param {Function} options.validateState - State validation function
 * @param {Function} options.mergeStrategy - State merge strategy function
 * @param {Boolean} options.enablePersistence - Enable state persistence
 * @param {Boolean} options.enableCrossTab - Enable cross-tab synchronization
 * @returns {Object} Real-time sync composable
 */
export function useRealTimeSync(options = {}) {
  const {
    entityName = 'dữ liệu',
    storeKey = 'default',
    validateState = null,
    mergeStrategy = null,
    enablePersistence = true,
    enableCrossTab = true,
    enableWebSocketIntegration = true,
    enableOptimisticUpdates = true,
    conflictResolutionStrategy = RESOLUTION_STRATEGIES.LAST_WRITE_WINS,
    autoRefreshDelay = 100
  } = options

  const toast = useToast()

  // WebSocket integration for real-time updates
  const webSocketManager = enableWebSocketIntegration ? useRealTimeOrderManagement() : null
  const webSocketMessageHandler = ref(null)

  // Core state management
  const syncState = ref({
    isConnected: false,
    lastSyncTime: null,
    syncVersion: 0,
    pendingChanges: new Map(),
    conflictQueue: [],
    syncErrors: [],
    webSocketConnected: false,
    optimisticUpdates: new Map(),
    rollbackQueue: []
  })

  // Event system for component reactivity
  const eventListeners = ref(new Map())
  const stateChangeEvents = ref([])
  const maxEventHistory = 100

  // Cross-tab synchronization
  const crossTabChannel = ref(null)
  const tabId = ref(`tab_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`)

  // State persistence
  const persistenceKey = `lapxpert_sync_${storeKey}`
  const persistedState = ref(null)

  // Real-time sync tracking (cache-free architecture)
  const realTimeSyncState = ref({
    lastSyncTime: null,
    syncedScopes: new Set(),
    pendingSync: false,
    syncVersion: '1.0.0'
  })

  // Performance monitoring
  const syncMetrics = ref({
    totalSyncs: 0,
    successfulSyncs: 0,
    failedSyncs: 0,
    averageSyncTime: 0,
    lastSyncDuration: 0,
    realTimeUpdates: 0,
    dataRefreshes: 0
  })

  /**
   * Initialize WebSocket integration for real-time state updates
   * Vietnamese Business Context: Khởi tạo tích hợp WebSocket cho cập nhật trạng thái thời gian thực
   */
  function initializeWebSocketIntegration() {
    if (!enableWebSocketIntegration || !webSocketManager) {
      console.warn('⚠️ WebSocket integration not enabled or available')
      return
    }

    try {
      // Watch WebSocket connection status
      watch(() => webSocketManager.isConnected.value, (connected) => {
        syncState.value.webSocketConnected = connected
        console.log(`🔌 WebSocket connection status changed: ${connected ? 'Connected' : 'Disconnected'}`)

        if (connected) {
          // Request fresh state sync when WebSocket reconnects
          requestSyncFromOtherTabs()
        }
      })

      // Watch for incoming WebSocket messages
      watch(() => webSocketManager.messageHistory.value, (newHistory, oldHistory) => {
        if (!newHistory || newHistory.length === 0) return

        // Process new messages
        const newMessages = newHistory.slice(0, newHistory.length - (oldHistory?.length || 0))
        newMessages.forEach(handleWebSocketMessage)
      }, { deep: true })

      // Set up message handler
      webSocketMessageHandler.value = handleWebSocketMessage

      console.log(`📡 WebSocket integration initialized for ${entityName} (${storeKey})`)
    } catch (error) {
      console.error('❌ Failed to initialize WebSocket integration:', error)
    }
  }

  /**
   * Create a serializable copy of state for cross-tab communication
   * Removes non-cloneable properties like functions, DOM elements, and circular references
   * @param {Object} state - State object to serialize
   * @returns {Object} Serializable state object
   */
  function createSerializableState(state) {
    if (!state || typeof state !== 'object') {
      return state
    }

    try {
      // Use JSON.parse(JSON.stringify()) to create a deep copy and remove non-cloneable properties
      // This will automatically exclude functions, undefined values, symbols, and circular references
      return JSON.parse(JSON.stringify(state))
    } catch (error) {
      console.warn('⚠️ Failed to serialize state, using fallback approach:', error)

      // Fallback: manually create a serializable object
      const serializable = {}

      for (const [key, value] of Object.entries(state)) {
        try {
          // Skip functions, symbols, and undefined values
          if (typeof value === 'function' || typeof value === 'symbol' || value === undefined) {
            continue
          }

          // Handle arrays
          if (Array.isArray(value)) {
            serializable[key] = value.map(item => createSerializableState(item))
            continue
          }

          // Handle objects (but avoid circular references)
          if (value && typeof value === 'object') {
            // Simple check for Vue reactive objects
            if (value.__v_isRef || value.__v_isReactive) {
              // Extract the raw value from Vue reactive objects
              serializable[key] = createSerializableState(value.value || value)
            } else {
              serializable[key] = createSerializableState(value)
            }
            continue
          }

          // Handle primitive values
          serializable[key] = value
        } catch (itemError) {
          console.warn(`⚠️ Skipping non-serializable property '${key}':`, itemError)
        }
      }

      return serializable
    }
  }

  /**
   * Initialize cross-tab synchronization
   */
  function initializeCrossTabSync() {
    if (!enableCrossTab || !window.BroadcastChannel) {
      console.warn('⚠️ Cross-tab synchronization not supported')
      return
    }

    try {
      crossTabChannel.value = new BroadcastChannel(`lapxpert_sync_${storeKey}`)

      crossTabChannel.value.addEventListener('message', handleCrossTabMessage)

      console.log(`📡 Cross-tab sync initialized for ${entityName} (${storeKey})`)
    } catch (error) {
      console.error('❌ Failed to initialize cross-tab sync:', error)
    }
  }

  /**
   * Handle WebSocket messages for real-time state updates
   * Vietnamese Business Context: Xử lý tin nhắn WebSocket cho cập nhật trạng thái thời gian thực
   * @param {Object} message - WebSocket message
   */
  function handleWebSocketMessage(message) {
    if (!message || !message.type) return

    try {
      console.log('📨 Processing WebSocket message for state sync:', message)

      switch (message.type) {
        case 'STATE_UPDATE':
          handleStateUpdateFromWebSocket(message)
          break
        case 'PRICE_UPDATE':
          handlePriceUpdateFromWebSocket(message)
          break
        case 'VOUCHER_UPDATE':
          handleVoucherUpdateFromWebSocket(message)
          break
        case 'ORDER_UPDATE':
          handleOrderUpdateFromWebSocket(message)
          break

        // Real-time data update message types
        case 'DATA_LIST_UPDATE':
        case 'SAN_PHAM_LIST_UPDATE':
        case 'PHIEU_GIAM_GIA_LIST_UPDATE':
        case 'DOT_GIAM_GIA_LIST_UPDATE':
        case 'HOA_DON_LIST_UPDATE':
        case 'NGUOI_DUNG_LIST_UPDATE':
          handleDataListUpdateFromWebSocket(message)
          break
        case 'SEARCH_INVALIDATION':
          handleSearchInvalidationFromWebSocket(message)
          break
        case 'CART_UPDATE':
          handleCartUpdateFromWebSocket(message)
          break
        case 'CATEGORY_UPDATE':
          handleCategoryUpdateFromWebSocket(message)
          break
        case 'SYSTEM_CONFIG_UPDATE':
          handleSystemConfigUpdateFromWebSocket(message)
          break
        case 'POPULAR_PRODUCTS_UPDATE':
          handlePopularProductsUpdateFromWebSocket(message)
          break
        case 'USER_SESSION_UPDATE':
          handleUserSessionUpdateFromWebSocket(message)
          break
        case 'PRODUCT_RATINGS_UPDATE':
          handleProductRatingsUpdateFromWebSocket(message)
          break
        case 'SHIPPING_FEES_UPDATE':
          handleShippingFeesUpdateFromWebSocket(message)
          break
        case 'BATCH_UPDATE':
          handleBatchUpdateFromWebSocket(message)
          break
        case 'THROTTLED_UPDATE':
          handleThrottledUpdateFromWebSocket(message)
          break

        default:
          // Check if message topic matches our entity
          if (message.topic && isRelevantTopic(message.topic)) {
            handleGenericWebSocketUpdate(message)
          }
      }
    } catch (error) {
      console.error('❌ Error handling WebSocket message:', error)
      syncState.value.syncErrors.push({
        type: 'WEBSOCKET_MESSAGE_ERROR',
        error: error.message,
        message,
        timestamp: new Date().toISOString()
      })
    }
  }

  /**
   * Handle cross-tab messages
   * @param {MessageEvent} event - Broadcast channel message event
   */
  function handleCrossTabMessage(event) {
    // Check if event.data exists to prevent destructuring null/undefined
    if (!event.data) {
      console.warn('⚠️ Received cross-tab message with null/undefined data')
      return
    }

    const { type, data, sourceTabId, timestamp } = event.data

    // Ignore messages from same tab
    if (sourceTabId === tabId.value) return

    try {
      switch (type) {
        case 'STATE_SYNC':
          handleRemoteStateSync(data, timestamp)
          break
        case 'STATE_CHANGE':
          handleRemoteStateChange(data, timestamp)
          break
        case 'CONFLICT_RESOLUTION':
          handleRemoteConflictResolution(data, timestamp)
          break
        case 'SYNC_REQUEST':
          handleSyncRequest(sourceTabId)
          break
        case 'WEBSOCKET_UPDATE':
          handleWebSocketUpdateFromCrossTab(data, timestamp)
          break
        default:
          console.warn(`⚠️ Unknown cross-tab message type: ${type}`)
      }
    } catch (error) {
      console.error('❌ Error handling cross-tab message:', error)
    }
  }

  /**
   * Broadcast message to other tabs
   * @param {String} type - Message type
   * @param {*} data - Message data
   */
  function broadcastToOtherTabs(type, data) {
    if (!crossTabChannel.value) return

    try {
      const message = {
        type,
        data,
        sourceTabId: tabId.value,
        timestamp: new Date().toISOString(),
        entityName,
        storeKey
      }

      // Validate that the message can be cloned before sending
      // This helps catch DataCloneError issues early
      if (data && typeof data === 'object') {
        try {
          // Test cloneability by attempting to serialize
          JSON.stringify(data)
        } catch (serializeError) {
          console.warn(`⚠️ Data for ${type} may not be cloneable, attempting to serialize:`, serializeError)
          message.data = createSerializableState(data)
        }
      }

      crossTabChannel.value.postMessage(message)
    } catch (error) {
      console.error(`❌ Failed to broadcast ${type}:`, error)

      // If it's a DataCloneError, try to provide more helpful information
      if (error.name === 'DataCloneError') {
        console.error('💡 DataCloneError suggests the data contains non-cloneable properties (functions, DOM elements, circular references)')
        console.error('📊 Problematic data:', data)
      }
    }
  }

  /**
   * Register event listener for state changes
   * @param {String} eventType - Event type
   * @param {Function} callback - Event callback function
   * @returns {Function} Unsubscribe function
   */
  function addEventListener(eventType, callback) {
    if (!eventListeners.value.has(eventType)) {
      eventListeners.value.set(eventType, new Set())
    }

    eventListeners.value.get(eventType).add(callback)

    // Return unsubscribe function
    return () => {
      const listeners = eventListeners.value.get(eventType)
      if (listeners) {
        listeners.delete(callback)
        if (listeners.size === 0) {
          eventListeners.value.delete(eventType)
        }
      }
    }
  }

  /**
   * Emit state change event
   * @param {String} eventType - Event type
   * @param {*} data - Event data
   */
  function emitStateChangeEvent(eventType, data) {
    const event = {
      type: eventType,
      data,
      timestamp: new Date().toISOString(),
      tabId: tabId.value,
      entityName,
      storeKey
    }

    // Add to event history
    stateChangeEvents.value.unshift(event)
    if (stateChangeEvents.value.length > maxEventHistory) {
      stateChangeEvents.value = stateChangeEvents.value.slice(0, maxEventHistory)
    }

    // Notify local listeners
    const listeners = eventListeners.value.get(eventType)
    if (listeners) {
      listeners.forEach(callback => {
        try {
          callback(event)
        } catch (error) {
          console.error(`❌ Error in event listener for ${eventType}:`, error)
        }
      })
    }

    // Broadcast to other tabs
    broadcastToOtherTabs('STATE_CHANGE', event)
  }

  /**
   * Validate state using provided validation function
   * @param {*} state - State to validate
   * @returns {Object} Validation result
   */
  function validateStateData(state) {
    if (!validateState) {
      return { isValid: true, errors: [] }
    }

    try {
      const result = validateState(state)
      return typeof result === 'boolean'
        ? { isValid: result, errors: [] }
        : result
    } catch (error) {
      return {
        isValid: false,
        errors: [`Validation error: ${error.message}`]
      }
    }
  }

  /**
   * Merge states using provided merge strategy
   * @param {*} currentState - Current state
   * @param {*} incomingState - Incoming state
   * @returns {*} Merged state
   */
  function mergeStates(currentState, incomingState) {
    if (!mergeStrategy) {
      // Default merge strategy: last-write-wins
      return incomingState
    }

    try {
      return mergeStrategy(currentState, incomingState)
    } catch (error) {
      console.error('❌ Error in merge strategy:', error)
      return incomingState // Fallback to last-write-wins
    }
  }

  /**
   * Handle remote state synchronization
   * @param {*} remoteState - Remote state data
   * @param {String} timestamp - Sync timestamp
   */
  function handleRemoteStateSync(remoteState, timestamp) {
    const validation = validateStateData(remoteState)

    if (!validation.isValid) {
      console.warn('⚠️ Invalid remote state received:', validation.errors)
      return
    }

    emitStateChangeEvent('REMOTE_SYNC_RECEIVED', {
      remoteState,
      timestamp,
      validation
    })
  }

  /**
   * Handle remote state change
   * @param {*} changeData - State change data
   * @param {String} timestamp - Change timestamp
   */
  function handleRemoteStateChange(changeData, timestamp) {
    emitStateChangeEvent('REMOTE_CHANGE_RECEIVED', {
      changeData,
      timestamp
    })
  }

  /**
   * Handle remote conflict resolution
   * @param {*} resolutionData - Conflict resolution data
   * @param {String} timestamp - Resolution timestamp
   */
  function handleRemoteConflictResolution(resolutionData, timestamp) {
    emitStateChangeEvent('CONFLICT_RESOLVED', {
      resolutionData,
      timestamp
    })
  }

  /**
   * Handle sync request from other tab
   * @param {String} _requestingTabId - ID of requesting tab
   */
  function handleSyncRequest(_requestingTabId) {
    // Respond with current state if we have it
    if (persistedState.value) {
      try {
        // Create a serializable copy of the state to avoid DataCloneError
        const serializableState = createSerializableState(persistedState.value)
        broadcastToOtherTabs('STATE_SYNC', serializableState)
      } catch (error) {
        console.error('❌ Failed to serialize state for cross-tab sync:', error)
      }
    }
  }



  /**
   * Handle state update from WebSocket messages
   * Vietnamese Business Context: Xử lý cập nhật trạng thái từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with state update
   */
  function handleStateUpdateFromWebSocket(message) {
    const stateData = message.payload || message.data
    if (!stateData) return

    // Apply optimistic update if enabled
    if (enableOptimisticUpdates) {
      applyOptimisticUpdate(stateData, message)
    }

    // Emit state change event
    emitStateChangeEvent('WEBSOCKET_STATE_UPDATE', {
      stateData,
      message,
      timestamp: new Date().toISOString()
    })

    // Broadcast to other tabs
    broadcastToOtherTabs('WEBSOCKET_UPDATE', {
      type: 'STATE_UPDATE',
      data: stateData,
      source: 'websocket'
    })
  }

  /**
   * Handle price update from WebSocket messages
   * Vietnamese Business Context: Xử lý cập nhật giá từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with price update
   */
  function handlePriceUpdateFromWebSocket(message) {
    if (!isRelevantTopic(message.topic, 'gia-san-pham')) return

    const priceData = message.payload || message.data

    // Emit price update event
    emitStateChangeEvent('WEBSOCKET_PRICE_UPDATE', {
      priceData,
      topic: message.topic,
      timestamp: new Date().toISOString()
    })

    // Trigger real-time data sync for pricing data
    emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
      scope: 'PRICING_DATA',
      entityId: priceData?.variantId || priceData?.sanPhamChiTietId,
      requiresRefresh: true,
      timestamp: new Date().toISOString()
    })
  }

  /**
   * Handle voucher update from WebSocket messages
   * Vietnamese Business Context: Xử lý cập nhật voucher từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with voucher update
   */
  function handleVoucherUpdateFromWebSocket(message) {
    if (!isRelevantTopic(message.topic, 'phieu-giam-gia') &&
        !isRelevantTopic(message.topic, 'dot-giam-gia')) return

    const voucherData = message.payload || message.data

    // Emit voucher update event
    emitStateChangeEvent('WEBSOCKET_VOUCHER_UPDATE', {
      voucherData,
      topic: message.topic,
      timestamp: new Date().toISOString()
    })

    // Trigger real-time data sync for voucher data
    emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
      scope: 'VOUCHER_DATA',
      entityId: voucherData?.id || voucherData?.voucherId,
      requiresRefresh: true,
      timestamp: new Date().toISOString()
    })
  }

  /**
   * Handle order update from WebSocket messages
   * Vietnamese Business Context: Xử lý cập nhật đơn hàng từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with order update
   */
  function handleOrderUpdateFromWebSocket(message) {
    if (!isRelevantTopic(message.topic, 'hoa-don')) return

    const orderData = message.payload || message.data

    // Emit order update event
    emitStateChangeEvent('WEBSOCKET_ORDER_UPDATE', {
      orderData,
      topic: message.topic,
      timestamp: new Date().toISOString()
    })

    // Trigger real-time data sync for order data
    emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
      scope: 'ORDER_DATA',
      entityId: orderData?.id || orderData?.hoaDonId,
      requiresRefresh: true,
      timestamp: new Date().toISOString()
    })
  }

  /**
   * Handle generic WebSocket update for relevant topics
   * Vietnamese Business Context: Xử lý cập nhật WebSocket chung cho các chủ đề liên quan
   * @param {Object} message - WebSocket message
   */
  function handleGenericWebSocketUpdate(message) {
    // Emit generic update event
    emitStateChangeEvent('WEBSOCKET_GENERIC_UPDATE', {
      message,
      timestamp: new Date().toISOString()
    })

    // Trigger real-time data sync based on topic
    const scope = getDataScopeFromTopic(message.topic)
    if (scope) {
      emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
        scope,
        requiresRefresh: true,
        timestamp: new Date().toISOString()
      })
    }
  }

  // ==================== ENHANCED REAL-TIME DATA HANDLERS ====================
  // Vietnamese Business Context: Bộ xử lý dữ liệu thời gian thực nâng cao

  /**
   * Handle data list update from WebSocket messages
   * Vietnamese Business Context: Xử lý cập nhật danh sách dữ liệu từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with data list update
   */
  function handleDataListUpdateFromWebSocket(message) {
    const payload = message.payload || message.data || message
    const { dataType, action, data, metadata, sequenceNumber } = payload

    console.log(`📋 Processing data list update: ${dataType} (${action})`, { sequenceNumber, metadata })

    // Emit data list update event
    emitStateChangeEvent('WEBSOCKET_DATA_LIST_UPDATE', {
      dataType,
      action,
      data,
      metadata,
      sequenceNumber,
      topic: message.topic,
      timestamp: new Date().toISOString()
    })

    // Trigger real-time data sync for the specific data type
    emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
      scope: dataType,
      reason: `Data list ${action.toLowerCase()}`,
      timestamp: new Date().toISOString(),
      metadata: { action, sequenceNumber }
    })

    // Broadcast to other tabs
    broadcastToOtherTabs('WEBSOCKET_UPDATE', {
      type: 'DATA_LIST_UPDATE',
      data: payload,
      source: 'websocket'
    })
  }

  /**
   * Handle search invalidation from WebSocket messages
   * Vietnamese Business Context: Xử lý vô hiệu hóa tìm kiếm từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with search invalidation
   */
  function handleSearchInvalidationFromWebSocket(message) {
    const payload = message.payload || message.data || message
    const { searchQuery, searchType, reason } = payload

    console.log(`🔍 Processing search invalidation: ${searchQuery} (${searchType})`)

    // Emit search invalidation event
    emitStateChangeEvent('WEBSOCKET_SEARCH_INVALIDATION', {
      searchQuery,
      searchType,
      reason,
      topic: message.topic,
      timestamp: new Date().toISOString()
    })

    // Trigger real-time data sync for search results
    emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
      scope: 'search_results',
      reason: reason || 'Search data updated',
      timestamp: new Date().toISOString(),
      metadata: { searchQuery, searchType }
    })
  }

  /**
   * Handle cart update from WebSocket messages
   * Vietnamese Business Context: Xử lý cập nhật giỏ hàng từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with cart update
   */
  function handleCartUpdateFromWebSocket(message) {
    const payload = message.payload || message.data || message
    const { userId, action, cartData } = payload

    console.log(`🛒 Processing cart update for user ${userId}: ${action}`)

    // Emit cart update event
    emitStateChangeEvent('WEBSOCKET_CART_UPDATE', {
      userId,
      action,
      cartData,
      topic: message.topic,
      timestamp: new Date().toISOString()
    })

    // Trigger real-time data sync for cart data
    emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
      scope: 'cart_data',
      reason: `Cart ${action.toLowerCase()}`,
      timestamp: new Date().toISOString(),
      metadata: { userId, action }
    })
  }

  /**
   * Handle category update from WebSocket messages
   * Vietnamese Business Context: Xử lý cập nhật danh mục từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with category update
   */
  function handleCategoryUpdateFromWebSocket(message) {
    const payload = message.payload || message.data || message
    const { categoryType, action, categoryData } = payload

    console.log(`📂 Processing category update: ${categoryType} (${action})`)

    // Emit category update event
    emitStateChangeEvent('WEBSOCKET_CATEGORY_UPDATE', {
      categoryType,
      action,
      categoryData,
      topic: message.topic,
      timestamp: new Date().toISOString()
    })

    // Trigger real-time data sync for categories
    emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
      scope: 'categories',
      reason: `Category ${action.toLowerCase()}`,
      timestamp: new Date().toISOString(),
      metadata: { categoryType, action }
    })
  }

  /**
   * Handle system config update from WebSocket messages
   * Vietnamese Business Context: Xử lý cập nhật cấu hình hệ thống từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with system config update
   */
  function handleSystemConfigUpdateFromWebSocket(message) {
    const payload = message.payload || message.data || message
    const { configKey, configValue, scope } = payload

    console.log(`⚙️ Processing system config update: ${configKey} (${scope})`)

    // Emit system config update event
    emitStateChangeEvent('WEBSOCKET_SYSTEM_CONFIG_UPDATE', {
      configKey,
      configValue,
      scope,
      topic: message.topic,
      timestamp: new Date().toISOString()
    })

    // Trigger real-time data sync for system config
    emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
      scope: 'system_config',
      reason: 'System configuration updated',
      timestamp: new Date().toISOString(),
      metadata: { configKey, scope }
    })
  }

  /**
   * Handle popular products update from WebSocket messages
   * Vietnamese Business Context: Xử lý cập nhật sản phẩm phổ biến từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with popular products update
   */
  function handlePopularProductsUpdateFromWebSocket(message) {
    const payload = message.payload || message.data || message
    const { timeframe, popularProducts } = payload

    console.log(`🔥 Processing popular products update: ${timeframe}`)

    // Emit popular products update event
    emitStateChangeEvent('WEBSOCKET_POPULAR_PRODUCTS_UPDATE', {
      timeframe,
      popularProducts,
      topic: message.topic,
      timestamp: new Date().toISOString()
    })

    // Trigger real-time data sync for popular products
    emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
      scope: 'popular_products',
      reason: 'Popular products updated',
      timestamp: new Date().toISOString(),
      metadata: { timeframe }
    })
  }

  /**
   * Handle user session update from WebSocket messages
   * Vietnamese Business Context: Xử lý cập nhật phiên người dùng từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with user session update
   */
  function handleUserSessionUpdateFromWebSocket(message) {
    const payload = message.payload || message.data || message
    const { userId, sessionData, action } = payload

    console.log(`👤 Processing user session update for ${userId}: ${action}`)

    // Emit user session update event
    emitStateChangeEvent('WEBSOCKET_USER_SESSION_UPDATE', {
      userId,
      sessionData,
      action,
      topic: message.topic,
      timestamp: new Date().toISOString()
    })

    // Trigger real-time data sync for user sessions
    emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
      scope: 'user_sessions',
      reason: `User session ${action.toLowerCase()}`,
      timestamp: new Date().toISOString(),
      metadata: { userId, action }
    })
  }

  /**
   * Handle product ratings update from WebSocket messages
   * Vietnamese Business Context: Xử lý cập nhật đánh giá sản phẩm từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with product ratings update
   */
  function handleProductRatingsUpdateFromWebSocket(message) {
    const payload = message.payload || message.data || message
    const { productId, ratingsData, action } = payload

    console.log(`⭐ Processing product ratings update for ${productId}: ${action}`)

    // Emit product ratings update event
    emitStateChangeEvent('WEBSOCKET_PRODUCT_RATINGS_UPDATE', {
      productId,
      ratingsData,
      action,
      topic: message.topic,
      timestamp: new Date().toISOString()
    })

    // Trigger real-time data sync for product ratings
    emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
      scope: 'product_ratings',
      reason: `Product ratings ${action.toLowerCase()}`,
      timestamp: new Date().toISOString(),
      metadata: { productId, action }
    })
  }

  /**
   * Handle shipping fees update from WebSocket messages
   * Vietnamese Business Context: Xử lý cập nhật phí vận chuyển từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with shipping fees update
   */
  function handleShippingFeesUpdateFromWebSocket(message) {
    const payload = message.payload || message.data || message
    const { addressData, shippingData, reason } = payload

    console.log(`🚚 Processing shipping fees update: ${reason}`)

    // Emit shipping fees update event
    emitStateChangeEvent('WEBSOCKET_SHIPPING_FEES_UPDATE', {
      addressData,
      shippingData,
      reason,
      topic: message.topic,
      timestamp: new Date().toISOString()
    })

    // Trigger real-time data sync for shipping fees
    emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
      scope: 'shipping_fees',
      reason: reason || 'Shipping fees updated',
      timestamp: new Date().toISOString(),
      metadata: { addressData }
    })
  }

  /**
   * Handle batch update from WebSocket messages
   * Vietnamese Business Context: Xử lý cập nhật hàng loạt từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with batch update
   */
  function handleBatchUpdateFromWebSocket(message) {
    const payload = message.payload || message.data || message
    const { dataType, batchId, batchSize, batchUpdates } = payload

    console.log(`📦 Processing batch update: ${dataType} (${batchSize} items, batch: ${batchId})`)

    // Emit batch update event
    emitStateChangeEvent('WEBSOCKET_BATCH_UPDATE', {
      dataType,
      batchId,
      batchSize,
      batchUpdates,
      topic: message.topic,
      timestamp: new Date().toISOString()
    })

    // Trigger real-time data sync for the data type
    emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
      scope: dataType,
      reason: 'Batch update completed',
      timestamp: new Date().toISOString(),
      metadata: { batchId, batchSize }
    })
  }

  /**
   * Handle throttled update from WebSocket messages
   * Vietnamese Business Context: Xử lý cập nhật được điều tiết từ tin nhắn WebSocket
   * @param {Object} message - WebSocket message with throttled update
   */
  function handleThrottledUpdateFromWebSocket(message) {
    const payload = message.payload || message.data || message
    const { throttleKey, throttleInterval, data } = payload

    console.log(`⏱️ Processing throttled update: ${throttleKey} (interval: ${throttleInterval}ms)`)

    // Emit throttled update event
    emitStateChangeEvent('WEBSOCKET_THROTTLED_UPDATE', {
      throttleKey,
      throttleInterval,
      data,
      topic: message.topic,
      timestamp: new Date().toISOString()
    })

    // Apply the throttled update with debouncing
    if (data) {
      emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
        scope: 'throttled_data',
        reason: 'Throttled update received',
        timestamp: new Date().toISOString(),
        metadata: { throttleKey, throttleInterval }
      })
    }
  }

  /**
   * Handle WebSocket update from cross-tab synchronization
   * Vietnamese Business Context: Xử lý cập nhật WebSocket từ đồng bộ đa tab
   * @param {Object} data - Update data from other tab
   * @param {String} timestamp - Update timestamp
   */
  function handleWebSocketUpdateFromCrossTab(data, timestamp) {
    // Process the update without re-broadcasting to avoid loops
    emitStateChangeEvent('CROSS_TAB_WEBSOCKET_UPDATE', {
      data,
      timestamp,
      source: 'cross_tab'
    })
  }

  /**
   * Handle real-time data sync signal from WebSocket
   * Vietnamese Business Context: Xử lý tín hiệu đồng bộ dữ liệu thời gian thực từ WebSocket
   * @param {Object} syncData - Real-time sync data
   */
  function handleRealTimeDataSync(syncData) {
    try {
      console.log('🔄 Received real-time data sync signal:', syncData)

      // Update real-time sync state
      realTimeSyncState.value.lastSyncTime = new Date().toISOString()
      realTimeSyncState.value.pendingSync = true

      // Track synced scopes
      if (syncData.scope) {
        realTimeSyncState.value.syncedScopes.add(syncData.scope)
      }

      // Update sync version if provided
      if (syncData.version) {
        realTimeSyncState.value.syncVersion = syncData.version
      }

      // Update metrics
      syncMetrics.value.realTimeUpdates++

      // Emit real-time data update event for components to handle
      emitStateChangeEvent('REAL_TIME_DATA_UPDATE', {
        scope: syncData.scope,
        timestamp: syncData.timestamp,
        requiresRefresh: syncData.requiresRefresh,
        eventType: syncData.eventType,
        entityId: syncData.entityId
      })

      // Auto-refresh if enabled and scope matches entity
      if (syncData.requiresRefresh && shouldAutoRefresh(syncData.scope)) {
        setTimeout(() => {
          performDataRefresh(syncData)
        }, autoRefreshDelay) // Use configurable delay
      }

      console.log(`✅ Real-time data sync processed for scope: ${syncData.scope}`)

    } catch (error) {
      console.error('❌ Error handling real-time data sync signal:', error)
    }
  }

  /**
   * Check if a topic is relevant to the current entity
   * Vietnamese Business Context: Kiểm tra xem chủ đề có liên quan đến thực thể hiện tại không
   * @param {String} topic - WebSocket topic
   * @param {String} specificType - Specific type to check for
   * @returns {Boolean} Whether topic is relevant
   */
  function isRelevantTopic(topic, specificType = null) {
    if (!topic) return false

    if (specificType) {
      return topic.includes(specificType)
    }

    // Check if topic is relevant to current entity
    const relevantTopics = {
      'hoaDon': ['hoa-don', 'order'],
      'sanPham': ['gia-san-pham', 'san-pham', 'product'],
      'phieuGiamGia': ['phieu-giam-gia', 'voucher'],
      'dotGiamGia': ['dot-giam-gia', 'campaign'],
      'tonKho': ['ton-kho', 'inventory'],
      'gia': ['gia-san-pham', 'price']
    }

    const entityTopics = relevantTopics[entityName] || []
    return entityTopics.some(entityTopic => topic.includes(entityTopic))
  }

  /**
   * Get data scope from WebSocket topic
   * Vietnamese Business Context: Lấy phạm vi dữ liệu từ chủ đề WebSocket
   * @param {String} topic - WebSocket topic
   * @returns {String|null} Data scope
   */
  function getDataScopeFromTopic(topic) {
    if (!topic) return null

    const topicScopeMap = {
      'gia-san-pham': 'PRICING_DATA',
      'san-pham': 'PRODUCT_DATA',
      'phieu-giam-gia': 'VOUCHER_DATA',
      'dot-giam-gia': 'VOUCHER_DATA',
      'hoa-don': 'ORDER_DATA',
      'ton-kho': 'INVENTORY_DATA'
    }

    for (const [topicKey, scope] of Object.entries(topicScopeMap)) {
      if (topic.includes(topicKey)) {
        return scope
      }
    }

    return 'GENERAL_DATA'
  }

  /**
   * Apply optimistic update to state
   * Vietnamese Business Context: Áp dụng cập nhật lạc quan cho trạng thái
   * @param {Object} stateData - State data to apply
   * @param {Object} message - Original WebSocket message
   */
  function applyOptimisticUpdate(stateData, message) {
    if (!enableOptimisticUpdates) return

    const updateId = `${Date.now()}_${Math.random().toString(36).substring(2, 9)}`

    // Store optimistic update
    syncState.value.optimisticUpdates.set(updateId, {
      stateData,
      message,
      timestamp: new Date().toISOString(),
      applied: true
    })

    // Emit optimistic update event
    emitStateChangeEvent('OPTIMISTIC_UPDATE_APPLIED', {
      updateId,
      stateData,
      message
    })

    // Schedule rollback check
    setTimeout(() => {
      checkOptimisticUpdateConfirmation(updateId)
    }, 5000) // 5 second timeout for confirmation
  }

  /**
   * Check optimistic update confirmation
   * Vietnamese Business Context: Kiểm tra xác nhận cập nhật lạc quan
   * @param {String} updateId - Update ID to check
   */
  function checkOptimisticUpdateConfirmation(updateId) {
    const update = syncState.value.optimisticUpdates.get(updateId)
    if (!update) return

    // If update is still pending, consider it failed and rollback
    if (update.applied && !update.confirmed) {
      rollbackOptimisticUpdate(updateId)
    }

    // Clean up old updates
    syncState.value.optimisticUpdates.delete(updateId)
  }

  /**
   * Rollback optimistic update
   * Vietnamese Business Context: Hoàn tác cập nhật lạc quan
   * @param {String} updateId - Update ID to rollback
   */
  function rollbackOptimisticUpdate(updateId) {
    const update = syncState.value.optimisticUpdates.get(updateId)
    if (!update) return

    // Add to rollback queue
    syncState.value.rollbackQueue.push({
      updateId,
      update,
      rollbackTime: new Date().toISOString()
    })

    // Emit rollback event
    emitStateChangeEvent('OPTIMISTIC_UPDATE_ROLLBACK', {
      updateId,
      update
    })

    console.warn('🔄 Rolling back optimistic update:', updateId)
  }

  /**
   * Resolve state conflicts using configured strategy
   * Vietnamese Business Context: Giải quyết xung đột trạng thái bằng chiến lược đã cấu hình
   * @param {Object} currentState - Current state
   * @param {Object} incomingState - Incoming state
   * @returns {Object} Resolved state
   */
  function resolveStateConflicts(currentState, incomingState) {
    try {
      const resolution = resolveStateConflict(
        currentState,
        incomingState,
        conflictResolutionStrategy,
        { entityName, storeKey }
      )

      // Track conflict resolution
      syncState.value.conflictQueue.push({
        currentState,
        incomingState,
        resolvedState: resolution.resolvedState,
        strategy: conflictResolutionStrategy,
        timestamp: new Date().toISOString()
      })

      // Emit conflict resolution event
      emitStateChangeEvent('STATE_CONFLICT_RESOLVED', {
        resolution,
        strategy: conflictResolutionStrategy
      })

      return resolution.resolvedState
    } catch (error) {
      console.error('❌ Error resolving state conflict:', error)
      // Fallback to last-write-wins
      return incomingState
    }
  }

  /**
   * Check if auto-refresh should be performed for the given scope
   * @param {String} scope - Data scope
   * @returns {Boolean} Whether to auto-refresh
   */
  function shouldAutoRefresh(scope) {
    // Auto-refresh for scopes that match the current entity
    const entityScopes = {
      'hoaDon': 'ORDER_DATA',
      'sanPham': 'PRODUCT_DATA',
      'phieuGiamGia': 'VOUCHER_DATA',
      'dotGiamGia': 'VOUCHER_DATA',
      'tonKho': 'INVENTORY_DATA',
      'gia': 'PRICING_DATA'
    }

    return entityScopes[entityName] === scope || scope === 'GENERAL_DATA'
  }

  /**
   * Perform data refresh after real-time sync
   * Vietnamese Business Context: Thực hiện làm mới dữ liệu sau khi đồng bộ thời gian thực
   * @param {Object} syncData - Real-time sync data
   */
  function performDataRefresh(syncData) {
    try {
      console.log('🔄 Performing data refresh for scope:', syncData.scope)

      // Update metrics
      syncMetrics.value.dataRefreshes++

      // Clear pending sync flag
      realTimeSyncState.value.pendingSync = false

      // Emit data refresh event
      emitStateChangeEvent('DATA_REFRESHED', {
        scope: syncData.scope,
        timestamp: new Date().toISOString(),
        refreshReason: 'REAL_TIME_SYNC'
      })

      // Request fresh data sync if state exists
      if (persistedState.value) {
        requestSyncFromOtherTabs()
      }

      console.log(`✅ Data refresh completed for scope: ${syncData.scope}`)

    } catch (error) {
      console.error('❌ Error performing data refresh:', error)
    }
  }

  /**
   * Persist state to localStorage
   * @param {*} state - State to persist
   */
  function persistState(state) {
    if (!enablePersistence) return

    try {
      // Create a serializable copy of the state to avoid issues with non-cloneable objects
      const serializableState = createSerializableState(state)

      const persistData = {
        state: serializableState,
        timestamp: new Date().toISOString(),
        version: syncState.value.syncVersion,
        tabId: tabId.value
      }

      localStorage.setItem(persistenceKey, JSON.stringify(persistData))
      // Store the serializable state to prevent DataCloneError in cross-tab communication
      persistedState.value = serializableState
    } catch (error) {
      console.error('❌ Failed to persist state:', error)
    }
  }

  /**
   * Load persisted state from localStorage
   * @returns {*} Persisted state or null
   */
  function loadPersistedState() {
    if (!enablePersistence) return null

    try {
      const stored = localStorage.getItem(persistenceKey)
      if (!stored) return null

      const persistData = JSON.parse(stored)

      // Validate persisted data
      const validation = validateStateData(persistData.state)
      if (!validation.isValid) {
        console.warn('⚠️ Invalid persisted state, clearing:', validation.errors)
        localStorage.removeItem(persistenceKey)
        return null
      }

      persistedState.value = persistData.state
      return persistData
    } catch (error) {
      console.error('❌ Failed to load persisted state:', error)
      localStorage.removeItem(persistenceKey)
      return null
    }
  }

  /**
   * Request sync from other tabs
   */
  function requestSyncFromOtherTabs() {
    broadcastToOtherTabs('SYNC_REQUEST', { requestingTab: tabId.value })
  }

  /**
   * Synchronize state with remote source
   * @param {*} newState - New state to sync
   * @param {Object} options - Sync options
   * @returns {Promise} Promise resolving to sync result
   */
  async function syncStateData(newState, options = {}) {
    const startTime = Date.now()
    syncMetrics.value.totalSyncs++

    try {
      // Validate new state
      const validation = validateStateData(newState)
      if (!validation.isValid) {
        throw new Error(`State validation failed: ${validation.errors.join(', ')}`)
      }

      // Merge with current state if needed
      const finalState = options.merge && persistedState.value
        ? mergeStates(persistedState.value, newState)
        : newState

      // Persist state
      persistState(finalState)

      // Update sync state
      syncState.value.lastSyncTime = new Date().toISOString()
      syncState.value.syncVersion++
      syncState.value.isConnected = true

      // Emit sync event
      emitStateChangeEvent('STATE_SYNCED', {
        state: finalState,
        options,
        syncVersion: syncState.value.syncVersion
      })

      // Update metrics
      const duration = Date.now() - startTime
      syncMetrics.value.successfulSyncs++
      syncMetrics.value.lastSyncDuration = duration
      syncMetrics.value.averageSyncTime =
        (syncMetrics.value.averageSyncTime * (syncMetrics.value.successfulSyncs - 1) + duration) /
        syncMetrics.value.successfulSyncs

      return { success: true, state: finalState, duration }
    } catch (error) {
      syncMetrics.value.failedSyncs++
      syncState.value.syncErrors.push({
        error: error.message,
        timestamp: new Date().toISOString(),
        state: newState
      })

      console.error(`❌ Sync failed for ${entityName}:`, error)

      toast.add({
        severity: 'error',
        summary: 'Lỗi đồng bộ',
        detail: `Không thể đồng bộ ${entityName}: ${error.message}`,
        life: 5000
      })

      return { success: false, error: error.message }
    }
  }

  // Computed properties
  const isHealthy = computed(() => {
    return syncState.value.isConnected &&
           syncState.value.syncErrors.length < 5 &&
           syncMetrics.value.failedSyncs / Math.max(syncMetrics.value.totalSyncs, 1) < 0.1
  })

  const syncSuccessRate = computed(() => {
    const total = syncMetrics.value.totalSyncs
    return total > 0 ? (syncMetrics.value.successfulSyncs / total * 100).toFixed(2) : 0
  })

  /**
   * Initialize all synchronization systems
   * Vietnamese Business Context: Khởi tạo tất cả hệ thống đồng bộ hóa
   */
  function initializeSync() {
    initializeCrossTabSync()
    initializeWebSocketIntegration()
    loadPersistedState()

    console.log(`🚀 Real-time sync initialized for ${entityName} (${storeKey})`)
  }

  // Initialize on creation
  onMounted(() => {
    initializeSync()
  })

  // Cleanup on unmount
  onUnmounted(() => {
    cleanup()
  })

  function cleanup() {
    // Close cross-tab channel
    if (crossTabChannel.value) {
      crossTabChannel.value.removeEventListener('message', handleCrossTabMessage)
      crossTabChannel.value.close()
      crossTabChannel.value = null
    }

    // Clear WebSocket integration
    if (webSocketMessageHandler.value) {
      webSocketMessageHandler.value = null
    }

    // Clear event listeners
    eventListeners.value.clear()

    // Clear state change events
    stateChangeEvents.value = []

    // Clear optimistic updates
    syncState.value.optimisticUpdates.clear()
    syncState.value.rollbackQueue = []

    console.log(`🧹 Cleanup completed for ${entityName} (${storeKey})`)
  }

  // Additional computed properties
  const isWebSocketConnected = computed(() => {
    return syncState.value.webSocketConnected
  })

  const hasOptimisticUpdates = computed(() => {
    return syncState.value.optimisticUpdates.size > 0
  })

  const hasPendingConflicts = computed(() => {
    return syncState.value.conflictQueue.length > 0
  })

  return {
    // State
    syncState: computed(() => syncState.value),
    persistedState: computed(() => persistedState.value),
    stateChangeEvents: computed(() => stateChangeEvents.value),
    syncMetrics: computed(() => syncMetrics.value),
    tabId: computed(() => tabId.value),
    realTimeSyncState: computed(() => realTimeSyncState.value),

    // Computed
    isHealthy,
    syncSuccessRate,
    isWebSocketConnected,
    hasOptimisticUpdates,
    hasPendingConflicts,

    // Core Methods
    syncStateData,
    addEventListener,
    emitStateChangeEvent,
    validateStateData,
    mergeStates,
    persistState,
    loadPersistedState,
    requestSyncFromOtherTabs,
    broadcastToOtherTabs,
    handleRealTimeDataSync,
    performDataRefresh,
    cleanup,

    // Enhanced WebSocket Methods
    handleWebSocketMessage,
    isRelevantTopic,
    getDataScopeFromTopic,
    applyOptimisticUpdate,
    rollbackOptimisticUpdate,
    resolveStateConflicts,

    // Initialization
    initializeSync
  }
}
