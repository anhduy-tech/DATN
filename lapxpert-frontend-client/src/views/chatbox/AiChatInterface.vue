<template>
  <div class="ai-chat-interface">
    <!-- Simplified Header -->
    <div class="chat-header">
      <div class="header-content">
        <div class="chat-title">
          <i class="pi pi-comments text-2xl mr-2"></i>
          <h2 class="text-xl font-semibold">Trợ Lý AI LapXpert</h2>
        </div>
      </div>
    </div>

    <!-- Khu vực chat messages -->
    <div class="chat-messages" ref="messagesContainer">
      <ScrollPanel style="height: 100%; width: 100%">
        <div class="messages-content">
          <!-- Welcome message -->
          <div v-if="messages.length === 0" class="welcome-message">
            <div class="welcome-content">
              <i class="pi pi-robot text-4xl text-blue-500 mb-3"></i>
              <h3 class="text-lg font-semibold mb-2">Chào mừng đến với Trợ Lý AI!</h3>
              <p class="text-gray-600">
                Tôi có thể giúp bạn tìm kiếm và tư vấn sản phẩm laptop.
                Hãy bắt đầu cuộc trò chuyện bằng cách gửi tin nhắn!
              </p>
            </div>
          </div>

          <!-- Chat messages -->
          <div
            v-for="message in messages"
            :key="message.message_id || message.messageId"
            class="message-wrapper"
            :class="{ 'user-message': isUserMessage(message), 'ai-message': !isUserMessage(message) }"
          >
            <div class="message-bubble">
              <div class="message-header">
                <div class="sender-info">
                  <Avatar
                    :label="getSenderInitial(message.sender)"
                    :class="isUserMessage(message) ? 'user-avatar' : 'ai-avatar'"
                    size="small"
                  />
                  <span class="sender-name">{{ message.sender }}</span>
                </div>
                <span class="message-time">{{ formatTime(message.timestamp) }}</span>
              </div>

              <div class="message-content">
                <p>{{ message.content }}</p>

                <!-- Product recommendations -->
                <div v-if="message.product_recommendations && message.product_recommendations.length > 0" class="product-recommendations">
                  <h4 class="recommendations-title">Gợi ý sản phẩm:</h4>
                  <div class="recommendations-list">
                    <div
                      v-for="product in message.product_recommendations"
                      :key="product.san_pham_chi_tiet_id"
                      class="product-card"
                    >
                      <div class="product-info">
                        <h5 class="product-name">{{ product.ten_san_pham }}</h5>
                        <p class="product-description">{{ product.mo_ta }}</p>
                        <div class="product-price">
                          {{ formatPrice(product.gia_ban) }}
                        </div>
                        <div class="similarity-score">
                          Độ phù hợp: {{ Math.round(product.similarity_score * 100) }}%
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Simple AI typing indicator -->
          <div v-if="isAiTyping" class="typing-indicator">
            <div class="typing-bubble">
              <div class="typing-dots">
                <span></span>
                <span></span>
                <span></span>
              </div>
              <div class="typing-content">
                <span class="typing-text">AI đang soạn tin nhắn...</span>
              </div>
            </div>
          </div>
        </div>
      </ScrollPanel>
    </div>

    <!-- Input area -->
    <div class="chat-input">
      <div class="input-container">
        <div class="input-wrapper">
          <InputText
            v-model="currentMessage"
            placeholder="Nhập tin nhắn của bạn..."
            class="message-input"
            :disabled="!isConnected || isAiTyping"
            @keyup.enter="handleSendMessage"
            autofocus
          />
          <Button
            icon="pi pi-send"
            class="send-button"
            :disabled="!isConnected || !currentMessage.trim() || isAiTyping"
            @click="handleSendMessage"
          />
        </div>

        <!-- Quick actions -->
        <div class="quick-actions">
          <Button
            label="Laptop gaming"
            size="small"
            outlined
            @click="sendQuickMessage('Tôi cần tư vấn laptop gaming')"
            :disabled="!isConnected || isAiTyping"
          />
          <Button
            label="Laptop văn phòng"
            size="small"
            outlined
            @click="sendQuickMessage('Tôi cần laptop cho công việc văn phòng')"
            :disabled="!isConnected || isAiTyping"
          />
          <Button
            label="Laptop học tập"
            size="small"
            outlined
            @click="sendQuickMessage('Tôi cần laptop cho học tập')"
            :disabled="!isConnected || isAiTyping"
          />
        </div>
      </div>
    </div>

    <!-- Connection error dialog -->
    <Dialog
      v-model:visible="showConnectionError"
      modal
      header="Lỗi kết nối"
      style="width: 400px"
    >
      <div class="error-content">
        <i class="pi pi-exclamation-triangle text-red-500 text-2xl mb-3"></i>
        <p>{{ connectionError }}</p>
      </div>
      <template #footer>
        <Button label="Thử lại" @click="retryConnection" />
        <Button label="Đóng" @click="showConnectionError = false" outlined />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
// Vue imports
import { ref, watch, nextTick, onMounted } from 'vue'

// Composables
import { useAiChatWebSocket } from '@/composables/useAiChatWebSocket'

// PrimeVue components
import ScrollPanel from 'primevue/scrollpanel'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Avatar from 'primevue/avatar'
import Dialog from 'primevue/dialog'

// WebSocket composable
const {
  isConnected,
  connectionError,
  messages,
  isAiTyping,
  connect,
  sendMessage
} = useAiChatWebSocket()

// ================== LOCAL STATE ==================
const currentMessage = ref('')
const messagesContainer = ref(null)
const showConnectionError = ref(false)

// ================== METHODS ==================
const isUserMessage = (message) => {
  // Define known sender types for defensive programming
  const userSenders = ['Khách hàng']
  const userMessageTypes = ['USER_MESSAGE', 'JOIN', 'LEAVE']
  const aiSenders = ['AI Assistant', 'AI_ASSISTANT'] // Handle both backend formats
  const systemSenders = ['System', 'Hệ thống']

  // Check if message is explicitly from user
  if (userSenders.includes(message.sender) || userMessageTypes.includes(message.message_type)) {
    return true
  }

  // Check if message is explicitly from AI or system (not user)
  if (aiSenders.includes(message.sender) ||
      systemSenders.includes(message.sender) ||
      message.message_type === 'AI_RESPONSE' ||
      message.message_type === 'SYSTEM_ERROR' ||
      message.message_type === 'ERROR') {
    return false
  }

  // Enhanced fallback logic: Check for AI-related senders before defaulting to user classification
  // This fixes the issue where AI messages were incorrectly classified as user messages
  if (message.sender && (
    message.sender.includes('AI') ||
    message.sender.includes('Assistant') ||
    message.sender.includes('Bot')
  )) {
    console.debug('AI message detected via fallback logic:', message.sender)
    return false
  }

  // Final fallback: Default to user message only for truly unknown cases
  // This is safer than assuming AI messages, as misclassified user messages are less problematic
  console.debug('Unknown message type, defaulting to user:', message.sender, message.message_type)
  return true
}

const getSenderInitial = (sender) => {
  // Handle both AI sender formats for consistent avatar display
  if (sender === 'AI Assistant' || sender === 'AI_ASSISTANT') return 'AI'

  // Handle system messages
  if (sender === 'System' || sender === 'Hệ thống') return 'SYS'

  // Defensive handling for empty or null sender
  if (!sender || typeof sender !== 'string') return '?'

  // Default: return first character uppercase
  return sender.charAt(0).toUpperCase()
}

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleTimeString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatPrice = (price) => {
  if (!price) return 'Liên hệ'
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND'
  }).format(price)
}

const handleSendMessage = () => {
  if (!currentMessage.value.trim() || !isConnected.value || isAiTyping.value) {
    return
  }

  const success = sendMessage(currentMessage.value)

  if (success) {
    currentMessage.value = ''
  }
}



const sendQuickMessage = (message) => {
  if (!isConnected.value || isAiTyping.value) return

  sendMessage(message)
}

const retryConnection = async () => {
  showConnectionError.value = false
  await connect()
}

const scrollToBottom = () => {
  nextTick(() => {
    const container = messagesContainer.value
    if (container) {
      const scrollPanel = container.querySelector('.p-scrollpanel-content')
      if (scrollPanel) {
        scrollPanel.scrollTop = scrollPanel.scrollHeight
      }
    }
  })
}

// Watchers
watch(messages, () => {
  scrollToBottom()
}, { deep: true })

watch(connectionError, (error) => {
  if (error) {
    showConnectionError.value = true
  }
})

// Lifecycle
onMounted(async () => {
  await connect()
})
</script>

<style scoped>
.ai-chat-interface {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-height: 800px;
  background: #f8f9fa;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.chat-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 1rem;
  border-bottom: 1px solid #e0e0e0;
}

.header-content {
  display: flex;
  justify-content: center;
  align-items: center;
}

.chat-title {
  display: flex;
  align-items: center;
}

.chat-messages {
  flex: 1;
  overflow: hidden;
  background: white;
}

.messages-content {
  padding: 1rem;
  min-height: 100%;
}

.welcome-message {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 300px;
  text-align: center;
}

.welcome-content {
  max-width: 400px;
}

.message-wrapper {
  margin-bottom: 1rem;
  display: flex;
}

.user-message {
  justify-content: flex-end;
}

.ai-message {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 70%;
  background: white;
  border-radius: 12px;
  padding: 0.75rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.user-message .message-bubble {
  background: #007bff;
  color: white;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
  font-size: 0.75rem;
  opacity: 0.8;
}

.sender-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.user-avatar {
  background: #28a745;
}

.ai-avatar {
  background: #6f42c1;
}

.message-content p {
  margin: 0;
  line-height: 1.4;
}

.product-recommendations {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}

.recommendations-title {
  font-size: 0.9rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.product-card {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 0.75rem;
  margin-bottom: 0.5rem;
}

.product-name {
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.product-description {
  font-size: 0.8rem;
  opacity: 0.9;
  margin-bottom: 0.5rem;
}

.product-price {
  font-weight: 600;
  color: #ffc107;
}

.similarity-score {
  font-size: 0.75rem;
  opacity: 0.8;
  margin-top: 0.25rem;
}

.typing-indicator {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 1rem;
}

.typing-bubble {
  background: #f1f3f4;
  border-radius: 12px;
  padding: 0.75rem;
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  max-width: 80%;
}

.typing-content {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.typing-text {
  font-size: 0.9rem;
  color: #333;
}



.typing-dots {
  display: flex;
  gap: 0.25rem;
}

.typing-dots span {
  width: 6px;
  height: 6px;
  background: #666;
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out;
}

.typing-dots span:nth-child(1) { animation-delay: -0.32s; }
.typing-dots span:nth-child(2) { animation-delay: -0.16s; }

@keyframes typing {
  0%, 80%, 100% { transform: scale(0.8); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

.chat-input {
  background: white;
  border-top: 1px solid #e0e0e0;
  padding: 1rem;
}

.input-wrapper {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.message-input {
  flex: 1;
}

.send-button {
  min-width: 50px;
}

.quick-actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.error-content {
  text-align: center;
  padding: 1rem;
}

/* Responsive design */
@media (max-width: 768px) {
  .ai-chat-interface {
    height: 100vh;
    border-radius: 0;
  }

  .message-bubble {
    max-width: 85%;
  }

  .quick-actions {
    flex-direction: column;
  }

  .quick-actions .p-button {
    width: 100%;
  }
}




</style>
