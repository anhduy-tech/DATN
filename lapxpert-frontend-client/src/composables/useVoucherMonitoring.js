import { ref, computed, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useRealTimeOrderManagement } from './useRealTimeOrderManagement'
import WebSocketLogger, { WebSocketLoggerUtils } from '@/utils/WebSocketLogger.js'

/**
 * Voucher Monitoring Composable
 * Handles real-time voucher expiration, new voucher notifications, and alternative recommendations
 * Integrates with existing voucher system and follows Vietnamese business terminology
 *
 * OPTIMIZATION NOTES:
 * - Replaced 29 console statements with intelligent WebSocketLogger
 * - Applied throttling to message filtering and processing logs
 * - Preserved Vietnamese voucher terminology (PHIEU_GIAM_GIA_*)
 * - Maintained integration callback logs for OrderCreate.vue
 * - Implemented log grouping for voucher operations
 */
export function useVoucherMonitoring() {
  const toast = useToast()
  const { messageHistory, isConnected, sendMessage } = useRealTimeOrderManagement()

  // Initialize WebSocket logger with optimized throttling for voucher operations
  const logger = WebSocketLogger.createWebSocketLogger('VoucherMonitoring', {
    throttleConfig: {
      // High-frequency events from audit findings
      voucher_message_filtering: { rate: 30, counter: 0, lastLogged: 0 },
      voucher_processing: { rate: 20, counter: 0, lastLogged: 0 },
      message_type_processing: { rate: 20, counter: 0, lastLogged: 0 },
      subscription_confirmation: { rate: 20, counter: 0, lastLogged: 0 },
      voucher_validation: { rate: 8, counter: 0, lastLogged: 0 },
      integration_callback: { rate: 5, counter: 0, lastLogged: 0 }
    }
  })

  // Voucher monitoring state
  const expiredVouchers = ref([])
  const newVouchers = ref([])
  const alternativeRecommendations = ref([])
  const voucherNotifications = ref([])

  // Better voucher suggestion state
  const betterVoucherSuggestions = ref([])
  const suggestionDialogVisible = ref(false)
  const currentSuggestion = ref(null)

  // Settings
  const showVoucherNotifications = ref(true)
  const autoApplyAlternatives = ref(false)

  // Enhanced integration support
  const integrationCallbacks = ref({
    onVoucherExpired: null,
    onNewVoucher: null,
    onAlternativeRecommendation: null,
    onBetterSuggestion: null
  })

  // Watch for voucher-related messages
  watch(messageHistory, (newHistory) => {
    const voucherMessages = newHistory.filter(msg => {
      const hasVoucherType = msg.type?.includes('VOUCHER') || msg.type?.includes('PHIEU_GIAM_GIA')
      const hasVoucherTopic = msg.topic?.includes('/topic/phieu-giam-gia/') || msg.topic?.includes('/topic/voucher/')
      const hasVoucherData = msg.voucherType === 'PHIEU_GIAM_GIA' || msg.voucherCode || msg.maPhieuGiamGia

      return hasVoucherType || hasVoucherTopic || hasVoucherData
    })

    WebSocketLoggerUtils.logVoucherMessageFiltering(logger, newHistory.length, voucherMessages.length)
    voucherMessages.forEach(processVoucherMessage)
  }, { deep: true })

  /**
   * Process incoming voucher message
   */
  const processVoucherMessage = (message) => {
    try {
      const messageType = message.type || extractTypeFromTopic(message.topic)
      logger.group('[VoucherMonitoring] Message Processing', 'debug')
      logger.trace('Processing voucher message', {
        messageType,
        originalType: message.type,
        topic: message.topic,
        voucherCode: message.voucherCode || message.maPhieuGiamGia,
        voucherId: message.voucherId
      }, 'processing', 'voucher_processing')

      switch (messageType) {
        case 'PHIEU_GIAM_GIA_EXPIRED':
          logger.debug('Handling expired voucher', { messageType }, 'voucher')
          handleExpiredVoucher(message)
          break
        case 'PHIEU_GIAM_GIA_NEW':
          logger.debug('Handling new voucher', { messageType }, 'voucher')
          handleNewVoucher(message)
          break
        case 'PHIEU_GIAM_GIA_UPDATED':
          logger.debug('Handling updated voucher', { messageType }, 'voucher')
          handleUpdatedVoucher(message)
          break
        case 'PHIEU_GIAM_GIA_ALTERNATIVES':
          logger.debug('Handling alternative recommendations', { messageType }, 'voucher')
          handleAlternativeRecommendations(message)
          break
        case 'PHIEU_GIAM_GIA_BETTER_SUGGESTION':
          logger.debug('Handling better voucher suggestion', { messageType }, 'voucher')
          handleBetterVoucherSuggestion(message)
          break
        // Support legacy message types for backward compatibility during transition
        case 'VOUCHER_EXPIRED':
          logger.debug('Handling expired voucher (legacy)', { messageType }, 'voucher')
          handleExpiredVoucher(message)
          break
        case 'VOUCHER_NEW':
          logger.debug('Handling new voucher (legacy)', { messageType }, 'voucher')
          handleNewVoucher(message)
          break
        case 'VOUCHER_UPDATED':
          logger.debug('Handling updated voucher (legacy)', { messageType }, 'voucher')
          handleUpdatedVoucher(message)
          break
        case 'VOUCHER_ALTERNATIVES':
          logger.debug('Handling alternative recommendations (legacy)', { messageType }, 'voucher')
          handleAlternativeRecommendations(message)
          break
        case 'VOUCHER_BETTER_SUGGESTION':
          logger.debug('Handling better voucher suggestion (legacy)', { messageType }, 'voucher')
          handleBetterVoucherSuggestion(message)
          break
        default:
          logger.debug('Unknown voucher message type', { messageType, message }, 'voucher')
      }
      logger.groupEnd() // End VoucherMonitoring Message Processing group
    } catch (error) {
      logger.critical('Error processing voucher message', {
        error: error.message,
        messageType: message.type,
        topic: message.topic
      }, 'processing')
      logger.groupEnd() // End group on error
    }
  }

  /**
   * Extract message type from topic using Vietnamese terminology
   */
  const extractTypeFromTopic = (topic) => {
    if (!topic) return null

    if (topic.includes('/expired')) return 'PHIEU_GIAM_GIA_EXPIRED'
    if (topic.includes('/new')) return 'PHIEU_GIAM_GIA_NEW'
    if (topic.includes('/updated')) return 'PHIEU_GIAM_GIA_UPDATED'
    if (topic.includes('/alternatives')) return 'PHIEU_GIAM_GIA_ALTERNATIVES'
    if (topic.includes('/better-suggestion')) return 'PHIEU_GIAM_GIA_BETTER_SUGGESTION'

    return null
  }

  /**
   * Handle expired voucher notification
   */
  const handleExpiredVoucher = (message) => {
    const expiredVoucher = {
      id: message.voucherId || Date.now(),
      code: message.voucherCode || message.maPhieuGiamGia,
      description: message.voucherDescription || message.moTa,
      discountValue: message.discountValue || message.giaTriGiam,
      discountType: message.discountType || message.loaiGiamGia,
      expirationTime: message.expirationTime || message.thoiGianHetHan,
      message: message.message || 'Phiếu giảm giá đã hết hạn',
      timestamp: new Date(message.timestamp || Date.now())
    }

    expiredVouchers.value.unshift(expiredVoucher)

    // Call integration callback
    if (integrationCallbacks.value.onVoucherExpired) {
      integrationCallbacks.value.onVoucherExpired(expiredVoucher)
    }

    if (showVoucherNotifications.value) {
      showExpiredVoucherNotification(expiredVoucher)
    }

    // Keep only last 50 expired vouchers
    if (expiredVouchers.value.length > 50) {
      expiredVouchers.value = expiredVouchers.value.slice(0, 50)
    }

    logger.debug('Voucher expired', {
      code: expiredVoucher.code,
      description: expiredVoucher.description,
      timestamp: expiredVoucher.timestamp
    }, 'voucher')
  }

  /**
   * Handle new voucher notification
   */
  const handleNewVoucher = (message) => {
    const newVoucher = {
      id: message.voucherId || Date.now(),
      code: message.voucherCode || message.maPhieuGiamGia,
      description: message.voucherDescription || message.moTa,
      discountValue: message.discountValue || message.giaTriGiam,
      discountType: message.discountType || message.loaiGiamGia,
      minimumOrderValue: message.minimumOrderValue || message.giaTriDonHangToiThieu,
      remainingQuantity: message.remainingQuantity || message.soLuongConLai,
      expirationTime: message.expirationTime || message.thoiGianHetHan,
      message: message.message || 'Phiếu giảm giá mới có hiệu lực',
      timestamp: new Date(message.timestamp || Date.now())
    }

    newVouchers.value.unshift(newVoucher)

    // Call integration callback
    if (integrationCallbacks.value.onNewVoucher) {
      integrationCallbacks.value.onNewVoucher(newVoucher)
    }

    if (showVoucherNotifications.value) {
      showNewVoucherNotification(newVoucher)
    }

    // Keep only last 20 new vouchers
    if (newVouchers.value.length > 20) {
      newVouchers.value = newVouchers.value.slice(0, 20)
    }

    logger.debug('New voucher available', {
      code: newVoucher.code,
      description: newVoucher.description,
      discountValue: newVoucher.discountValue
    }, 'voucher')
  }

  /**
   * Handle updated voucher notification (for discount value changes, etc.)
   */
  const handleUpdatedVoucher = (message) => {
    logger.group('[VoucherMonitoring] Update Processing', 'debug')
    logger.trace('handleUpdatedVoucher called', {
      messageType: message.type,
      voucherId: message.voucherId,
      voucherCode: message.voucherCode || message.maPhieuGiamGia
    }, 'processing', 'voucher_processing')

    const updatedVoucher = {
      id: message.voucherId || message.data?.id || Date.now(),
      code: message.voucherCode || message.maPhieuGiamGia || message.data?.maPhieuGiamGia,
      description: message.voucherDescription || message.moTa || message.data?.moTa,
      discountValue: message.discountValue || message.giaTriGiam || message.data?.giaTriGiam,
      discountType: message.discountType || message.loaiGiamGia || message.data?.loaiGiamGia,
      updateType: message.updateType || 'UPDATED',
      message: message.message || 'Phiếu giảm giá đã được cập nhật',
      timestamp: new Date(message.timestamp || Date.now())
    }

    logger.debug('Processed voucher update data', {
      code: updatedVoucher.code,
      updateType: updatedVoucher.updateType,
      discountValue: updatedVoucher.discountValue
    }, 'voucher')

    // Call integration callback for voucher updates (revalidation)
    if (integrationCallbacks.value.onVoucherExpired) {
      logger.trace('Calling integration callback for voucher update', {
        voucherCode: updatedVoucher.code,
        callbackType: 'onVoucherExpired'
      }, 'integration', 'integration_callback')
      // Use the expired callback for revalidation since that's what OrderCreate expects
      integrationCallbacks.value.onVoucherExpired(updatedVoucher)
    } else {
      logger.debug('No integration callback registered for voucher updates', null, 'integration')
    }

    // Show notification for voucher updates
    if (showVoucherNotifications.value) {
      logger.debug('Showing voucher update notification', {
        voucherCode: updatedVoucher.code
      }, 'notification')
      toast.add({
        severity: 'info',
        summary: 'Voucher cập nhật',
        detail: `Phiếu giảm giá ${updatedVoucher.code} đã được cập nhật`,
        life: 3000
      })
    }

    logger.debug('Voucher update processing completed', {
      code: updatedVoucher.code,
      updateType: updatedVoucher.updateType
    }, 'voucher')
    logger.groupEnd() // End VoucherMonitoring Update Processing group
  }

  /**
   * Handle alternative voucher recommendations
   */
  const handleAlternativeRecommendations = (message) => {
    const recommendation = {
      id: Date.now(),
      expiredVoucherId: message.expiredVoucherId,
      expiredVoucherCode: message.expiredVoucherCode,
      primaryAlternative: message.primaryAlternative,
      additionalAlternatives: message.additionalAlternatives || [],
      message: message.message || 'Tìm thấy phiếu giảm giá thay thế',
      timestamp: new Date(message.timestamp || Date.now())
    }

    alternativeRecommendations.value.unshift(recommendation)

    // Call integration callback
    if (integrationCallbacks.value.onAlternativeRecommendation) {
      integrationCallbacks.value.onAlternativeRecommendation(recommendation)
    }

    if (showVoucherNotifications.value) {
      showAlternativeRecommendationNotification(recommendation)
    }

    // Keep only last 10 recommendations
    if (alternativeRecommendations.value.length > 10) {
      alternativeRecommendations.value = alternativeRecommendations.value.slice(0, 10)
    }

    logger.debug('Alternative vouchers recommended', {
      originalCode: recommendation.originalCode,
      alternativeCount: recommendation.alternatives?.length || 0,
      reason: recommendation.reason
    }, 'voucher')
  }

  /**
   * Handle better voucher suggestion
   */
  const handleBetterVoucherSuggestion = (message) => {
    const suggestion = {
      id: Date.now(),
      currentVoucherId: message.currentVoucherId,
      currentVoucherCode: message.currentVoucherCode,
      betterVoucher: message.betterVoucher,
      currentDiscount: message.currentDiscount,
      betterDiscount: message.betterDiscount,
      savingsAmount: message.savingsAmount,
      message: message.message || 'Tìm thấy voucher tốt hơn',
      timestamp: new Date(message.timestamp || Date.now())
    }

    betterVoucherSuggestions.value.unshift(suggestion)

    // Call integration callback
    if (integrationCallbacks.value.onBetterSuggestion) {
      integrationCallbacks.value.onBetterSuggestion(suggestion)
    }

    if (showVoucherNotifications.value) {
      showBetterVoucherSuggestionNotification(suggestion)
    }

    // Keep only last 5 suggestions
    if (betterVoucherSuggestions.value.length > 5) {
      betterVoucherSuggestions.value = betterVoucherSuggestions.value.slice(0, 5)
    }

    logger.debug('Better voucher suggested', {
      originalCode: suggestion.originalCode,
      betterCode: suggestion.betterCode,
      potentialSavings: suggestion.potentialSavings,
      reason: suggestion.reason
    }, 'voucher')
  }

  /**
   * Show expired voucher notification
   */
  const showExpiredVoucherNotification = (voucher) => {
    const notification = {
      severity: 'warn',
      summary: '⏰ Voucher hết hạn',
      detail: `${voucher.code} - ${voucher.description || 'Phiếu giảm giá'} đã hết hiệu lực`,
      life: 8000,
      group: 'voucher-updates'
    }

    toast.add(notification)
    addToNotificationHistory(notification, voucher, 'EXPIRED')
  }

  /**
   * Show new voucher notification
   */
  const showNewVoucherNotification = (voucher) => {
    const notification = {
      severity: 'success',
      summary: '🎉 Voucher mới',
      detail: `${voucher.code} - Giảm ${formatCurrency(voucher.discountValue)} đã có hiệu lực`,
      life: 6000,
      group: 'voucher-updates'
    }

    toast.add(notification)
    addToNotificationHistory(notification, voucher, 'NEW')
  }

  /**
   * Show alternative recommendation notification
   */
  const showAlternativeRecommendationNotification = (recommendation) => {
    const alternativeCount = 1 + (recommendation.additionalAlternatives?.length || 0)

    const notification = {
      severity: 'info',
      summary: '💡 Voucher thay thế',
      detail: `Tìm thấy ${alternativeCount} phiếu giảm giá thay thế cho ${recommendation.expiredVoucherCode}`,
      life: 10000,
      group: 'voucher-updates'
    }

    toast.add(notification)
    addToNotificationHistory(notification, recommendation, 'ALTERNATIVES')
  }

  /**
   * Show better voucher suggestion notification
   */
  const showBetterVoucherSuggestionNotification = (suggestion) => {
    const notification = {
      severity: 'success',
      summary: '💰 Voucher tốt hơn',
      detail: `Tìm thấy voucher tiết kiệm thêm ${formatCurrency(suggestion.savingsAmount)}`,
      life: 12000,
      group: 'voucher-updates'
    }

    toast.add(notification)
    addToNotificationHistory(notification, suggestion, 'BETTER_SUGGESTION')

    // Show suggestion dialog
    currentSuggestion.value = suggestion
    suggestionDialogVisible.value = true
  }

  /**
   * Add notification to history
   */
  const addToNotificationHistory = (notification, data, type) => {
    voucherNotifications.value.unshift({
      ...notification,
      id: Date.now(),
      timestamp: new Date(),
      data,
      type
    })

    // Keep only last 50 notifications
    if (voucherNotifications.value.length > 50) {
      voucherNotifications.value = voucherNotifications.value.slice(0, 50)
    }
  }

  /**
   * Subscribe to voucher monitoring
   */
  const subscribeToVoucherMonitoring = () => {
    // Use separate, non-nested group for VoucherMonitoring operations
    logger.group('[VoucherMonitoring] Subscription Setup', 'debug')
    logger.debug('Attempting to subscribe to voucher monitoring', {
      isConnected: isConnected.value
    }, 'subscription')

    if (!isConnected.value) {
      logger.debug('Cannot subscribe to voucher monitoring: WebSocket not connected', null, 'subscription')
      logger.groupEnd()
      return false
    }

    const subscriptionMessage = {
      type: 'SUBSCRIBE_VOUCHER_MONITORING',
      topics: [
        '/topic/phieu-giam-gia/expired',
        '/topic/phieu-giam-gia/new',
        '/topic/phieu-giam-gia/updated',
        '/topic/phieu-giam-gia/alternatives',
        '/topic/phieu-giam-gia/better-suggestion',
        '/topic/voucher/all'  // General voucher topic for all voucher notifications
      ],
      timestamp: new Date().toISOString()
    }

    logger.trace('Sending voucher monitoring subscription', {
      type: subscriptionMessage.type,
      topicCount: subscriptionMessage.topics?.length || 0
    }, 'subscription', 'subscription_confirmation')
    const result = sendMessage(subscriptionMessage)
    logger.debug('Subscription result', { success: result }, 'subscription')
    logger.groupEnd() // End VoucherMonitoring Subscription Setup group
    return result
  }

  /**
   * Request voucher validation
   */
  const requestVoucherValidation = (voucherCode, customerId, orderTotal) => {
    if (!isConnected.value) {
      logger.debug('Cannot request voucher validation: WebSocket not connected', {
        voucherCode
      }, 'validation')
      return false
    }

    const validationMessage = {
      type: 'VALIDATE_VOUCHER',
      voucherCode,
      customerId,
      orderTotal,
      timestamp: new Date().toISOString()
    }

    return sendMessage(validationMessage)
  }

  /**
   * Toggle voucher notifications
   */
  const toggleVoucherNotifications = () => {
    showVoucherNotifications.value = !showVoucherNotifications.value

    toast.add({
      severity: 'info',
      summary: 'Cài đặt thông báo',
      detail: showVoucherNotifications.value
        ? 'Đã bật thông báo voucher'
        : 'Đã tắt thông báo voucher',
      life: 3000
    })
  }

  /**
   * Process better voucher suggestion (accept/reject)
   */
  const processBetterVoucherSuggestion = (suggestion, action) => {
    if (action === 'accept') {
      // Emit event for parent component to handle voucher replacement
      return {
        type: 'ACCEPT_BETTER_VOUCHER',
        suggestion,
        currentVoucherCode: suggestion.currentVoucherCode,
        betterVoucher: suggestion.betterVoucher
      }
    } else if (action === 'reject') {
      // Just close the dialog and mark as processed
      return {
        type: 'REJECT_BETTER_VOUCHER',
        suggestion
      }
    }
  }

  /**
   * Close suggestion dialog
   */
  const closeSuggestionDialog = () => {
    suggestionDialogVisible.value = false
    currentSuggestion.value = null
  }

  /**
   * Clear voucher history
   */
  const clearVoucherHistory = () => {
    expiredVouchers.value = []
    newVouchers.value = []
    alternativeRecommendations.value = []
    betterVoucherSuggestions.value = []
    voucherNotifications.value = []
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
  const recentExpiredVouchers = computed(() => {
    const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000)
    return expiredVouchers.value.filter(voucher =>
      voucher.timestamp > oneHourAgo
    )
  })

  const recentNewVouchers = computed(() => {
    const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000)
    return newVouchers.value.filter(voucher =>
      voucher.timestamp > oneHourAgo
    )
  })

  const hasVoucherUpdates = computed(() => {
    return expiredVouchers.value.length > 0 ||
           newVouchers.value.length > 0 ||
           alternativeRecommendations.value.length > 0 ||
           betterVoucherSuggestions.value.length > 0
  })

  const hasBetterVoucherSuggestions = computed(() => {
    return betterVoucherSuggestions.value.length > 0
  })

  return {
    // State
    expiredVouchers,
    newVouchers,
    alternativeRecommendations,
    betterVoucherSuggestions,
    voucherNotifications,
    showVoucherNotifications,
    autoApplyAlternatives,

    // Better voucher suggestion state
    suggestionDialogVisible,
    currentSuggestion,

    // Computed
    recentExpiredVouchers,
    recentNewVouchers,
    hasVoucherUpdates,
    hasBetterVoucherSuggestions,

    // Methods
    subscribeToVoucherMonitoring,
    requestVoucherValidation,
    toggleVoucherNotifications,
    clearVoucherHistory,
    formatCurrency,

    // Better voucher suggestion methods
    processBetterVoucherSuggestion,
    closeSuggestionDialog,

    // Internal methods (for testing)
    processVoucherMessage,
    handleExpiredVoucher,
    handleNewVoucher,
    handleAlternativeRecommendations,
    handleBetterVoucherSuggestion,

    // Integration support
    integrationCallbacks,
    setIntegrationCallback: (type, callback) => {
      if (integrationCallbacks.value.hasOwnProperty(type)) {
        integrationCallbacks.value[type] = callback
      }
    }
  }
}
