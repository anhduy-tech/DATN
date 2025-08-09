import { ref, onUnmounted } from 'vue';
import { Stomp } from '@stomp/stompjs';

export function useRealTimeChat(userId) {
    const messages = ref([]);
    const isConnected = ref(false);
    const conversationId = ref(null);

    let stompClient = null;
    let subscription = null;

    const setConversationId = (id) => {
        conversationId.value = id;
        connect(); // Automatically connect when conversation ID is set
    };

    const connect = () => {
        if (isConnected.value || !conversationId.value) return;

        const socket = new WebSocket('ws://localhost:8080/ws');
        stompClient = Stomp.over(socket);

        stompClient.connect({}, frame => {
            isConnected.value = true;
            console.log('Connected to human chat:', frame);
            subscription = stompClient.subscribe(`/topic/chat/${conversationId.value}`, (message) => {
                const receivedMessage = JSON.parse(message.body);
                messages.value.push({
                    id: `msg_${Date.now()}`,
                    text: receivedMessage.content,
                    sender: receivedMessage.sender === userId ? 'customer' : 'staff',
                    timestamp: new Date(receivedMessage.timestamp)
                });
            });
        }, error => {
            console.error('Error connecting to human chat:', error);
            isConnected.value = false;
        });
    };

    const sendMessage = (text, sender) => {
        if (!stompClient || !isConnected.value || !text.trim()) return;

        const chatMessage = {
            sender: sender, // 'customer' or 'staff'
            content: text,
            timestamp: new Date().toISOString(),
            message_type: 'CHAT'
        };

        stompClient.send(`/app/chat/${conversationId.value}/send`, {}, JSON.stringify(chatMessage));
        
        // Optimistically add to messages list
        messages.value.push({
            id: `msg_${Date.now()}`,
            text: text,
            sender: 'customer',
            timestamp: new Date()
        });
    };

    const disconnect = () => {
        if (stompClient) {
            if (subscription) {
                subscription.unsubscribe();
            }
            stompClient.disconnect(() => {
                console.log('Disconnected from human chat');
            });
        }
        isConnected.value = false;
        messages.value = [];
    };

    onUnmounted(() => {
        disconnect();
    });

    return {
        messages,
        isConnected,
        sendMessage,
        disconnect,
        setConversationId
    };
}