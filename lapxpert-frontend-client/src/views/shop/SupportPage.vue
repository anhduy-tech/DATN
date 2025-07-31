<template>
  <div class="support-page">
    <!-- Main Page Content -->
    <div class="main-content">
      <!-- Hero Section -->
      <section class="hero-section relative bg-cover bg-center py-20" style="background-image: url('https://images.unsplash.com/photo-1516321318423-24d798c7b2f0?ixlib=rb-4.0.3&auto=format&fit=crop&w=1920&q=80')">
        <div class="absolute inset-0 bg-black opacity-50"></div>
        <div class="container mx-auto px-4 text-center relative z-10">
          <h1 class="text-5xl font-bold text-white mb-4 animate-fade-in-down">Hỗ trợ LapXpert</h1>
          <p class="text-xl text-white mb-8 max-w-3xl mx-auto animate-fade-in-up">
            Tìm kiếm laptop hoàn hảo? Đội ngũ hỗ trợ 24/7 và Trợ lý AI của chúng tôi sẵn sàng giúp bạn chọn sản phẩm phù hợp nhất!
          </p>
          <Button
            label="Chat ngay"
            icon="pi pi-comments"
            class="p-button-lg p-button-rounded bg-white text-primary-600 hover:bg-primary-100 transition-colors"
            @click="isExpanded = true"
          />
        </div>
      </section>

      <!-- Features Section -->
      <section class="features-section py-16 bg-gray-50">
        <div class="container mx-auto px-4">
          <h2 class="text-3xl font-bold text-center mb-12">Tại sao chọn hỗ trợ LapXpert?</h2>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-8">
            <Card class="feature-card shadow-lg hover:shadow-xl transition-shadow">
              <template #content>
                <div class="text-center">
                  <i class="pi pi-robot text-4xl text-primary-600 mb-4"></i>
                  <h3 class="text-xl font-semibold mb-2">Trợ lý AI thông minh</h3>
                  <p class="text-gray-600">Nhận gợi ý laptop phù hợp chỉ trong vài giây với AI của chúng tôi.</p>
                </div>
              </template>
            </Card>
            <Card class="feature-card shadow-lg hover:shadow-xl transition-shadow">
              <template #content>
                <div class="text-center">
                  <i class="pi pi-users text-4xl text-primary-600 mb-4"></i>
                  <h3 class="text-xl font-semibold mb-2">Hỗ trợ con người 24/7</h3>
                  <p class="text-gray-600">Liên hệ với đội ngũ chuyên gia của chúng tôi bất cứ lúc nào.</p>
                </div>
              </template>
            </Card>
            <Card class="feature-card shadow-lg hover:shadow-xl transition-shadow">
              <template #content>
                <div class="text-center">
                  <i class="pi pi-star text-4xl text-primary-600 mb-4"></i>
                  <h3 class="text-xl font-semibold mb-2">Tư vấn cá nhân hóa</h3>
                  <p class="text-gray-600">Giải pháp tùy chỉnh theo ngân sách và nhu cầu của bạn.</p>
                </div>
              </template>
            </Card>
          </div>
        </div>
      </section>

      <!-- FAQ Section -->
      <section class="faq-section py-16">
        <div class="container  ">
          <h2 class="text-3xl font-bold text-center mb-12">Câu hỏi thường gặp</h2>
          <Accordion :activeIndex="0" class=" mx-auto">
            <AccordionTab header="Làm thế nào để chọn laptop phù hợp với nhu cầu?">
              <p class="text-gray-700">
                Xác định mục đích sử dụng (gaming, văn phòng, học tập), ngân sách, và các yêu cầu về cấu hình như CPU, RAM, hoặc dung lượng lưu trữ. Sử dụng Trợ lý AI hoặc liên hệ nhân viên hỗ trợ để nhận gợi ý chi tiết!
              </p>
            </AccordionTab>
            <AccordionTab header="Chính sách bảo hành của LapXpert như thế nào?">
              <p class="text-gray-700">
                Tất cả sản phẩm được bảo hành từ 12 đến 36 tháng tùy model. Bạn có thể kiểm tra thông tin bảo hành trên trang sản phẩm hoặc liên hệ chúng tôi để được hướng dẫn.
              </p>
            </AccordionTab>
            <AccordionTab header="Tôi có thể đổi trả sản phẩm trong bao lâu?">
              <p class="text-gray-700">
                LapXpert hỗ trợ đổi trả trong 7 ngày nếu sản phẩm lỗi do nhà sản xuất. Vui lòng giữ nguyên hộp và phụ kiện để quá trình xử lý nhanh chóng.
              </p>
            </AccordionTab>
            <AccordionTab header="Làm thế nào để liên hệ hỗ trợ trực tiếp?">
              <p class="text-gray-700">
                Bạn có thể sử dụng bong bóng chat ở góc phải dưới để trò chuyện với AI hoặc nhân viên hỗ trợ, hoặc gọi hotline 1900-XXXX, email support@lapxpert.com.
              </p>
            </AccordionTab>
          </Accordion>
        </div>
      </section>
    </div>

    <!-- Chat Bubble -->
    <div class="chat-bubble" :style="{ top: position.y + 'px', left: position.x + 'px' }" ref="chatBubble">
      <!-- Collapsed bubble icon -->
      <div v-if="!isExpanded" class="bubble-icon" @click="toggleChat" @mousedown="startDragging">
        <i class="pi pi-comments text-3xl text-white"></i>
      </div>

      <!-- Expanded chat window -->
      <div v-if="isExpanded" class="chat-window shadow-lg rounded-lg bg-white" :style="{ width: windowSize.width + 'px', maxHeight: windowSize.height + 'px' }">
        <div class="chat-header p-2 bg-primary-600 text-white flex justify-between items-center" @mousedown="startDragging">
          <h3 class="text-lg font-bold">Trung tâm Hỗ trợ</h3>
          <div class="header-actions flex gap-2">
            <Button
              icon="pi pi-trash"
              class="p-button-text p-button-sm"
              @click="confirmClearHistory"
              :disabled="!hasMessages"
            />
            <Button
              icon="pi pi-question-circle"
              class="p-button-text p-button-sm"
              @click="showHelp = true"
            />
            <Button
              icon="pi pi-info-circle"
              class="p-button-text p-button-sm"
              @click="showSidebar = !showSidebar"
            />
            <Button icon="pi pi-times" class="p-button-text p-button-sm" @click="toggleChat" />
          </div>
        </div>

        <div class="chat-content flex">
          <!-- Main chat area -->
          <div class="chat-main p-2">
            <!-- AI Chat Interface -->
            <div v-if="!isHumanSupport" class="ai-chat-wrapper">
              <ScrollPanel :style="{ height: (windowSize.height - 150) + 'px', width: '100%' }">
                <div class="messages-content p-2">
                  <!-- Welcome message -->
                  <div v-if="aiMessages.length === 0" class="welcome-message">
                    <div class="welcome-content">
                      <i class="pi pi-robot text-4xl text-blue-500 mb-3"></i>
                      <h3 class="text-lg font-semibold mb-2">Chào mừng đến với Trợ Lý AI!</h3>
                      <p class="text-gray-600">Tôi có thể giúp bạn tìm kiếm và tư vấn sản phẩm laptop. Hãy bắt đầu bằng cách gửi tin nhắn!</p>
                    </div>
                  </div>

                  <!-- AI Chat messages -->
                  <div
                    v-for="message in aiMessages"
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
                                <div class="product-price">{{ formatPrice(product.gia_ban) }}</div>
                                <div class="similarity-score">Độ phù hợp: {{ Math.round(product.similarity_score * 100) }}%</div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- AI Typing indicator -->
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

              <!-- Input area for AI -->
              <div class="chat-input p-2">
                <div class="input-container">
                  <div class="input-wrapper">
                    <InputText
                      v-model="currentMessage"
                      placeholder="Nhập tin nhắn của bạn..."
                      class="message-input w-full"
                      :disabled="!isAiConnected || isAiTyping"
                      @keyup.enter="handleSendMessage"
                      autofocus
                    />
                    <Button
                      icon="pi pi-send"
                      class="send-button"
                      :disabled="!isAiConnected || !currentMessage.trim() || isAiTyping"
                      @click="handleSendMessage"
                    />
                  </div>
                  <div class="quick-actions">
                    <Button
                      label="Laptop gaming"
                      size="small"
                      outlined
                      @click="sendQuickMessage('Tôi cần tư vấn laptop gaming')"
                      :disabled="!isAiConnected || isAiTyping"
                    />
                    <Button
                      label="Laptop văn phòng"
                      size="small"
                      outlined
                      @click="sendQuickMessage('Tôi cần laptop cho công việc văn phòng')"
                      :disabled="!isAiConnected || isAiTyping"
                    />
                    <Button
                      label="Laptop học tập"
                      size="small"
                      outlined
                      @click="sendQuickMessage('Tôi cần laptop cho học tập')"
                      :disabled="!isAiConnected || isAiTyping"
                    />
                  </div>
                </div>
              </div>
            </div>

            <!-- Human Support Chat Interface -->
            <div v-if="isHumanSupport" class="human-chat-wrapper">
              <div class="chat-header-info mb-2 flex justify-center items-center">
                <div v-if="participants.length <= 1" class="text-center text-gray-500">
                  Đang đợi kết nối...
                </div>
              </div>

              <ScrollPanel :style="{ height: (windowSize.height - 150) + 'px' }" class="mb-2 border p-2 rounded">
                <div v-for="(msg, index) in humanMessages" :key="index" class="message-item mb-2" :class="{ 'text-right': msg.sender === username }">
                  <div class="message-bubble inline-block p-2 rounded-lg" :class="msg.sender === username ? 'bg-blue-500 text-white' : 'bg-gray-200'">
                    <strong class="block">{{ msg.sender }}</strong>
                    <span>{{ msg.content }}</span>
                    <div class="text-xs opacity-75 mt-1">{{ formatTimestamp(msg.timestamp) }}</div>
                  </div>
                </div>
              </ScrollPanel>

              <div class="flex gap-2">
                <InputText v-model="newMessage" placeholder="Nhập tin nhắn..." class="w-full" @keyup.enter="sendHumanMessage" :disabled="!isHumanConnected || participants.length <= 1" />
                <Button icon="pi pi-send" @click="sendHumanMessage" :disabled="!isHumanConnected || !newMessage.trim() || participants.length <= 1" />
              </div>
            </div>
          </div>

          <!-- Sidebar -->
          <div v-if="showSidebar && isExpanded" class="chat-sidebar p-2">
            <Card class="help-card">
              <template #header>
                <div class="card-header flex justify-between items-center p-2">
                  <h3>Gợi ý sử dụng</h3>
                  <Button icon="pi pi-times" text size="small" @click="showSidebar = false" />
                </div>
              </template>
              <template #content>
                <div class="help-content p-2">
                  <div class="help-section">
                    <h4>Câu hỏi mẫu:</h4>
                    <ul class="help-list">
                      <li>"Tôi cần laptop gaming dưới 30 triệu"</li>
                      <li>"Laptop nào phù hợp cho sinh viên?"</li>
                      <li>"So sánh laptop Dell và HP"</li>
                      <li>"Laptop có card đồ họa mạnh"</li>
                    </ul>
                  </div>
                  <div class="help-section">
                    <h4>Tính năng:</h4>
                    <ul class="help-list">
                      <li>Tư vấn sản phẩm thông minh</li>
                      <li>Gợi ý dựa trên nhu cầu</li>
                      <li>So sánh thông số kỹ thuật</li>
                      <li>Hỗ trợ 24/7</li>
                    </ul>
                  </div>
                  <div class="help-section">
                    <h4>Liên hệ hỗ trợ:</h4>
                    <div class="contact-info">
                      <p><i class="pi pi-phone mr-2"></i>1900-xxxx</p>
                      <p><i class="pi pi-envelope mr-2"></i>support@lapxpert.com</p>
                    </div>
                  </div>
                </div>
              </template>
            </Card>
          </div>
        </div>

        <!-- Resize handle -->
        <div class="resize-handle" @mousedown="startResizing"></div>
      </div>

      <!-- Help Dialog -->
      <Dialog v-model:visible="showHelp" modal header="Hướng dẫn sử dụng Trợ Lý AI" style="width: 400px">
        <div class="help-dialog-content p-2">
          <div class="help-step">
            <div class="step-number">1</div>
            <div class="step-content">
              <h4>Bắt đầu cuộc trò chuyện</h4>
              <p>Nhập câu hỏi hoặc mô tả nhu cầu của bạn vào ô tin nhắn</p>
            </div>
          </div>
          <div class="help-step">
            <div class="step-number">2</div>
            <div class="step-content">
              <h4>Nhận gợi ý sản phẩm</h4>
              <p>AI sẽ phân tích và đưa ra những gợi ý sản phẩm phù hợp nhất</p>
            </div>
          </div>
          <div class="help-step">
            <div class="step-number">3</div>
            <div class="step-content">
              <h4>Tương tác và làm rõ</h4>
              <p>Đặt thêm câu hỏi để AI hiểu rõ hơn nhu cầu của bạn</p>
            </div>
          </div>
          <div class="help-step">
            <div class="step-number">4</div>
            <div class="step-content">
              <h4>Nhận tư vấn chi tiết</h4>
              <p>Nhận được thông tin chi tiết về sản phẩm và lời khuyên mua hàng</p>
            </div>
          </div>
        </div>
        <template #footer>
          <Button label="Đã hiểu" @click="showHelp = false" />
        </template>
      </Dialog>

      <!-- Clear History Confirmation -->
      <Dialog v-model:visible="showClearConfirm" modal header="Xác nhận xóa lịch sử" style="width: 400px">
        <div class="confirm-content p-2">
          <i class="pi pi-exclamation-triangle text-orange-500 text-2xl mb-3"></i>
          <p>Bạn có chắc chắn muốn xóa toàn bộ lịch sử trò chuyện?</p>
          <p class="text-sm text-gray-600 mt-2">Hành động này không thể hoàn tác.</p>
        </div>
        <template #footer>
          <Button label="Hủy" @click="showClearConfirm = false" outlined />
          <Button label="Xóa" @click="clearChatHistory" severity="danger" />
        </template>
      </Dialog>

      <!-- Connection Error Dialog -->
      <Dialog v-model:visible="showConnectionError" modal header="Lỗi kết nối" style="width: 400px">
        <div class="error-content p-2">
          <i class="pi pi-exclamation-triangle text-red-500 text-2xl mb-3"></i>
          <p>{{ connectionError }}</p>
        </div>
        <template #footer>
          <Button label="Thử lại" @click="retryConnection" />
          <Button label="Đóng" @click="showConnectionError = false" outlined />
        </template>
      </Dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onUnmounted, nextTick, computed, onMounted } from 'vue';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import ScrollPanel from 'primevue/scrollpanel';
import Avatar from 'primevue/avatar';
import Dialog from 'primevue/dialog';
import Card from 'primevue/card';
import Accordion from 'primevue/accordion';
import AccordionTab from 'primevue/accordiontab';
import { useAiChatWebSocket } from '@/composables/useAiChatWebSocket';
import { connectWebSocket, disconnectWebSocket, sendPublicMessage, sendJoinNotification } from '@/apis/chat';

// AI Chat WebSocket
const {
  isConnected: isAiConnected,
  connectionError,
  messages: aiMessages,
  isAiTyping,
  connect: connectAi,
  sendMessage: sendAiMessage
} = useAiChatWebSocket();

// Human Support WebSocket
const isHumanSupport = ref(false);
const humanMessages = ref([]);
const newMessage = ref('');
const currentMessage = ref('');
const isHumanConnected = ref(false);
const participants = ref([]);
const username = ref(`Khach_${Math.random().toString(36).substr(2, 5)}`);
const sessionId = ref(`support_${Math.random().toString(36).substr(2, 9)}`);

// Chat Bubble State
const isExpanded = ref(false);
const position = ref({ x: window.innerWidth - 60 - 20, y: window.innerHeight - 60 - 20 }); // Bottom-right corner
const windowSize = ref({ width: 400, height: 600 }); // Initial size
const isDragging = ref(false);
const isResizing = ref(false);
const dragOffset = ref({ x: 0, y: 0 });
const resizeOffset = ref({ x: 0, y: 0 });

// Sidebar and Dialogs
const showSidebar = ref(true);
const showHelp = ref(false);
const showClearConfirm = ref(false);
const showConnectionError = ref(false);

const hasMessages = computed(() => {
  return aiMessages.value.length > 0 || humanMessages.value.length > 0;
});

const formatTimestamp = (timestamp) => {
  return new Date(timestamp).toLocaleString('vi-VN');
};

const formatTime = (timestamp) => {
  if (!timestamp) return '';
  const date = new Date(timestamp);
  return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
};

const formatPrice = (price) => {
  if (!price) return 'Liên hệ';
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price);
};

const isUserMessage = (message) => {
  const userSenders = ['Khách hàng'];
  const userMessageTypes = ['USER_MESSAGE', 'JOIN', 'LEAVE'];
  const aiSenders = ['AI Assistant', 'AI_ASSISTANT'];
  const systemSenders = ['System', 'Hệ thống'];

  if (userSenders.includes(message.sender) || userMessageTypes.includes(message.message_type)) {
    return true;
  }

  if (
    aiSenders.includes(message.sender) ||
    systemSenders.includes(message.sender) ||
    message.message_type === 'AI_RESPONSE' ||
    message.message_type === 'SYSTEM_ERROR' ||
    message.message_type === 'ERROR'
  ) {
    return false;
  }

  if (message.sender && (message.sender.includes('AI') || message.sender.includes('Assistant') || message.sender.includes('Bot'))) {
    console.debug('AI message detected via fallback logic:', message.sender);
    return false;
  }

  console.debug('Unknown message type, defaulting to user:', message.sender, message.message_type);
  return true;
};

const getSenderInitial = (sender) => {
  if (sender === 'AI Assistant' || sender === 'AI_ASSISTANT') return 'AI';
  if (sender === 'System' || sender === 'Hệ thống') return 'SYS';
  if (!sender || typeof sender !== 'string') return '?';
  return sender.charAt(0).toUpperCase();
};

const connectHuman = () => {
  if (isHumanConnected.value) return;

  connectWebSocket(
    username.value,
    sessionId.value,
    (message) => {
      humanMessages.value.push(message);
    },
    (message) => {
      console.log('Private message received:', message);
    },
    (joinMessage) => {
      if (!participants.value.includes(joinMessage.username)) {
        participants.value.push(joinMessage.username);
      }
    },
    () => {
      isHumanConnected.value = true;
      sendJoinNotification(sessionId.value, username.value);
    },
    (error) => {
      isHumanConnected.value = false;
      console.error('WebSocket error:', error);
    }
  );
};

const handleSendMessage = () => {
  if (!currentMessage.value.trim() || !isAiConnected.value || isAiTyping.value) {
    return;
  }

  const messageContent = currentMessage.value.toLowerCase();
  if (!isHumanSupport.value && messageContent.includes('nhân viên') && messageContent.includes('hỗ trợ')) {
    isHumanSupport.value = true;
    connectHuman();
  }

  if (!isHumanSupport.value) {
    const success = sendAiMessage(currentMessage.value);
    if (success) {
      currentMessage.value = '';
    }
  }
};

const sendHumanMessage = () => {
  if (newMessage.value.trim() && isHumanConnected.value && participants.value.length > 1) {
    sendPublicMessage(sessionId.value, username.value, newMessage.value.trim());
    newMessage.value = '';
  }
};

const sendQuickMessage = (message) => {
  if (!isAiConnected.value || isAiTyping.value) return;

  const messageContent = message.toLowerCase();
  if (!isHumanSupport.value && messageContent.includes('nhân viên') && messageContent.includes('hỗ trợ')) {
    isHumanSupport.value = true;
    connectHuman();
  } else {
    sendAiMessage(message);
  }
};

const toggleChat = () => {
  isExpanded.value = !isExpanded.value;
  if (isExpanded.value) {
    // Position the expanded window to the left and slightly above, ensuring it stays within viewport
    const newX = Math.max(20, Math.min(position.value.x, window.innerWidth - windowSize.value.width - 20));
    const newY = Math.max(20, Math.min(position.value.y - windowSize.value.height + 60, window.innerHeight - windowSize.value.height - 20));
    position.value = { x: newX, y: newY };
    if (isHumanSupport.value) {
      connectHuman();
    }
  } else {
    // Return to bottom-right corner when collapsed
    position.value = {
      x: window.innerWidth - 60 - 20,
      y: window.innerHeight - 60 - 20,
    };
  }
};

const startDragging = (event) => {
  event.preventDefault(); // Prevent text selection or other default behaviors
  isDragging.value = true;
  const bubble = document.querySelector('.chat-bubble');
  const rect = bubble.getBoundingClientRect();
  dragOffset.value = {
    x: event.clientX - position.value.x,
    y: event.clientY - position.value.y,
  };
};

const onDrag = (event) => {
  if (isDragging.value) {
    const bubble = document.querySelector('.chat-bubble');
    const rect = bubble.getBoundingClientRect();
    const width = isExpanded.value ? windowSize.value.width : 60; // Use window width when expanded, bubble width when collapsed
    const height = isExpanded.value ? windowSize.value.height : 60; // Use window height when expanded, bubble height when collapsed

    position.value = {
      x: Math.max(0, Math.min(event.clientX - dragOffset.value.x, window.innerWidth - width)),
      y: Math.max(0, Math.min(event.clientY - dragOffset.value.y, window.innerHeight - height)),
    };
  }
};

const stopDragging = () => {
  isDragging.value = false;
};

const startResizing = (event) => {
  isResizing.value = true;
  resizeOffset.value = {
    x: event.clientX,
    y: event.clientY,
  };
};

const onResize = (event) => {
  if (isResizing.value) {
    const deltaX = event.clientX - resizeOffset.value.x;
    const deltaY = event.clientY - resizeOffset.value.y;
    windowSize.value = {
      width: Math.max(300, Math.min(600, windowSize.value.width + deltaX)),
      height: Math.max(400, Math.min(800, windowSize.value.height + deltaY)),
    };
    resizeOffset.value = {
      x: event.clientX,
      y: event.clientY,
    };
    // Adjust position to keep the window within viewport
    position.value = {
      x: Math.max(0, Math.min(position.value.x, window.innerWidth - windowSize.value.width)),
      y: Math.max(0, Math.min(position.value.y, window.innerHeight - windowSize.value.height)),
    };
  }
};

const stopResizing = () => {
  isResizing.value = false;
};

window.addEventListener('mousemove', onDrag);
window.addEventListener('mouseup', stopDragging);
window.addEventListener('mousemove', onResize);
window.addEventListener('mouseup', stopResizing);

watch(humanMessages, () => {
  nextTick(() => {
    const container = document.querySelector('.human-chat-wrapper .p-scrollpanel-content');
    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  });
}, { deep: true });

watch(aiMessages, () => {
  nextTick(() => {
    const container = document.querySelector('.ai-chat-wrapper .p-scrollpanel-content');
    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  });
}, { deep: true });

watch(connectionError, (error) => {
  if (error) {
    showConnectionError.value = true;
  }
});

onMounted(async () => {
  await connectAi();
  const updatePosition = () => {
    if (!isExpanded.value) {
      position.value = {
        x: window.innerWidth - 60 - 20,
        y: window.innerHeight - 60 - 20,
      };
    }
  };
  window.addEventListener('resize', updatePosition);
  onUnmounted(() => window.removeEventListener('resize', updatePosition));
});
</script>

<style scoped>
.support-page {
  position: relative;
  min-height: 100vh;
}

.main-content {
  padding-bottom: 80px; /* Space for chat bubble */
}

.chat-bubble {
  position: fixed;
  z-index: 1000;
}

.bubble-icon {
  width: 60px;
  height: 60px;
  background-color: #2563eb;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: move;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
}

.chat-window {
  overflow: hidden;
  display: flex;
  flex-direction: column;
  position: relative;
}

.chat-header {
  cursor: move;
}

.chat-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.chat-main {
  flex: 1;
  min-width: 0;
}

.chat-sidebar {
  width: 200px;
  background: #f8f9fa;
  border-left: 1px solid #e0e0e0;
}

.ai-chat-wrapper,
.human-chat-wrapper {
  min-height: 400px;
  display: flex;
  flex-direction: column;
}

.messages-content {
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
  max-width: 300px;
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
  border-radius: 12px;
  padding: 0.75rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  font-weight: 500;
  text-shadow: 0 1px 1px rgba(0, 0, 0, 0.05);
}

.ai-message .message-bubble {
  background: #fff;
  color: #333;
}

.user-message .message-bubble {
  background: #007bff;
  color: #fff;
}

.human-chat-wrapper .message-bubble {
  background: #e5e7eb;
  color: #333;
}

.human-chat-wrapper .user-message .message-bubble {
  background: #2563eb;
  color: #fff;
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
  border-top: 1px solid #e0e0e0;
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

.help-card {
  height: fit-content;
}

.card-header h3 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
}

.help-section {
  margin-bottom: 1.5rem;
}

.help-section h4 {
  font-size: 0.95rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
  color: #495057;
}

.help-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.help-list li {
  padding: 0.25rem 0;
  font-size: 0.85rem;
  color: #6c757d;
  position: relative;
  padding-left: 1rem;
}

.help-list li::before {
  content: "•";
  color: #007bff;
  position: absolute;
  left: 0;
}

.contact-info p {
  margin: 0.25rem 0;
  font-size: 0.85rem;
  color: #6c757d;
  display: flex;
  align-items: center;
}

.help-dialog-content {
  padding: 1rem 0;
}

.help-step {
  display: flex;
  align-items: flex-start;
  margin-bottom: 1.5rem;
}

.step-number {
  width: 32px;
  height: 32px;
  background: #007bff;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 0.9rem;
  margin-right: 1rem;
  flex-shrink: 0;
}

.step-content h4 {
  margin: 0 0 0.5rem 0;
  font-size: 1rem;
  font-weight: 600;
}

.step-content p {
  margin: 0;
  color: #6c757d;
  line-height: 1.4;
}

.confirm-content,
.error-content {
  text-align: center;
  padding: 1rem;
}

.resize-handle {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 15px;
  height: 15px;
  background: #2563eb;
  cursor: nwse-resize;
  border-top-left-radius: 4px;
}

/* Additional styles for main content */
.hero-section {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.features-section,
.faq-section,
.contact-section,
.footer {
  padding: 4rem 1rem;
}

.feature-card {
  border-radius: 8px;
  padding: 1.5rem;
}

.animate-fade-in-down {
  animation: fadeInDown 1s ease-out;
}

.animate-fade-in-up {
  animation: fadeInUp 1s ease-out;
}

@keyframes fadeInDown {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>