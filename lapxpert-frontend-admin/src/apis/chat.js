import axios from 'axios'

const API_BASE_URL = 'http://localhost:8080'
let ws = null
let subscriptions = {}
let reconnectInterval = null

/**
 * Tạo frame STOMP
 * @param {string} command - Lệnh STOMP (e.g., CONNECT, SUBSCRIBE)
 * @param {Object} headers - Headers cho frame
 * @param {string} body - Nội dung frame (nếu có)
 * @returns {string} - Chuỗi frame STOMP
 */
function createStompFrame(command, headers, body = '') {
  let frame = `${command}\n`
  for (const [key, value] of Object.entries(headers)) {
    frame += `${key}:${value}\n`
  }
  frame += '\n' + body + '\0'
  return frame
}

/**
 * Phân tích frame STOMP
 * @param {string} frame - Chuỗi frame STOMP
 * @returns {Object} - Đối tượng chứa command, headers, body
 */
function parseStompFrame(frame) {
  const lines = frame.split('\n')
  const command = lines[0]
  const headers = {}
  let bodyStart = 1
  for (let i = 1; i < lines.length; i++) {
    if (lines[i] === '') {
      bodyStart = i + 1
      break
    }
    const [key, value] = lines[i].split(':')
    headers[key] = value
  }
  const body = lines.slice(bodyStart).join('\n').replace('\0', '')
  return { command, headers, body }
}

/**
 * Kết nối WebSocket và đăng ký topic/queue
 * @param {string} username - Tên người dùng
 * @param {string} sessionId - ID phòng chat
 * @param {function} onPublicMessage - Callback khi nhận tin nhắn công khai
 * @param {function} onPrivateMessage - Callback khi nhận tin nhắn riêng tư
 * @param {function} onJoinMessage - Callback khi có người mới tham gia
 * @param {function} onConnected - Callback khi kết nối thành công
 * @param {function} onError - Callback khi có lỗi
 */
export function connectWebSocket(username, sessionId, onPublicMessage, onPrivateMessage, onJoinMessage, onConnected, onError) {
  if (!username || !sessionId) {
    console.error('Vui lòng cung cấp tên người dùng và ID phòng')
    onError('Vui lòng cung cấp tên người dùng và ID phòng')
    return
  }

  // Ngắt kết nối hiện tại nếu có
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.close()
  }

  ws = new WebSocket('ws://localhost:8080/ws')

  ws.onopen = () => {
    // Gửi frame CONNECT
    const connectFrame = createStompFrame('CONNECT', {
      'accept-version': '1.1,1.0',
      'heart-beat': '10000,10000'
    })
    ws.send(connectFrame)

    // Đăng ký topic công khai
    const publicSubId = `sub-public-${sessionId}-${Date.now()}`
    const publicFrame = createStompFrame('SUBSCRIBE', {
      id: publicSubId,
      destination: `/topic/chatbox/${sessionId}`
    })
    ws.send(publicFrame)
    subscriptions[publicSubId] = onPublicMessage

    // Đăng ký queue riêng tư
    const privateSubId = `sub-private-${username}-${Date.now()}`
    const privateFrame = createStompFrame('SUBSCRIBE', {
      id: privateSubId,
      destination: `/user/${username}/queue/chat`
    })
    ws.send(privateFrame)
    subscriptions[privateSubId] = onPrivateMessage

    // Đăng ký topic thông báo tham gia
    const joinSubId = `sub-join-${sessionId}-${Date.now()}`
    const joinFrame = createStompFrame('SUBSCRIBE', {
      id: joinSubId,
      destination: `/topic/join/${sessionId}`
    })
    ws.send(joinFrame)
    subscriptions[joinSubId] = onJoinMessage

    // Bắt đầu kiểm tra kết nối định kỳ
    if (!reconnectInterval) {
      reconnectInterval = setInterval(() => {
        if (!isWebSocketConnected()) {
          console.log('WebSocket bị ngắt, đang thử kết nối lại...')
          connectWebSocket(username, sessionId, onPublicMessage, onPrivateMessage, onJoinMessage, onConnected, onError)
        }
      }, 5000)
    }

    console.log('Đã kết nối WebSocket')
    onConnected()
  }

  ws.onmessage = (event) => {
    const frame = parseStompFrame(event.data)
    if (frame.command === 'MESSAGE') {
      const subId = frame.headers['subscription']
      const callback = subscriptions[subId]
      if (callback) {
        try {
          const msg = JSON.parse(frame.body)
          console.log(`Nhận tin nhắn: ${JSON.stringify(msg)}`)
          callback(msg)
        } catch (error) {
          console.error(`Lỗi khi xử lý tin nhắn: ${error.message}`)
          onError(`Lỗi khi xử lý tin nhắn: ${error.message}`)
        }
      }
    }
  }

  ws.onerror = (error) => {
    console.error(`Lỗi WebSocket: ${error}`)
    onError(`Lỗi WebSocket: ${error}`)
  }

  ws.onclose = () => {
    console.log('Kết nối WebSocket đã đóng')
    onError('Kết nối WebSocket đã đóng')
  }
}

/**
 * Ngắt kết nối WebSocket
 * @param {function} onDisconnected - Callback khi ngắt kết nối
 */
export function disconnectWebSocket(onDisconnected) {
  if (reconnectInterval) {
    clearInterval(reconnectInterval)
    reconnectInterval = null
  }
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.close()
    ws = null
    subscriptions = {}
    console.log('Đã ngắt kết nối WebSocket')
    onDisconnected()
  } else {
    console.log('WebSocket đã ngắt hoặc chưa kết nối')
    onDisconnected()
  }
}

/**
 * Gửi tin nhắn công khai qua API
 * @param {string} sessionId - ID phòng chat
 * @param {string} sender - Tên người gửi
 * @param {string} content - Nội dung tin nhắn
 * @returns {Promise} - Promise từ axios
 */
export async function sendPublicMessage(sessionId, sender, content) {
  try {
    const response = await axios.post(`${API_BASE_URL}/api/chat/public`, {
      sessionId,
      sender,
      content,
      timestamp: Date.now()
    })
    console.log('Đã gửi tin nhắn công khai:', { sessionId, sender, content })
    return response
  } catch (error) {
    console.error(`Không thể gửi tin nhắn công khai: ${error.message}`)
    throw new Error(`Không thể gửi tin nhắn công khai: ${error.message}`)
  }
}

/**
 * Gửi tin nhắn riêng tư qua API
 * @param {string} targetUser - Tên người nhận
 * @param {string} sender - Tên người gửi
 * @param {string} content - Nội dung tin nhắn
 * @returns {Promise} - Promise từ axios
 */
export async function sendPrivateMessage(targetUser, sender, content) {
  try {
    const response = await axios.post(`${API_BASE_URL}/api/chat/private`, {
      targetUser,
      sender,
      content,
      timestamp: Date.now()
    })
    console.log('Đã gửi tin nhắn riêng tư:', { targetUser, sender, content })
    return response
  } catch (error) {
    console.error(`Không thể gửi tin nhắn riêng tư: ${error.message}`)
    throw new Error(`Không thể gửi tin nhắn riêng tư: ${error.message}`)
  }
}

/**
 * Gửi thông báo tham gia phòng
 * @param {string} sessionId - ID phòng chat
 * @param {string} username - Tên người dùng
 * @returns {Promise} - Promise từ axios
 */
export async function sendJoinNotification(sessionId, username) {
  try {
    const response = await axios.post(`${API_BASE_URL}/api/chat/join`, {
      sessionId,
      username,
      timestamp: Date.now()
    })
    console.log('Đã gửi thông báo tham gia:', { sessionId, username })
    return response
  } catch (error) {
    console.error(`Không thể gửi thông báo tham gia: ${error.message}`)
    throw new Error(`Không thể gửi thông báo tham gia: ${error.message}`)
  }
}

/**
 * Kiểm tra trạng thái kết nối WebSocket
 * @returns {boolean} - True nếu đã kết nối
 */
export function isWebSocketConnected() {
  return ws && ws.readyState === WebSocket.OPEN
}
