import { ref, computed, watch, nextTick } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useRealTimeOrderManagement } from './useRealTimeOrderManagement'

/**
 * Real-time Pricing Composable with Enhanced State Synchronization
 * Handles real-time price updates and notifications for product variants
 * Integrates with enhanced state synchronization and follows Vietnamese business terminology
 */
export function useRealTimePricing() {
  const toast = useToast()
  const {
    messageHistory,
    isConnected,
    sendMessage,
    subscribeToVariantPriceTopics
  } = useRealTimeOrderManagement()

  // Direct WebSocket integration for pricing data
  // Using useRealTimeOrderManagement for direct WebSocket subscription

  // Price update state
  const priceUpdates = ref([])
  const lastPriceUpdate = ref(null)
  const affectedVariants = ref(new Set())

  // Price change notifications
  const priceChangeNotifications = ref([])
  const showPriceWarnings = ref(true)

  // Enhanced integration support
  const integrationCallbacks = ref({
    onPriceUpdate: null,
    onVariantAffected: null,
    onPriceNotification: null
  })

  // Direct WebSocket subscription for price updates
  // Subscribe to /topic/gia-san-pham/{variantId} topics directly

  // Enhanced WebSocket message watching for price updates
  watch(messageHistory, (newHistory) => {
    console.log('💰 useRealTimePricing: Checking message history, total messages:', newHistory.length)

    const priceMessages = newHistory.filter(msg =>
      msg.type === 'PRICE_UPDATE' ||
      msg.topic?.includes('/topic/gia-san-pham/')
    )

    if (priceMessages.length > 0) {
      console.log('💰 useRealTimePricing: Found price messages:', priceMessages.length, priceMessages)
    } else {
      console.log('💰 useRealTimePricing: No price messages found in', newHistory.length, 'total messages')
      // Log all message topics for debugging
      const topics = newHistory.map(msg => msg.topic).filter(Boolean)
      if (topics.length > 0) {
        console.log('💰 useRealTimePricing: Available topics:', [...new Set(topics)])
      }
    }

    priceMessages.forEach(async (message) => {
      console.log('💰 useRealTimePricing: Processing price message:', message)
      await processePriceUpdate(message)
      // Force Vue reactivity update
      await nextTick()
    })
  }, { deep: true })

  /**
   * Process incoming price update message with enhanced reactivity
   * Enhanced debugging for circular reference fix verification
   */
  const processePriceUpdate = async (message) => {
    try {
      console.log('💰 processePriceUpdate: Raw message received:', message)

      const priceUpdate = {
        id: message.id || Date.now(),
        variantId: message.variantId,
        productName: message.productName || message.tenSanPham,
        variantInfo: message.variantInfo || message.thongTinBienThe,
        oldPrice: message.oldPrice || message.giaCu,
        newPrice: message.newPrice || message.giaMoi,
        changeAmount: message.changeAmount || message.soTienThayDoi,
        changePercent: message.changePercent || message.phanTramThayDoi,
        reason: message.reason || message.lyDo || 'Cập nhật giá tự động',
        timestamp: message.timestamp || new Date(),
        severity: determinePriceSeverity(message)
      }

      // Reduced logging - only log for debugging when needed
      // console.log('💰 processePriceUpdate: Extracted price update:', priceUpdate)

      // Add to price updates list
      priceUpdates.value.unshift(priceUpdate)
      lastPriceUpdate.value = priceUpdate

      // Track affected variants
      if (priceUpdate.variantId) {
        affectedVariants.value.add(priceUpdate.variantId)
      }

      // Call integration callbacks
      if (integrationCallbacks.value.onPriceUpdate) {
        integrationCallbacks.value.onPriceUpdate(priceUpdate)
      }

      if (integrationCallbacks.value.onVariantAffected && priceUpdate.variantId) {
        integrationCallbacks.value.onVariantAffected(priceUpdate.variantId, priceUpdate)
      }

      // Show notification if enabled
      if (showPriceWarnings.value) {
        showPriceChangeNotification(priceUpdate)
      }

      // Keep only last 100 price updates
      if (priceUpdates.value.length > 100) {
        priceUpdates.value = priceUpdates.value.slice(0, 100)
      }

      // Reduced logging - only log significant price changes
      if (Math.abs(priceUpdate.changePercent || 0) >= 5) {
        console.log('💰 Significant price update:', priceUpdate.variantId, priceUpdate.changePercent + '%')
      }

      // Force Vue reactivity update
      await nextTick()

    } catch (error) {
      console.error('Error processing price update:', error, message)
    }
  }

  /**
   * Determine severity level for price change
   */
  const determinePriceSeverity = (update) => {
    const changePercent = Math.abs(update.changePercent || update.phanTramThayDoi || 0)

    if (changePercent >= 20) return 'error'    // 20%+ change
    if (changePercent >= 10) return 'warn'     // 10-19% change
    if (changePercent >= 5) return 'info'      // 5-9% change
    return 'success'                           // <5% change
  }

  /**
   * Show price change notification
   */
  const showPriceChangeNotification = (priceUpdate) => {
    const isIncrease = priceUpdate.newPrice > priceUpdate.oldPrice
    const changeText = isIncrease ? 'tăng' : 'giảm'
    const icon = isIncrease ? '📈' : '📉'

    const notification = {
      severity: priceUpdate.severity,
      summary: `${icon} Giá ${changeText}`,
      detail: `${priceUpdate.productName || 'Sản phẩm'} - ${priceUpdate.variantInfo || ''}\nGiá mới: ${formatCurrency(priceUpdate.newPrice)}`,
      life: 5000,
      group: 'price-updates'
    }

    toast.add(notification)

    // Add to notifications history
    priceChangeNotifications.value.unshift({
      ...notification,
      id: Date.now(),
      timestamp: new Date(),
      priceUpdate
    })

    // Keep only last 20 notifications
    if (priceChangeNotifications.value.length > 20) {
      priceChangeNotifications.value = priceChangeNotifications.value.slice(0, 20)
    }
  }

  /**
   * Check if variant has recent price changes
   */
  const hasRecentPriceChange = (variantId, minutesAgo = 30) => {
    const cutoffTime = new Date(Date.now() - minutesAgo * 60 * 1000)
    return priceUpdates.value.some(update =>
      update.variantId === variantId &&
      new Date(update.timestamp) > cutoffTime
    )
  }

  /**
   * Get price updates for specific variant
   */
  const getPriceUpdatesForVariant = (variantId) => {
    return priceUpdates.value.filter(update => update.variantId === variantId)
  }

  /**
   * Get latest price for variant
   */
  const getLatestPriceForVariant = (variantId) => {
    const updates = getPriceUpdatesForVariant(variantId)
    return updates.length > 0 ? updates[0].newPrice : null
  }

  /**
   * Subscribe to price updates for specific variants with graceful degradation
   * Enhanced to use direct topic subscription
   */
  const subscribeToPriceUpdates = (variantIds) => {
    if (!isConnected.value) {
      console.warn('💰 Cannot subscribe to price updates: WebSocket not connected - graceful degradation active')

      // Show user notification about degraded functionality
      toast.add({
        severity: 'warn',
        summary: 'Kết nối thời gian thực',
        detail: 'Cập nhật giá thời gian thực tạm thời không khả dụng',
        life: 3000
      })

      return false
    }

    // Use direct topic subscription for specific variants
    const success = subscribeToVariantPriceTopics(variantIds)

    if (success) {
      console.log('💰 Successfully subscribed to price updates for variants:', variantIds)
    } else {
      console.warn('💰 Failed to subscribe to price updates - graceful degradation active')

      toast.add({
        severity: 'warn',
        summary: 'Cập nhật giá',
        detail: 'Không thể đăng ký cập nhật giá thời gian thực',
        life: 3000
      })
    }

    return success
  }

  /**
   * Request current prices for variants with graceful degradation
   */
  const requestCurrentPrices = (variantIds) => {
    if (!isConnected.value) {
      console.warn('💰 Cannot request prices: WebSocket not connected - graceful degradation active')
      return false
    }

    const requestMessage = {
      type: 'REQUEST_CURRENT_PRICES',
      variantIds: Array.isArray(variantIds) ? variantIds : [variantIds],
      timestamp: new Date().toISOString()
    }

    const success = sendMessage(requestMessage)

    if (success) {
      console.log('💰 Successfully requested current prices for variants:', variantIds)
    } else {
      console.warn('💰 Failed to request current prices - graceful degradation active')
    }

    return success
  }

  /**
   * Toggle price change notifications
   */
  const togglePriceWarnings = () => {
    showPriceWarnings.value = !showPriceWarnings.value

    toast.add({
      severity: 'info',
      summary: 'Cài đặt thông báo',
      detail: showPriceWarnings.value
        ? 'Đã bật thông báo thay đổi giá'
        : 'Đã tắt thông báo thay đổi giá',
      life: 3000
    })
  }

  /**
   * Clear price update history
   */
  const clearPriceHistory = () => {
    priceUpdates.value = []
    priceChangeNotifications.value = []
    affectedVariants.value.clear()
    lastPriceUpdate.value = null
  }

  /**
   * Format currency for display
   */
  const formatCurrency = (amount) => {
    if (amount == null) return '0 ₫'
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND'
    }).format(amount)
  }

  // Computed properties
  const recentPriceUpdates = computed(() => {
    const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000)
    return priceUpdates.value.filter(update =>
      new Date(update.timestamp) > oneHourAgo
    )
  })

  const affectedVariantsList = computed(() => {
    return Array.from(affectedVariants.value)
  })

  const hasPriceUpdates = computed(() => {
    return priceUpdates.value.length > 0
  })

  return {
    // State
    priceUpdates,
    lastPriceUpdate,
    affectedVariants: affectedVariantsList,
    priceChangeNotifications,
    showPriceWarnings,

    // Computed
    recentPriceUpdates,
    hasPriceUpdates,

    // Methods
    hasRecentPriceChange,
    getPriceUpdatesForVariant,
    getLatestPriceForVariant,
    subscribeToPriceUpdates,
    requestCurrentPrices,
    togglePriceWarnings,
    clearPriceHistory,
    formatCurrency,

    // Internal methods (for testing)
    processePriceUpdate,
    determinePriceSeverity,

    // Integration support
    integrationCallbacks,
    setIntegrationCallback: (type, callback) => {
      if (integrationCallbacks.value.hasOwnProperty(type)) {
        integrationCallbacks.value[type] = callback
      }
    }
  }
}
