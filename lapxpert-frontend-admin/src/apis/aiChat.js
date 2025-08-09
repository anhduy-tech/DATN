import axios from 'axios'

const API_BASE_URL = 'http://localhost:8080'

/**
 * AI Chat API Service
 * Provides REST API endpoints for AI chat functionality
 */

/**
 * Send chat message to AI service with extended timeout for AI processing
 * @param {Object} chatRequest - Chat request object
 * @param {string} chatRequest.message - User message
 * @param {string} chatRequest.userId - User ID (optional)
 * @param {string} chatRequest.sessionId - Session ID (optional)
 * @param {Function} onProgress - Optional progress callback for long-running requests
 * @returns {Promise} - Promise with AI response
 */
export async function sendChatMessage(chatRequest) {
  try {
    const response = await axios.post(`${API_BASE_URL}/api/ai-chat/chat`, chatRequest, {
      headers: {
        'Content-Type': 'application/json'
      },
      timeout: 180000 // 3 minutes timeout for AI processing (aligned with 180s backend timeout)
    })

    console.log('AI chat response received:', response.data)
    return response.data
  } catch (error) {
    console.error('Error sending chat message:', error)

    if (error.code === 'ECONNABORTED') {
      throw new Error('AI đang xử lý quá lâu (hơn 3 phút). Vui lòng thử lại với câu hỏi ngắn gọn hơn hoặc kiểm tra kết nối mạng.')
    }

    if (error.response) {
      // Server responded with error status
      const status = error.response.status
      const message = error.response.data?.message || error.response.data?.error || 'Lỗi không xác định'

      switch (status) {
        case 400:
          throw new Error(`Yêu cầu không hợp lệ: ${message}`)
        case 500:
          throw new Error(`Lỗi server: ${message}`)
        case 503:
          throw new Error('Dịch vụ AI tạm thời không khả dụng. Vui lòng thử lại sau.')
        default:
          throw new Error(`Lỗi ${status}: ${message}`)
      }
    } else if (error.request) {
      // Network error
      throw new Error('Không thể kết nối đến server. Vui lòng kiểm tra kết nối mạng.')
    } else {
      // Other error
      throw new Error(`Lỗi: ${error.message}`)
    }
  }
}

/**
 * Send chat message with progress tracking for long AI processing
 * @param {Object} chatRequest - Chat request object
 * @param {Function} onProgress - Progress callback function
 * @returns {Promise} - Promise with AI response
 */
export async function sendChatMessageWithProgress(chatRequest, onProgress = null) {
  const startTime = Date.now()

  // Progress tracking intervals
  const progressInterval = setInterval(() => {
    const elapsed = Math.floor((Date.now() - startTime) / 1000)
    if (onProgress) {
      if (elapsed < 30) {
        onProgress({ stage: 'processing', message: 'AI đang phân tích câu hỏi...', elapsed })
      } else if (elapsed < 60) {
        onProgress({ stage: 'searching', message: 'AI đang tìm kiếm sản phẩm phù hợp...', elapsed })
      } else if (elapsed < 120) {
        onProgress({ stage: 'generating', message: 'AI đang tạo phản hồi chi tiết...', elapsed })
      } else {
        onProgress({ stage: 'finalizing', message: 'AI đang hoàn thiện câu trả lời...', elapsed })
      }
    }
  }, 5000) // Update every 5 seconds

  try {
    const response = await sendChatMessage(chatRequest)
    clearInterval(progressInterval)

    if (onProgress) {
      const totalTime = Math.floor((Date.now() - startTime) / 1000)
      onProgress({ stage: 'completed', message: 'Hoàn thành!', elapsed: totalTime })
    }

    return response
  } catch (error) {
    clearInterval(progressInterval)

    if (onProgress) {
      const totalTime = Math.floor((Date.now() - startTime) / 1000)
      onProgress({ stage: 'error', message: 'Có lỗi xảy ra', elapsed: totalTime, error: error.message })
    }

    throw error
  }
}

/**
 * Get AI chat service health status
 * @returns {Promise} - Promise with health status
 */
export async function getAiChatHealth() {
  try {
    const response = await axios.get(`${API_BASE_URL}/api/ai-chat/health`, {
      timeout: 5000
    })

    return response.data
  } catch (error) {
    console.error('Error checking AI chat health:', error)
    throw new Error('Không thể kiểm tra trạng thái dịch vụ AI')
  }
}

/**
 * Get AI chat service status
 * @returns {Promise} - Promise with service status
 */
export async function getAiChatStatus() {
  try {
    const response = await axios.get(`${API_BASE_URL}/api/ai-chat/status`, {
      timeout: 5000
    })

    return response.data
  } catch (error) {
    console.error('Error checking AI chat status:', error)
    throw new Error('Không thể kiểm tra trạng thái dịch vụ AI')
  }
}

/**
 * Test AI chat connection
 * @returns {Promise} - Promise with connection test result
 */
export async function testAiChatConnection() {
  try {
    const testMessage = {
      message: 'Test connection',
      userId: 'test-user',
      sessionId: `test-${Date.now()}`
    }

    const response = await sendChatMessage(testMessage)
    return {
      success: true,
      response: response
    }
  } catch (error) {
    return {
      success: false,
      error: error.message
    }
  }
}

/**
 * Format chat message for API
 * @param {string} content - Message content
 * @param {string} userId - User ID
 * @param {string} sessionId - Session ID
 * @returns {Object} - Formatted chat request
 */
export function formatChatRequest(content, userId = 'anonymous', sessionId = null) {
  return {
    message: content.trim(),
    userId: userId,
    sessionId: sessionId || `session-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
  }
}

/**
 * Validate chat message
 * @param {string} message - Message to validate
 * @returns {Object} - Validation result
 */
export function validateChatMessage(message) {
  if (!message || typeof message !== 'string') {
    return {
      valid: false,
      error: 'Tin nhắn không được để trống'
    }
  }

  const trimmed = message.trim()

  if (trimmed.length === 0) {
    return {
      valid: false,
      error: 'Tin nhắn không được để trống'
    }
  }

  if (trimmed.length > 1000) {
    return {
      valid: false,
      error: 'Tin nhắn quá dài (tối đa 1000 ký tự)'
    }
  }

  return {
    valid: true,
    message: trimmed
  }
}

/**
 * Extract product recommendations from AI response
 * @param {Object} aiResponse - AI response object
 * @returns {Array} - Array of product recommendations
 */
export function extractProductRecommendations(aiResponse) {
  if (!aiResponse || !aiResponse.productRecommendations) {
    return []
  }

  return aiResponse.productRecommendations.map(product => ({
    id: product.san_pham_chi_tiet_id || product.id,
    name: product.ten_san_pham || product.name,
    description: product.mo_ta || product.description,
    price: product.gia_ban || product.price,
    similarityScore: product.similarity_score || product.score || 0,
    image: product.hinh_anh || product.image,
    url: product.url || `/products/${product.san_pham_chi_tiet_id || product.id}`
  }))
}

/**
 * Format price for display
 * @param {number} price - Price value
 * @returns {string} - Formatted price string
 */
export function formatPrice(price) {
  if (!price || isNaN(price)) {
    return 'Liên hệ'
  }

  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND'
  }).format(price)
}

/**
 * Generate unique session ID
 * @returns {string} - Unique session ID
 */
export function generateSessionId() {
  const timestamp = Date.now()
  const random = Math.random().toString(36).substr(2, 9)
  return `ai-chat-${timestamp}-${random}`
}

/**
 * Check if AI service is available
 * @returns {Promise<boolean>} - True if service is available
 */
export async function isAiServiceAvailable() {
  try {
    await getAiChatHealth()
    return true
  } catch (error) {
    console.warn('AI service not available:', error.message)
    return false
  }
}

export default {
  sendChatMessage,
  sendChatMessageWithProgress,
  getAiChatHealth,
  getAiChatStatus,
  testAiChatConnection,
  formatChatRequest,
  validateChatMessage,
  extractProductRecommendations,
  formatPrice,
  generateSessionId,
  isAiServiceAvailable
}
