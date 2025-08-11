import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { getActiveSessions } from '@/apis/chat';

export const useChatStore = defineStore('chat', () => {
  // State
  const waitingRooms = ref([]); // Tất cả phòng từ API
  const disabledRooms = ref(JSON.parse(localStorage.getItem('disabledRooms') || '[]'));
  const isLoading = ref(false); // Trạng thái loading
  const pollingInterval = ref(null);

  // Getters
  const waitingRoomCount = computed(() => filteredRooms.value.length); 
  const filteredRooms = computed(() =>
    waitingRooms.value.filter((room) => !disabledRooms.value.includes(room.sessionId))
  );

  // Actions
  /**
   * Lấy danh sách phòng chat đang hoạt động từ backend.
   */
  async function fetchWaitingRooms() {
    try {
      isLoading.value = true;
      const activeSessions = await getActiveSessions();
      // Chuẩn hóa dữ liệu: đảm bảo là array của { sessionId }
      waitingRooms.value = activeSessions.map((session) =>
        typeof session === 'string' ? { sessionId: session } : session
      );
    } catch (error) {
      console.error('Lỗi khi lấy danh sách phòng chờ:', error.message);
      waitingRooms.value = [];
    } finally {
      isLoading.value = false;
    }
  }

  /**
   * Bắt đầu polling danh sách phòng mỗi 10 giây.
   */
  function startPolling() {
    fetchWaitingRooms(); // Gọi ngay lần đầu
    if (pollingInterval.value) {
      clearInterval(pollingInterval.value);
    }
    pollingInterval.value = setInterval(fetchWaitingRooms, 10000);
    console.log('Bắt đầu polling danh sách phòng chat.');
  }

  /**
   * Dừng polling danh sách phòng.
   */
  function stopPolling() {
    if (pollingInterval.value) {
      clearInterval(pollingInterval.value);
      pollingInterval.value = null;
      console.log('Dừng polling danh sách phòng chat.');
    }
  }

  /**
   * Vô hiệu hóa một phòng chat và lưu vào localStorage.
   * @param {string} sessionId - ID của phòng cần vô hiệu hóa
   */
  function disableRoom(sessionId) {
    if (!disabledRooms.value.includes(sessionId)) {
      disabledRooms.value.push(sessionId);
      localStorage.setItem('disabledRooms', JSON.stringify(disabledRooms.value));
    }
  }

  /**
   * Reset danh sách phòng bị vô hiệu hóa.
   */
  function resetDisabledRooms() {
    disabledRooms.value = [];
    localStorage.setItem('disabledRooms', JSON.stringify([]));
  }

  return {
    waitingRooms,
    disabledRooms,
    isLoading,
    waitingRoomCount,
    filteredRooms,
    fetchWaitingRooms,
    startPolling,
    stopPolling,
    disableRoom,
    resetDisabledRooms,
  };
});