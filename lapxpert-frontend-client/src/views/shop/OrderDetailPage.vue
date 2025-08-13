<template>
  <Toast />

  <!-- Page Header -->
  <div class="card mb-6">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 bg-primary/10 rounded-lg flex items-center justify-center">
          <i class="pi pi-shopping-cart text-lg text-primary"></i>
        </div>
        <div>
          <h1 class="font-semibold text-xl text-surface-900 m-0">
            Chi tiết đơn hàng {{ order?.maHoaDon || '' }}
          </h1>
          <p class="text-surface-500 text-sm mt-1 mb-0">
            Xem thông tin chi tiết đơn hàng của bạn
          </p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <!-- Action Buttons (simplified for customer) -->
        <Button v-if="order" label="Xem hóa đơn" icon="pi pi-file-pdf" severity="info" outlined size="small"
          @click="showReceiptPreview = true" />
        <Button label="Làm mới" icon="pi pi-refresh" severity="secondary" outlined size="small" @click="refreshData"
          :loading="loading" />
        <Button icon="pi pi-arrow-left" severity="secondary" outlined size="small" @click="goBack"
          v-tooltip.left="'Quay lại'" />
      </div>
    </div>
  </div>

  <!-- Loading State -->
  <div v-if="loading && !order" class="text-center py-12">
    <ProgressSpinner />
    <p class="mt-4 text-surface-600">Đang tải thông tin đơn hàng...</p>
  </div>

  <!-- Error State -->
  <div v-else-if="error" class="text-center py-12">
    <i class="pi pi-exclamation-triangle text-4xl text-red-500 mb-4 block"></i>
    <h3 class="text-xl font-semibold mb-2">Có lỗi xảy ra</h3>
    <p class="text-surface-600 mb-4">{{ error }}</p>
    <Button label="Thử lại" icon="pi pi-refresh" @click="loadOrder" />
  </div>

  <!-- Order Content -->
  <div v-else-if="order" class="card">
    <!-- Modern Tabs Layout -->
    <Tabs value="info" class="order-detail-tabs">
      <TabList class="mb-6">
        <Tab value="info" class="flex items-center gap-2">
          <i class="pi pi-info"></i>
          <span>Thông tin đơn hàng</span>
        </Tab>
        <Tab value="items" class="flex items-center gap-2">
          <i class="pi pi-list"></i>
          <span>Sản phẩm</span>
          <Badge :value="order.chiTiet?.length || 0" severity="info" class="ml-2" />
        </Tab>
        <Tab value="payment" class="flex items-center gap-2">
          <i class="pi pi-credit-card"></i>
          <span>Thanh toán</span>
        </Tab>
        <Tab value="timeline" class="flex items-center gap-2">
          <i class="pi pi-clock"></i>
          <span>Trạng thái</span>
        </Tab>
      </TabList>

      <TabPanels>
        <TabPanel value="info">
          <!-- Order Information -->
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
            <!-- Basic Information -->
            <div class="card border border-surface-200 dark:border-surface-700 h-full">
              <div class="flex items-center gap-2 mb-4">
                <i class="pi pi-info-circle text-primary"></i>
                <span class="font-semibold text-xl">Thông tin cơ bản</span>
              </div>
              <div class="space-y-4">
                <div>
                  <label class="text-sm font-medium text-surface-600">Mã đơn hàng</label>
                  <p class="font-mono text-lg">{{ order.maHoaDon }}</p>
                </div>

                <div>
                  <label class="text-sm font-medium text-surface-600">Loại đơn hàng</label>
                  <div class="flex items-center gap-2 mt-1">
                    <i :class="getOrderTypeInfo(order.loaiHoaDon).icon" class="text-primary"></i>
                    <span class="font-medium">{{ getOrderTypeInfo(order.loaiHoaDon).label }}</span>
                  </div>
                </div>

                <div>
                  <label class="text-sm font-medium text-surface-600">Trạng thái đơn hàng</label>
                  <div class="mt-1">
                    <Badge :value="getOrderStatusInfo(order.trangThaiDonHang).label"
                      :severity="getOrderStatusInfo(order.trangThaiDonHang).severity" class="text-sm" />
                  </div>
                </div>

                <div>
                  <label class="text-sm font-medium text-surface-600">Ngày tạo</label>
                  <p class="text-lg">{{ formatDateTime(order.ngayTao) }}</p>
                </div>

                <div v-if="order.ngayCapNhat">
                  <label class="text-sm font-medium text-surface-600">Ngày cập nhật</label>
                  <p class="text-lg">{{ formatDateTime(order.ngayCapNhat) }}</p>
                </div>

                <div v-if="order.ghiChu">
                  <label class="text-sm font-medium text-surface-600">Ghi chú</label>
                  <p class="text-sm bg-surface-50 dark:bg-surface-800 p-3 rounded">{{ order.ghiChu }}</p>
                </div>
              </div>
            </div>

            <!-- Customer Information -->
            <div class="card border border-surface-200 dark:border-700 h-full">
              <div class="flex items-center gap-2 mb-4">
                <i class="pi pi-user text-primary"></i>
                <span class="font-semibold text-xl">Thông tin khách hàng</span>
              </div>
              <div class="space-y-4">
                <div v-if="order.khachHang">
                  <label class="text-sm font-medium text-surface-600">Tên khách hàng</label>
                  <p class="text-lg font-semibold">{{ order.khachHang.hoTen }}</p>
                </div>
                <div v-else>
                  <p class="text-lg font-semibold text-surface-500">Khách lẻ</p>
                </div>

                <div v-if="order.khachHang?.soDienThoai">
                  <label class="text-sm font-medium text-surface-600">Số điện thoại</label>
                  <p class="text-lg">{{ order.khachHang.soDienThoai }}</p>
                </div>

                <div v-if="order.khachHang?.email">
                  <label class="text-sm font-medium text-surface-600">Email</label>
                  <p class="text-lg">{{ order.khachHang.email }}</p>
                </div>

                <!-- Delivery Information -->
                <div v-if="order.diaChiGiaoHang || order.nguoiNhanTen || order.nguoiNhanSdt">
                  <label class="text-sm font-medium text-surface-600">Thông tin giao hàng</label>
                  <div class="text-sm bg-surface-50 dark:bg-surface-800 p-3 rounded space-y-2">
                    <div v-if="order.nguoiNhanTen" class="flex items-center gap-2">
                      <i class="pi pi-user text-surface-500"></i>
                      <span><strong>Người nhận:</strong> {{ order.nguoiNhanTen }}</span>
                    </div>
                    <div v-if="order.nguoiNhanSdt" class="flex items-center gap-2">
                      <i class="pi pi-phone text-surface-500"></i>
                      <span><strong>Số điện thoại:</strong> {{ order.nguoiNhanSdt }}</span>
                    </div>
                    <div v-if="order.diaChiGiaoHang" class="flex items-start gap-2">
                      <i class="pi pi-map-marker text-surface-500 mt-0.5"></i>
                      <div class="flex-1">
                        <strong>Địa chỉ giao hàng:</strong>
                        <div class="mt-1 space-y-2">
                          <div v-if="typeof order.diaChiGiaoHang === 'object'">
                            <!-- Full address summary -->
                            <div
                              class="p-2 bg-surface-100 dark:bg-surface-700 rounded text-surface-800 dark:text-surface-200">
                              {{ formatDetailedAddress(order.diaChiGiaoHang) }}
                            </div>
                          </div>
                          <div v-else class="text-surface-700">
                            {{ order.diaChiGiaoHang }}
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Staff Information (simplified for customer) -->
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start mt-6">
            <div class="card border border-surface-200 dark:border-surface-700 h-full">
              <div class="flex items-center gap-2 mb-4">
                <i class="pi pi-users text-primary"></i>
                <span class="font-semibold text-xl">Thông tin nhân viên hỗ trợ</span>
              </div>
              <div class="space-y-4">
                <div v-if="order.nhanVien">
                  <label class="text-sm font-medium text-surface-600">Nhân viên tạo đơn</label>
                  <p class="text-lg font-semibold">{{ order.nhanVien.hoTen || 'Không có thông tin' }}</p>
                </div>
                <div v-else>
                  <label class="text-sm font-medium text-surface-600">Nhân viên tạo đơn</label>
                  <p class="text-lg text-surface-500">Không có thông tin nhân viên</p>
                </div>
              </div>
            </div>

            <!-- Order Summary -->
            <div class="card border border-surface-200 dark:border-surface-700 h-full">
              <div class="flex items-center gap-2 mb-4">
                <i class="pi pi-calculator text-primary"></i>
                <span class="font-semibold text-xl">Tổng kết đơn hàng</span>
              </div>
              <div class="space-y-4">
                <div class="flex justify-between items-center">
                  <span class="text-surface-600">Tổng sản phẩm:</span>
                  <span class="font-semibold">{{ distinctProductCount }} sản phẩm</span>
                </div>

                <div class="flex justify-between items-center">
                  <span class="text-surface-600">Tổng tiền hàng:</span>
                  <span class="font-semibold">{{ formatCurrency(order.tongTienHang || 0) }}</span>
                </div>

                <div v-if="order.phiVanChuyen" class="flex justify-between items-center">
                  <span class="text-surface-600">Phí vận chuyển:</span>
                  <span class="font-semibold">{{ formatCurrency(order.phiVanChuyen) }}</span>
                </div>

                <div v-if="order.giaTriGiamGiaVoucher" class="flex justify-between items-center">
                  <span class="text-surface-600">Giảm giá voucher:</span>
                  <span class="font-semibold text-green-600">-{{ formatCurrency(order.giaTriGiamGiaVoucher) }}</span>
                </div>

                <hr class="border-surface-200 dark:border-surface-700">

                <div class="flex justify-between items-center">
                  <span class="text-lg font-semibold">Tổng thanh toán:</span>
                  <span class="text-xl font-bold text-primary">{{ formatCurrency(order.tongThanhToan) }}</span>
                </div>
              </div>
            </div>
          </div>
        </TabPanel>

        <TabPanel value="items">
          <!-- Order Items -->
          <div class="card border border-surface-200 dark:border-surface-700">
            <div class="flex items-center gap-2 mb-4">
              <i class="pi pi-box text-primary"></i>
              <span class="font-semibold text-xl">Danh sách sản phẩm</span>
            </div>

            <div v-if="!order.chiTiet || order.chiTiet.length === 0" class="text-center py-8">
              <i class="pi pi-box text-4xl text-surface-400 mb-4 block"></i>
              <p class="text-surface-600">Không có sản phẩm trong đơn hàng</p>
            </div>

            <DataTable v-else :value="order.chiTiet" showGridlines class="p-datatable-sm" responsiveLayout="scroll">
              <Column field="sanPhamChiTiet.sanPham.tenSanPham" header="Sản phẩm" class="min-w-48">
                <template #body="{ data }">
                  <div class="flex items-center gap-3">
                    <img v-if="getProductImage(data)" :src="getProductImage(data)"
                      :alt="data.tenSanPhamSnapshot || data.sanPhamChiTiet?.sanPham?.tenSanPham"
                      class="w-12 h-12 object-cover rounded border" @error="handleImageError" />
                    <div
                      class="w-12 h-12 bg-surface-100 dark:bg-surface-800 rounded border flex items-center justify-center"
                      v-else>
                      <i class="pi pi-image text-surface-400"></i>
                    </div>
                    <div>
                      <p class="font-semibold">{{ data.tenSanPhamSnapshot || data.sanPhamChiTiet?.sanPham?.tenSanPham ||
                        'Không có tên' }}</p>
                      <p class="text-sm text-surface-600">{{ data.skuSnapshot || data.sanPhamChiTiet?.sanPham?.maSanPham
                        || 'Không có mã' }}</p>

                      <!-- Simplified Serial Numbers Display -->
                      <div class="mt-2">
                        <!-- Show serial numbers if available -->
                        <div v-if="getSerialNumbers(data).length > 0" class="flex flex-wrap gap-1">
                          <div v-for="serial in getSerialNumbers(data)" :key="serial"
                            class="inline-flex items-center gap-1 px-2 py-1 bg-primary-50 text-primary-700 border border-primary-200 rounded text-xs font-mono">
                            <i class="pi pi-hashtag text-xs"></i>
                            <span>Serial: {{ serial }}</span>
                          </div>
                        </div>

                        <!-- No serial numbers available -->
                        <!-- <div v-else class="flex items-center gap-2">
                          <span class="text-xs text-surface-500 italic">Không có serial number</span>
                        </div> -->
                      </div>
                    </div>
                  </div>
                </template>
              </Column>

              <Column header="Giá bán" class="text-right min-w-28">
                <template #body="{ data }">
                  <div class="space-y-1">
                    <div class="font-semibold">{{ formatCurrency(data.giaBan) }}</div>
                    <div v-if="data.giaGoc && data.giaGoc !== data.giaBan"
                      class="text-xs text-surface-500 line-through">
                      {{ formatCurrency(data.giaGoc) }}
                    </div>
                  </div>
                </template>
              </Column>

              <Column header="Thành tiền" class="text-right min-w-32">
                <template #body="{ data }">
                  <span class="font-bold text-primary">{{ formatCurrency(data.thanhTien || (data.giaBan * data.soLuong))
                    }}</span>
                </template>
              </Column>
            </DataTable>

            <!-- Order Items Summary -->
            <div class="mt-6 pt-4 border-t border-surface-200 dark:border-surface-700">
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div class="text-center">
                  <div class="text-2xl font-bold text-green-600">{{ totalQuantity }}</div>
                  <div class="text-sm text-surface-600">Tổng số lượng</div>
                </div>
                <div class="text-center">
                  <div class="text-2xl font-bold text-orange-600">{{ formatCurrency(totalAmount) }}</div>
                  <div class="text-sm text-surface-600">Tổng tiền hàng</div>
                </div>
              </div>
            </div>
          </div>
        </TabPanel>

        <TabPanel value="payment">
          <!-- Enhanced Payment Information using new components -->
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <!-- Payment Status Component (simplified for customer) -->
            <PaymentStatus :payment-status="order.trangThaiThanhToan" :total-amount="order.tongThanhToan || 0"
              :paid-amount="getPaidAmount()" :payment-method="getPaymentMethod()"
              :transaction-id="order.maGiaoDichThanhToan" :payment-date="order.ngayThanhToan"
              :payment-history="paymentHistory" :processing="loading" :show-actions="false" />

            <!-- Payment Summary Component -->
            <PaymentSummary :subtotal="order.tongTienHang || 0" :shipping-fee="order.phiVanChuyen || 0"
              :voucher-discount="order.giaTriGiamGiaVoucher || 0" :campaign-discount="0"
              :total-amount="order.tongThanhToan || 0" :total-items="totalQuantity" :payment-method="getPaymentMethod()"
              :payment-status="order.trangThaiThanhToan" :paid-amount="getPaidAmount()"
              :applied-vouchers="order.hoaDonPhieuGiamGias || []" :show-payment-status="true" />
          </div>

          <!-- Vouchers Applied -->
          <div v-if="order.hoaDonPhieuGiamGias && order.hoaDonPhieuGiamGias.length > 0" class="mt-6">
            <div class="card border border-surface-200 dark:border-surface-700">
              <div class="flex items-center gap-2 mb-4">
                <i class="pi pi-ticket text-primary"></i>
                <span class="font-semibold text-xl">Voucher đã áp dụng</span>
              </div>

              <div class="space-y-3">
                <div v-for="hoaDonVoucher in order.hoaDonPhieuGiamGias" :key="hoaDonVoucher.id"
                  class="flex items-center justify-between p-3 bg-surface-50 dark:bg-surface-800 rounded border">
                  <div class="flex items-center gap-3">
                    <i class="pi pi-ticket text-green-600"></i>
                    <div>
                      <p class="font-semibold">{{ hoaDonVoucher.phieuGiamGia?.tenPhieu || 'Voucher' }}</p>
                      <p class="text-sm text-surface-600">{{ hoaDonVoucher.phieuGiamGia?.maPhieu ||
                        hoaDonVoucher.maPhieu }}</p>
                    </div>
                  </div>
                  <div class="text-right">
                    <p class="font-semibold text-green-600">-{{ formatCurrency(hoaDonVoucher.giaTriGiam) }}</p>
                    <p class="text-xs text-surface-600">
                      {{ hoaDonVoucher.phieuGiamGia?.loaiGiamGia === 'PHAN_TRAM' ? `${hoaDonVoucher.giaTriGiam}%` : 'Số tiền cố định' }}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </TabPanel>

        <TabPanel value="timeline">
          <!-- Order Timeline -->
          <div class="card border border-surface-200 dark:border-surface-700">
            <div class="flex items-center justify-between mb-6">
              <div class="flex items-center gap-2">
                <i class="pi pi-clock text-primary"></i>
                <span class="font-semibold text-xl">Timeline trạng thái đơn hàng</span>
              </div>
              <div class="text-sm text-surface-600">
                {{orderTimeline.filter(item => item.completed).length}} / {{ orderTimeline.length }} bước hoàn thành
              </div>
            </div>

            <!-- Progress Summary -->
            <div class="mb-6 p-4 bg-surface-50 dark:bg-surface-800 rounded-lg">
              <div class="flex items-center justify-between mb-3">
                <span class="text-sm font-medium text-surface-700 dark:text-surface-300">Tiến trình đơn hàng</span>
                <span class="text-sm text-surface-600">
                  {{Math.round((orderTimeline.filter(item => item.completed).length / orderTimeline.length) * 100)}}%
                </span>
              </div>
              <div class="w-full bg-surface-200 dark:bg-surface-700 rounded-full h-2">
                <div class="bg-primary h-2 rounded-full transition-all duration-300"
                  :style="{ width: `${Math.round((orderTimeline.filter(item => item.completed).length / orderTimeline.length) * 100)}%` }">
                </div>
              </div>
              <div class="flex justify-between mt-2 text-xs text-surface-600">
                <span>Bắt đầu</span>
                <span v-if="order.trangThaiDonHang === 'HOAN_THANH'" class="text-green-600 font-medium">Hoàn
                  thành</span>
                <span v-else-if="order.trangThaiDonHang === 'DA_HUY'" class="text-red-600 font-medium">Đã hủy</span>
                <span v-else class="text-primary font-medium">Đang xử lý</span>
              </div>
            </div>

            <Timeline :value="orderTimeline" layout="horizontal" class="w-full">
              <!-- Marker -->
              <template #marker="{ item }">
                <div
                  class="relative flex items-center justify-center w-10 h-10 rounded-full border-2 transition-all duration-300"
                  :class="item.markerClass">
                  <i :class="item.icon" class="text-sm"></i>
                  <!-- Current status indicator -->
                  <div v-if="item.current"
                    class="absolute -top-1 -right-1 w-3 h-3 bg-primary rounded-full border-2 border-white"></div>
                  <!-- Pending status indicator -->
                  <div v-if="item.pending"
                    class="absolute inset-0 rounded-full border-2 border-dashed border-surface-300"></div>
                </div>
              </template>

              <!-- Content -->
              <template #content="{ item }">
                <Card
                  class="border border-surface-200 dark:border-surface-700 shadow-sm hover:shadow-md transition-shadow duration-300 min-w-[225px] min-h-[250px] max-w-[225px] mx-2"
                  :class="{
                    'opacity-60': item.pending,
                    'ring-1 ring-primary-200': item.current,
                    'bg-surface-50 dark:bg-surface-800': item.pending
                  }">
                  <template #content>
                    <div class="space-y-2 p-4">
                      <!-- Header -->
                      <div class="flex flex-col items-start">
                        <!-- Title -->
                        <span class="font-semibold text-base text-surface-900 dark:text-surface-0"
                          :class="{ 'text-surface-500': item.pending }">
                          {{ item.title }}
                        </span>

                        <!-- Status and Indicators -->
                        <div class="flex items-center gap-2 mt-1">
                          <Badge :value="item.status" :severity="item.severity"
                            :class="{ 'opacity-60': item.pending }" />
                          <!-- Status indicators -->
                          <div class="flex items-center gap-1">
                            <i v-if="item.completed && !item.current"
                              class="pi pi-check-circle text-green-500 text-xs"></i>
                            <i v-if="item.current" class="pi pi-clock text-primary text-xs"></i>
                            <i v-if="item.pending" class="pi pi-hourglass text-surface-400 text-xs"></i>
                          </div>
                        </div>

                        <!-- Timestamp -->
                        <div class="mt-1">
                          <span v-if="item.timestamp" class="text-xs text-surface-600 dark:text-surface-400">
                            {{ formatDateTime(item.timestamp) }}
                          </span>
                          <div v-else-if="item.pending" class="text-xs text-surface-500 italic">
                            Chờ xử lý
                          </div>
                        </div>
                      </div>

                      <!-- Description -->
                      <p v-if="item.description" class="text-sm text-surface-700 dark:text-surface-300 line-clamp-2"
                        :class="{ 'text-surface-500': item.pending }">
                        {{ item.description }}
                      </p>

                      <!-- User -->
                      <div v-if="item.user" class="text-xs text-surface-600 dark:text-surface-400"
                        :class="{ 'text-surface-500': item.pending }">
                        Bởi: {{ item.user }}
                      </div>

                      <!-- Type-specific info -->
                      <div v-if="item.type === 'payment'"
                        class="text-xs bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300 p-2 rounded flex items-center">
                        <i class="pi pi-info-circle mr-1"></i>
                        Giao dịch thanh toán đã được xác nhận
                      </div>
                      <div v-if="item.type === 'refund'"
                        class="text-xs bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300 p-2 rounded flex items-center">
                        <i class="pi pi-info-circle mr-1"></i>
                        Tiền đã được hoàn trả về tài khoản khách hàng
                      </div>
                      <div v-if="item.type === 'cancellation'"
                        class="text-xs bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300 p-2 rounded flex items-center">
                        <i class="pi pi-exclamation-triangle mr-1"></i>
                        Đơn hàng đã bị hủy và không thể khôi phục
                      </div>
                    </div>
                  </template>
                </Card>
              </template>
            </Timeline>
          </div>
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>

  <!-- Receipt Preview Dialog -->
  <ReceiptPreviewDialog v-model:visible="showReceiptPreview" :order-id="order?.id" :order-code="order?.maHoaDon" />
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useOrderStore } from '@/stores/orderStore'
import orderApi from '@/apis/orderApi'
import storageApi from '@/apis/storage'
import serialNumberApi from '@/apis/serialNumberApi'
import PaymentStatus from '@/views/orders/components/PaymentStatus.vue'
import PaymentSummary from '@/views/orders/components/PaymentSummary.vue'
import ReceiptPreviewDialog from '@/views/orders/components/ReceiptPreviewDialog.vue'

// PrimeVue Components
import Toast from 'primevue/toast'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import Badge from 'primevue/badge'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Timeline from 'primevue/timeline'
import Card from 'primevue/card'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const orderStore = useOrderStore()

// Component state
const order = ref(null)
const loading = ref(false)
const error = ref(null)
const showReceiptPreview = ref(false)
const imageUrlCache = ref(new Map())
const orderSerialNumbers = ref([])

// Computed properties
const totalQuantity = computed(() => {
  if (!order.value?.chiTiet) return 0
  return order.value.chiTiet.reduce((total, item) => total + item.soLuong, 0)
})

const totalAmount = computed(() => {
  if (!order.value?.chiTiet) return 0
  return order.value.chiTiet.reduce((total, item) => total + (item.thanhTien || (item.giaBan * item.soLuong)), 0)
})

const distinctProductCount = computed(() => {
  if (!order.value?.chiTiet) return 0
  const uniqueProducts = new Set()
  order.value.chiTiet.forEach(item => {
    const productKey = item.tenSanPhamSnapshot || item.sanPhamChiTiet?.sanPham?.tenSanPham || item.sanPhamChiTiet?.sanPham?.id
    if (productKey) {
      uniqueProducts.add(productKey)
    }
  })
  return uniqueProducts.size
})

const orderTimeline = computed(() => {
  if (!order.value) return []

  const timeline = []
  let currentStatus = order.value.trangThaiDonHang
  const currentPaymentStatus = order.value.trangThaiThanhToan

  // If payment is complete, force the timeline to show 'HOAN_THANH' status
  if (currentPaymentStatus === 'DA_THANH_TOAN') {
    currentStatus = 'HOAN_THANH'
  }

  const getOrderLifecycle = () => {
    const baseLifecycle = [
      'CHO_XAC_NHAN',
      'DA_XAC_NHAN',
      'DANG_XU_LY',
      'CHO_GIA_HANG',
      'DANG_GIAO_HANG',
      'HOAN_THANH'
    ]

    if (order.value.loaiHoaDon === 'TAI_QUAY') {
      return [
        'CHO_XAC_NHAN',
        'DA_XAC_NHAN',
        'DANG_XU_LY',
        'HOAN_THANH'
      ]
    }
    return baseLifecycle
  }

  const lifecycle = getOrderLifecycle()
  const currentStatusIndex = lifecycle.indexOf(currentStatus)

  // 1. Add creation event
  timeline.push({
    status: 'Tạo đơn hàng',
    title: 'Đơn hàng được tạo',
    description: `Đơn hàng ${order.value.maHoaDon} được tạo thành công`,
    timestamp: order.value.ngayTao,
    user: order.value.nhanVien?.hoTen || 'Hệ thống',
    icon: 'pi pi-plus',
    severity: 'success',
    markerClass: 'bg-green-100 border-green-300 text-green-600',
    completed: true,
    type: 'creation'
  })

  // 2. Add payment events if payment is confirmed
  if (currentPaymentStatus === 'DA_THANH_TOAN' && order.value.ngayThanhToan) {
    timeline.push({
      status: 'Thanh toán',
      title: 'Thanh toán thành công',
      description: `Thanh toán qua ${getPaymentMethodLabel(order.value.phuongThucThanhToan)} - ${formatCurrency(order.value.tongThanhToan)}`,
      timestamp: order.value.ngayThanhToan,
      user: order.value.nguoiXacNhanThanhToan || order.value.nhanVien?.hoTen || 'Hệ thống',
      icon: 'pi pi-credit-card',
      severity: 'success',
      markerClass: 'bg-green-100 border-green-300 text-green-600',
      completed: true,
      type: 'payment'
    })
  }

  // 3. Add all lifecycle statuses
  lifecycle.forEach((status, index) => {
    const statusInfo = getOrderStatusInfo(status)
    const isCompleted = index <= currentStatusIndex
    const isCurrent = status === currentStatus
    const isPending = index > currentStatusIndex && currentStatus !== 'DA_HUY'

    if (currentStatus === 'DA_HUY' && index > currentStatusIndex) {
      return
    }

    let timestamp = null
    let user = 'Hệ thống'
    let description = getStatusDescription(status)

    if (isCurrent) {
      timestamp = order.value.ngayCapNhat || order.value.ngayTao
      user = order.value.nguoiCapNhat || order.value.nhanVien?.hoTen || 'Hệ thống'
    } else if (isCompleted) {
      timestamp = order.value.ngayTao
      description += ' (Đã hoàn thành)'
    } else if (isPending) {
      description = `Chờ ${statusInfo.label.toLowerCase()}`
    }

    timeline.push({
      status: statusInfo.label,
      title: isCurrent ? `Hiện tại: ${statusInfo.label}` :
        isCompleted ? `Đã hoàn thành: ${statusInfo.label}` :
          `Sắp tới: ${statusInfo.label}`,
      description,
      timestamp,
      user: isPending ? null : user,
      icon: statusInfo.icon,
      severity: isPending ? 'secondary' : statusInfo.severity,
      markerClass: isPending ? 'bg-surface-100 border-surface-300 text-surface-500' :
        isCurrent ? getTimelineMarkerClass(status) + ' ring-2 ring-primary-200' :
          getTimelineMarkerClass(status),
      completed: isCompleted,
      current: isCurrent,
      pending: isPending,
      type: 'status'
    })
  })

  // 4. Add cancellation event if order is cancelled
  if (currentStatus === 'DA_HUY') {
    timeline.push({
      status: 'Đã hủy',
      title: 'Đơn hàng đã bị hủy',
      description: order.value.lyDoHuy || 'Đơn hàng đã bị hủy',
      timestamp: order.value.ngayCapNhat || order.value.ngayTao,
      user: order.value.nguoiCapNhat || order.value.nhanVien?.hoTen || 'Hệ thống',
      icon: 'pi pi-times-circle',
      severity: 'danger',
      markerClass: 'bg-red-100 border-red-300 text-red-600',
      completed: true,
      type: 'cancellation'
    })
  }

  // 5. Add return/refund events if applicable
  if (currentStatus === 'YEU_CAU_TRA_HANG' || currentStatus === 'DA_TRA_HANG') {
    timeline.push({
      status: 'Trả hàng',
      title: currentStatus === 'YEU_CAU_TRA_HANG' ? 'Yêu cầu trả hàng' : 'Đã trả hàng',
      description: currentStatus === 'YEU_CAU_TRA_HANG' ?
        'Khách hàng yêu cầu trả hàng' :
        'Đã xử lý trả hàng thành công',
      timestamp: order.value.ngayCapNhat || order.value.ngayTao,
      user: order.value.nguoiCapNhat || 'Hệ thống',
      icon: 'pi pi-undo',
      severity: 'warning',
      markerClass: 'bg-yellow-100 border-yellow-300 text-yellow-600',
      completed: true,
      type: 'return'
    })
  }

  if (currentPaymentStatus === 'DA_HOAN_TIEN') {
    timeline.push({
      status: 'Hoàn tiền',
      title: 'Đã hoàn tiền',
      description: `Hoàn tiền thành công - ${formatCurrency(order.value.tongThanhToan)}`,
      timestamp: order.value.ngayHoanTien || order.value.ngayCapNhat,
      user: order.value.nguoiXacNhanHoanTien || 'Hệ thống',
      icon: 'pi pi-money-bill',
      severity: 'info',
      markerClass: 'bg-blue-100 border-blue-300 text-blue-600',
      completed: true,
      type: 'refund'
    })
  }

  return timeline.sort((a, b) => {
    const businessOrder = {
      'creation': 1,
      'payment': 2,
      'status': 3,
      'cancellation': 4,
      'return': 5,
      'refund': 6
    }

    const aOrder = businessOrder[a.type] || 999
    const bOrder = businessOrder[b.type] || 999

    if (aOrder !== bOrder) {
      return aOrder - bOrder
    }

    if (a.type === 'status' && b.type === 'status') {
      const lifecycleOrder = ['CHO_XAC_NHAN', 'DA_XAC_NHAN', 'DANG_XU_LY', 'CHO_GIA_HANG', 'DANG_GIAO_HANG', 'HOAN_THANH']

      if (a.pending && b.pending) {
        const aIndex = lifecycleOrder.findIndex(status => a.status.includes(getOrderStatusInfo(status).label))
        const bIndex = lifecycleOrder.findIndex(status => b.status.includes(getOrderStatusInfo(status).label))
        return aIndex - bIndex
      }

      const aIndex = lifecycleOrder.findIndex(status => a.status.includes(getOrderStatusInfo(status).label))
      const bIndex = lifecycleOrder.findIndex(status => b.status.includes(getOrderStatusInfo(status).label))
      return aIndex - bIndex
    }

    if (a.pending && !b.pending) return 1
    if (!a.pending && b.pending) return -1

    if (a.timestamp && b.timestamp) {
      return new Date(a.timestamp) - new Date(b.timestamp)
    }

    return 0
  })
})

// Methods
const formatDateTime = (dateString) => {
  if (!dateString) return 'Không có thông tin'

  const date = new Date(dateString)
  return date.toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const formatCurrency = (amount) => {
  if (amount === null || amount === undefined) return '0 ₫'
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND'
  }).format(amount)
}

const getOrderStatusInfo = (status) => {
  return orderStore.getOrderStatusInfo(status)
}

const getOrderTypeInfo = (type) => {
  return orderStore.getOrderTypeInfo(type)
}

const getPaymentMethodLabel = (method) => {
  const methodMap = {
    'TIEN_MAT': 'Tiền mặt',
    'VNPAY': 'VNPay',
    'MOMO': 'MoMo'
  }
  return methodMap[method] || method || 'Không xác định'
}

const formatDetailedAddress = (addressObj) => {
  if (!addressObj) return 'Không có địa chỉ'

  const parts = []
  if (addressObj.duong) parts.push(addressObj.duong)
  if (addressObj.phuongXa) parts.push(addressObj.phuongXa)
  if (addressObj.quanHuyen) parts.push(addressObj.quanHuyen)
  if (addressObj.tinhThanh) parts.push(addressObj.tinhThanh)
  if (addressObj.quocGia) parts.push(addressObj.quocGia)

  return parts.join(', ') || 'Không có địa chỉ'
}

const getProductImage = (orderItem) => {
  let imageFilename = null

  if (orderItem.hinhAnhSnapshot) {
    imageFilename = orderItem.hinhAnhSnapshot
  } else if (orderItem.sanPhamChiTiet?.hinhAnh) {
    if (Array.isArray(orderItem.sanPhamChiTiet.hinhAnh) && orderItem.sanPhamChiTiet.hinhAnh.length > 0) {
      imageFilename = orderItem.sanPhamChiTiet.hinhAnh[0]
    } else if (typeof orderItem.sanPhamChiTiet.hinhAnh === 'string') {
      imageFilename = orderItem.sanPhamChiTiet.hinhAnh
    }
  } else if (orderItem.sanPhamChiTiet?.sanPham?.hinhAnh) {
    if (Array.isArray(orderItem.sanPhamChiTiet.sanPham.hinhAnh) && orderItem.sanPhamChiTiet.sanPham.hinhAnh.length > 0) {
      imageFilename = orderItem.sanPhamChiTiet.sanPham.hinhAnh[0]
    } else if (typeof orderItem.sanPhamChiTiet.sanPham.hinhAnh === 'string') {
      imageFilename = orderItem.sanPhamChiTiet.sanPham.hinhAnh
    }
  }

  if (!imageFilename) return null

  if (imageFilename.startsWith('http')) return imageFilename

  if (imageUrlCache.value.has(imageFilename)) {
    return imageUrlCache.value.get(imageFilename)
  }

  loadProductImageUrl(imageFilename)

  return null
}

const loadProductImageUrl = async (imageFilename) => {
  try {
    const presignedUrl = await storageApi.getPresignedUrl('products', imageFilename)
    imageUrlCache.value.set(imageFilename, presignedUrl)
    imageUrlCache.value = new Map(imageUrlCache.value)
  } catch (error) {
    console.warn('Error getting presigned URL for product image:', imageFilename, error)
    imageUrlCache.value.set(imageFilename, null)
  }
}

const handleImageError = (event) => {
  event.target.style.display = 'none'
  const placeholder = event.target.nextElementSibling
  if (placeholder) {
    placeholder.style.display = 'flex'
  }
}

const loadOrderSerialNumbers = async () => {
  if (!order.value?.id) return

  try {
    const serialNumbers = await serialNumberApi.getSerialNumbersByOrder(order.value.id.toString())
    orderSerialNumbers.value = serialNumbers || []
  } catch (error) {
    console.warn('Error loading serial numbers for order:', error)
    orderSerialNumbers.value = []
  }
}

const getSerialNumbers = (orderItem) => {
  const variantId = orderItem.sanPhamChiTietId || orderItem.sanPhamChiTiet?.id

  if (!variantId || !orderSerialNumbers.value.length) {
    return []
  }

  const variantSerialNumbers = orderSerialNumbers.value.filter(sn =>
    sn.sanPhamChiTietId === variantId
  )

  if (!variantSerialNumbers.length) {
    return []
  }

  const availableSerialNumbers = variantSerialNumbers
    .map(sn => sn.serialNumberValue)
    .filter(sn => sn && sn.trim())

  const distributedSerialNumbers = []
  let serialNumberIndex = 0

  const variantOrderItems = order.value.chiTiet.filter(item =>
    (item.sanPhamChiTietId || item.sanPhamChiTiet?.id) === variantId
  )

  const currentItemIndex = variantOrderItems.findIndex(item => item === orderItem)

  if (currentItemIndex === -1) {
    return []
  }

  for (let i = 0; i < variantOrderItems.length; i++) {
    const item = variantOrderItems[i]
    const itemQuantity = item.soLuong || 1

    if (i === currentItemIndex) {
      for (let j = 0; j < itemQuantity && serialNumberIndex < availableSerialNumbers.length; j++) {
        distributedSerialNumbers.push(availableSerialNumbers[serialNumberIndex])
        serialNumberIndex++
      }
      break
    } else {
      serialNumberIndex += itemQuantity
    }
  }

  return distributedSerialNumbers
}

const getPaymentMethod = () => {
  if (!order.value) return null
  if (order.value.phuongThucThanhToan) {
    return order.value.phuongThucThanhToan
  }
  console.warn('Order', order.value.id, 'missing stored payment method, using inference logic for backward compatibility')
  if (order.value.trangThaiThanhToan === 'DA_THANH_TOAN') {
    if (order.value.loaiHoaDon === 'TAI_QUAY' && order.value.trangThaiDonHang === 'HOAN_THANH') {
      return 'TIEN_MAT'
    }
    if (order.value.trangThaiDonHang === 'DA_GIAO_HANG') {
      return 'COD'
    }
    return 'VNPAY'
  }
  return null
}

const getPaidAmount = () => {
  if (!order.value) return 0
  if (order.value.trangThaiThanhToan === 'DA_THANH_TOAN') {
    return order.value.tongThanhToan || 0
  }
  return order.value.soTienDaThanhToan || 0
}

const getStatusDescription = (status) => {
  const descriptionMap = {
    'CHO_XAC_NHAN': 'Đơn hàng đang chờ xác nhận từ nhân viên. Vui lòng kiểm tra thông tin và xác nhận.',
    'DA_XAC_NHAN': 'Đơn hàng đã được xác nhận và sẵn sàng xử lý. Bắt đầu chuẩn bị hàng hóa.',
    'DANG_XU_LY': 'Đơn hàng đang được chuẩn bị và đóng gói sản phẩm. Nhân viên đang kiểm tra chất lượng và chuẩn bị hàng hóa.',
    'CHO_GIAO_HANG': 'Đơn hàng đã được đóng gói hoàn tất và sẵn sàng giao hàng. Chờ đơn vị vận chuyển nhận hàng.',
    'DANG_GIAO_HANG': 'Đơn hàng đang được vận chuyển đến địa chỉ khách hàng. Theo dõi tiến trình giao hàng.',
    'DA_GIAO_HANG': 'Đơn hàng đã được giao thành công đến khách hàng.',
    'HOAN_THANH': 'Đơn hàng đã hoàn thành toàn bộ quy trình. Khách hàng đã nhận hàng và hài lòng.',
    'DA_HUY': 'Đơn hàng đã bị hủy và không thể khôi phục.',
    'YEU_CAU_TRA_HANG': 'Khách hàng yêu cầu trả hàng. Đang xem xét và xử lý yêu cầu.',
    'DA_TRA_HANG': 'Đã xử lý trả hàng thành công. Hàng hóa đã được nhận lại và kiểm tra.'
  }
  return descriptionMap[status] || 'Không có mô tả'
}

const getTimelineMarkerClass = (status) => {
  const classMap = {
    'CHO_XAC_NHAN': 'bg-yellow-100 border-yellow-300 text-yellow-600',
    'DA_XAC_NHAN': 'bg-blue-100 border-blue-300 text-blue-600',
    'DANG_XU_LY': 'bg-orange-100 border-orange-300 text-orange-600',
    'CHO_GIAO_HANG': 'bg-purple-100 border-purple-300 text-purple-600',
    'DANG_GIAO_HANG': 'bg-indigo-100 border-indigo-300 text-indigo-600',
    'DA_GIAO_HANG': 'bg-teal-100 border-teal-300 text-teal-600',
    'HOAN_THANH': 'bg-green-100 border-green-300 text-green-600',
    'DA_HUY': 'bg-red-100 border-red-300 text-red-600',
    'YEU_CAU_TRA_HANG': 'bg-amber-100 border-amber-300 text-amber-600',
    'DA_TRA_HANG': 'bg-amber-100 border-amber-300 text-amber-600'
  }
  return classMap[status] || 'bg-surface-100 border-surface-300 text-surface-600'
}

const loadOrder = async () => {
  loading.value = true
  error.value = null

  try {
    const orderId = route.params.id
    if (!orderId) {
      throw new Error('ID đơn hàng không hợp lệ')
    }

    order.value = await orderStore.fetchOrderById(orderId)

    if (!order.value) {
      throw new Error('Không tìm thấy đơn hàng')
    }

    await loadOrderSerialNumbers()

  } catch (err) {
    error.value = err.message || 'Lỗi tải dữ liệu đơn hàng'
    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: error.value,
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const refreshData = async () => {
  await loadOrder()
  toast.add({
    severity: 'success',
    summary: 'Thành công',
    detail: 'Đã làm mới dữ liệu',
    life: 2000
  })
}

const goBack = () => {
  router.back()
}

const printOrder = async () => {
  if (!order.value) {
    toast.add({
      severity: 'warn',
      summary: 'Cảnh báo',
      detail: 'Không có thông tin đơn hàng để in',
      life: 3000
    })
    return
  }

  try {
    const response = await orderApi.printOrderReceipt(order.value.id)

    if (response.success) {
      const blob = new Blob([response.data], { type: 'application/pdf' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `hoa-don-${order.value.maHoaDon || order.value.id}.pdf`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)

      toast.add({
        severity: 'success',
        summary: 'Thành công',
        detail: 'Hóa đơn đã được tải xuống',
        life: 3000
      })
    } else {
      throw new Error(response.message || 'Không thể tạo hóa đơn')
    }
  } catch (err) {
    console.error('Error printing receipt:', err)

    let errorMessage = 'Không thể in hóa đơn'
    if (err.response?.status === 403) {
      errorMessage = 'Bạn không có quyền in hóa đơn. Vui lòng liên hệ quản trị viên.'
    } else if (err.response?.status === 401) {
      errorMessage = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    } else if (err.message) {
      errorMessage = err.message
    }

    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: errorMessage,
      life: 5000
    })
  }
}

// Lifecycle
onMounted(async () => {
  await loadOrder()
})
</script>

<style scoped>
/* Card styling improvements */
.card {
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: box-shadow 0.2s ease;
}

.card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
}

/* Equal height cards */
.grid.items-start>.card.h-full {
  min-height: 300px;
  /* Minimum height for order info cards */
}

/* Tab styling */
.order-detail-tabs :deep(.p-tablist) {
  border-bottom: 1px solid var(--surface-border);
}

.order-detail-tabs :deep(.p-tab) {
  padding: 1rem 1.5rem;
  font-weight: 500;
}

.order-detail-tabs :deep(.p-tab:hover) {
  background-color: var(--surface-hover);
}

.order-detail-tabs :deep(.p-tab[aria-selected="true"]) {
  color: var(--primary-color);
  border-bottom: 2px solid var(--primary-color);
}

/* Badge styling */
:deep(.p-badge) {
  font-size: 0.75rem;
  padding: 0.25rem 0.5rem;
}
</style>
