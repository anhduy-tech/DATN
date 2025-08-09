/**
 * Simple return URL configuration utility for payment gateways.
 * Uses window.location.origin for consistent URL construction.
 */

/**
 * Get the return URL for payment gateways.
 *
 * @param {string} path - Optional path to append (default: '/orders/payment-return')
 * @returns {string} Complete return URL for payment gateways
 */
export const getPaymentReturnUrl = (path = '/orders/payment-return') => {
  const baseUrl = window.location.origin
  const cleanPath = path.startsWith('/') ? path : `/${path}`
  return `${baseUrl}${cleanPath}`
}

export default {
  getPaymentReturnUrl
}
