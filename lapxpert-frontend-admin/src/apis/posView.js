import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080';
let ws = null;
let subscriptions = {};
let reconnectInterval = null;

/**
 * Lấy staffId từ localStorage
 * @returns {string|null} - ID nhân viên hoặc null nếu không tìm thấy
 */
function getStaffId() {
  try {
    const nguoiDung = JSON.parse(localStorage.getItem('nguoiDung'));
    return nguoiDung?.id ? String(nguoiDung.id) : null;
  } catch (error) {
    console.error('Lỗi khi lấy staffId từ localStorage:', error);
    return null;
  }
}

/**
 * Tạo frame STOMP
 * @param {string} command - Lệnh STOMP
 * @param {Object} headers - Headers cho frame
 * @param {string} body - Nội dung frame
 * @returns {string} - Chuỗi frame STOMP
 */
function createStompFrame(command, headers, body = '') {
  let frame = `${command}\n`;
  for (const [key, value] of Object.entries(headers)) {
    frame += `${key}:${value}\n`;
  }
  frame += '\n' + body + '\0';
  return frame;
}

/**
 * Phân tích frame STOMP
 * @param {string} frame - Chuỗi frame STOMP
 * @returns {Object} - Đối tượng chứa command, headers, body
 */
function parseStompFrame(frame) {
  const lines = frame.split('\n');
  const command = lines[0];
  const headers = {};
  let bodyStart = 1;
  for (let i = 1; i < lines.length; i++) {
    if (lines[i] === '') {
      bodyStart = i + 1;
      break;
    }
    const [key, value] = lines[i].split(':');
    headers[key] = value;
  }
  const body = lines.slice(bodyStart).join('\n').replace('\0', '');
  return { command, headers, body };
}

/**
 * Kết nối WebSocket và đăng ký topic POS
 * @param {function} onPosUpdate - Callback khi nhận cập nhật POS
 * @param {function} onConnected - Callback khi kết nối thành công
 * @param {function} onError - Callback khi có lỗi
 */
export function connectPosWebSocket(onPosUpdate, onConnected, onError) {
  const staffId = getStaffId();
  if (!staffId) {
    console.error('Không tìm thấy staffId trong localStorage');
    onError('Không tìm thấy staffId trong localStorage');
    return;
  }

  ws = new WebSocket('ws://localhost:8080/ws');

  ws.onopen = () => {
    const connectFrame = createStompFrame('CONNECT', {
      'accept-version': '1.1,1.0',
      'heart-beat': '10000,10000',
    });
    ws.send(connectFrame);

    const posSubId = `sub-pos-${staffId}-${Date.now()}`;
    const posFrame = createStompFrame('SUBSCRIBE', {
      id: posSubId,
      destination: `/topic/pos-app/${staffId}`,
    });
    ws.send(posFrame);
    subscriptions[posSubId] = onPosUpdate;

    if (!reconnectInterval) {
      reconnectInterval = setInterval(() => {
        if (!isWebSocketConnected()) {
          console.log('WebSocket bị ngắt, đang thử kết nối lại...');
          connectPosWebSocket(onPosUpdate, onConnected, onError);
        }
      }, 5000);
    }

    console.log(`Đã kết nối WebSocket cho POS với staffId: ${staffId}`);
    onConnected();
  };

  ws.onmessage = (event) => {
    const frame = parseStompFrame(event.data);
    if (frame.command === 'MESSAGE') {
      const subId = frame.headers['subscription'];
      const callback = subscriptions[subId];
      if (callback) {
        try {
          const msg = JSON.parse(frame.body);
          console.log(`Nhận cập nhật POS: ${JSON.stringify(msg)}`);
          callback(msg);
        } catch (error) {
          console.error(`Lỗi khi xử lý cập nhật POS: ${error.message}`);
          onError(`Lỗi khi xử lý cập nhật POS: ${error.message}`);
        }
      }
    }
  };

  ws.onerror = (error) => {
    console.error(`Lỗi WebSocket: ${error}`);
    onError(`Lỗi WebSocket: ${error}`);
  };

  ws.onclose = () => {
    console.log('Kết nối WebSocket đã đóng');
    onError('Kết nối WebSocket đã đóng');
  };
}

/**
 * Gửi cập nhật POS qua API
 * @param {Object} data - Dữ liệu cập nhật
 * @returns {Promise} - Promise từ axios
 */
export async function sendPosUpdate(data) {
  const staffId = getStaffId();
  if (!staffId) {
    throw new Error('Không tìm thấy staffId trong localStorage');
  }

  try {
    const response = await axios.post(`${API_BASE_URL}/api/pos/pos-app/send?roomId=${staffId}`, data);
    console.log('Đã gửi cập nhật POS:', { roomId: staffId, data });
    return response;
  } catch (error) {
    console.error(`Không thể gửi cập nhật POS: ${error.message}`);
    throw new Error(`Không thể gửi cập nhật POS: ${error.message}`);
  }
}

/**
 * Kiểm tra trạng thái kết nối WebSocket
 * @returns {boolean} - True nếu đã kết nối
 */
export function isWebSocketConnected() {
  return ws && ws.readyState === WebSocket.OPEN;
}