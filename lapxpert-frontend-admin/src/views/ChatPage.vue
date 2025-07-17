<template>
  <div class="chat-page p-4">
    <h1 class="text-2xl font-bold mb-4">Chat Thời Gian Thực</h1>
    <Card class="chat-container">
      <template #content>
        <!-- Nhập thông tin người dùng -->
        <div class="p-field mb-3">
          <label for="username" class="block mb-2">Tên người dùng</label>
          <InputText id="username" v-model="username" placeholder="Nhập tên người dùng" class="w-full" />
        </div>
        <div class="p-field mb-3">
          <label for="sessionId" class="block mb-2">Phòng chat </label>
          <InputText id="sessionId" v-model="sessionId" placeholder="Nhập ID phòng (e.g., room123)" class="w-full" />
        </div>
        <Button label="Kết nối" icon="pi pi-link" @click="connect" :disabled="!username || !sessionId" class="mb-3" />


        <!-- Danh sách tin nhắn -->
        <ScrollPanel style="height: 400px" class="mb-3">
          <div v-for="(msg, index) in messages" :key="index" class="p-2 border-bottom-1 border-gray-200" :class="{ 'own-message': msg.sender === username, 'private-message': msg.isPrivate }">
            <img :src="getAvatar(msg.sender)" class="avatar" />
            <strong>{{ msg.sender }} ({{ formatTimestamp(msg.timestamp) }})</strong>
            <span v-if="msg.isPrivate" class="private-label">[Riêng tư]</span>: {{ msg.content }}
          </div>
        </ScrollPanel>

        <!-- Gửi tin nhắn công khai -->
        <div class="mb-3">
          <h4 class="text-sm font-semibold mb-2">Tin nhắn công khai</h4>
          <div class="flex gap-2">
            <InputText v-model="publicMessage" placeholder="Nhập tin nhắn công khai" class="w-full" @keyup.enter="sendPublic" />
            <Button icon="pi pi-send" @click="sendPublic" :disabled="!isConnected || !publicMessage" />
          </div>
        </div>
      </template>
    </Card>
  </div>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import ScrollPanel from 'primevue/scrollpanel'
import { connectWebSocket, disconnectWebSocket, sendPublicMessage, sendJoinNotification } from '@/apis/chat'

const username = ref('')
const sessionId = ref('chatTest')
const publicMessage = ref('')
const messages = ref([])
const isConnected = ref(false)

// Format timestamp
const formatTimestamp = (timestamp) => {
  return new Date(timestamp).toLocaleString('vi-VN')
}

// Tạo avatar
const getAvatar = (sender) => `https://ui-avatars.com/api/?name=${sender}&size=24`

// Kết nối WebSocket
const connect = async () => {
  // Ngắt kết nối hiện tại nếu có
  disconnectWebSocket(() => {
    isConnected.value = false
    console.log('Đã ngắt kết nối WebSocket trước khi đổi phòng')
  })

  // Reset danh sách tin nhắn
  messages.value = []

  connectWebSocket(
    username.value,
    sessionId.value,
    (message) => {
      messages.value.push(message)
      console.log(`Tin nhắn công khai: ${message.sender}: ${message.content}`)
    },
    (message) => {
      messages.value.push(message)
      console.log(`Tin nhắn riêng tư: ${message.sender}: ${message.content}`)
    },
    (message) => {
      console.log(`Người mới tham gia: ${message.username} đã tham gia phòng ${message.sessionId}`)
    },
    () => {
      isConnected.value = true
      console.log('Kết nối WebSocket thành công')
      sendJoinNotification(sessionId.value, username.value).catch((error) => {
        console.error(`Lỗi gửi thông báo tham gia: ${error.message}`)
      })
    },
    (error) => {
      isConnected.value = false
      console.error(`Lỗi: ${error}`)
    }
  )
}

// Gửi tin nhắn công khai
const sendPublic = async () => {
  try {
    await sendPublicMessage(sessionId.value, username.value, publicMessage.value)
    publicMessage.value = ''
  } catch (error) {
    console.error(`Lỗi gửi tin nhắn công khai: ${error.message}`)
  }
}

// Tự động cuộn xuống tin nhắn mới nhất
watch(messages, () => {
  setTimeout(() => {
    const container = document.querySelector('.p-scrollpanel-content')
    if (container) container.scrollTop = container.scrollHeight
  }, 100)
})

// Ngắt kết nối khi component bị hủy
onUnmounted(() => {
  disconnectWebSocket(() => {
    isConnected.value = false
    console.log('Đã ngắt kết nối WebSocket')
  })
})
</script>

<style scoped>
.chat-page {
  max-width: 800px;
  margin: 0 auto;
}
.chat-container {
  background: white;
  padding: 1rem;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
.connection-status.connected {
  color: green;
}
.connection-status.disconnected {
  color: red;
}
.own-message {
  background-color: #e6f3ff;
  border-radius: 8px;
  padding: 0.5rem;
}
.private-message {
  background-color: #e6ffe6;
  border-radius: 8px;
  padding: 0.5rem;
}
.private-label {
  color: green;
  font-weight: bold;
  margin-right: 0.5rem;
}
.avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  margin-right: 8px;
  vertical-align: middle;
}
</style>
