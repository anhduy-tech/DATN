```vue
<template>
  <div class="chat-page flex flex-col min-h-[calc(100vh-160px)]">
    <div class="flex flex-col lg:flex-row gap-4 flex-1">
      <!-- Khu vực chat (70%) -->
      <div class="w-full lg:w-[70%] bg-white p-4 rounded-lg shadow-md flex flex-col">
        <!-- Trạng thái kết nối -->
        <div class="mb-4 flex items-center">
          <span :class="isConnected ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'" class="px-2 py-1 rounded text-sm font-medium">
            {{ isConnected ? 'Đã kết nối' : 'Chưa kết nối' }}
          </span>
          <span v-if="sessionId" class="ml-2 text-sm">Phòng hiện tại: {{ sessionId }}</span>
        </div>

        <!-- Danh sách tin nhắn -->
        <div class="flex-1 overflow-y-auto mb-4 border border-gray-200 rounded p-2 max-h-[calc(100vh-250px)]">
          <div v-for="(msg, index) in currentMessages" :key="index" class="p-2 border-b border-gray-200 flex" :class="{ 'justify-end': msg.sender === username, 'justify-start': msg.sender !== username }">
            <div class="max-w-[70%] flex" :class="{ 'flex-row-reverse': msg.sender === username }">
              <img :src="getAvatar(msg.sender)" class="w-6 h-6 rounded-full mx-2" />
              <div class="p-2 rounded-lg" :class="{ 'bg-blue-50': msg.sender === username, 'bg-gray-100': msg.sender !== username, 'bg-green-50': msg.isPrivate }">
                <div class="flex items-center">
                  <strong>{{ msg.sender }} ({{ formatTimestamp(msg.timestamp) }})</strong>
                  <span v-if="msg.isPrivate" class="ml-2 text-green-600 font-bold text-sm">[Riêng tư]</span>
                </div>
                <span>{{ msg.content }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Gửi tin nhắn công khai -->
        <div class="mb-3">
          <label class="block mb-2 font-semibold">Tin nhắn công khai</label>
          <div class="flex gap-2">
            <input v-model="publicMessage" placeholder="Nhập tin nhắn công khai" class="w-full p-2 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500" @keyup.enter="sendPublic" />
            <button class="bg-blue-500 text-white p-2 rounded hover:bg-blue-600 disabled:bg-gray-400" :disabled="!isConnected || !publicMessage" @click="sendPublic">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"></path>
              </svg>
            </button>
          </div>
        </div>
      </div>

      <!-- Danh sách phòng chat (30%) -->
      <div class="w-full lg:w-[30%] bg-white p-4 rounded-lg shadow-md overflow-y-auto">
        <div class="mb-3 flex justify-between items-center">
          <label class="block font-semibold text-lg">Danh sách phòng chat</label>
          <button class="bg-gray-500 text-white px-3 py-1 rounded hover:bg-gray-600" @click="loadActiveRooms">
            Làm mới
          </button>
        </div>
        <div v-if="loadingRooms" class="flex justify-center">
          <svg class="animate-spin h-8 w-8 text-gray-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
        </div>
        <div v-else-if="filteredRooms.length === 0" class="text-gray-500 text-center">Không có phòng chat nào đang hoạt động</div>
        <ul v-else class="space-y-2 max-h-[calc(100vh-150px)] overflow-y-auto">
          <li v-for="room in filteredRooms" :key="room.sessionId" class="p-2 border border-gray-200 rounded cursor-pointer hover:bg-gray-100 flex justify-between items-center" :class="{ 'bg-blue-100': room.sessionId === sessionId }" @click="connectToRoom(room.sessionId)">
            <span>Phòng: {{ room.sessionId }}</span>
            <button v-if="room.sessionId === sessionId" class="bg-red-500 text-white px-2 py-1 rounded text-sm hover:bg-red-600" @click.stop="disableRoom(room.sessionId)">Vô hiệu hóa</button>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, computed } from 'vue'
import { connectWebSocket, disconnectWebSocket, sendPublicMessage, sendJoinNotification, getActiveSessions, isWebSocketConnected } from '@/apis/chat'

const username = ref('Hỗ trợ viên') // Tên người dùng cố định
const sessionId = ref('') // sessionId của phòng hiện tại
const publicMessage = ref('')
const messagesByRoom = ref({}) // Lưu tin nhắn theo sessionId
const currentMessages = ref([]) // Tin nhắn của phòng hiện tại
const isConnected = ref(false)
const activeRooms = ref([])
const loadingRooms = ref(true)
const disabledRooms = ref(JSON.parse(localStorage.getItem('disabledRooms') || '[]')) // Lưu danh sách phòng bị vô hiệu hóa

// Lọc danh sách phòng để loại bỏ các phòng bị vô hiệu hóa
const filteredRooms = computed(() => activeRooms.value.filter(room => !disabledRooms.value.includes(room.sessionId)))

// Format timestamp
const formatTimestamp = (timestamp) => {
  return new Date(timestamp).toLocaleString('vi-VN')
}

// Tạo avatar
const getAvatar = (sender) => `https://ui-avatars.com/api/?name=${sender}&size=24`

// Lấy danh sách phòng chat đang hoạt động
const loadActiveRooms = async () => {
  try {
    loadingRooms.value = true
    const sessions = await getActiveSessions()
    activeRooms.value = sessions.map(sessionId => ({ sessionId }))
  } catch (error) {
    console.error('Lỗi tải danh sách phòng:', error.message)
  } finally {
    loadingRooms.value = false
  }
}

// Vô hiệu hóa phòng chat
const disableRoom = (sessionIdToDisable) => {
  if (!disabledRooms.value.includes(sessionIdToDisable)) {
    disabledRooms.value.push(sessionIdToDisable)
    localStorage.setItem('disabledRooms', JSON.stringify(disabledRooms.value))
  }
  if (sessionId.value === sessionIdToDisable) {
    disconnectWebSocket(() => {
      isConnected.value = false
      sessionId.value = ''
      currentMessages.value = []
      console.log('Đã ngắt kết nối WebSocket sau khi vô hiệu hóa phòng')
    })
  }
  loadActiveRooms() // Làm mới danh sách phòng
}

// Kết nối tới phòng chat
const connectToRoom = async (selectedSessionId) => {
  if (!selectedSessionId) return

  // Lưu tin nhắn của phòng hiện tại (nếu có)
  if (sessionId.value && currentMessages.value.length > 0) {
    messagesByRoom.value[sessionId.value] = [...currentMessages.value]
  }

  // Cập nhật sessionId
  sessionId.value = selectedSessionId

  // Load tin nhắn cũ của phòng (nếu có)
  currentMessages.value = messagesByRoom.value[sessionId.value] || []

  // Ngắt kết nối hiện tại nếu có
  disconnectWebSocket(() => {
    isConnected.value = false
    console.log('Đã ngắt kết nối WebSocket trước khi đổi phòng')
  })

  // Kết nối WebSocket
  connectWebSocket(
    username.value,
    sessionId.value,
    (message) => {
      currentMessages.value.push(message)
      console.log(`Tin nhắn công khai: ${message.sender}: ${message.content}`)
    },
    (message) => {
      currentMessages.value.push(message)
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
      // Tự động thử kết nối lại
      setTimeout(() => {
        if (!isWebSocketConnected() && sessionId.value) {
          console.log(`Thử kết nối lại với phòng ${sessionId.value}...`)
          connectToRoom(sessionId.value)
        }
      }, 5000)
    }
  )
}

// Gửi tin nhắn công khai
const sendPublic = async () => {
  try {
    await sendPublicMessage(sessionId.value, username.value, publicMessage.value)
    publicMessage.value = ''
  } catch (error) {
    console.error('Lỗi gửi tin nhắn công khai:', error.message)
  }
}

// Tự động cuộn xuống tin nhắn mới nhất
watch(currentMessages, () => {
  setTimeout(() => {
    const container = document.querySelector('.flex-1.overflow-y-auto')
    if (container) container.scrollTop = container.scrollHeight
  }, 100)
})

// Tải danh sách phòng khi component được mount và làm mới mỗi 10 giây
onMounted(() => {
  loadActiveRooms()
  const interval = setInterval(loadActiveRooms, 10000)
  onUnmounted(() => clearInterval(interval))
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
.avatar {
  vertical-align: middle;
}
</style>
```