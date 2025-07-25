# LapXpert Frontend - Vue 3 + PrimeVue

Frontend application cho hệ thống quản lý laptop LapXpert, được xây dựng với Vue 3 Composition API và PrimeVue UI framework. Hệ thống đã được tối ưu hóa với kiến trúc đơn giản, tập trung vào các chức năng cốt lõi.

## Kiến Trúc Frontend

### Công Nghệ Sử Dụng
- **Vue 3** với Composition API và `<script setup>`
- **PrimeVue** - Enterprise UI component library
- **Pinia** - State management hiện đại
- **Vue Router** - Client-side routing
- **Vite** - Build tool và development server
- **Axios** - HTTP client với interceptors

### Cấu Trúc Thư Mục
```
src/
├── apis/           # API service layers
├── components/     # Reusable Vue components
├── composables/    # Vue 3 composables
├── layout/         # Layout components
├── router/         # Vue Router configuration
├── stores/         # Pinia stores
└── views/          # Page components
```

## Recommended IDE Setup

[VSCode](https://code.visualstudio.com/) + [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (và tắt Vetur nếu đã cài).

## Thiết Lập Dự Án

### Cài Đặt Dependencies
```sh
npm install
```

### Development Server (Hot-Reload)
```sh
npm run dev
```
Server sẽ chạy tại `http://localhost:3000`

### Build Production
```sh
npm run build
```

### Lint Code
```sh
npm run lint
```

## Quy Ước Phát Triển

### Vietnamese Business Domain
Hệ thống sử dụng thuật ngữ kinh doanh tiếng Việt:
- **SanPham** - Sản phẩm (Product)
- **SanPhamChiTiet** - Biến thể sản phẩm (Product Variant)
- **HoaDon** - Đơn hàng (Order)
- **KhachHang** - Khách hàng (Customer)
- **NhanVien** - Nhân viên (Staff)
- **DiaChi** - Địa chỉ (Address)

### Component Patterns
- Sử dụng **Composition API** với `<script setup>`
- **PrimeVue components** cho UI consistency
- **Pinia stores** cho state management
- **Composables** cho logic tái sử dụng

### API Integration
- Sử dụng **axios interceptors** cho authentication
- **Vietnamese field names** trong API calls (taiKhoan, matKhau)
- **Error handling** với Vietnamese messages
- **Loading states** với PrimeVue ProgressSpinner

### Styling
- **TailwindCSS** cho utility classes
- **PrimeVue themes** cho component styling
- **Responsive design** với mobile-first approach

## AI Chat System (Streamlined)

### Kiến Trúc AI Chat Đơn Giản
Hệ thống AI Chat đã được tối ưu hóa với kiến trúc đơn giản:

- **Unified Hybrid Approach**: Tất cả tin nhắn đều được xử lý qua endpoint `/chat/recommend`
- **No Intent Classification**: Loại bỏ phân loại ý định phức tạp, AI tự quyết định phản hồi
- **Vietnamese Language Support**: Hỗ trợ tiếng Việt với pyvi tokenization
- **Product Recommendations**: Tìm kiếm sản phẩm bằng vector similarity
- **WebSocket Integration**: Real-time chat với streaming responses
- **180-second Timeout**: Tương thích với timeout dài cho AI processing

### Core Components
- **AiChatInterface.vue**: Giao diện chat đơn giản với typing indicator
- **useAiChatWebSocket.js**: WebSocket composable cho real-time communication
- **GitHub AI Integration**: Sử dụng Mistral Medium 3 model thay vì Ollama local

### Removed Features (Streamlined)
- ❌ Complex progress indicators và status displays
- ❌ Health check endpoints và monitoring
- ❌ Intent classification service
- ❌ Multiple AI service endpoints
- ❌ Complex state management cho streaming progress
