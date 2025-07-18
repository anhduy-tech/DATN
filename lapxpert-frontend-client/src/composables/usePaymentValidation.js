import { computed } from 'vue'

/**
 * Enhanced payment method validation composable
 * Provides sophisticated validation rules for different order types and contexts
 */
export function usePaymentValidation() {

  /**
   * Payment method definitions with enhanced metadata
   */
  const paymentMethods = computed(() => [
    {
      value: 'TIEN_MAT',
      label: 'Tiền mặt',
      icon: 'pi pi-money-bill',
      description: 'Thanh toán bằng tiền mặt (tại quầy hoặc khi giao hàng)',
      allowedChannels: ['TAI_QUAY', 'ONLINE'], // Both channels - includes former COD
      requiresOnlineGateway: false,
      instantConfirmation: true, // For POS, false for delivery
      supportedCurrencies: ['VND'],
      minimumAmount: 0,
      processingTime: 'Tức thì (POS) hoặc khi giao hàng (Online)',
      fees: 0
    },

    {
      value: 'VNPAY',
      label: 'VNPay',
      icon: 'pi pi-credit-card',
      description: 'Thanh toán qua cổng VNPay',
      allowedChannels: ['ONLINE', 'TAI_QUAY'], // Both channels
      requiresOnlineGateway: true,
      instantConfirmation: false,
      supportedCurrencies: ['VND'],
      minimumAmount: 10000, // 10,000 VND minimum
      processingTime: '1-3 phút',
      fees: 0.025 // 2.5% fee
    },
    {
      value: 'MOMO',
      label: 'MoMo',
      icon: 'pi pi-mobile',
      description: 'Thanh toán qua ví điện tử MoMo',
      allowedChannels: ['ONLINE', 'TAI_QUAY'], // Both channels
      requiresOnlineGateway: true,
      instantConfirmation: false,
      supportedCurrencies: ['VND'],
      minimumAmount: 0,
      processingTime: '1-3 phút',
      fees: 0
    }
  ])

  /**
   * Get available payment methods for a specific order type and context
   * @param {string} orderType - Order type (ONLINE, TAI_QUAY)
   * @param {Object} context - Additional context (amount, customer, etc.)
   * @returns {Array} Available payment methods
   */
  const getAvailablePaymentMethods = (orderType, context = {}) => {
    const { amount = 0, isDelivery = false } = context

    return paymentMethods.value.filter(method => {
      // Check if method is allowed for this channel
      if (!method.allowedChannels.includes(orderType)) {
        return false
      }

      // Check minimum amount limit
      if (amount < method.minimumAmount) {
        return false
      }

      // Special business rules
      if (method.value === 'TIEN_MAT') {
        // Cash only for POS orders (already filtered by allowedChannels)
        return true
      }



      return true
    })
  }

  /**
   * Validate a specific payment method for an order
   * @param {string} paymentMethod - Payment method to validate
   * @param {string} orderType - Order type (ONLINE, TAI_QUAY)
   * @param {Object} context - Validation context
   * @returns {Object} Validation result
   */
  const validatePaymentMethod = (paymentMethod, orderType, context = {}) => {
    const method = paymentMethods.value.find(m => m.value === paymentMethod)

    if (!method) {
      return {
        isValid: false,
        errors: ['Phương thức thanh toán không hợp lệ'],
        warnings: []
      }
    }

    const errors = []
    const warnings = []
    const { amount = 0, isDelivery = false } = context

    // Channel validation
    if (!method.allowedChannels.includes(orderType)) {
      errors.push(`${method.label} không được hỗ trợ cho loại đơn hàng ${orderType === 'ONLINE' ? 'trực tuyến' : 'tại quầy'}`)
    }

    // Amount validation
    if (amount < method.minimumAmount) {
      errors.push(`Số tiền tối thiểu cho ${method.label} là ${formatCurrency(method.minimumAmount)}`)
    }

    // Business rule validation
    if (paymentMethod === 'TIEN_MAT' && orderType === 'ONLINE' && !isDelivery) {
      errors.push('Thanh toán tiền mặt cho đơn hàng online chỉ áp dụng cho đơn hàng giao tận nơi')
    }



    return {
      isValid: errors.length === 0,
      errors,
      warnings,
      method
    }
  }

  /**
   * Get payment method by value
   * @param {string} value - Payment method value
   * @returns {Object|null} Payment method object
   */
  const getPaymentMethod = (value) => {
    return paymentMethods.value.find(method => method.value === value) || null
  }

  /**
   * Calculate payment fees for a method and amount
   * @param {string} paymentMethod - Payment method
   * @param {number} amount - Order amount
   * @returns {number} Fee amount
   */
  const calculatePaymentFee = (paymentMethod, amount) => {
    const method = getPaymentMethod(paymentMethod)
    if (!method) return 0

    if (method.fees === 0) return 0

    // Percentage-based fee
    if (method.fees < 1) {
      return Math.round(amount * method.fees)
    }

    // Fixed fee
    return method.fees
  }

  /**
   * Get payment method recommendations based on context
   * @param {string} orderType - Order type
   * @param {Object} context - Context for recommendations
   * @returns {Array} Recommended payment methods
   */
  const getRecommendedPaymentMethods = (orderType, context = {}) => {
    const availableMethods = getAvailablePaymentMethods(orderType, context)
    const { amount = 0, isDelivery = false } = context

    // Sort by recommendation score
    return availableMethods.map(method => {
      let score = 0

      // Prefer instant confirmation methods
      if (method.instantConfirmation) score += 10

      // Prefer methods with lower fees
      const fee = calculatePaymentFee(method.value, amount)
      score += (1000 - fee) / 100

      // Context-specific preferences
      if (orderType === 'TAI_QUAY') {
        if (method.value === 'TIEN_MAT') score += 20 // Prefer cash for POS
      } else {
        if (method.value === 'VNPAY') score += 15 // Prefer online payment for online orders
        if (isDelivery && method.value === 'TIEN_MAT') score += 10 // Cash on delivery good for delivery
      }

      return { ...method, recommendationScore: score }
    }).sort((a, b) => b.recommendationScore - a.recommendationScore)
  }

  /**
   * Format currency amount
   * @param {number} amount - Amount to format
   * @returns {string} Formatted currency
   */
  const formatCurrency = (amount) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND'
    }).format(amount)
  }

  /**
   * Check if payment method requires additional verification
   * @param {string} paymentMethod - Payment method
   * @param {number} amount - Order amount
   * @returns {boolean} Whether additional verification is needed
   */
  const requiresAdditionalVerification = (paymentMethod, amount) => {
    const method = getPaymentMethod(paymentMethod)
    if (!method) return false

    // High-value cash transactions need verification
    if (paymentMethod === 'TIEN_MAT' && amount > 20000000) {
      return true
    }

    // High-value cash on delivery orders need verification
    if (paymentMethod === 'TIEN_MAT' && amount > 15000000) {
      return true
    }

    return false
  }

  return {
    // Data
    paymentMethods,

    // Methods
    getAvailablePaymentMethods,
    validatePaymentMethod,
    getPaymentMethod,
    calculatePaymentFee,
    getRecommendedPaymentMethods,
    requiresAdditionalVerification,
    formatCurrency
  }
}
