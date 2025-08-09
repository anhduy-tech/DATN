<template>
  <div class="container mx-auto p-8">
    <Card class="shadow-lg rounded-lg">
      <template #title>
        <h1 class="text-3xl font-bold text-center text-primary-600 mb-4">Trung tâm Hỗ trợ</h1>
      </template>
      <template #content>
        <TabView v-model:activeIndex="activeTab">
          <TabPanel header="Trợ lý AI">
            <div class="ai-chat-wrapper">
              <AiChatInterface style="height: 600px;" />
            </div>
          </TabPanel>
          <TabPanel header="Hỗ trợ viên">
            <div class="human-chat-wrapper p-4">
              <div class="chat-header-info mb-3 flex justify-between items-center">
                <div>
                  <span class="font-semibold">Tên của bạn:</span> {{ username }} |
                  <span class="font-semibold">Phòng:</span> {{ sessionId }}
                </div>
                <div class="flex items-center gap-2">
                    <i class="pi pi-users"></i>
                    <span class="font-bold">{{ participants.length }}</span>
                </div>
              </div>

              <ScrollPanel style="height: 400px" class="mb-3 border p-2 rounded">
                <div v-for="(msg, index) in messages" :key="index" class="message-item mb-2" :class="{ 'text-right': msg.sender === username }">
                  <div class="message-bubble inline-block p-2 rounded-lg" :class="msg.sender === username ? 'bg-blue-500 text-white' : 'bg-gray-200'">
                    <strong class="block">{{ msg.sender }}</strong>
                    <span>{{ msg.content }}</span>
                    <div class="text-xs opacity-75 mt-1">{{ formatTimestamp(msg.timestamp) }}</div>
                  </div>
                </div>
              </ScrollPanel>

              <div class="flex gap-2">
                <InputText v-model="newMessage" placeholder="Nhập tin nhắn..." class="w-full" @keyup.enter="sendMessage" :disabled="!isConnected" />
                <Button icon="pi pi-send" @click="sendMessage" :disabled="!isConnected || !newMessage.trim()" />
              </div>
            </div>
          </TabPanel>
        </TabView>
      </template>
    </Card>
  </div>
</template>

<script setup>
import { ref, watch, onUnmounted, nextTick } from 'vue';
import Card from 'primevue/card';
import Button from 'primevue/button';
import TabView from 'primevue/tabview';
import TabPanel from 'primevue/tabpanel';
import InputText from 'primevue/inputtext';
import ScrollPanel from 'primevue/scrollpanel';
import AiChatInterface from '@/views/chatbox/AiChatInterface.vue';
import { connectWebSocket, disconnectWebSocket, sendPublicMessage, sendJoinNotification } from '@/apis/chat';

const activeTab = ref(0);
const messages = ref([]);
const newMessage = ref('');
const isConnected = ref(false);
const participants = ref([]);

const username = ref(`Khach_${Math.random().toString(36).substr(2, 5)}`);
const sessionId = ref(`support_${Math.random().toString(36).substr(2, 9)}`);

const formatTimestamp = (timestamp) => {
  return new Date(timestamp).toLocaleString('vi-VN');
};

const connect = () => {
  if (isConnected.value) return;

  connectWebSocket(
    username.value,
    sessionId.value,
    (message) => { // onPublicMessage
      messages.value.push(message);
    },
    (message) => { // onPrivateMessage
      console.log('Private message received:', message);
    },
    (joinMessage) => { // onJoinMessage
      // Instead of pushing a message, we update the participant list
      if (!participants.value.includes(joinMessage.username)) {
        participants.value.push(joinMessage.username);
      }
    },
    () => { // onConnected
      isConnected.value = true;
      sendJoinNotification(sessionId.value, username.value);
    },
    (error) => { // onError
      isConnected.value = false;
      console.error('WebSocket error:', error);
    }
  );
};

const sendMessage = () => {
  if (newMessage.value.trim() && isConnected.value) {
    sendPublicMessage(sessionId.value, username.value, newMessage.value.trim());
    newMessage.value = '';
  }
};

watch(activeTab, (newIndex) => {
  if (newIndex === 1) {
    connect();
  }
});

watch(messages, () => {
  nextTick(() => {
    const container = document.querySelector('.p-scrollpanel-content');
    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  });
}, { deep: true });

onUnmounted(() => {
  disconnectWebSocket(() => {
    console.log('Disconnected from WebSocket.');
  });
});

</script>

<style scoped>
.ai-chat-wrapper, .human-chat-wrapper {
  min-height: 600px;
}
</style>
