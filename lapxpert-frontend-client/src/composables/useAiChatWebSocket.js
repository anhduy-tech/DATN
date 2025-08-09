import { ref, onUnmounted } from 'vue'
import { Stomp } from '@stomp/stompjs'

/**
 * Composable cho AI Chat WebSocket functionality
 * Sử dụng SockJS/STOMP để kết nối với backend AI chat service
 * Updated for streamlined architecture with unified AiChatService integration
 */
export function useAiChatWebSocket() {
  // Reactive state
  const isConnected = ref(false)
  const isConnecting = ref(false)
  const connectionError = ref(null)
  const messages = ref([])
  const isAiTyping = ref(false)
  const aiStatus = ref('READY') // READY, PROCESSING, ERROR, STREAMING



  // WebSocket connection
  let stompClient = null
  let sessionId = null
  let subscriptions = []
  let aiResponseTimeout = null
  const AI_RESPONSE_TIMEOUT = 180000 // 3 minutes timeout for AI responses

  // Message deduplication system (Fix for Issue 3)
  const processedMessageIds = new Set()
  const MAX_PROCESSED_IDS = 1000 // Prevent memory leaks by limiting Set size

  /**
   * Tạo unique session ID cho chat session
   */
  const generateSessionId = () => {
    return `ai-chat-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`
  }

  /**
   * Shared function to clear AI response timeout
   * Provides consistent timeout clearing mechanism across all message subscriptions
   * Fixes Issue 2: Prevents timeout messages from appearing after successful responses
   */
  const clearAiResponseTimeout = () => {
    if (aiResponseTimeout) {
      clearTimeout(aiResponseTimeout)
      aiResponseTimeout = null
      console.debug('AI response timeout cleared')
    }
  }

  /**
   * Add message with deduplication to prevent duplicate messages in UI
   * Fixes Issue 3: Prevents duplicate customer messages from appearing
   */
  const addMessageWithDeduplication = (message) => {
    // Generate message ID if not present
    const messageId = message.message_id || message.messageId || `msg-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`

    console.debug('Processing message for deduplication:', {
      messageId,
      sender: message.sender,
      content: message.content?.substring(0, 50) + '...',
      messageType: message.message_type,
      hasExistingId: !!(message.message_id || message.messageId)
    })

    // Check if message already processed
    if (processedMessageIds.has(messageId)) {
      console.warn('Duplicate message detected, skipping:', {
        messageId,
        sender: message.sender,
        content: message.content?.substring(0, 50) + '...',
        processedCount: processedMessageIds.size
      })
      return false
    }

    // Add to processed set
    processedMessageIds.add(messageId)

    // Cleanup old IDs to prevent memory leaks
    if (processedMessageIds.size > MAX_PROCESSED_IDS) {
      const idsToRemove = Array.from(processedMessageIds).slice(0, processedMessageIds.size - MAX_PROCESSED_IDS)
      idsToRemove.forEach(id => processedMessageIds.delete(id))
      console.debug('Cleaned up old message IDs, removed:', idsToRemove.length)
    }

    // Add message to messages array
    messages.value.push({
      ...message,
      message_id: messageId,
      timestamp: new Date(message.timestamp || Date.now())
    })

    console.debug('Message added successfully:', {
      messageId,
      sender: message.sender,
      totalMessages: messages.value.length,
      processedIdsCount: processedMessageIds.size
    })
    return true
  }

  /**
   * Kết nối đến AI Chat WebSocket
   */
  const connect = async () => {
    if (isConnected.value || isConnecting.value) {
      console.log('Đã kết nối hoặc đang kết nối')
      return
    }

    try {
      isConnecting.value = true
      connectionError.value = null
      sessionId = generateSessionId()

      // Use native WebSocket for better compatibility
      const socket = new WebSocket('ws://localhost:8080/ws')
      stompClient = Stomp.over(socket)

      console.log('Using native WebSocket connection')

      // Disable debug logging in production
      stompClient.debug = (str) => {
        console.log('STOMP: ' + str)
      }

      // Connect to WebSocket
      await new Promise((resolve, reject) => {
        stompClient.connect(
          {},
          (frame) => {
            console.log('Kết nối AI Chat WebSocket thành công:', frame)
            isConnected.value = true
            isConnecting.value = false

            // Subscribe to AI chat topics
            subscribeToTopics()

            // Send join message
            sendJoinMessage()

            resolve(frame)
          },
          (error) => {
            console.error('Lỗi kết nối AI Chat WebSocket:', error)
            connectionError.value = 'Không thể kết nối đến server chat'
            isConnecting.value = false
            reject(error)
          }
        )
      })

    } catch (error) {
      console.error('Lỗi khi khởi tạo kết nối:', error)
      connectionError.value = 'Lỗi khởi tạo kết nối'
      isConnecting.value = false
    }
  }

  /**
   * Đăng ký các topic WebSocket
   */
  const subscribeToTopics = () => {
    if (!stompClient || !sessionId) return

    // Subscribe to AI chat messages
    const messageSubscription = stompClient.subscribe(
      `/topic/ai-chat/${sessionId}`,
      (message) => {
        try {
          const chatMessage = JSON.parse(message.body)
          console.log('Nhận tin nhắn AI chat:', chatMessage)

          // Add message with deduplication to prevent duplicates
          addMessageWithDeduplication({
            ...chatMessage.data,
            timestamp: chatMessage.timestamp || chatMessage.data.timestamp
          })

          // Clear AI response timeout when receiving any message
          // This prevents timeout messages from appearing after successful responses
          clearAiResponseTimeout()

        } catch (error) {
          console.error('Lỗi xử lý tin nhắn AI chat:', error)

          // Clear timeout on error as well to prevent false timeout messages
          clearAiResponseTimeout()
        }
      }
    )
    subscriptions.push(messageSubscription)

    // Subscribe to AI responses
    const responseSubscription = stompClient.subscribe(
      `/topic/ai-chat/${sessionId}/response`,
      (message) => {
        try {
          const response = JSON.parse(message.body)
          console.log('Nhận phản hồi AI:', response)

          // Add AI response with deduplication to prevent duplicates
          addMessageWithDeduplication({
            ...response.data,
            timestamp: response.timestamp || response.data.timestamp
          })

          // Clear AI response timeout using shared function
          clearAiResponseTimeout()

          // Update AI status
          isAiTyping.value = false
          aiStatus.value = 'READY'

        } catch (error) {
          console.error('Lỗi xử lý phản hồi AI:', error)

          // Clear AI response timeout using shared function
          clearAiResponseTimeout()

          isAiTyping.value = false
          aiStatus.value = 'ERROR'
        }
      }
    )
    subscriptions.push(responseSubscription)

    // Subscribe to AI status updates
    const statusSubscription = stompClient.subscribe(
      `/topic/ai-chat/${sessionId}/status`,
      (message) => {
        try {
          const statusUpdate = JSON.parse(message.body)
          console.log('Cập nhật trạng thái AI:', statusUpdate)

          aiStatus.value = statusUpdate.status

          // Update typing indicator
          if (statusUpdate.status === 'PROCESSING') {
            isAiTyping.value = true
          } else {
            isAiTyping.value = false
          }

        } catch (error) {
          console.error('Lỗi xử lý trạng thái AI:', error)
        }
      }
    )
    subscriptions.push(statusSubscription)


  }

  /**
   * Gửi tin nhắn tham gia chat
   */
  const sendJoinMessage = () => {
    if (!stompClient || !sessionId) return

    const joinMessage = {
      session_id: sessionId,
      sender: 'Khách hàng',
      content: 'Đã tham gia chat',
      timestamp: new Date().toISOString(),
      message_type: 'JOIN'
    }

    stompClient.send(`/app/ai-chat/${sessionId}/join`, {}, JSON.stringify(joinMessage))
  }

  /**
   * Gửi tin nhắn chat đến AI
   */
  const sendMessage = (content) => {
    if (!stompClient || !sessionId || !content.trim()) {
      console.warn('Không thể gửi tin nhắn: thiếu kết nối hoặc nội dung')
      return false
    }

    try {
      // Generate consistent message ID that will be used by both local addition and server echo
      // Enhanced ID generation for better uniqueness and debugging
      const messageId = `user-msg-${sessionId}-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`

      const chatMessage = {
        message_id: messageId,
        session_id: sessionId,
        sender: 'Khách hàng',
        content: content.trim(),
        timestamp: new Date().toISOString(),
        message_type: 'USER_MESSAGE'
      }

      console.debug('Sending user message with ID:', messageId, 'Content:', content.trim())

      // Add user message with deduplication to prevent duplicates when server echoes back
      const addResult = addMessageWithDeduplication({
        ...chatMessage,
        timestamp: chatMessage.timestamp
      })

      if (!addResult) {
        console.warn('Message was not added due to deduplication:', messageId)
        return false
      }

      // Send message to server with the same consistent ID
      stompClient.send(`/app/ai-chat/${sessionId}/send`, {}, JSON.stringify(chatMessage))

      // Update AI status
      aiStatus.value = 'PROCESSING'
      isAiTyping.value = true

      // Set timeout for AI response
      clearAiResponseTimeout()

      aiResponseTimeout = setTimeout(() => {
        console.warn('AI response timeout after 3 minutes')
        isAiTyping.value = false
        aiStatus.value = 'ERROR'

        // Add timeout message with deduplication
        addMessageWithDeduplication({
          message_id: `timeout-${Date.now()}`,
          session_id: sessionId,
          sender: 'Hệ thống',
          content: 'AI đang xử lý quá lâu. Vui lòng thử lại với câu hỏi ngắn gọn hơn hoặc kiểm tra kết nối mạng.',
          timestamp: new Date(),
          message_type: 'SYSTEM_ERROR'
        })
      }, AI_RESPONSE_TIMEOUT)

      return true
    } catch (error) {
      console.error('Lỗi gửi tin nhắn:', error)
      return false
    }
  }















  /**
   * Ngắt kết nối WebSocket
   */
  const disconnect = () => {
    try {
      if (sessionId && stompClient) {
        // Send leave message
        const leaveMessage = {
          session_id: sessionId,
          sender: 'Khách hàng',
          content: 'Đã rời khỏi chat',
          timestamp: new Date().toISOString(),
          message_type: 'LEAVE'
        }

        stompClient.send(`/app/ai-chat/${sessionId}/leave`, {}, JSON.stringify(leaveMessage))
      }

      // Clear AI response timeout using shared function
      clearAiResponseTimeout()

      // Unsubscribe from all topics
      subscriptions.forEach(subscription => {
        if (subscription && subscription.unsubscribe) {
          subscription.unsubscribe()
        }
      })
      subscriptions = []

      // Disconnect STOMP client
      if (stompClient) {
        stompClient.disconnect(() => {
          console.log('Đã ngắt kết nối AI Chat WebSocket')
        })
        stompClient = null
      }

      // Reset state
      isConnected.value = false
      isConnecting.value = false
      connectionError.value = null
      sessionId = null
      isAiTyping.value = false
      aiStatus.value = 'READY'

    } catch (error) {
      console.error('Lỗi khi ngắt kết nối:', error)
    }
  }

  /**
   * Xóa lịch sử chat
   */
  const clearMessages = () => {
    messages.value = []
    // Clear processed message IDs to prevent memory leaks and allow fresh start
    processedMessageIds.clear()
    console.debug('Messages and processed IDs cleared')
  }

  /**
   * Lấy session ID hiện tại
   */
  const getCurrentSessionId = () => {
    return sessionId
  }

  // Cleanup khi component unmount
  onUnmounted(() => {
    disconnect()
  })

  return {
    // State
    isConnected,
    isConnecting,
    connectionError,
    messages,
    isAiTyping,
    aiStatus,

    // Methods
    connect,
    disconnect,
    sendMessage,
    clearMessages,
    getCurrentSessionId
  }
}
