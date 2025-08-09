# LapXpert - Hệ thống quản lý bán lẻ Laptop

## Tổng quan

Đây là một dự án hệ thống quản lý bán lẻ toàn diện, bao gồm trang web cho khách hàng, trang quản trị cho nhân viên, và một hệ thống backend mạnh mẽ để xử lý toàn bộ logic nghiệp vụ.

## Sơ đồ công nghệ

### Backend

- **Ngôn ngữ:** Java 21
- **Framework:** Spring Boot 3.4.4
- **Modules chính:**
  - **Spring Web:** Xây dựng RESTful APIs.
  - **Spring Data JPA (Hibernate):** Tương tác với cơ sở dữ liệu quan hệ.
  - **Spring Security:** Xác thực và phân quyền người dùng (JWT).
  - **Spring WebSocket:** Xây dựng các tính năng real-time (chat, thông báo).
  - **Spring Batch:** Xử lý các tác vụ nền theo lô.
  - **Spring Cache:** Tăng tốc độ truy vấn với Caching.
- **Cơ sở dữ liệu:**
  - **PostgreSQL:** Cơ sở dữ liệu chính.
  - **Redis:** Dùng cho Caching và các tác vụ real-time.
  - **Liquibase:** Quản lý và theo dõi phiên bản thay đổi của database.
- **Build Tool:** Gradle

### Frontend (Admin & Client)

- **Framework:** Vue.js 3
- **Build Tool:** Vite
- **Ngôn ngữ:** JavaScript (ES Modules)
- **Quản lý trạng thái:** Pinia
- **Routing:** Vue Router
- **UI Framework:**
  - **PrimeVue:** Bộ thư viện component UI.
  - **Tailwind CSS:** Framework CSS cho việc tùy biến giao diện.
- **Giao tiếp Backend:**
  - **Axios:** Cho các yêu cầu HTTP/REST.
  - **StompJS & SockJS:** Cho kết nối WebSocket.

### AI Chatbox

- **Mô hình ngôn ngữ (LLM):** Mistral Medium 3 (qua GitHub AI).
- **Tìm kiếm ngữ nghĩa (Semantic Search):**
  - **pgvector:** Extension của PostgreSQL để tìm kiếm vector.
  - **Python Embed API:** Dịch vụ Python để xử lý và tìm kiếm vector.
- **Prompt Engineering:** Các mẫu prompt tiếng Việt được tối ưu hóa cho các tác vụ tư vấn, so sánh sản phẩm.

### Dịch vụ bên ngoài (External Services)

- **Thanh toán Online:**
  - **VNPay:** Tích hợp cổng thanh toán VNPay.
  - **MoMo:** Tích hợp cổng thanh toán MoMo.
- **Gửi Email:** Tích hợp dịch vụ gửi mail qua SMTP (sử dụng Spring Boot Starter Mail).
- **Lưu trữ file:** MinIO Object Storage.