# LapXpert Development Guidelines for AI Agents

## Project Overview

**LapXpert** is a Vietnamese e-commerce/retail management system for laptop/computer sales with Spring Boot 3.4.4 backend and Vue 3 frontend using PrimeVue UI library.

**Technology Stack:**
- Backend: Spring Boot 3.4.4, Java 17, PostgreSQL, Redis, Liquibase, JWT, MinIO
- Frontend: Vue 3.5.13, PrimeVue 4.3.3, Pinia, Tailwind CSS, Vite
- AI Chat: Python FastAPI, GitHub AI (Mistral Medium 3), Vietnamese Embeddings, WebSocket
- Payment Gateways: VNPay, MoMo, VietQR
- Infrastructure: Docker, Traefik, pgBouncer
- Testing: JUnit 5, Mockito, Spring Boot Test, Playwright E2E
- Code Quality: ESLint, Oxlint, Prettier

## Vietnamese Business Domain Rules

### Entity and Field Naming
- **MUST** use Vietnamese entity names: `SanPham`, `HoaDon`, `NguoiDung`, `PhieuGiamGia`, `DotGiamGia`
- **MUST** use Vietnamese field names: `tenSanPham`, `maSanPham`, `trangThai`, `ngayTao`
- **MUST** use Vietnamese enum values: `CHO_XAC_NHAN`, `DA_THANH_TOAN`, `HOAN_THANH`
- **PROHIBITED**: Using English field names in Vietnamese business entities

### API Endpoint Patterns
- **MUST** use Vietnamese endpoint paths: `/api/v1/san-pham`, `/api/v1/hoa-don`, `/api/v1/nguoi-dung`
- **MUST** use Vietnamese request/response field names in DTOs
- **EXAMPLE**: `HoaDonDto` uses `khachHangId`, `nhanVienId`, `trangThaiDonHang`

## Authentication System Rules

### Login API Specific Requirements
- **MUST** use Vietnamese field names: `taiKhoan` for email, `matKhau` for password
- **PROHIBITED**: Using `email` and `password` field names in login requests
- **LOCATION**: `frontend/src/apis/auth.js` and backend authentication controllers

### User Role Management
- **MUST** distinguish between `KhachHang` (customers) and `NhanVien` (staff) roles
- **MUST** use `VaiTro` enum: `CUSTOMER`, `STAFF`, `ADMIN`
- **CRITICAL**: Never assign staff members as customers in order creation

## AI Chat System Rules (Streamlined Architecture)

### Core Architecture Principles
- **UNIFIED APPROACH**: All chat messages use single `/chat/recommend` endpoint
- **NO INTENT CLASSIFICATION**: Removed complex intent classification service
- **AI-DRIVEN DECISIONS**: GitHub AI model decides response type based on context
- **VIETNAMESE FIRST**: All prompts and responses prioritize Vietnamese language
- **PRODUCT-AWARE**: Always performs vector search but AI decides relevance

### Endpoint Structure
- **PRIMARY**: `/api/ai-chat/chat` (Java) → `/chat/recommend` (Python)
- **STREAMING**: WebSocket integration for real-time responses
- **TIMEOUT**: 180-second compatibility for AI processing
- **FALLBACK**: Graceful degradation when AI service unavailable

### Implementation Requirements
- **MUST** use unified AiChatService for all chat operations
- **MUST** preserve Vietnamese language processing with pyvi tokenization
- **MUST** maintain WebSocket streaming functionality
- **PROHIBITED**: Adding back complex progress indicators or health monitoring
- **PROHIBITED**: Implementing separate conversational vs product endpoints

### Response Handling
- **HYBRID RESPONSES**: AI contextualizes product recommendations naturally
- **VIETNAMESE MESSAGES**: All error messages and responses in Vietnamese
- **GRACEFUL FALLBACK**: System continues working when AI service is down
- **PRODUCT INTEGRATION**: Vector similarity search always performed

### WebSocket Integration Patterns
- **TOPIC STRUCTURE**: Use `/topic/ai-chat/{sessionId}/messages`, `/topic/ai-chat/{sessionId}/status`, `/topic/ai-chat/{sessionId}/stream`, `/topic/ai-chat/{sessionId}/complete`
- **STREAMING CHUNKS**: Handle real-time AI response chunks via WebSocket
- **SESSION MANAGEMENT**: Generate unique session IDs using `ai-chat-{timestamp}-{random}`
- **CONNECTION HANDLING**: Implement automatic reconnection with exponential backoff
- **TIMEOUT COMPATIBILITY**: Maintain 180-second timeout for AI processing

### Python Service Organization Standards
- **SECTION HEADERS**: Use `# ================== SECTION NAME ==================` format
- **ENDPOINT ORGANIZATION**: Group endpoints logically (API Endpoints, Streaming Helper Functions, etc.)
- **SERVICE INITIALIZATION**: Place service loading in dedicated section with proper logging
- **DATABASE HELPERS**: Separate database functions in dedicated section
- **LIFECYCLE MANAGEMENT**: Use startup/shutdown events for resource management

## Code Organization and Maintenance Standards

### Java Service Organization
- **SECTION HEADERS**: Use `// ================== SECTION NAME ==================` format
- **METHOD GROUPING**: Organize methods logically (SERVICE STATE, CORE SERVICE METHODS, STREAMING METHODS, SERVICE STATUS METHODS)
- **UNUSED CODE REMOVAL**: Remove legacy methods related to intent classification and routing statistics
- **IMPORT OPTIMIZATION**: Remove unused imports after code cleanup
- **FIELD ORGANIZATION**: Group related fields with descriptive comments

### Vue Component Organization
- **IMPORT GROUPING**: Organize imports in logical groups (Vue imports, Composables, PrimeVue components)
- **SECTION HEADERS**: Use `// ================== SECTION NAME ==================` format
- **STATE ORGANIZATION**: Group reactive state, computed properties, and methods separately
- **COMPOSABLE INTEGRATION**: Properly destructure and use composable returns

### Documentation Update Requirements
- **SIMULTANEOUS UPDATES**: When updating architecture, update README.md, shrimp-rules.md, and code comments
- **ARCHITECTURE REFLECTION**: Ensure documentation reflects current streamlined approach
- **EXAMPLE CONSISTENCY**: Update code examples to match current implementation
- **VIETNAMESE TERMINOLOGY**: Maintain Vietnamese business terminology in all documentation

## Payment Gateway Security Standards

### Critical Security Requirements
- **MANDATORY**: All payment gateway implementations MUST follow these security requirements
- **PROHIBITED**: Hardcoded secrets, credentials, or API keys in source code
- **REQUIRED**: Environment-based configuration for all payment gateway settings
- **MANDATORY**: Comprehensive signature verification for all payment callbacks

### VNPay Security Implementation
```java
// ✅ REQUIRED - Proper HMAC-SHA512 signature verification
public boolean verifyVNPaySignature(Map<String, String> params, String signature) {
    // Remove signature from params
    params.remove("vnp_SecureHash");
    params.remove("vnp_SecureHashType");

    // Sort parameters alphabetically
    List<String> fieldNames = new ArrayList<>(params.keySet());
    Collections.sort(fieldNames);

    // Build hash data with proper US_ASCII encoding
    StringBuilder hashData = new StringBuilder();
    for (String fieldName : fieldNames) {
        String fieldValue = params.get(fieldName);
        if (fieldValue != null && !fieldValue.isEmpty()) {
            hashData.append(fieldName).append('=')
                   .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
            if (!fieldName.equals(fieldNames.get(fieldNames.size() - 1))) {
                hashData.append('&');
            }
        }
    }

    // Generate HMAC-SHA512 signature
    String computedSignature = hmacSHA512(secretKey, hashData.toString());
    return signature.equals(computedSignature);
}
```

### MoMo v3 API Security Implementation
```java
// ✅ REQUIRED - MoMo v3 API with proper request ID and signature
public String createMoMoPayment(PaymentRequest request) {
    String requestId = UUID.randomUUID().toString();
    String orderId = request.getOrderId();

    Map<String, String> params = new HashMap<>();
    params.put("partnerCode", momoConfig.getPartnerCode());
    params.put("requestId", requestId);
    params.put("amount", String.valueOf(request.getAmount()));
    params.put("orderId", orderId);
    params.put("orderInfo", request.getOrderInfo());
    params.put("requestType", "captureWallet");
    params.put("extraData", "");
    params.put("lang", "vi");

    // Generate signature with proper parameter sorting
    String signature = generateMoMoSignature(params, momoConfig.getSecretKey());
    params.put("signature", signature);

    return callMoMoAPI(params);
}
```

### VietQR Implementation Requirements
```java
// ✅ REQUIRED - VietQR Quick Link and Full API support
@Service
public class VietQRService {

    // Quick Link for simple QR generation
    public String generateQuickQR(String bankId, String accountNo, long amount, String description) {
        return String.format("https://img.vietqr.io/image/%s-%s-compact2.png?amount=%d&addInfo=%s",
                bankId, accountNo, amount, URLEncoder.encode(description, StandardCharsets.UTF_8));
    }

    // Full API for advanced features
    public VietQRResponse generateAdvancedQR(VietQRRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", vietQRConfig.getClientId());
        headers.set("x-api-key", vietQRConfig.getApiKey());

        return restTemplate.postForObject(vietQRConfig.getApiUrl(),
                new HttpEntity<>(request, headers), VietQRResponse.class);
    }
}
```

### IPN (Instant Payment Notification) Security
```java
// ✅ REQUIRED - Comprehensive IPN validation and processing
@PostMapping("/vnpay-ipn")
public ResponseEntity<Map<String, String>> handleVNPayIPN(HttpServletRequest request) {
    Map<String, String> response = new HashMap<>();

    try {
        // 1. Extract and validate all parameters
        Map<String, String> params = extractParameters(request);
        String vnp_SecureHash = params.get("vnp_SecureHash");

        // 2. Validate signature
        if (!vnPayService.verifySignature(params, vnp_SecureHash)) {
            log.error("VNPay IPN signature validation failed for TxnRef: {}",
                     params.get("vnp_TxnRef"));
            response.put("RspCode", "97");
            response.put("Message", "Invalid Signature");
            return ResponseEntity.ok(response);
        }

        // 3. Process payment result with audit trail
        String transactionStatus = params.get("vnp_TransactionStatus");
        String txnRef = params.get("vnp_TxnRef");

        if ("00".equals(transactionStatus)) {
            // Payment successful - update order with audit
            Long orderId = Long.parseLong(txnRef);
            hoaDonService.confirmPayment(orderId, PhuongThucThanhToan.VNPAY);

            log.info("VNPay payment confirmed for order: {}", orderId);
            response.put("RspCode", "00");
            response.put("Message", "Confirm Success");
        } else {
            log.warn("VNPay payment failed - TxnRef: {}, Status: {}", txnRef, transactionStatus);
            response.put("RspCode", "00");
            response.put("Message", "Payment Failed");
        }

    } catch (Exception e) {
        log.error("Error processing VNPay IPN: {}", e.getMessage());
        response.put("RspCode", "99");
        response.put("Message", "Unknown Error");
    }

    return ResponseEntity.ok(response);
}
```

### Payment Gateway Configuration Security
```java
// ✅ REQUIRED - Environment-based configuration
@ConfigurationProperties(prefix = "vnpay")
@Data
public class VNPayConfig {
    private String tmnCode;
    private String hashSecret;
    private String payUrl;
    private String apiUrl;
    private String returnUrl;

    // ❌ PROHIBITED - Never hardcode secrets
    // private static final String HASH_SECRET = "hardcoded_secret";
}

// application.properties
vnpay.tmn-code=${VNPAY_TMN_CODE:default_test_code}
vnpay.hash-secret=${VNPAY_HASH_SECRET:default_test_secret}
vnpay.pay-url=${VNPAY_PAY_URL:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}
```

## Order Management System Rules

### HoaDon (Order) Creation Requirements
- **MUST** assign both `khachHangId` and `nhanVienId` for all orders
- **MUST** separate customer (`KhachHang`) from staff member (`NhanVien`) assignment
- **MUST** support walk-in customers (null `KhachHang`) while tracking responsible staff
- **LOCATION**: `HoaDonService.java` lines 128-133 for staff-customer separation logic

### Order Status Management
- **MUST** use Vietnamese order statuses: `CHO_XAC_NHAN`, `DA_XAC_NHAN`, `DANG_CHUAN_BI`, `DANG_GIAO_HANG`, `HOAN_THANH`, `DA_HUY`
- **MUST** use Vietnamese payment statuses: `CHUA_THANH_TOAN`, `DA_THANH_TOAN`, `THAT_BAI`
- **MUST** implement automatic payment status for `TAI_QUAY` orders (set to `DA_THANH_TOAN`)

### Payment Method Management
- **MUST** maintain strict frontend-backend consistency for supported payment methods
- **SUPPORTED METHODS**: Only `TIEN_MAT`, `VNPAY`, `MOMO` are officially supported
- **BACKEND ENUM**: `PhuongThucThanhToan.java` defines the authoritative list of payment methods
- **FRONTEND VALIDATION**: `usePaymentValidation.js` must match backend enum exactly
- **DISPLAY MAPPINGS**: All payment method display components must only include supported methods
- **PROHIBITED**: Adding payment methods to frontend without corresponding backend implementation
- **PROHIBITED**: Including unsupported payment methods (e.g., CHUYEN_KHOAN) in any frontend component
- **LOCATION**: Backend enum at `src/main/java/com/lapxpert/backend/hoadon/enums/PhuongThucThanhToan.java`
- **LOCATION**: Frontend validation at `frontend/src/composables/usePaymentValidation.js`

### Payment Status Lifecycle
- **MUST** use comprehensive 7-state payment status system: `CHUA_THANH_TOAN`, `CHO_THANH_TOAN`, `THANH_TOAN_MOT_PHAN`, `DA_THANH_TOAN`, `THANH_TOAN_LOI`, `DA_HOAN_TIEN`, `CHO_XU_LY_HOAN_TIEN`
- **MUST** implement robust payment status transition validation in `HoaDonService.validatePaymentStatusTransition()`
- **MUST** handle payment method-specific status initialization (cash payments start as `DA_THANH_TOAN`, gateway payments start as `CHUA_THANH_TOAN`)
- **MUST** integrate payment status changes with inventory management and audit logging

### Voucher System Integration
- **MUST** implement automatic voucher application logic in order creation
- **MUST** display "Lựa chọn tốt nhất" badge only on single highest discount voucher
- **MUST** use cross-category comparison for best voucher identification
- **LOCATION**: `OrderCreate.vue` voucher section with `getBestOverallVoucher()` method

## Product Management Architecture

### Product-Variant-Serial Number Hierarchy
- **MUST** follow 3-tier structure: `SanPham` → `SanPhamChiTiet` (variants) → `SerialNumber`
- **MUST** use SKU-based variant management with auto-generation
- **MUST** implement 8-core attribute system: CPU, RAM, GPU, MauSac, BoNho, ManHinh, DanhMuc, ThuongHieu
- **MUST** handle serial number lifecycle: AVAILABLE, RESERVED, SOLD, RETURNED

### SKU Generation Rules
- **MUST** append random character sequences to ensure uniqueness
- **MUST** implement deduplication logic with suffix increment system
- **PROHIBITED**: Using simple incremental SKUs that may cause constraint violations

### Image Management
- **MUST** use MinIO for file storage with presigned URLs
- **MUST** support 5 image slots per product with square aspect ratio
- **MUST** implement fallback image loading logic for product displays

## Backend Refactoring Patterns

### Template Method Pattern for Attribute Services
- **MUST** extend `AttributeCodeGeneratorService<T>` for all attribute services (CPU, RAM, GPU, MauSac, etc.)
- **MUST** implement abstract methods: `getLastCode()`, `getCodePrefix()`, `getPrefixLength()`, `getEntityTypeName()`
- **MUST** preserve Vietnamese naming conventions in entity code generation
- **MUST** maintain audit trail functionality through base class inheritance
- **LOCATION**: `src/main/java/com/lapxpert/backend/sanpham/domain/service/thuoctinh/AttributeCodeGeneratorService.java`
- **EXAMPLE**: `CpuService`, `RamService`, `GpuService` all extend this base class

### Service Deduplication Rules
- **MUST** identify common patterns across services before creating new implementations
- **MUST** use template method pattern for code generation logic
- **MUST** preserve 100% backward compatibility during refactoring
- **PROHIBITED**: Duplicating code generation logic across attribute services

## Frontend Component Architecture

### Vue 3 Composition API Requirements
- **MUST** use `<script setup>` syntax for all new components
- **MUST** use Composition API with `ref`, `computed`, `watch` from Vue 3
- **MUST** implement proper reactive state management with Pinia stores

### Component Composition Patterns
- **MUST** create base components for reusable UI patterns (e.g., `BaseThongKeCard`)
- **MUST** use slot-based composition for flexible content injection
- **MUST** implement consistent prop interfaces across similar components
- **LOCATION**: `frontend/src/components/ThongKe/cards/BaseThongKeCard.vue`
- **EXAMPLE**: Dashboard cards using BaseThongKeCard with main-content, additional-content, quick-stats slots

### PrimeVue Component Patterns
- **MUST** use PrimeVue components: `DataTable`, `Select`, `AutoComplete`, `Dialog`, `Card`
- **MUST** follow PrimeVue theming with Aura preset
- **MUST** implement proper severity levels: `success`, `warning`, `danger`, `info`
- **EXAMPLE**: Order status badges with Vietnamese labels and appropriate severities

### Composables Usage
- **MUST** create composables for reusable logic: `useProductFilters`, `useOrderValidation`, `useErrorHandling`
- **MUST** place composables in `frontend/src/composables/` directory
- **MUST** follow naming convention: `use[FunctionalityName].js`

## State Management Rules

### Pinia Store Patterns
- **MUST** use Vietnamese store names: `productstore`, `orderStore`, `customerstore`
- **MUST** implement proper getters, actions, and state management
- **MUST** handle loading states and error handling in stores
- **LOCATION**: `frontend/src/stores/` directory

### API Service Integration
- **MUST** create dedicated API services: `orderApi.js`, `product.js`, `user.js`
- **MUST** use Vietnamese method names: `getAllOrders`, `createHoaDon`, `updateSanPham`
- **MUST** implement proper error handling and response formatting

## Database and Migration Rules

### Liquibase Migration Patterns
- **MUST** use Vietnamese table names: `san_pham`, `hoa_don`, `nguoi_dung`
- **MUST** use Vietnamese column names: `ten_san_pham`, `ma_san_pham`, `ngay_tao`
- **MUST** implement audit trail tables: `[entity_name]_audit_history`
- **LOCATION**: `src/main/resources/db/changelog/` directory

### Audit Trail Requirements
- **MUST** extend `BaseAuditableEntity` for all business entities
- **MUST** create corresponding `[Entity]AuditHistory` entities
- **MUST** implement audit trail tracking for all CRUD operations

## Configuration Management Rules

### Environment Variable Externalization
- **MUST** externalize all hardcoded values to environment variables with fallbacks
- **MUST** use pattern: `${ENV_VAR_NAME:default_value}` in application.properties
- **MUST** document all environment variables in deployment guides
- **PROHIBITED**: Hardcoding credentials, URLs, or environment-specific values
- **EXAMPLE**: `spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/lapxpert}`

### Configuration Security
- **MUST** use environment variables for sensitive data (passwords, API keys, secrets)
- **MUST** provide development defaults for non-sensitive configuration
- **MUST** document production environment variable requirements
- **LOCATION**: `src/main/resources/application.properties` and `frontend/.env`

## Code Quality Standards

### ESLint and Oxlint Integration
- **MUST** use dual linting system: ESLint with Oxlint plugin for optimal performance
- **MUST** run Oxlint first for fast correctness checks, then ESLint for comprehensive analysis
- **MUST** use `npm run lint` for sequential execution: `oxlint . --fix -D correctness` then `eslint . --fix`
- **MUST** detect and remove unused imports automatically
- **MUST** follow Vue 3 specific linting rules with Vietnamese business term exceptions
- **MUST** use `no-unused-vars` with underscore prefix pattern for intentionally unused variables
- **MUST** allow single-word component names for Vietnamese business terms (`vue/multi-word-component-names: 'off'`)
- **LOCATION**: `frontend/eslint.config.js`

### Import Management
- **MUST** remove unused imports during development
- **MUST** organize imports by type: Vue imports, PrimeVue imports, local imports
- **MUST** use consistent import patterns across components
- **PROHIBITED**: Leaving unused imports in production code

### Code Formatting Standards
- **MUST** use 2-space indentation for frontend code
- **MUST** use UTF-8 encoding with final newline
- **MUST** trim trailing whitespace
- **MUST** follow max line length of 100 characters
- **LOCATION**: `frontend/.editorconfig`

### Development Tooling and Script Standards
- **MUST** use `npm run dev` for development server with hot-reload
- **MUST** use `npm run build` for production builds with optimization
- **MUST** use `npm run lint` for comprehensive code quality checks (Oxlint + ESLint)
- **MUST** use `npm run format` for code formatting with Prettier
- **MUST** use `npm run clean:all` for complete code cleanup (format + imports)
- **MUST** use `npm run lint:check` for dry-run linting verification
- **MUST** configure Vite with environment-specific optimizations
- **MUST** use manual chunks for production builds: vendor, primevue, utils
- **LOCATION**: `frontend/package.json` scripts section
- **LOCATION**: `frontend/vite.config.mjs` for build configuration

## File Coordination Requirements

### Documentation Updates
- **MUST** update `docs/TASK.MD` for all major changes with phase-based tracking
- **MUST** use Vietnamese business terminology in documentation
- **MUST** follow phase numbering: Phase X.Y format with completion status (✅)

### Multi-File Coordination
- **WHEN** modifying backend entities → **MUST** update corresponding DTOs, mappers, repositories, services
- **WHEN** adding new API endpoints → **MUST** update frontend API services and store actions
- **WHEN** changing database schema → **MUST** create Liquibase migration and update entity classes
- **WHEN** modifying order creation → **MUST** update both `HoaDonService.java` and `OrderCreate.vue`

## Refactoring Guidelines

### Systematic Refactoring Approach
- **MUST** follow phase-based refactoring: Analysis → Planning → Implementation → Verification
- **MUST** maintain 100% backward compatibility during refactoring
- **MUST** preserve Vietnamese business domain terminology
- **MUST** document refactoring decisions and patterns for future reference
- **MUST** test compilation and functionality after each refactoring phase

### Deduplication Strategy
- **MUST** identify common patterns before implementing new functionality
- **MUST** create abstract base classes or composables for shared logic
- **MUST** use template method pattern for algorithm variations
- **MUST** preserve existing API contracts during deduplication

### Refactoring Verification
- **MUST** verify all tests pass after refactoring
- **MUST** check that Vietnamese field names remain consistent
- **MUST** ensure audit trail functionality is preserved
- **MUST** validate that API responses maintain expected format

## Completed Refactoring History (December 2025)

### Phase 1: Safe Immediate Removals ✅
**Objective**: Remove clearly unused/redundant elements with zero risk

1. **SanPham Controller Duplicate Endpoint Removal** ✅
   - **Removed**: `/addMultiple` endpoint (lines 58-62) in SanPhamController
   - **Rationale**: Identical functionality to existing `/add` endpoint
   - **Impact**: Zero functional impact, reduced API surface area
   - **Frontend Fix**: Updated ProductForm.vue to use `/add` endpoint consistently

2. **Cache Strategy Implementation** ✅
   - **Decision**: Implemented proper @Cacheable annotations instead of removal
   - **Rationale**: Cache strategy analysis revealed performance benefits
   - **Implementation**: Added @Cacheable to SanPhamService methods with appropriate cache names
   - **Impact**: Improved performance while maintaining Vietnamese business terminology

3. **Payment Method Counting Implementation** ✅
   - **Removed**: TODO placeholder in HoaDonRepository (lines 143-147)
   - **Implemented**: Proper payment method counting query using HoaDonThanhToan relationship
   - **Rationale**: Replaced hardcoded placeholder with actual business logic
   - **Impact**: Accurate payment statistics functionality

### Phase 2: Analyzed Removals ✅
**Objective**: Remove elements after thorough analysis and dependency verification

4. **ThongKe Service Consolidation Analysis** ✅
   - **Analyzed**: ThongKeDTService and ThongKeHDService for consolidation potential
   - **Finding**: Both services are orphaned with zero usage across codebase
   - **Recommendation**: Remove both services (safe removal, zero dependencies)
   - **Preserved**: ThongKeServiceImpl as comprehensive statistics service

5. **Payment Timeout Handling Implementation** ✅
   - **Implemented**: Complete timeout handling logic in PaymentMonitoringService
   - **Features**: Inventory release, customer notifications, order status updates
   - **Integration**: SerialNumberService for inventory, EmailService for notifications
   - **Vietnamese Compliance**: All notifications and audit messages in Vietnamese

6. **Quarter Growth Calculation Implementation** ✅
   - **Implemented**: Quarter-over-quarter growth calculation in ThongKeServiceImpl
   - **Logic**: Year-over-year quarterly comparison using existing data structures
   - **Consistency**: Follows same patterns as existing growth calculations
   - **Performance**: Efficient single-pass calculation without additional queries

### Phase 3: Optional Cleanup ✅
**Objective**: Evaluate and clean up potentially unused elements

7. **Serial Number Validation Methods Removal** ✅
   - **Removed**: `findDuplicateSerialNumbers()` and `findInvalidSerialNumberFormat()` from SerialNumberRepository
   - **Verification**: Comprehensive codebase search confirmed zero usage
   - **Rationale**: Validation handled at service layer and database constraint level
   - **Impact**: Reduced repository complexity, maintained validation functionality

8. **TrangThaiSerialNumber Enum Values Evaluation** ✅
   - **Evaluated**: IN_TRANSIT, QUALITY_CONTROL, DISPLAY_UNIT, DISPOSED enum values
   - **Finding**: All values actively used in business logic, frontend UI, and status transitions
   - **Decision**: Retain all enum values (removal would break system functionality)
   - **Rationale**: Values serve legitimate business purposes and support future requirements

### Refactoring Outcomes Summary

**Successfully Completed**: 8/8 tasks (100% completion rate)
**Backward Compatibility**: 100% maintained across all changes
**Vietnamese Terminology**: Fully preserved in all implementations
**Audit Trail Functionality**: Intact and enhanced where applicable
**System Stability**: No breaking changes introduced

**Key Achievements**:
- Removed 3 truly unused elements (duplicate endpoint, validation methods)
- Implemented 4 missing functionalities (cache strategy, payment counting, timeout handling, quarter growth)
- Enhanced system performance through proper caching implementation
- Strengthened payment processing with comprehensive timeout handling
- Improved statistics accuracy with complete quarter growth calculations
- Maintained system integrity by preserving essential enum values

**Architecture Improvements**:
- Cleaner API surface with duplicate endpoint removal
- Better performance through strategic caching implementation
- More robust payment processing with timeout handling
- Enhanced statistics with complete growth calculations
- Simplified repository interfaces without unused methods

## Cache-Free Architecture Standards

### Redis Usage Rules
- **PROHIBITED**: Using Redis for caching mechanisms - System uses cache-free architecture
- **REQUIRED**: Use Redis ONLY for Pub/Sub messaging and distributed locking
- **MUST** preserve Redis infrastructure for WebSocket real-time communication
- **MUST** use Redisson client for distributed locking operations
- **LOCATION**: `src/main/java/com/lapxpert/backend/common/config/RedisConfig.java`

### Distributed Locking Performance Optimization
- **MUST** use optimized lock timeouts: 10s wait, 20s lease for standard operations
- **MUST** use fine-grained locking: 5s wait, 10s lease for individual serial number operations
- **MUST** implement batch database operations: `saveAll()`, `findAllById()` instead of individual operations
- **MUST** defer non-critical operations outside lock scope (audit trails, WebSocket events)
- **PROHIBITED**: Nesting distributed locks - inline logic to avoid nested locking
- **LOCATION**: `src/main/java/com/lapxpert/backend/sanpham/service/SerialNumberService.java`

### Performance Optimization Patterns
```java
// ✅ REQUIRED - Optimized distributed locking with batch operations
final List<SerialNumber> reservedSerialNumbers = new ArrayList<>();
final AtomicReference<InventoryUpdateEvent> eventToPublish = new AtomicReference<>();

distributedLockService.executeWithLock(lockKey, () -> {
    // Critical operations only - batch processing
    List<SerialNumber> savedSerialNumbers = serialNumberRepository.saveAll(toUpdate);
    reservedSerialNumbers.addAll(savedSerialNumbers);
    eventToPublish.set(event);
    return null;
}, 10, 20); // Optimized timeouts

// Non-critical operations outside lock
auditHistoryRepository.saveAll(auditEntries);
eventPublisher.publishEvent(eventToPublish.get());
```

### Thread Safety Requirements
- **MUST** use `AtomicReference` for sharing data between lock and post-lock operations
- **MUST** use final collections for thread-safe data collection during locks
- **MUST** implement proper thread-safe event publishing patterns

## AI Agent Collaboration Standards

### Task Management Requirements
- **MUST** use shrimp task manager for all complex implementations
- **MUST** break down large tasks into manageable subtasks (1-2 days each)
- **MUST** update task progress continuously with detailed findings
- **MUST** verify task completion against verification criteria before marking complete
- **MUST** use continuous mode execution with ≥80 verification score for automatic progression

### Research and Documentation Standards
- **MUST** fetch official documentation URLs before implementation
- **MUST** analyze official API specifications and examples
- **MUST** update research findings based on official sources
- **MUST** create comprehensive gap analysis documents for modernization projects

### Implementation Verification Process
- **MUST** conduct thorough testing of all payment gateway integrations
- **MUST** verify signature verification algorithms against official examples
- **MUST** test all payment flows including success, failure, and timeout scenarios
- **MUST** validate IPN handling with proper error responses
- **MUST** run `./gradlew compileJava` after backend modifications
- **MUST** verify application startup with `./gradlew bootRun`

### Code Quality Assurance
- **MUST** follow existing code patterns and architectural decisions
- **MUST** maintain 100% backward compatibility during modernization
- **MUST** preserve Vietnamese business terminology throughout
- **MUST** implement comprehensive audit trails for all payment operations
- **MUST** check for compilation errors before marking tasks complete

### Security Validation Requirements
- **MUST** validate all payment gateway security implementations
- **MUST** ensure proper HMAC signature verification
- **MUST** verify environment-based configuration usage
- **MUST** test IPN security and validation logic

## Prohibited Actions

### Code Modifications
- **PROHIBITED**: Changing Vietnamese business terminology to English
- **PROHIBITED**: Modifying authentication field names (`taiKhoan`, `matKhau`)
- **PROHIBITED**: Removing audit trail functionality from entities
- **PROHIBITED**: Using English enum values for Vietnamese business states
- **PROHIBITED**: Mixing customer and staff roles in order assignment
- **PROHIBITED**: Duplicating code patterns that have established base classes

### Payment Gateway Security Violations
- **PROHIBITED**: Hardcoding payment gateway secrets or API keys
- **PROHIBITED**: Skipping signature verification in payment callbacks
- **PROHIBITED**: Using deprecated API versions (VNPay < 2.1.0, MoMo < v3)
- **PROHIBITED**: Implementing payment processing without proper error handling
- **PROHIBITED**: Missing IPN validation or audit trail logging

### Architecture Violations
- **PROHIBITED**: Bypassing the Product-Variant-Serial Number hierarchy
- **PROHIBITED**: Direct database access without repository pattern
- **PROHIBITED**: Frontend API calls without proper error handling
- **PROHIBITED**: Modifying core PrimeVue theming without project-wide consideration
- **PROHIBITED**: Creating new attribute services without extending AttributeCodeGeneratorService

### Price Display Violations
- **PROHIBITED**: Using effective prices (oldPrice/newPrice) instead of original prices (originalOldPrice/originalNewPrice) in price displays
- **PROHIBITED**: Displaying discounted prices in price change notifications instead of original price changes
- **PROHIBITED**: Using English price field names without Vietnamese fallbacks in WebSocket messages
- **PROHIBITED**: Leaving temporary testing notifications in production code
- **PROHIBITED**: Showing promotional price changes (giaKhuyenMai) instead of original price changes (giaBan)

### Documentation Violations
- **PROHIBITED**: Making major changes without updating documentation
- **PROHIBITED**: Using English terminology in Vietnamese business documentation
- **PROHIBITED**: Skipping phase-based implementation tracking for complex features

## Real-time Order Management Standards

### Enhanced WebSocket Infrastructure Requirements
- **MUST** use Spring WebSocket with STOMP messaging protocol for real-time communication
- **MUST** configure public access for WebSocket endpoints (`/ws`, `/ws-sockjs`) for simplicity
- **MUST** create dedicated WebSocket configuration class extending `AbstractWebSocketMessageBrokerConfigurer`
- **MUST** use Vietnamese topic naming: `/topic/gia-san-pham`, `/topic/voucher-cap-nhat`, `/topic/don-hang-het-han`
- **MUST** integrate with Redis Pub/Sub for distributed messaging across instances
- **MUST** implement unified WebSocket configuration in `websocket` module for centralized management
- **MUST** configure production-ready transport limits and heartbeat monitoring (30s intervals)
- **MUST** use high-concurrency thread pool optimization for WebSocket message handling
- **LOCATION**: `src/main/java/com/lapxpert/backend/websocket/config/WebSocketServiceConfig.java`
- **LOCATION**: `src/main/java/com/lapxpert/backend/common/service/WebSocketIntegrationService.java`

### WebSocket Integration Service Pattern
- **MUST** inject `WebSocketIntegrationService` into all business services for real-time updates
- **MUST** use transaction-aware message queuing with after-commit delivery
- **MUST** implement comprehensive message routing for all entity types
- **MUST** use Vietnamese business terminology in WebSocket topics and messages
- **MUST** defer WebSocket event publishing outside distributed locks for performance
```java
// ✅ REQUIRED - WebSocket integration pattern
@Service
public class SanPhamService {
    private final WebSocketIntegrationService webSocketService;

    @Transactional
    public SanPham updateSanPham(SanPham sanPham) {
        SanPham saved = sanPhamRepository.save(sanPham);

        // Defer WebSocket publishing outside locks
        webSocketService.publishSanPhamUpdate(saved);

        return saved;
    }
}
```

### Real-time Price Update Implementation
- **MUST** implement price change detection in `SanPhamService` with WebSocket notifications
- **MUST** create `PriceChangeNotificationService` for broadcasting price updates
- **MUST** use audit trail to track price changes with timestamp and old/new values
- **MUST** implement frontend composable `useRealTimePricing` for WebSocket subscription
- **MUST** display price change warnings in Vietnamese: "Giá sản phẩm đã thay đổi từ X thành Y"
- **CRITICAL**: Backend WebSocket messages MUST send original prices (giaBan) in both `oldPrice`/`newPrice` and `giaCu`/`giaMoi` fields
- **CRITICAL**: Frontend components MUST use `originalOldPrice`/`originalNewPrice` fields, never `oldPrice`/`newPrice` for price displays
- **LOCATION**: Backend service in `src/main/java/com/lapxpert/backend/sanpham/domain/service/`
- **LOCATION**: Frontend composable in `frontend/src/composables/useRealTimePricing.js`

### Inventory Management and Serial Number Optimization
- **MUST** use fine-grained serial number locking for high-concurrency scenarios
- **MUST** implement `reserveSpecificSerialNumbers()` with individual serial number locks (5s-10s duration)
- **MUST** use batch operations for all serial number CRUD operations
- **MUST** combine distributed locking with optimistic locking for double protection
- **MUST** implement inventory update events for WebSocket real-time notifications
```java
// ✅ REQUIRED - Fine-grained serial number locking pattern
public List<SerialNumber> reserveSpecificSerialNumbers(List<Long> serialNumberIds, String channel, String orderId, String user) {
    List<SerialNumber> reservedSerialNumbers = new ArrayList<>();

    // Process each serial number with its own fine-grained lock
    for (Long serialNumberId : serialNumberIds) {
        String lockKey = distributedLockService.getSerialNumberLockKey(serialNumberId);

        SerialNumber reservedSerial = distributedLockService.executeWithLock(lockKey, () -> {
            return optimisticLockingService.executeWithRetry(() -> {
                // Critical section - minimal operations only
                SerialNumber serialNumber = serialNumberRepository.findById(serialNumberId)
                        .orElseThrow(() -> new IllegalArgumentException("Serial number not found: " + serialNumberId));

                serialNumber.reserveWithTracking(channel, orderId);
                return serialNumberRepository.save(serialNumber);
            });
        }, 5, 10); // Very short lock duration for individual serial numbers

        reservedSerialNumbers.add(reservedSerial);
    }

    // Batch operations outside locks
    auditHistoryRepository.saveAll(auditEntries);
    return reservedSerialNumbers;
}
```

### Order Expiration and Inventory Management
- **MUST** implement `@Scheduled` job for order expiration checking (every 5 minutes)
- **MUST** create `OrderExpirationService` with configurable expiration time (default 24 hours)
- **MUST** implement inventory release logic when orders expire using batch operations
- **MUST** send email notifications to customers about order expiration
- **MUST** use Vietnamese notification templates: "Đơn hàng của bạn đã hết hạn"
- **MUST** integrate with distributed locking for inventory release operations
- **LOCATION**: `src/main/java/com/lapxpert/backend/hoadon/domain/service/OrderExpirationService.java`

### Shipping API Integration Standards
- **MUST** create abstract `ShippingCalculatorService` for multiple shipping providers
- **MUST** implement `GiaoHangNhanhService` and `GHTKService` extending base service
- **MUST** use environment-based configuration for shipping API credentials
- **MUST** implement fallback mechanism when primary shipping API fails
- **MUST** cache shipping fee calculations for 30 minutes to reduce API calls
- **MUST** allow manual override of calculated shipping fees
- **LOCATION**: `src/main/java/com/lapxpert/backend/shipping/` package structure

### Advanced Voucher Management Rules
- **MUST** implement real-time voucher validation with WebSocket notifications
- **MUST** create `VoucherMonitoringService` for detecting expired/invalid vouchers
- **MUST** implement voucher quantity restoration when orders are cancelled
- **MUST** create voucher recommendation engine for suggesting better alternatives
- **MUST** use Vietnamese voucher status messages: "Voucher đã hết hạn", "Có voucher tốt hơn"
- **LOCATION**: `src/main/java/com/lapxpert/backend/phieugiamgia/domain/service/VoucherMonitoringService.java`

### Real-time Communication Patterns
- **MUST** use topic-based messaging for broadcast notifications (price changes, voucher updates)
- **MUST** use user-specific queues for personal notifications (order expiration, payment status)
- **MUST** implement connection heartbeat and automatic reconnection in frontend
- **MUST** handle WebSocket connection failures gracefully with fallback to polling
- **MUST** limit WebSocket message frequency to prevent spam (max 10 messages/second per user)

### WebSocket Testing and Development Standards
- **MUST** use `WebSocketTestController` for testing Vietnamese topic naming conventions
- **MUST** implement test endpoints for verifying real-time messaging functionality
- **MUST** test WebSocket message routing with `/app/test-message` and `/topic/test-response`
- **MUST** verify heartbeat configuration and connection stability under load
- **MUST** test cross-tab synchronization with multiple browser instances
- **MUST** validate message serialization/deserialization with Vietnamese field names
- **LOCATION**: `src/main/java/com/lapxpert/backend/common/websocket/WebSocketTestController.java`

### Enhanced Frontend Real-time Integration
- **MUST** use `useUnifiedRealTimeManager` for centralized real-time state coordination
- **MUST** implement intelligent connection management with network awareness
- **MUST** create `useDataTableRealTime` composable for real-time DataTable updates
- **MUST** implement automatic subscription management based on active order tabs
- **MUST** use Pinia store for managing real-time data updates
- **MUST** implement visual indicators for real-time updates (badges, notifications)
- **MUST** provide user controls for enabling/disabling real-time features
- **MUST** implement cross-tab synchronization with message queuing for offline scenarios
- **MUST** use `EnhancedRealTimeStatusPanel` component for connection status monitoring
- **LOCATION**: `frontend/src/composables/useUnifiedRealTimeManager.js`
- **LOCATION**: `frontend/src/composables/useDataTableRealTime.js`
- **LOCATION**: `frontend/src/components/EnhancedRealTimeStatusPanel.vue`

## Price Display and Notification Standards

### Critical Price Display Rules
- **CRITICAL**: ALL price displays MUST use original prices (giaBan) not effective/discounted prices (giaKhuyenMai)
- **CRITICAL**: Price change notifications MUST display original price changes, not promotional price changes
- **CRITICAL**: Use `originalOldPrice` and `originalNewPrice` fields in all price change components
- **PROHIBITED**: Using `oldPrice` and `newPrice` fields for price displays (these contain effective prices)
- **PROHIBITED**: Displaying discounted prices (e.g., 18,417,650 VND) instead of original prices (e.g., 19,387,000 VND)

### PriceChangeWarning Component Standards
- **MUST** use `change.originalOldPrice` and `change.originalNewPrice` for all price displays
- **MUST** use Vietnamese terminology: "Giá sản phẩm đã thay đổi từ X thành Y" for SKU_PRICE_DIFFERENCE
- **MUST** use Vietnamese terminology: "Giá gốc đã thay đổi từ X thành Y" for other price changes
- **MUST** calculate price differences using original prices: `originalNewPrice - originalOldPrice`
- **MUST** preserve Vietnamese currency formatting: `new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' })`
- **LOCATION**: `frontend/src/views/orders/components/PriceChangeWarning.vue`

### WebSocket Price Update Message Structure
- **MUST** send original prices in WebSocket messages using both field naming conventions:
  - English fields: `oldPrice`, `newPrice` (containing original prices, not effective prices)
  - Vietnamese fields: `giaCu`, `giaMoi` (containing original prices, not effective prices)
- **MUST** include product identification: `variantId`, `productName`, `tenSanPham`, `sku`
- **MUST** include change metadata: `changeAmount`, `changePercent`, `reason`, `timestamp`
- **MUST** use Vietnamese reason text: "Cập nhật giá sản phẩm"
- **LOCATION**: `src/main/java/com/lapxpert/backend/common/service/WebSocketIntegrationService.java`

### Frontend Price Update Processing
- **MUST** use `useRealTimePricing` composable for WebSocket price update subscription
- **MUST** extract both English and Vietnamese price fields: `oldPrice || giaCu`, `newPrice || giaMoi`
- **MUST** process price updates with proper Vietnamese currency formatting
- **MUST** integrate with PriceChangeWarning component for user notifications
- **MUST** maintain price update history for debugging and audit purposes
- **LOCATION**: `frontend/src/composables/useRealTimePricing.js`

## Frontend Component Testing and Cleanup Standards

### Temporary Testing Code Removal
- **MUST** remove all temporary testing notifications after WebSocket integration verification
- **PROHIBITED**: Leaving temporary price notifications in production code
- **MUST** clean up unused variables and imports after removing temporary code
- **MUST** verify no layout gaps or spacing issues after removing temporary elements
- **EXAMPLE**: Remove temporary "Thông báo thay đổi giá" notifications from OrderCreate.vue

### Component Modification Verification
- **MUST** run `diagnostics` tool on all modified Vue components before marking tasks complete
- **MUST** verify no compilation errors in modified components
- **MUST** check that component props and emits interfaces remain unchanged
- **MUST** verify Vietnamese business terminology preservation in all text content
- **MUST** test component integration with parent components after modifications

### Price Component Integration Testing
- **MUST** verify PriceChangeWarning component displays original prices correctly
- **MUST** test price change acknowledgment functionality
- **MUST** verify WebSocket price updates trigger component re-renders
- **MUST** test both regular and promotional pricing scenarios
- **MUST** verify Vietnamese currency formatting consistency (19,387,000 VND format)

## Real-time DataTable Integration Standards

### DataTable Real-time Update Patterns
- **MUST** use `useDataTableRealTime` composable for all DataTable components requiring real-time updates
- **MUST** configure entity-specific refresh callbacks with proper debouncing (200ms for products, 500ms for orders)
- **MUST** implement selective updates based on WebSocket topic filtering
- **MUST** use Vietnamese entity types: `sanPham`, `hoaDon`, `phieuGiamGia`, `nguoiDung`
- **MUST** integrate with existing store refresh methods (`forceRefreshProducts`, `refreshOrders`)
- **MUST** handle WebSocket connection failures gracefully with fallback to manual refresh
- **LOCATION**: `frontend/src/composables/useDataTableRealTime.js`

### Real-time DataTable Configuration
```javascript
// ✅ REQUIRED - Real-time DataTable integration pattern
const realTimeDataTable = useDataTableRealTime({
  entityType: 'sanPham',
  storeKey: 'productList',
  refreshCallback: async (refreshInfo) => {
    await productStore.forceRefreshProducts()
    if (refreshInfo.source === 'WEBSOCKET' && refreshInfo.topic?.includes('gia-san-pham')) {
      await dynamicPricing.refreshPricing()
    }
  },
  debounceDelay: 200,
  enableSelectiveUpdates: true,
  topicFilters: ['san-pham', 'gia-san-pham', 'ton-kho', 'product']
})
```

### Cross-tab Synchronization Enhancement
- **MUST** implement message queuing for offline scenarios with automatic replay
- **MUST** use BroadcastChannel API for cross-tab communication
- **MUST** synchronize real-time updates, connection status, and notifications across tabs
- **MUST** handle tab registration and deregistration with proper cleanup
- **MUST** implement Vietnamese notification messages for cross-tab events
- **MUST** coordinate WebSocket connections to prevent duplicate subscriptions

## AI Decision-Making Standards

### Priority Order for Ambiguous Situations
1. **Preserve Vietnamese business terminology** over English conventions
2. **Maintain existing audit trail patterns** over simplified implementations
3. **Follow established PrimeVue patterns** over custom UI solutions
4. **Use existing composables and stores** over creating new ones
5. **Update documentation** as part of implementation, not as afterthought
6. **Prioritize security compliance** for payment gateway implementations
7. **Use official API specifications** over web search results
8. **Implement real-time features with graceful degradation** over complex fallback systems
9. **Use WebSocket for real-time communication** over polling mechanisms
10. **Use unified real-time management** over individual WebSocket implementations
11. **Implement E2E testing** for critical workflows and real-time features

### Error Resolution Approach
1. **Check existing patterns** in similar components/services first
2. **Verify Vietnamese field names** match backend expectations
3. **Ensure proper role separation** in user management scenarios
4. **Validate audit trail implementation** for entity modifications
5. **Test integration points** between frontend and backend Vietnamese APIs
6. **Verify payment gateway security** against official documentation
7. **Test signature verification** with official test cases
8. **Verify WebSocket connection handling** and error recovery mechanisms
9. **Test real-time feature performance** under concurrent user scenarios

### Implementation Verification
- **VERIFY** all Vietnamese field names match between frontend and backend
- **VERIFY** proper role-based access control implementation
- **VERIFY** audit trail functionality for all entity operations
- **VERIFY** PrimeVue component integration follows project patterns
- **VERIFY** documentation updates reflect actual implementation changes
- **VERIFY** payment gateway security implementations against official specs
- **VERIFY** comprehensive testing of payment flows and error scenarios
- **VERIFY** WebSocket security and authentication mechanisms
- **VERIFY** real-time notification delivery and user experience
- **VERIFY** shipping API integration and fallback mechanisms
- **VERIFY** voucher monitoring and recommendation accuracy
- **VERIFY** Oxlint and ESLint integration working correctly
- **VERIFY** Playwright E2E tests covering critical workflows
- **VERIFY** unified real-time manager coordination
- **VERIFY** cross-tab synchronization functionality
- **VERIFY** real-time DataTable updates with proper filtering

### Payment Gateway Modernization Checklist
- **VERIFY** API version compliance (VNPay 2.1.0+, MoMo v3, VietQR v2)
- **VERIFY** proper HMAC signature algorithms (SHA512 for VNPay, SHA256 for MoMo)
- **VERIFY** environment-based configuration for all secrets
- **VERIFY** comprehensive IPN handling with proper error responses
- **VERIFY** audit trail logging for all payment operations
- **VERIFY** timeout handling and inventory management
- **VERIFY** Vietnamese error messages and user notifications

### Real-time Feature Verification Checklist
- **VERIFY** WebSocket connection establishment and authentication
- **VERIFY** real-time price update notifications and UI updates
- **VERIFY** order expiration job execution and inventory release
- **VERIFY** shipping fee calculation accuracy and API integration
- **VERIFY** voucher monitoring and recommendation engine functionality
- **VERIFY** Vietnamese messaging consistency across all real-time features
- **VERIFY** performance impact of real-time features on system resources
- **VERIFY** unified real-time manager coordination across components
- **VERIFY** cross-tab synchronization message delivery and processing
- **VERIFY** real-time DataTable updates with proper debouncing and filtering
- **VERIFY** enhanced status panel displays accurate connection information

## E2E Testing and Quality Assurance Standards

### Playwright E2E Testing Requirements
- **MUST** use Playwright for end-to-end testing of critical user workflows
- **MUST** test WebSocket real-time features including price updates and order notifications
- **MUST** implement Vietnamese business scenario testing (order creation, payment flows)
- **MUST** test cross-browser compatibility (Chromium, Firefox, WebKit)
- **MUST** create page object models for reusable test components
- **MUST** test responsive design across different viewport sizes
- **MUST** implement visual regression testing for UI consistency
- **LOCATION**: `frontend/tests/e2e/` directory structure
- **LOCATION**: `frontend/playwright.config.js` for configuration

### E2E Test Scenarios
- **MUST** test complete order creation workflow with Vietnamese business terminology
- **MUST** test real-time price updates and user notifications
- **MUST** test payment gateway integrations (VNPay, MoMo, VietQR)
- **MUST** test WebSocket connection handling and reconnection scenarios
- **MUST** test cross-tab synchronization functionality
- **MUST** test voucher application and validation workflows
- **MUST** test shipping fee calculation with GHN API integration

## Task Verification and Continuous Mode Standards

### Task Completion Verification Requirements
- **MUST** achieve ≥80 verification score before automatic progression to next task
- **MUST** run comprehensive diagnostics on all modified files
- **MUST** execute `./gradlew compileJava` for backend changes verification
- **MUST** verify application startup with `./gradlew bootRun` and check logs
- **MUST** validate WebSocket connectivity and Redis connections
- **MUST** confirm all "Related Files" entries are properly implemented
- **MUST** run Playwright E2E tests for critical workflow verification

### Self-Verification Questions (MANDATORY)
1. Did I implement ALL components listed in the task requirements?
2. Are there any compilation or lint errors in modified files?
3. Did I remove all unused code and imports?
4. Did I address every "Related Files" entry in the task?
5. Is 100% backward compatibility maintained?
6. Are Vietnamese naming conventions preserved throughout?
7. Do API responses remain unchanged from original contracts?
8. Did I consider and implement better solutions where applicable?
9. Are distributed locking optimizations properly implemented?
10. Is WebSocket integration functional and properly tested?
11. Do price displays use original prices (originalOldPrice/originalNewPrice) not effective prices?
12. Are all temporary testing notifications removed from production code?
13. Do price change notifications display original price changes in Vietnamese format?
14. Is WebSocket price update message structure correct with both English and Vietnamese fields?
15. Are Oxlint and ESLint both passing without errors?
16. Do E2E tests pass for modified workflows?
17. Is cross-tab synchronization working correctly?
18. Are real-time DataTable updates functioning properly?
19. Are code section headers properly organized using standard format?
20. Is documentation updated to reflect architectural changes?
21. Are WebSocket topics correctly structured for streaming functionality?
22. Is the unified hybrid approach properly implemented without intent classification?
23. Are Vietnamese language processing requirements maintained?
24. Is Python service organization following established patterns?

### Continuous Mode Execution Rules
- **MUST** automatically progress to next dependent task after ≥80 verification score
- **MUST** skip tasks explicitly marked for skipping by user
- **MUST** maintain task dependency order unless explicitly overridden
- **MUST** provide comprehensive task summaries with performance metrics
- **MUST** document all optimization improvements (lock duration reductions, batch operation benefits)
- **MUST** preserve audit trail functionality throughout all optimizations

### Performance Optimization Verification
- **VERIFY** lock duration reductions (target: 55% reduction from 45s to 20s)
- **VERIFY** batch operation implementation (target: 80-90% reduction in database round trips)
- **VERIFY** fine-grained locking for high-concurrency scenarios
- **VERIFY** deferred non-critical operations outside lock scope
- **VERIFY** thread-safe event publishing with AtomicReference patterns
- **VERIFY** WebSocket event publishing performance impact

### Price Notification System Verification
- **VERIFY** PriceChangeWarning component uses originalOldPrice/originalNewPrice fields exclusively
- **VERIFY** WebSocket messages contain original prices in both English and Vietnamese field names
- **VERIFY** Price change notifications display original prices (19,387,000 VND format) not discounted prices
- **VERIFY** Vietnamese business terminology preserved in all price change messages
- **VERIFY** Temporary testing notifications completely removed from production code
- **VERIFY** Component compilation successful with no errors after price display modifications
- **VERIFY** useRealTimePricing composable correctly processes WebSocket price update messages
- **VERIFY** Backend WebSocketIntegrationService sends original prices (giaBan) not effective prices (giaKhuyenMai)

## Recent Architecture Improvements and Standards

### Payment System Architecture Rules (Updated 2025-06-28)
- **MUST** use direct service integration pattern without factory abstractions
- **MUST** inject payment services directly: `VNPayService`, `MoMoService`
- **MUST** use centralized `PaymentAuditLogger` for all payment audit logging
- **MUST** follow consistent naming: `[Gateway]Service` (e.g., `VNPayService`, `MoMoService`)
- **MUST** use `PaymentValidationService` for centralized validation logic when needed
- **PROHIBITED**: Creating factory patterns or unnecessary abstraction layers
- **EXAMPLE**: HoaDonService uses `vnPayService` and `moMoService` directly with `paymentAuditLogger`

### Frontend Configuration Simplification (Updated 2025-01-28)
- **MUST** use `window.location.origin` for payment return URLs
- **PROHIBITED**: Complex environment variable dependencies in `returnUrlConfig.js`
- **PROHIBITED**: `.env.example` files with multiple fallback configurations
- **MUST** keep configuration utilities under 50 lines
- **EXAMPLE**: `returnUrlConfig.js` simplified from 137 lines to 20 lines

### VNPay-Specific UI Component Removal (Updated 2025-01-28)
- **PROHIBITED**: Gateway-specific UI components (`vnpayPaymentState`, `momoPaymentState`)
- **PROHIBITED**: Gateway-specific loading indicators and error states
- **MUST** use generic `creating` state for all payment methods
- **MUST** remove gateway-specific functions (`handleVNPayError`, `classifyVNPayError`, etc.)
- **MUST** use consistent error handling patterns across all payment gateways

### Code Simplification Guidelines (Updated 2025-01-28)
- **MUST** prioritize code simplicity over feature complexity
- **MUST** consolidate similar functionality into shared utilities
- **PROHIBITED**: Spreading payment logic across multiple files and components
- **MUST** remove over-engineered solutions in favor of straightforward implementations
- **MUST** ensure all payment methods use consistent error handling and state management patterns

### Architecture Analysis Requirements (Updated 2025-01-28)
- **MUST** conduct comprehensive analysis of existing architecture before creating new services
- **MUST** identify existing abstractions (interfaces, abstract classes, service implementations)
- **MUST** analyze current design patterns before implementing new functionality
- **MUST** determine if new functionality can be integrated into existing classes
- **PROHIBITED**: Creating parallel implementations when existing abstractions exist
- **MUST** ensure consistency with established architecture patterns

### Enhanced PaymentGatewayService Interface (Updated 2025-01-28)
- **MUST** use `getGatewayDisplayName()` default method for consistent naming
- **MUST** implement Vietnamese display names in gateway services
- **EXAMPLE**: VNPay returns "VNPay", MoMo returns "MoMo", TIEN_MAT returns "Tiền mặt"

### Task Management Quality Standards (Updated 2025-01-28)
- **MUST** use Shrimp Task Manager for complex implementations requiring structured planning
- **MUST** achieve ≥80 verification score for task completion
- **MUST** run `./gradlew compileJava` for backend changes verification
- **MUST** verify ALL files in "Related Files" section are implemented
- **MUST** remove unused imports and dependencies after refactoring
- **MUST** maintain 100% backward compatibility during architecture improvements

## Streamlined Architecture Validation Standards (Updated 2025-01-07)

### AI Chat System Validation Requirements
- **VERIFY** unified `/chat/recommend` endpoint handles all chat types correctly
- **VERIFY** intent classification components are completely removed
- **VERIFY** GitHub AI (Mistral Medium 3) integration is functional
- **VERIFY** Vietnamese language processing with pyvi tokenization works
- **VERIFY** WebSocket streaming functionality is preserved
- **VERIFY** 180-second timeout compatibility is maintained
- **VERIFY** product recommendation vector search is operational
- **VERIFY** fallback responses are in Vietnamese when AI service unavailable

### Code Organization Validation
- **VERIFY** section headers follow `==================` format consistently
- **VERIFY** Java services have logical method grouping (STATE, CORE METHODS, STREAMING, STATUS)
- **VERIFY** Python services have proper functional organization
- **VERIFY** Vue components have organized imports (Vue, Composables, PrimeVue)
- **VERIFY** unused legacy code (intent classification, routing stats) is removed
- **VERIFY** documentation reflects current streamlined architecture

### WebSocket Integration Validation
- **VERIFY** topic structure follows `/topic/ai-chat/{sessionId}/{type}` pattern
- **VERIFY** streaming chunks are handled correctly in real-time
- **VERIFY** session ID generation uses proper format
- **VERIFY** connection handling includes automatic reconnection
- **VERIFY** timeout handling works with 180-second AI processing
