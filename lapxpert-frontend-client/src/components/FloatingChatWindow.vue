<template>
    <div class="floating-chat-window" :class="{ 'minimized': isMinimized }" ref="chatWindow">
        <div class="chat-header" @mousedown="startDrag">
            <span class="font-bold">
                {{ chatMode === 'ai' ? 'AI Assistant' : `Hỗ trợ viên - Phòng: ${chatRoomId}` }}
            </span>
            <div class="header-buttons">
                <button @click="toggleMinimize" class="minimize-btn">
                    <i :class="isMinimized ? 'pi pi-window-maximize' : 'pi pi-window-minimize'"></i>
                </button>
                <button @click="closeChat" class="close-btn">
                    <i class="pi pi-times"></i>
                </button>
            </div>
        </div>
        <div v-show="!isMinimized" class="chat-content">
            <div class="messages" ref="messagesContainer">
                <div v-for="msg in displayedMessages" :key="msg.id" class="message" :class="`message-${msg.sender}`">
                    <div class="message-content">
                        <p v-html="msg.text"></p>
                        <small class="timestamp">{{ new Date(msg.timestamp).toLocaleTimeString() }}</small>
                    </div>
                </div>
            </div>

            <div v-if="chatMode === 'ai'" class="ai-suggestions">
                <div class="suggestions-container">
                    <Button 
                        v-for="suggestion in aiSuggestions" 
                        :key="suggestion" 
                        :label="suggestion" 
                        class="p-button-sm p-button-outlined" 
                        @click="sendSuggestion(suggestion)" 
                    />
                </div>
                 <Button
                    label="Chuyển sang nói chuyện với nhân viên"
                    icon="pi pi-user"
                    class="p-button-sm p-button-secondary switch-human-btn"
                    @click="switchToHumanChat"
                />
            </div>

            <div class="chat-input">
                <InputText
                    type="text"
                    v-model="newMessage"
                    placeholder="Nhập tin nhắn..."
                    @keyup.enter="sendMessage"
                    class="flex-grow"
                    :disabled="isConnecting"
                />
                <Button icon="pi pi-send" @click="sendMessage" :disabled="isConnecting || !newMessage.trim()" />
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, computed, watch } from 'vue';
import InputText from 'primevue/inputtext';
import Button from 'primevue/button';
import { useAiChatWebSocket } from '@/composables/useAiChatWebSocket.js';
import { useRealTimeChat } from '@/composables/useRealTimeChat.js';

const emit = defineEmits(['close']);

const chatMode = ref('ai'); // 'ai' or 'human'
const isMinimized = ref(false);
const newMessage = ref('');
const messagesContainer = ref(null);
const chatRoomId = ref(null);

// --- User and session IDs ---
const userId = `user_${Date.now()}`; // Simple unique ID for demo

// --- AI Suggestions ---
const aiSuggestions = ref([
    'Sản phẩm này còn hàng không?',
    'Chính sách bảo hành thế nào?',
    'Theo dõi đơn hàng của tôi.'
]);

// --- Composables for AI and Human Chat ---
const { 
    messages: aiMessages, 
    sendMessage: sendAiMessage, 
    isConnected: isAiConnected, 
    disconnect: disconnectAi 
} = useAiChatWebSocket(userId);

const { 
    messages: humanMessages, 
    sendMessage: sendHumanMessage, 
    isConnected: isHumanConnected, 
    disconnect: disconnectHuman,
    setConversationId, // Assuming the composable has a method to set the room/conversation ID
} = useRealTimeChat(userId);

const isConnecting = computed(() => {
    if (chatMode.value === 'ai') return !isAiConnected.value;
    return !isHumanConnected.value;
});

const displayedMessages = computed(() => {
    const messages = chatMode.value === 'ai' ? aiMessages.value : humanMessages.value;
    return messages.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
});

const sendSuggestion = (suggestion) => {
    sendAiMessage(suggestion);
    scrollToBottom();
};

const sendMessage = () => {
    const text = newMessage.value.trim();
    if (!text) return;

    if (chatMode.value === 'ai') {
        sendAiMessage(text);
    } else {
        sendHumanMessage(text, 'customer');
    }
    newMessage.value = '';
    scrollToBottom();
};

const switchToHumanChat = () => {
    // Generate a random room ID
    chatRoomId.value = `room_${Math.random().toString(36).substr(2, 9)}`
    
    // Disconnect from AI to save resources
    disconnectAi();

    // Set the conversation ID for the human chat
    if (setConversationId) {
        setConversationId(chatRoomId.value);
    }

    chatMode.value = 'human';

    // Add a system message to provide context
    humanMessages.value.push({
        id: `system_${Date.now()}`,
        text: `Đã kết nối với phòng chat ${chatRoomId.value}. Một nhân viên sẽ sớm tham gia.`,
        sender: 'system',
        timestamp: new Date()
    });
};

const closeChat = () => {
    emit('close');
};

const toggleMinimize = () => {
    isMinimized.value = !isMinimized.value;
};

const scrollToBottom = () => {
    nextTick(() => {
        if (messagesContainer.value) {
            messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
        }
    });
};

watch(displayedMessages, () => {
    scrollToBottom();
}, { deep: true });

onMounted(() => {
    scrollToBottom();
});

onUnmounted(() => {
    disconnectAi();
    disconnectHuman();
});

// --- Drag functionality ---
const chatWindow = ref(null);
let isDragging = false;
let dragOffsetX = 0;
let dragOffsetY = 0;

const startDrag = (event) => {
    if (event.target.tagName === 'BUTTON' || event.target.closest('button')) {
        return;
    }
    isDragging = true;
    const rect = chatWindow.value.getBoundingClientRect();
    dragOffsetX = event.clientX - rect.left;
    dragOffsetY = event.clientY - rect.top;
    document.addEventListener('mousemove', onDrag);
    document.addEventListener('mouseup', stopDrag);
};

const onDrag = (event) => {
    if (isDragging) {
        const x = event.clientX - dragOffsetX;
        const y = event.clientY - dragOffsetY;
        chatWindow.value.style.left = `${x}px`;
        chatWindow.value.style.top = `${y}px`;
    }
};

const stopDrag = () => {
    isDragging = false;
    document.removeEventListener('mousemove', onDrag);
    document.removeEventListener('mouseup', stopDrag);
};
</script>

<style scoped>
.floating-chat-window {
    position: fixed;
    bottom: 20px;
    right: 20px;
    width: 370px;
    max-width: 90vw;
    background-color: #ffffff;
    border-radius: 12px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
    display: flex;
    flex-direction: column;
    transition: all 0.3s ease-in-out;
    z-index: 1000;
    overflow: hidden;
}

.floating-chat-window.minimized {
    height: 52px;
    width: 250px;
}

.chat-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    background-color: var(--primary-color, #4f46e5);
    color: var(--primary-color-text, #ffffff);
    border-top-left-radius: 12px;
    border-top-right-radius: 12px;
    cursor: move;
    user-select: none;
}

.header-buttons {
    display: flex;
    gap: 8px;
}

.header-buttons button {
    background: none;
    border: none;
    color: var(--primary-color-text, #ffffff);
    cursor: pointer;
    font-size: 1.1rem;
    padding: 4px;
    border-radius: 50%;
    transition: background-color 0.2s;
}
.header-buttons button:hover {
    background-color: rgba(255,255,255,0.2);
}

.chat-content {
    display: flex;
    flex-direction: column;
    height: 450px;
    overflow: hidden;
}

.messages {
    flex-grow: 1;
    overflow-y: auto;
    padding: 16px;
    background-color: #f9fafb;
}

.message {
    display: flex;
    margin-bottom: 12px;
    max-width: 95%;
}

.message-content {
    padding: 10px 14px;
    border-radius: 18px;
    position: relative;
    line-height: 1.5;
}

.message-customer {
    justify-content: flex-end;
}
.message-customer .message-content {
    background-color: var(--primary-color, #4f46e5);
    color: var(--primary-color-text, #ffffff);
    border-bottom-right-radius: 4px;
}

.message-staff .message-content,
.message-ai .message-content,
.message-system .message-content {
    background-color: #e5e7eb;
    color: #1f2937;
    border-bottom-left-radius: 4px;
}
.message-system {
    justify-content: center;
    text-align: center;
    font-style: italic;
    font-size: 0.9rem;
    color: #6b7280;
}
.message-system .message-content {
    background-color: transparent;
    color: #6b7280;
}

.timestamp {
    display: block;
    font-size: 0.7rem;
    color: #9ca3af;
    margin-top: 5px;
    text-align: right;
    opacity: 0.8;
}

.message-customer .timestamp {
    color: #d1d5db;
}

.ai-suggestions {
    padding: 12px 16px;
    border-top: 1px solid #e5e7eb;
    background-color: #ffffff;
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.suggestions-container {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}

.switch-human-btn {
    margin-top: 8px;
}

.chat-input {
    display: flex;
    gap: 8px;
    padding: 12px 16px;
    border-top: 1px solid #e5e7eb;
    background-color: #ffffff;
}
</style>