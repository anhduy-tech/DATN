<template>
  <div class="ai-chat-page">
    <div class="page-header">
      <div class="header-content">
        <div class="title-section">
          <h1 class="page-title">
            <i class="pi pi-robot mr-2"></i>
            Trợ Lý AI LapXpert
          </h1>
          <p class="page-subtitle">
            Tư vấn và hỗ trợ tìm kiếm sản phẩm laptop thông minh
          </p>
        </div>
        
        <div class="header-actions">
          <Button
            label="Xóa lịch sử"
            icon="pi pi-trash"
            outlined
            size="small"
            @click="confirmClearHistory"
            :disabled="!hasMessages"
          />
          <Button
            label="Hướng dẫn"
            icon="pi pi-question-circle"
            outlined
            size="small"
            @click="showHelp = true"
          />
        </div>
      </div>
    </div>

    <div class="chat-container">
      <div class="chat-wrapper">
        <AiChatInterface ref="chatInterface" />
      </div>
      
      <!-- Sidebar với thông tin hỗ trợ -->
      <div class="chat-sidebar" v-if="showSidebar">
        <Card class="help-card">
          <template #header>
            <div class="card-header">
              <h3>Gợi ý sử dụng</h3>
              <Button
                icon="pi pi-times"
                text
                size="small"
                @click="showSidebar = false"
              />
            </div>
          </template>
          <template #content>
            <div class="help-content">
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

    <!-- Help Dialog -->
    <Dialog 
      v-model:visible="showHelp" 
      modal 
      header="Hướng dẫn sử dụng Trợ Lý AI"
      style="width: 600px"
    >
      <div class="help-dialog-content">
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
    <Dialog 
      v-model:visible="showClearConfirm" 
      modal 
      header="Xác nhận xóa lịch sử"
      style="width: 400px"
    >
      <div class="confirm-content">
        <i class="pi pi-exclamation-triangle text-orange-500 text-2xl mb-3"></i>
        <p>Bạn có chắc chắn muốn xóa toàn bộ lịch sử trò chuyện?</p>
        <p class="text-sm text-gray-600 mt-2">Hành động này không thể hoàn tác.</p>
      </div>
      
      <template #footer>
        <Button label="Hủy" @click="showClearConfirm = false" outlined />
        <Button label="Xóa" @click="clearChatHistory" severity="danger" />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AiChatInterface from './AiChatInterface.vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'

// Refs
const chatInterface = ref(null)
const showSidebar = ref(true)
const showHelp = ref(false)
const showClearConfirm = ref(false)

// Computed
const hasMessages = computed(() => {
  return chatInterface.value?.messages?.length > 0
})

// Methods
const confirmClearHistory = () => {
  if (hasMessages.value) {
    showClearConfirm.value = true
  }
}

const clearChatHistory = () => {
  if (chatInterface.value) {
    chatInterface.value.clearMessages()
  }
  showClearConfirm.value = false
}

// Lifecycle
onMounted(() => {
  // Auto-hide sidebar on mobile
  if (window.innerWidth < 1024) {
    showSidebar.value = false
  }
})
</script>

<style scoped>
.ai-chat-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f8f9fa;
}

.page-header {
  background: white;
  border-bottom: 1px solid #e0e0e0;
  padding: 1rem 2rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  max-width: 1400px;
  margin: 0 auto;
}

.title-section {
  flex: 1;
}

.page-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: #2c3e50;
  margin: 0;
  display: flex;
  align-items: center;
}

.page-subtitle {
  color: #6c757d;
  margin: 0.25rem 0 0 0;
  font-size: 0.95rem;
}

.header-actions {
  display: flex;
  gap: 0.75rem;
}

.chat-container {
  flex: 1;
  display: flex;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
  overflow: hidden;
}

.chat-wrapper {
  flex: 1;
  padding: 1rem;
  min-width: 0;
}

.chat-sidebar {
  width: 300px;
  padding: 1rem;
  background: #f8f9fa;
  border-left: 1px solid #e0e0e0;
}

.help-card {
  height: fit-content;
  position: sticky;
  top: 1rem;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border-bottom: 1px solid #e0e0e0;
}

.card-header h3 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
}

.help-content {
  padding: 1rem;
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

.confirm-content {
  text-align: center;
  padding: 1rem;
}

/* Responsive design */
@media (max-width: 1024px) {
  .chat-container {
    flex-direction: column;
  }
  
  .chat-sidebar {
    width: 100%;
    border-left: none;
    border-top: 1px solid #e0e0e0;
    max-height: 200px;
    overflow-y: auto;
  }
  
  .page-header {
    padding: 1rem;
  }
  
  .header-content {
    flex-direction: column;
    gap: 1rem;
    align-items: flex-start;
  }
  
  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }
}

@media (max-width: 768px) {
  .ai-chat-page {
    height: 100vh;
  }
  
  .chat-wrapper {
    padding: 0.5rem;
  }
  
  .page-title {
    font-size: 1.5rem;
  }
  
  .header-actions {
    flex-direction: column;
    width: 100%;
  }
  
  .header-actions .p-button {
    width: 100%;
  }
}
</style>
