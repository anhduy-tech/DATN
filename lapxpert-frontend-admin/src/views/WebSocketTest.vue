<template>
  <div class="p-grid p-fluid">
    <div class="p-col-12">
      <div class="card">
        <h1>WebSocket Test</h1>
        <div class="p-grid">
          <div class="p-col-12 p-md-6">
            <div class="p-inputgroup">
              <InputText placeholder="Enter Room ID" v-model="roomId" />
              <Button label="Connect" @click="connect" :disabled="isConnected" />
              <Button label="Disconnect" @click="disconnect" :disabled="!isConnected" class="p-button-danger" />
            </div>
          </div>
        </div>
        <div class="p-col-12">
          <h2>Received Messages</h2>
          <pre>{{ receivedMessages }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref } from 'vue';
import SockJS from 'sockjs-client';
import { Stomp } from '@stomp/stompjs';

export default {
  setup() {
    const roomId = ref('');
    const receivedMessages = ref([]);
    const isConnected = ref(false);
    let stompClient = null;

    const connect = () => {
      if (roomId.value && !isConnected.value) {
        const socket = new SockJS('http://localhost:8080/ws');
        stompClient = Stomp.over(socket);
        stompClient.connect({}, () => {
          isConnected.value = true;
          stompClient.subscribe(`/topic/pos-app/${roomId.value}`, (message) => {
            receivedMessages.value.push(JSON.parse(message.body));
          });
        });
      }
    };

    const disconnect = () => {
      if (stompClient && isConnected.value) {
        stompClient.disconnect();
        isConnected.value = false;
      }
    };

    return {
      roomId,
      receivedMessages,
      isConnected,
      connect,
      disconnect,
    };
  },
};
</script>

<style scoped>
</style>