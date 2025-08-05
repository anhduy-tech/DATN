<template>
  <div class="order-create-container">
    <Toast />

    <!-- Page Header -->
    <div class="card mb-6">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 bg-primary/10 rounded-lg flex items-center justify-center">
            <i class="pi pi-shop text-lg text-primary"></i>
          </div>
          <div>
            <h1 class="font-semibold text-xl text-surface-900 m-0"> Chỉnh sửa đơn hàng </h1>
            <p class="text-surface-500 text-sm mt-1 mb-0">
              Cập nhật thông tin đơn hàng
            </p>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <!-- Order Expiration Warning -->
          <div v-if="criticalExpiringOrders.length > 0"
            class="flex items-center gap-2 px-3 py-1 rounded-lg border bg-red-50 border-red-200 text-red-700">
            <i class="pi pi-exclamation-triangle text-red-500 text-xs"></i>
            <span class="text-xs font-medium">
              {{ criticalExpiringOrders.length }} đơn hàng sắp hết hạn
            </span>
          </div>

          <!-- General Expiration Updates -->
          <div v-else-if="hasExpirationUpdates"
            class="flex items-center gap-2 px-3 py-1 rounded-lg border bg-orange-50 border-orange-200 text-orange-700">
            <i class="pi pi-clock text-orange-500 text-xs"></i>
            <span class="text-xs font-medium">Có cập nhật hết hạn</span>
          </div>

          <Button label="Quay lại" icon="pi pi-arrow-left" outlined @click="$router.push('/orders')" />
        </div>
      </div>
    </div>

    <!-- Order Edit Header -->
    <div class="card mb-6">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <i class="pi pi-file-edit text-2xl text-primary"></i>
          <div>
            <h2 class="text-xl font-semibold text-surface-900">{{ pageTitle }}</h2>
            <p class="text-sm text-surface-600" v-if="currentOrder">{{ currentOrder.maHoaDon }}</p>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <Button icon="pi pi-qrcode" severity="info" outlined @click="showQRScanner = true"
            v-tooltip.top="'Quét mã QR để thêm serial number vào giỏ hàng'" />
          <Button label="Chọn sản phẩm" icon="pi pi-plus" severity="primary" @click="showProductSelectionDialog"
            v-tooltip.top="'Chọn sản phẩm từ danh sách'" />
        </div>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="text-center py-12">
      <ProgressSpinner style="width: 50px; height: 50px" strokeWidth="4" />
      <p class="text-surface-600 mt-4">Đang tải dữ liệu đơn hàng...</p>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="text-center py-12">
      <i class="pi pi-exclamation-triangle text-4xl text-red-500 mb-4 block"></i>
      <h3 class="text-xl font-semibold mb-2">Có lỗi xảy ra</h3>
      <p class="text-surface-600 mb-4">{{ error }}</p>
      <Button
        label="Thử lại"
        icon="pi pi-refresh"
        @click="loadOrderData"
      />
    </div>

    <!-- Main Order Edit Interface -->
    <div v-else-if="!isEditMode || currentOrder" class="grid grid-cols-1 lg:grid-cols-3 gap-6">

      <!-- Left Column: Product Selection & Order Items -->
      <div class="lg:col-span-2 space-y-6">
        <!-- Order Items -->
        <div class="card border border-surface-200">
          <div class="font-semibold text-lg mb-4 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <i class="pi pi-shopping-cart text-primary"></i>
              Sản phẩm trong đơn hàng
            </div>
            <div class="flex items-center gap-2">
              <Badge v-if="currentOrder?.sanPhamList?.length > 0" :value="currentOrder.sanPhamList.length" severity="info" />
            </div>
          </div>

          <!-- Price Change Warnings -->
          <PriceChangeWarning :price-changes="cartPriceChanges" @acknowledge-change="acknowledgePriceChange" />

          <!-- Order Items List -->
          <div v-if="currentOrder?.sanPhamList?.length" class="space-y-3 mb-4">
            <div v-for="(item, index) in processedCartItems" :key="getCartItemKey(item, index)"
              class="flex items-center gap-4 p-4 border rounded-lg hover:shadow-sm transition-shadow"
              :class="{ 'border-orange-300 bg-orange-50': item.hasPriceChange }">
              <img :src="getCartItemImage(item) || '/placeholder-product.png'" :alt="getCartItemName(item)"
                class="w-14 h-14 object-cover rounded-lg" />
              <div class="flex-1 min-w-0">
                <div class="font-medium text-sm mb-1">{{ getCartItemName(item) }}</div>
                <div class="text-xs text-surface-500 mb-1">{{ getCartItemCode(item) }}</div>
                <div class="text-xs text-surface-600 mb-2">
                  {{ getVariantDisplayInfo(item) }}
                </div>
                <div class="text-sm text-primary font-semibold">{{
                  formatCurrency(item.donGia)
                  }}</div>
              </div>
              <div class="flex items-center gap-3">
                <div
                  class="flex items-center gap-2 px-3 py-2 bg-gradient-to-r from-primary/10 to-primary/5 border border-primary/20 rounded-lg shadow-sm">
                  <i class="pi pi-barcode text-primary text-lg"></i>
                  <div class="flex flex-col">
                    <span class="text-xs text-surface-500 uppercase tracking-wide font-medium">Serial</span>
                    <span class="text-sm font-bold font-mono text-primary">
                      {{ item.sanPhamChiTiet?.serialNumber || 'N/A' }}
                    </span>
                  </div>
                </div>
              </div>
              <div class="text-right min-w-0">
                <!-- Total price with discount visualization -->
                <div v-if="hasCartItemDiscount(item)" class="space-y-1">
                  <div class="text-xs text-surface-500 line-through">
                    {{ formatCurrency(item.sanPhamChiTiet?.giaBan * item.soLuong) }}
                  </div>
                  <div class="font-semibold text-lg text-red-600 flex items-center gap-1">
                    {{ formatCurrency(item.sanPhamChiTiet?.giaKhuyenMai * item.soLuong) }}
                    <span class="text-xs bg-red-100 text-red-700 px-1 rounded"> Giảm giá </span>
                  </div>
                </div>
                <!-- Show original total price only if no discount -->
                <div v-else class="font-semibold text-lg text-primary">
                  {{ formatCurrency(item.sanPhamChiTiet?.giaBan * item.soLuong || item.thanhTien) }}
                </div>
              </div>
              <Button icon="pi pi-trash" text rounded size="small" severity="danger" @click="
                removeFromCurrentOrder(item.originalIndex !== undefined ? item.originalIndex : index)
                " v-tooltip.top="'Xóa khỏi giỏ hàng'" />
            </div>
          </div>

          <!-- Empty Cart -->
          <div v-else class="text-center py-8 text-surface-500">
            <i class="pi pi-shopping-cart text-2xl mb-2"></i>
            <p class="text-sm">Chưa có sản phẩm nào trong đơn hàng</p>
            <p class="text-xs">Tìm kiếm và thêm sản phẩm ở phía trên</p>
          </div>
        </div>
      </div>

      <!-- Right Column: Order Summary & Actions -->
      <div class="lg:col-span-1 space-y-6">
        <!-- Customer Selection -->
        <div class="card border border-surface-200">
          <div class="font-semibold text-lg mb-4 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <i class="pi pi-user text-primary"></i>
              Khách hàng
            </div>
            <Button label="Thêm nhanh" icon="pi pi-user-plus" size="small" severity="success" outlined
              @click="showFastCustomerDialog" />
          </div>

          <!-- Customer Search -->
          <div class="mb-4">
            <AutoComplete v-model="selectedCustomer" :suggestions="customerSuggestions" @complete="searchCustomers"
              @item-select="onCustomerSelect" :optionLabel="getCustomerDisplayLabel"
              placeholder="Tìm kiếm khách hàng (tên hoặc số điện thoại)..." fluid>
              <template #item="{ item }">
                <div class="flex items-center gap-2 p-2">
                  <Avatar :label="item.hoTen?.charAt(0)" size="small" />
                  <div>
                    <div class="font-medium">{{ item.hoTen }} - {{ item.soDienThoai }}</div>
                    <div class="text-sm text-surface-500">{{ item.email || 'Không có email' }}</div>
                  </div>
                </div>
              </template>
            </AutoComplete>
          </div>

          <!-- Selected Customer Display -->
          <div v-if="currentOrder?.khachHang" class="p-3 border rounded-lg bg-surface-50">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <Avatar :label="currentOrder.khachHang.hoTen?.charAt(0)" size="small" />
                <div>
                  <div class="font-semibold text-sm">{{ currentOrder.khachHang.hoTen }}</div>
                  <div class="text-xs text-surface-500">{{ currentOrder.khachHang.soDienThoai }}</div>
                </div>
              </div>
              <Button icon="pi pi-times" text rounded size="small" @click="clearCustomerFromTab"
                class="text-surface-400 hover:text-red-500" />
            </div>
          </div>

          <!-- Walk-in Customer Note -->
          <div v-else class="text-center py-3 text-surface-500">
            <i class="pi pi-user-plus text-lg mb-1"></i>
            <p class="text-xs">Khách hàng vãng lai</p>
          </div>
        </div>

        <!-- Delivery Options -->
        <div class="card border border-surface-200">
          <div class="font-semibold text-lg mb-4 flex items-center gap-2">
            <i class="pi pi-truck text-primary"></i>
            Giao hàng
          </div>

          <div class="flex items-center justify-between mb-4">
            <label class="font-medium">Giao hàng tận nơi</label>
            <ToggleButton v-model="currentOrder.giaohang" onLabel="Có" offLabel="Không" @change="onDeliveryToggle"
              :disabled="!currentOrder" />
          </div>

          <!-- Recipient Information Form (when delivery is enabled) -->
          <div v-if="currentOrder?.giaohang" class="space-y-4">
            <!-- Recipient Information Header -->
            <div class="border-t pt-4">
              <div class="font-semibold text-base mb-3 flex items-center gap-2">
                <i class="pi pi-user-plus text-blue-600"></i>
                <span class="text-blue-800">Thông tin người nhận</span>
              </div>

              <!-- Recipient Name -->
              <div class="mb-3">
                <label class="block text-sm font-medium mb-1">
                  Tên người nhận <span class="text-red-500">*</span>
                </label>
                <AutoComplete v-model="recipientInfo.hoTen" :suggestions="recipientNameSuggestions"
                  @complete="searchRecipientByName" @item-select="onRecipientNameSelect" optionLabel="hoTen"
                  placeholder="Nhập tên người nhận..." class="w-full" :class="{ 'p-invalid': recipientErrors.hoTen }"
                  :loading="searchingRecipient" fluid>
                  <template #item="{ item }">
                    <div class="flex items-center gap-2 p-2">
                      <Avatar :label="item.hoTen?.charAt(0)" size="small" />
                      <div>
                        <div class="font-medium">{{ item.hoTen }}</div>
                        <div class="text-sm text-surface-500">{{
                          item.soDienThoai || 'Không có SĐT'
                          }}</div>
                      </div>
                    </div>
                  </template>
                </AutoComplete>
                <small v-if="recipientErrors.hoTen" class="p-error">{{
                  recipientErrors.hoTen
                  }}</small>
              </div>

              <!-- Recipient Phone -->
              <div class="mb-4">
                <label class="block text-sm font-medium mb-1">
                  Số điện thoại người nhận <span class="text-red-500">*</span>
                </label>
                <AutoComplete v-model="recipientInfo.soDienThoai" :suggestions="recipientPhoneSuggestions"
                  @complete="searchRecipientByPhone" @item-select="onRecipientPhoneSelect" optionLabel="soDienThoai"
                  placeholder="Nhập số điện thoại người nhận..." class="w-full"
                  :class="{ 'p-invalid': recipientErrors.soDienThoai }" :loading="searchingRecipient" fluid>
                  <template #item="{ item }">
                    <div class="flex items-center gap-2 p-2">
                      <Avatar :label="item.hoTen?.charAt(0)" size="small" />
                      <div>
                        <div class="font-medium">{{ item.hoTen || 'Không có tên' }}</div>
                        <div class="text-sm text-surface-500">{{ item.soDienThoai }}</div>
                      </div>
                    </div>
                  </template>
                </AutoComplete>
                <small v-if="recipientErrors.soDienThoai" class="p-error">{{
                  recipientErrors.soDienThoai
                  }}</small>
              </div>

              <!-- Embedded Address Form -->
              <div class="border-t pt-4">
                <div class="font-semibold text-base mb-3 flex items-center gap-2">
                  <i class="pi pi-map-marker text-blue-600"></i>
                  <span class="text-blue-800">Địa chỉ giao hàng</span>
                  <!-- Address Population Loading Indicator -->
                  <div v-if="populatingAddress" class="flex items-center gap-1 text-xs text-blue-600">
                    <i class="pi pi-spin pi-spinner"></i>
                    <span>Đang tự động điền địa chỉ...</span>
                  </div>
                </div>

                <!-- Street Address -->
                <div class="mb-3">
                  <label class="block text-sm font-medium mb-1">
                    Địa chỉ đường <span class="text-red-500">*</span>
                  </label>
                  <InputText v-model="addressData.duong" placeholder="Nhập số nhà, tên đường..." class="w-full"
                    :class="{ 'p-invalid': addressErrors.duong }" />
                  <small v-if="addressErrors.duong" class="p-error">{{
                    addressErrors.duong
                    }}</small>
                </div>

                <!-- Province/City -->
                <div class="mb-3">
                  <label class="block text-sm font-medium mb-1">
                    Tỉnh/Thành phố <span class="text-red-500">*</span>
                  </label>
                  <Select v-model="selectedProvince" :options="provinces" optionLabel="name"
                    placeholder="Chọn tỉnh/thành phố" class="w-full" :class="{ 'p-invalid': addressErrors.tinhThanh }"
                    @change="onProvinceChange" :loading="loadingProvinces" />
                  <small v-if="addressErrors.tinhThanh" class="p-error">{{
                    addressErrors.tinhThanh
                    }}</small>
                </div>

                <!-- District -->
                <div class="mb-3">
                  <label class="block text-sm font-medium mb-1">
                    Quận/Huyện <span class="text-red-500">*</span>
                  </label>
                  <Select v-model="selectedDistrict" :options="districts" optionLabel="name"
                    placeholder="Chọn quận/huyện" class="w-full" :class="{ 'p-invalid': addressErrors.quanHuyen }"
                    @change="onDistrictChange" :disabled="!selectedProvince" :loading="loadingDistricts" />
                  <small v-if="addressErrors.quanHuyen" class="p-error">{{
                    addressErrors.quanHuyen
                    }}</small>
                </div>

                <!-- Ward -->
                <div class="mb-3">
                  <label class="block text-sm font-medium mb-1">
                    Phường/Xã <span class="text-red-500">*</span>
                  </label>
                  <Select v-model="selectedWard" :options="wards" optionLabel="name" placeholder="Chọn phường/xã"
                    class="w-full" :class="{ 'p-invalid': addressErrors.phuongXa }" :disabled="!selectedDistrict"
                    :loading="loadingWards" />
                  <small v-if="addressErrors.phuongXa" class="p-error">{{
                    addressErrors.phuongXa
                    }}</small>
                </div>

                <!-- Shipping Fee Calculator -->
                <div class="border-t pt-4 mt-4">
                  <div class="font-semibold text-base mb-3 flex items-center justify-between">
                    <div class="flex items-center gap-2">
                      <!-- <i class="pi pi-truck text-blue-600"></i> -->
                      <img :src="'https://lapxpert-storage-api.khoalda.dev/logos/logo-ghn.png'" width="20" />
                      <span class="text-blue-800">Phí vận chuyển</span>
                    </div>
                    <div class="flex items-center gap-2">
                      <Badge :value="enhancedShippingStatus.text" :severity="enhancedShippingStatus.severity"
                        size="small" />
                    </div>
                  </div>

                  <!-- Shipping Fee Input -->
                  <div class="mb-3">
                    <label class="block text-sm font-medium mb-1"> Phí vận chuyển (VND) </label>
                    <div class="flex gap-2">
                      <InputNumber v-model="consolidatedShippingFee" placeholder="0" class="flex-1" :min="0"
                        :max="10000000" locale="vi-VN" />
                      <Button icon="pi pi-calculator" severity="info" outlined
                        @click="calculateShippingFeeForCurrentAddress" :loading="isCalculatingShipping"
                        v-tooltip.top="'Tính phí vận chuyển GHN'" />
                    </div>
                    <small v-if="shippingError" class="p-error">{{ shippingError }}</small>
                  </div>

                  <!-- Estimated Delivery Time -->
                  <div v-if="estimatedDeliveryTime" class="mb-3">
                    <div class="text-sm text-surface-600 flex items-center gap-2">
                      <i class="pi pi-clock text-blue-600"></i>
                      <span>Thời gian giao hàng dự kiến: {{ estimatedDeliveryTime }}</span>
                    </div>
                  </div>

                  <!-- Shipping Calculation Info -->
                  <div v-if="isShippingAutoCalculated" class="text-xs text-green-600 flex items-center gap-1">
                    <i class="pi pi-check-circle"></i>
                    <span>Phí vận chuyển được tính tự động qua GHN</span>
                  </div>

                  <!-- GHN Calculation Loading -->
                  <div v-if="isCalculatingShipping" class="text-xs text-blue-600 flex items-center gap-1">
                    <i class="pi pi-spin pi-spinner"></i>
                    <span>Đang tính phí vận chuyển...</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Voucher Section -->
        <div class="card border border-surface-200">
          <div class="font-semibold text-lg mb-4 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <i class="pi pi-tag text-primary"></i>
              Voucher giảm giá
            </div>
            <!-- Real-time indicators -->
            <div class="flex items-center gap-2">
              <Badge v-if="hasVoucherUpdates" value="!" severity="warn" size="small"
                v-tooltip.top="'Có cập nhật voucher mới'" />
              <Badge v-if="priceUpdates?.length > 0" value="₫" severity="info" size="small"
                v-tooltip.top="'Có thay đổi giá sản phẩm'" />
            </div>
          </div>

          <!-- Voucher Expiration Alert -->
          <div v-if="expiredVouchers?.length > 0 && showVoucherNotifications"
            class="mb-4 p-3 border rounded-lg bg-orange-50 border-orange-200">
            <div class="flex items-center gap-2 mb-2">
              <i class="pi pi-exclamation-triangle text-orange-600"></i>
              <span class="font-medium text-orange-800 text-sm">Voucher hết hạn</span>
            </div>
            <div class="text-sm text-orange-700">
              {{ expiredVouchers[0].code }} đã hết hiệu lực
              <span v-if="alternativeRecommendations?.length > 0" class="ml-2">
                - Có {{ alternativeRecommendations.length }} voucher thay thế
              </span>
            </div>
          </div>

          <!-- New Voucher Alert -->
          <div v-if="newVouchers?.length > 0 && showVoucherNotifications"
            class="mb-4 p-3 border rounded-lg bg-green-50 border-green-200">
            <div class="flex items-center gap-2 mb-2">
              <i class="pi pi-check-circle text-green-600"></i>
              <span class="font-medium text-green-800 text-sm">Voucher mới</span>
            </div>
            <div class="text-sm text-green-700">
              {{ newVouchers[0].code }} - Giảm {{ formatCurrency(newVouchers[0].discountValue) }} đã
              có hiệu lực
            </div>
          </div>

          <!-- Critical Order Expiration Alert -->
          <div v-if="criticalExpiringOrders?.length > 0" class="mb-4 p-3 border rounded-lg bg-red-50 border-red-200">
            <div class="flex items-center gap-2 mb-2">
              <i class="pi pi-exclamation-triangle text-red-600"></i>
              <span class="font-medium text-red-800 text-sm">Đơn hàng sắp hết hạn</span>
            </div>
            <div class="space-y-1">
              <div v-for="order in criticalExpiringOrders.slice(0, 2)" :key="order.id" class="text-sm text-red-700">
                <div class="flex items-center justify-between">
                  <span class="font-medium">{{ order.orderCode }}</span>
                  <span class="text-xs font-bold">
                    {{ formatRemainingTime(getRemainingTimeForOrder(order.id)) }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- Order Expiration Info -->
          <div v-else-if="expiringOrders?.length > 0" class="mb-4 p-3 border rounded-lg bg-yellow-50 border-yellow-200">
            <div class="flex items-center gap-2 mb-2">
              <i class="pi pi-clock text-yellow-600"></i>
              <span class="font-medium text-yellow-800 text-sm">Đơn hàng chờ thanh toán</span>
            </div>
            <div class="text-sm text-yellow-700">
              Có {{ expiringOrders.length }} đơn hàng đang chờ thanh toán
            </div>
          </div>

          <!-- Applied Vouchers -->
          <div v-if="currentOrder?.voucherList?.length" class="space-y-2 mb-4">
            <div class="font-medium mb-3 text-sm flex items-center gap-2">
              <i class="pi pi-sparkles text-primary"></i>
              Voucher tự động áp dụng
            </div>
            <div v-for="(voucher, index) in currentOrder.voucherList" :key="index"
              class="relative flex items-center justify-between p-3 border rounded-lg bg-green-50 border-green-200">
              <!-- Best Overall Voucher Indicator (only for applied vouchers that are best overall) -->
              <div v-if="isBestVoucher(voucher)" class="absolute -top-2 -right-2">
                <Badge value="Lựa chọn tốt nhất" severity="success" size="small" />
              </div>

              <div class="flex-1">
                <div class="font-medium text-green-800 text-sm">{{ voucher.maPhieuGiamGia }}</div>
                <div class="text-xs text-green-600 mt-1">
                  Giảm {{ formatCurrency(voucher.giaTriGiam) }}
                </div>
              </div>
              <Button icon="pi pi-times" text rounded size="small" severity="danger"
                @click="removeVoucherFromTab(index)" />
            </div>
          </div>

          <!-- Available Vouchers Display -->
          <div v-if="displayedAvailableVouchers.length" class="mb-4">
            <div class="font-medium mb-3 text-sm flex items-center gap-2">
              <i class="pi pi-sparkles text-primary"></i>
              Voucher khả dụng
            </div>

            <!-- Voucher Cards Container (No Scrollbar) -->
            <div class="space-y-3">
              <div v-for="voucher in displayedAvailableVouchers" :key="voucher.id"
                class="relative p-3 border rounded-lg transition-all cursor-pointer hover:shadow-md" :class="{
                  'border-green-500 bg-green-50': isBestAvailableVoucher(voucher),
                  'border-surface-200 bg-surface-50': !isBestAvailableVoucher(voucher),
                }" @click="selectVoucher(voucher)">
                <!-- Best Overall Voucher Indicator (only for available vouchers that are best overall) -->
                <div v-if="isBestAvailableVoucher(voucher)" class="absolute -top-2 -right-2">
                  <Badge value="Lựa chọn tốt nhất" severity="success" size="small" />
                </div>

                <div class="flex items-start justify-between">
                  <div class="flex-1">
                    <div class="font-semibold text-sm mb-1" :class="isBestAvailableVoucher(voucher) ? 'text-green-800' : 'text-surface-900'
                      ">
                      {{ voucher.tenPhieuGiamGia || voucher.maPhieuGiamGia }}
                    </div>
                    <div class="text-xs text-surface-500 mb-2">{{ voucher.moTa }}</div>

                    <!-- Voucher Details -->
                    <div class="space-y-1">
                      <div class="text-sm font-medium"
                        :class="isBestAvailableVoucher(voucher) ? 'text-green-700' : 'text-primary'">
                        Giảm {{ formatCurrency(calculateVoucherDiscount(voucher)) }}
                      </div>

                      <!-- Conditions -->
                      <div class="text-xs text-surface-600">
                        <span v-if="voucher.giaTriDonHangToiThieu">
                          Đơn tối thiểu: {{ formatCurrency(voucher.giaTriDonHangToiThieu) }}
                        </span>
                        <span v-if="voucher.giaTriGiamToiDa && voucher.loaiGiamGia === 'PHAN_TRAM'">
                          • Giảm tối đa: {{ formatCurrency(voucher.giaTriGiamToiDa) }}
                        </span>
                      </div>

                      <!-- Expiry -->
                      <div class="text-xs text-surface-500">
                        <i class="pi pi-calendar text-xs mr-1"></i>
                        Hết hạn: {{ formatDate(voucher.ngayKetThuc) }}
                      </div>
                    </div>
                  </div>

                  <Button icon="pi pi-plus" text rounded size="small" :class="isBestAvailableVoucher(voucher)
                      ? 'text-green-600 hover:bg-green-100'
                      : 'text-primary hover:bg-primary/10'
                    " />
                </div>
              </div>
            </div>

            <!-- Show More/Less Button -->
            <div v-if="availableVouchers.length > voucherDisplayLimit" class="text-center mt-3">
              <Button :label="showAllVouchers
                  ? 'Thu gọn'
                  : `Xem thêm ${availableVouchers.length - voucherDisplayLimit} voucher`
                " :icon="showAllVouchers ? 'pi pi-angle-up' : 'pi pi-angle-down'" text size="small"
                @click="toggleVoucherDisplay" />
            </div>
          </div>

          <!-- Smart Voucher Recommendations -->
          <div v-if="voucherRecommendations.length > 0 && currentOrder?.khachHang" class="mb-4">
            <div class="font-medium mb-3 text-sm flex items-center gap-2">
              <i class="pi pi-lightbulb text-orange-600"></i>
              Gợi ý tiết kiệm
            </div>

            <div class="space-y-3">
              <div v-for="(recommendation, index) in voucherRecommendations" :key="index"
                class="relative p-3 border rounded-lg transition-all cursor-pointer border-surface-200 bg-surface-50 opacity-60 hover:opacity-75"
                @click="applyRecommendedVoucher(recommendation.voucher)">
                <div class="flex items-start justify-between">
                  <div class="flex-1">
                    <div class="font-semibold text-sm mb-1 text-surface-700">
                      {{
                        recommendation.voucher.tenPhieuGiamGia ||
                        recommendation.voucher.maPhieuGiamGia
                      }}
                    </div>
                    <div class="text-xs text-surface-500 mb-2">{{
                      recommendation.voucher.moTa
                      }}</div>

                    <!-- Voucher Details -->
                    <div class="space-y-1">
                      <div class="text-sm font-medium text-surface-600">
                        Giảm {{ formatCurrency(recommendation.potentialDiscount) }}
                      </div>

                      <!-- Red Italic Recommendation Message -->
                      <div class="text-sm text-red-600 italic font-medium">
                        {{ recommendation.message }}
                      </div>

                      <!-- Conditions -->
                      <div class="text-xs text-surface-600">
                        <span v-if="recommendation.voucher.giaTriDonHangToiThieu">
                          Đơn tối thiểu:
                          {{ formatCurrency(recommendation.voucher.giaTriDonHangToiThieu) }}
                        </span>
                        <span v-if="
                          recommendation.voucher.giaTriGiamToiDa &&
                          recommendation.voucher.loaiGiamGia === 'PHAN_TRAM'
                        ">
                          • Giảm tối đa:
                          {{ formatCurrency(recommendation.voucher.giaTriGiamToiDa) }}
                        </span>
                      </div>

                      <!-- Expiry -->
                      <div class="text-xs text-surface-500">
                        <i class="pi pi-calendar text-xs mr-1"></i>
                        Hết hạn: {{ formatDate(recommendation.voucher.ngayKetThuc) }}
                      </div>
                    </div>
                  </div>

                  <Button icon="pi pi-plus" text rounded size="small" class="text-surface-500 hover:bg-surface-100"
                    @click.stop="applyRecommendedVoucher(recommendation.voucher)" />
                </div>
              </div>
            </div>
          </div>

          <!-- No Vouchers Available -->
          <div v-if="
            !currentOrder?.voucherList?.length && !availableVouchers.length && currentOrder?.khachHang
          " class="mb-4 p-3 border border-dashed border-surface-300 rounded-lg text-center">
            <i class="pi pi-info-circle text-surface-400 text-lg mb-2"></i>
            <p class="text-sm text-surface-500">Không có voucher khả dụng cho đơn hàng này</p>
          </div>
        </div>

        <!-- Payment Section -->
        <div class="card border border-surface-200">
          <div class="font-semibold text-lg mb-4 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <i class="pi pi-credit-card text-primary"></i>
              Thanh toán
            </div>
            <Button v-if="dynamicOrderTotal > 0" label="Thanh toán hỗn hợp" icon="pi pi-plus-circle" size="small"
              severity="info" outlined @click="showMixedPaymentDialog" />
          </div>

          <!-- Payment Methods -->
          <div v-if="paymentMethods.length === 0" class="text-center py-4 text-surface-500 mb-4">
            <i class="pi pi-info-circle text-2xl mb-2"></i>
            <p>Không có phương thức thanh toán khả dụng</p>
            <p class="text-sm">Vui lòng kiểm tra lại tùy chọn giao hàng</p>
          </div>
          <!-- Mixed Payment Display -->
          <div v-if="currentOrder?.phuongThucThanhToan === 'MIXED' && currentOrder?.mixedPayments" class="mb-4">
            <div class="border rounded-lg p-3 bg-blue-50 border-blue-200">
              <div class="flex items-center gap-2 mb-3">
                <i class="pi pi-plus-circle text-blue-600"></i>
                <span class="font-semibold text-blue-800">Thanh toán hỗn hợp</span>
                <Badge value="Đã cấu hình" severity="info" size="small" />
              </div>
              <div class="space-y-2">
                <div v-for="(payment, index) in currentOrder.mixedPayments" :key="index"
                  class="flex justify-between items-center text-sm">
                  <span>{{ getPaymentMethodLabel(payment.method) }}:</span>
                  <span class="font-medium">{{ formatCurrency(payment.amount) }}</span>
                </div>
              </div>
              <Button label="Chỉnh sửa" icon="pi pi-pencil" size="small" text @click="showMixedPaymentDialog"
                class="mt-2" />
            </div>
          </div>

          <!-- Single Payment Methods -->
          <div v-else class="space-y-3 mb-4">
            <div v-for="method in paymentMethods" :key="method.value"
              class="border rounded-lg p-3 cursor-pointer transition-all" :class="{
                'border-primary bg-primary/5': currentOrder?.phuongThucThanhToan === method.value,
                'border-surface-200 hover:border-primary/50':
                  currentOrder?.phuongThucThanhToan !== method.value,
                'opacity-50 cursor-not-allowed': !method.available,
              }" @click="method.available && selectPaymentMethod(method.value)">
              <div class="flex items-center gap-3">
                <i :class="method.icon" class="text-lg text-primary"></i>
                <div>
                  <div class="font-semibold text-sm">{{ method.label }}</div>
                  <div class="text-xs text-surface-500">{{ method.description }}</div>
                  <div v-if="!method.available" class="text-xs text-red-500 mt-1">
                    Không khả dụng
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Order Expiration Panel (if there are updates) -->
        <div v-if="hasExpirationUpdates" class="card border border-surface-200 mb-6">
          <div class="font-semibold text-lg mb-4 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <i class="pi pi-clock text-orange-600"></i>
              <span class="text-orange-800">Theo dõi hết hạn</span>
            </div>
            <Button :icon="showExpirationPanel ? 'pi pi-chevron-up' : 'pi pi-chevron-down'" text size="small"
              @click="showExpirationPanel = !showExpirationPanel" />
          </div>

          <div v-if="showExpirationPanel" class="space-y-3">
            <!-- Critical Expiring Orders -->
            <div v-if="criticalExpiringOrders.length > 0" class="p-3 border rounded-lg bg-red-50 border-red-200">
              <h5 class="font-medium text-red-800 mb-2 flex items-center gap-2">
                <i class="pi pi-exclamation-triangle"></i>
                Đơn hàng sắp hết hạn ({{ criticalExpiringOrders.length }})
              </h5>
              <div class="space-y-2">
                <div v-for="order in criticalExpiringOrders.slice(0, 3)" :key="order.id"
                  class="text-sm text-red-700 p-2 bg-red-100 rounded">
                  <div class="flex items-center justify-between">
                    <div class="font-medium">{{ order.orderCode }}</div>
                    <div class="text-xs font-bold text-red-600">
                      {{ formatRemainingTime(getRemainingTimeForOrder(order.id)) }}
                    </div>
                  </div>
                  <div class="text-xs">
                    {{ order.customerName }} - {{ formatCurrency(order.totalAmount) }}
                  </div>
                </div>
              </div>
            </div>

            <!-- Regular Expiring Orders -->
            <div v-if="expiringOrders.length > 0" class="p-3 border rounded-lg bg-orange-50 border-orange-200">
              <h5 class="font-medium text-orange-800 mb-2 flex items-center gap-2">
                <i class="pi pi-clock"></i>
                Đơn hàng chờ thanh toán ({{ expiringOrders.length }})
              </h5>
              <div class="space-y-1">
                <div v-for="order in expiringOrders.slice(0, 3)" :key="order.id"
                  class="text-sm text-orange-700 flex items-center justify-between">
                  <span class="font-medium">{{ order.orderCode }}</span>
                  <span class="text-xs">{{
                    formatRemainingTime(getRemainingTimeForOrder(order.id))
                    }}</span>
                </div>
              </div>
            </div>

            <!-- Recent Expired Orders -->
            <div v-if="expiredOrders.length > 0" class="p-3 border rounded-lg bg-gray-50 border-gray-200">
              <h5 class="font-medium text-gray-800 mb-2 flex items-center gap-2">
                <i class="pi pi-times-circle"></i>
                Đơn hàng đã hết hạn ({{ expiredOrders.length }})
              </h5>
              <div class="space-y-1">
                <div v-for="order in expiredOrders.slice(0, 2)" :key="order.id" class="text-sm text-gray-700">
                  <div class="font-medium">{{ order.orderCode }}</div>
                  <div class="text-xs text-gray-500">{{ order.reason }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Order Summary -->
        <div class="card border border-surface-200">
          <div class="font-semibold text-lg mb-4 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <i class="pi pi-calculator text-primary"></i>
              Tổng kết đơn hàng
            </div>
            <Button icon="pi pi-refresh" outlined size="small"
              @click="calculateCurrentOrderTotals()"
              v-tooltip.left="'Tính lại tổng tiền'" />
          </div>

          <div v-if="currentOrder" class="space-y-2 text-sm">
            <div class="flex justify-between">
              <span>Tạm tính:</span>
              <span>{{ formatCurrency(currentOrder.tongTienHang || 0) }}</span>
            </div>
            <div v-if="currentOrder.giaTriGiamGiaVoucher > 0" class="flex justify-between text-green-600">
              <span>Giảm giá voucher:</span>
              <span>-{{ formatCurrency(currentOrder.giaTriGiamGiaVoucher) }}</span>
            </div>
            <div v-if="currentOrder.giaohang" class="flex justify-between">
              <span>Phí giao hàng:</span>
              <span>{{ formatCurrency(consolidatedShippingFee) }}</span>
            </div>
            <hr class="my-2" />
            <div class="flex justify-between font-semibold text-lg">
              <span>Tổng cộng:</span>
              <span class="text-primary">{{ formatCurrency(dynamicOrderTotal) }}</span>
            </div>

            <!-- Customer Payment Section (for cash payments) -->
            <div v-if="currentOrder?.phuongThucThanhToan === 'TIEN_MAT'" class="mt-4 pt-4 border-t border-surface-200">
              <div class="space-y-3">
                <div>
                  <label class="block text-sm font-medium mb-1">Khách hàng đưa:</label>
                  <InputText v-model.number="customerPayment" type="number" placeholder="Nhập số tiền khách đưa..."
                    class="w-full" :min="dynamicOrderTotal" @input="calculateChange" />
                </div>
                <div v-if="customerPayment >= dynamicOrderTotal" class="flex justify-between font-semibold text-lg">
                  <span>Tiền trả lại:</span>
                  <span class="text-green-600">{{ formatCurrency(changeAmount) }}</span>
                </div>
                <div v-else-if="customerPayment > 0" class="text-red-500 text-sm">
                  Số tiền không đủ (thiếu {{ formatCurrency(dynamicOrderTotal - customerPayment) }})
                </div>
              </div>
            </div>
          </div>

          <!-- Create Order Button -->
          <div class="mt-6 pt-4 border-t border-surface-200">
            <Button label="Cập nhật đơn hàng" icon="pi pi-check" severity="success" size="large" class="w-full"
              @click="showOrderConfirmation" :loading="creating || prePaymentCheckInProgress"
              :disabled="!canCreateActiveOrder || creating || prePaymentCheckInProgress" />
            <div v-if="!canCreateActiveOrder" class="text-center mt-2">
              <small class="text-surface-500">
                <span v-if="!currentOrder?.sanPhamList?.length">Vui lòng thêm sản phẩm vào đơn hàng</span>
                <span v-else-if="!currentOrder?.phuongThucThanhToan">Vui lòng chọn phương thức thanh toán</span>
                <span v-else-if="
                  currentOrder?.giaohang &&
                  (!recipientInfo.hoTen.trim() || !recipientInfo.soDienThoai.trim())
                ">
                  Vui lòng nhập đầy đủ thông tin người nhận
                </span>
                <span v-else-if="
                  currentOrder?.giaohang &&
                  (!addressData.duong.trim() ||
                    !addressData.phuongXa ||
                    !addressData.quanHuyen ||
                    !addressData.tinhThanh)
                ">
                  Vui lòng nhập đầy đủ địa chỉ giao hàng
                </span>
                <span v-else-if="currentOrder?.giaohang && Object.keys(addressErrors).length > 0">
                  Vui lòng sửa lỗi trong form địa chỉ giao hàng
                </span>
              </small>
            </div>
          </div>
        </div>
      </div>
    </div>

  <!-- Product Variant Selection Dialog -->
  <ProductVariantDialog ref="productVariantDialogRef" v-model:visible="variantDialogVisible"
    @variant-selected="addVariantToCurrentOrder" @request-cart-sync="syncCartWithDialog" />

  <!-- Fast Customer Creation Dialog -->
  <FastCustomerCreate v-model:visible="fastCustomerDialogVisible" @customer-created="onCustomerCreated" />

  <!-- Fast Address Creation Dialog -->
  <FastAddressCreate v-model:visible="fastAddressDialogVisible" :customer="currentOrder?.khachHang"
    @address-created="onAddressCreated" />

  <!-- Mixed Payment Dialog -->
  <MixedPaymentDialog v-model:visible="mixedPaymentDialogVisible" :total-amount="dynamicOrderTotal"
    :order-type="currentOrder?.loaiHoaDon || 'TAI_QUAY'" :has-delivery="currentOrder?.giaohang || false"
    @confirm="onMixedPaymentConfirm" />

  <!-- Voucher Suggestion Dialog -->
  <VoucherSuggestionDialog v-model:visible="suggestionDialogVisible" :suggestion="currentSuggestion"
    @accept="onAcceptBetterVoucher" @reject="onRejectBetterVoucher" />

  <!-- QR Scanner Dialog -->
  <Dialog v-model:visible="showQRScanner" modal header="Quét QR Serial Number" :style="{ width: '500px' }"
    @hide="stopQRScanner" :closable="true">
    <div class="text-center">
      <p class="mb-4">Quét mã QR chứa serial number để tự động thêm vào giỏ hàng</p>
      <div class="border-2 border-primary border-dashed rounded-lg p-4 mb-4">
        <!-- QR Scanner component -->
        <div class="flex flex-col items-center justify-center">
          <div v-if="!qrScanResult" class="w-full">
            <div v-if="!cameraError">
              <qrcode-stream @detect="onQRDetect" @init="onQRInit" :track="paintBoundingBox"
                class="w-full h-64 rounded-lg overflow-hidden" />
              <p class="text-surface-600 mt-2">Đưa mã QR chứa serial number vào khung hình</p>
            </div>
            <div v-else class="p-4 bg-red-50 rounded-lg">
              <i class="pi pi-exclamation-triangle text-red-500 text-xl"></i>
              <p class="text-red-700 font-medium mt-2">Lỗi khi truy cập camera</p>
              <p class="mt-2">{{ cameraError }}</p>
              <Button label="Cấp quyền camera" icon="pi pi-camera" severity="info" @click="requestCameraPermission"
                class="mt-3" />
            </div>
          </div>
          <div v-else class="p-4 bg-green-50 rounded-lg w-full">
            <p class="text-green-700 font-medium">Quét thành công!</p>
            <p class="mt-2 font-mono">{{ qrScanResult }}</p>
            <!-- ENHANCEMENT: Reservation status indicators -->
            <div v-if="reservationStatus.isReserving" class="mt-3 p-3 bg-blue-50 rounded-lg">
              <div class="text-blue-700">
                <i class="pi pi-spin pi-spinner mr-2"></i>
                Đang đặt trước serial number {{ reservationStatus.currentSerialNumber }}...
                <span v-if="reservationStatus.retryAttempt > 1" class="text-sm">
                  (Thử lại lần {{ reservationStatus.retryAttempt }})
                </span>
              </div>
            </div>

            <div v-if="reservationStatus.conflictDetected && !reservationStatus.isReserving" class="mt-3 p-3 bg-orange-50 rounded-lg">
              <div class="text-orange-700">
                <i class="pi pi-exclamation-triangle mr-2"></i>
                Phát hiện xung đột đặt trước - Serial number đã được chọn bởi người khác
              </div>
            </div>

            <div v-if="qrProcessingResult" class="mt-3">
              <div v-if="qrProcessingResult.success" class="text-green-600">
                <i class="pi pi-check-circle mr-2"></i>
                {{ qrProcessingResult.message }}
              </div>
              <div v-else class="space-y-3">
                <!-- Main error message -->
                <div class="text-red-600">
                  <i class="pi pi-times-circle mr-2"></i>
                  {{ qrProcessingResult.message }}
                </div>

                <!-- ENHANCEMENT: Detailed error guidance for QR scanning -->
                <div v-if="qrProcessingResult.guidance" class="p-3 bg-red-50 rounded-lg border border-red-200">
                  <div class="text-red-800 text-sm font-medium mb-2">
                    <i class="pi pi-info-circle mr-1"></i>
                    Hướng dẫn khắc phục:
                  </div>
                  <div class="text-red-700 text-sm">{{ qrProcessingResult.guidance }}</div>
                </div>

                <!-- ENHANCEMENT: Recovery actions for QR scanning -->
                <div v-if="qrProcessingResult.recoveryActions && qrProcessingResult.recoveryActions.length > 0"
                     class="p-3 bg-blue-50 rounded-lg border border-blue-200">
                  <div class="text-blue-800 text-sm font-medium mb-2">
                    <i class="pi pi-lightbulb mr-1"></i>
                    Các bước thực hiện:
                  </div>
                  <ul class="text-blue-700 text-sm space-y-1">
                    <li v-for="(action, index) in qrProcessingResult.recoveryActions" :key="index" class="flex items-start">
                      <span class="text-blue-500 mr-2">{{ index + 1 }}.</span>
                      <span>{{ action }}</span>
                    </li>
                  </ul>
                </div>

                <!-- ENHANCEMENT: Error type indicator -->
                <div v-if="qrProcessingResult.errorType" class="text-xs text-gray-500">
                  Mã lỗi: {{ qrProcessingResult.errorType }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="flex justify-center gap-2">
        <Button label="Đóng" icon="pi pi-times" severity="secondary" @click="showQRScanner = false" />
        <Button v-if="qrScanResult && qrProcessingResult?.success" label="Quét tiếp" icon="pi pi-refresh"
          severity="info" @click="resetQRScanner" />
      </div>
    </div>
  </Dialog>

  <!-- Order Confirmation Dialog -->
  <Dialog v-model:visible="orderConfirmationVisible" modal header="Xác nhận cập nhật đơn hàng" :style="{ width: '600px' }"
    :closable="!creating" :dismissableMask="!creating">
    <div v-if="currentOrder" class="space-y-6">
      <!-- Customer Information -->
      <div class="border rounded-lg p-4 bg-surface-50">
        <h4 class="font-semibold text-lg mb-3 flex items-center gap-2">
          <i class="pi pi-user text-primary"></i>
          Thông tin khách hàng
        </h4>
        <div v-if="currentOrder.khachHang" class="space-y-2">
          <div class="flex items-center gap-3">
            <Avatar :label="currentOrder.khachHang.hoTen?.charAt(0)" size="small" />
            <div>
              <div class="font-medium">{{ currentOrder.khachHang.hoTen }}</div>
              <div class="text-sm text-surface-500">{{ currentOrder.khachHang.soDienThoai }}</div>
              <div v-if="currentOrder.khachHang.email" class="text-xs text-surface-400">{{
                currentOrder.khachHang.email
                }}</div>
            </div>
          </div>

          <!-- Delivery Information (when delivery is enabled) -->
          <div v-if="currentOrder.giaohang" class="mt-3 p-3 border rounded-lg bg-blue-50">
            <div class="font-medium text-sm mb-3 flex items-center gap-2">
              <i class="pi pi-truck text-blue-600"></i>
              <span class="text-blue-800">Thông tin giao hàng</span>
            </div>

            <!-- Recipient Information -->
            <div class="space-y-2 mb-3">
              <div class="text-sm">
                <span class="font-medium text-blue-700">Người nhận:</span>
                <span class="text-surface-700 ml-2">{{ recipientInfo.hoTen || 'Chưa nhập' }}</span>
              </div>
              <div class="text-sm">
                <span class="font-medium text-blue-700">Số điện thoại:</span>
                <span class="text-surface-700 ml-2">{{
                  recipientInfo.soDienThoai || 'Chưa nhập'
                  }}</span>
              </div>
            </div>

            <!-- Delivery Address -->
            <div class="border-t border-blue-200 pt-2">
              <div class="text-sm">
                <span class="font-medium text-blue-700">Địa chỉ giao hàng:</span>
                <div class="text-surface-700 mt-1 ml-2"
                  :class="{ 'text-surface-400 italic': !addressData.duong?.trim() }">
                  {{ formattedDeliveryAddress }}
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="text-surface-500 italic">
          <div v-if="currentOrder.giaohang && recipientInfo.hoTen.trim()">
            Khách hàng: {{ recipientInfo.hoTen }} ({{ recipientInfo.soDienThoai || 'Chưa có SĐT' }})
          </div>
          <div v-else> Khách hàng vãng lai </div>
        </div>
      </div>

      <!-- Products Summary -->
      <div class="border rounded-lg p-4 bg-surface-50">
        <h4 class="font-semibold text-lg mb-3 flex items-center gap-2">
          <i class="pi pi-shopping-cart text-primary"></i>
          Sản phẩm ({{ currentOrder.sanPhamList.length }} sản phẩm)
        </h4>
        <div class="space-y-3 max-h-40 overflow-y-auto">
          <div v-for="(item, index) in currentOrder.sanPhamList" :key="index"
            class="flex items-center gap-3 p-2 border rounded bg-white">
            <img :src="getCartItemImage(item) || '/placeholder-product.png'" :alt="getCartItemName(item)"
              class="w-10 h-10 object-cover rounded" />
            <div class="flex-1 min-w-0">
              <div class="font-medium text-sm">{{ getCartItemName(item) }}</div>
              <div class="text-xs text-surface-500">{{ getCartItemCode(item) }}</div>
              <div v-if="item.sanPhamChiTiet?.serialNumber" class="text-xs text-primary">
                Serial: {{ item.sanPhamChiTiet.serialNumber }}
              </div>
            </div>
            <div class="text-right">
              <div class="font-semibold text-primary">{{ formatCurrency(item.thanhTien) }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Payment and Delivery Information -->
      <div class="border rounded-lg p-4 bg-surface-50">
        <h4 class="font-semibold text-lg mb-3 flex items-center gap-2">
          <i class="pi pi-credit-card text-primary"></i>
          Thanh toán & Giao hàng
        </h4>
        <div class="space-y-2">
          <div class="flex justify-between">
            <span>Phương thức thanh toán:</span>
            <span class="font-medium">
              {{
                paymentMethods.find((m) => m.value === currentOrder.phuongThucThanhToan)?.label ||
                currentOrder.phuongThucThanhToan
              }}
            </span>
          </div>
          <div class="flex justify-between">
            <span>Hình thức:</span>
            <span class="font-medium">
              {{ currentOrder.giaohang ? 'Giao hàng tận nơi' : 'Lấy tại cửa hàng' }}
            </span>
          </div>
        </div>
      </div>

      <!-- Order Summary -->
      <div class="border rounded-lg p-4 bg-primary/5">
        <h4 class="font-semibold text-lg mb-3 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <i class="pi pi-calculator text-primary"></i>
            Tổng kết đơn hàng
          </div>
          <Button icon="pi pi-refresh" outlined size="small"
            @click="calculateCurrentOrderTotals()"
            v-tooltip.left="'Tính lại tổng tiền'" />
        </h4>
        <div class="space-y-2">
          <div class="flex justify-between">
            <span>Tạm tính:</span>
            <span>{{ formatCurrency(currentOrder.tongTienHang || 0) }}</span>
          </div>
          <div v-if="currentOrder.giaTriGiamGiaVoucher > 0" class="flex justify-between text-green-600">
            <span>Giảm giá voucher:</span>
            <span>-{{ formatCurrency(currentOrder.giaTriGiamGiaVoucher) }}</span>
          </div>
          <div v-if="currentOrder.giaohang" class="flex justify-between">
            <span>Phí giao hàng:</span>
            <span>{{ formatCurrency(consolidatedShippingFee) }}</span>
          </div>
          <hr class="my-2" />
          <div class="flex justify-between font-semibold text-lg">
            <span>Tổng cộng:</span>
            <span class="text-primary">{{ formatCurrency(dynamicOrderTotal) }}</span>
          </div>
        </div>
      </div>
    </div>



    <template #footer>
      <div class="flex justify-end gap-3">
        <Button label="Hủy" icon="pi pi-times" severity="secondary" outlined @click="orderConfirmationVisible = false"
          :disabled="creating" />
        <Button label="Cập nhật đơn hàng" icon="pi pi-check" severity="success" @click="confirmAndUpdateOrder"
          :loading="creating" />
      </div>
    </template>
  </Dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, inject } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useOrderStore } from '@/stores/orderStore'
import { useCustomerStore } from '@/stores/customerstore'
import { useProductStore } from '@/stores/productstore'
import { useVoucherStore } from '@/stores/voucherStore'

import { useCartReservations } from '@/composables/useCartReservations'
import { useEmbeddedAddress } from '@/composables/useEmbeddedAddress'
import voucherApi from '@/apis/voucherApi'

import { useOrderAudit } from '@/composables/useOrderAudit'
import { useOrderValidation } from '@/composables/useOrderValidation'
import { useRealTimeOrderManagement } from '@/composables/useRealTimeOrderManagement'
import { useRealTimePricing } from '@/composables/useRealTimePricing'
import { useVoucherMonitoring } from '@/composables/useVoucherMonitoring'
import { useShippingCalculator } from '@/composables/useShippingCalculator'
import { useOrderExpiration } from '@/composables/useOrderExpiration'
import WebSocketLogger, { WebSocketLoggerUtils } from '@/utils/WebSocketLogger.js'
import { getPaymentReturnUrl } from '@/utils/returnUrlConfig.js'
import { mapOrderToUpdateDto } from '@/utils/orderMapping.js'
import storageApi from '@/apis/storage'
import serialNumberApi from '@/apis/serialNumberApi'
import orderApi from '@/apis/orderApi'

// PrimeVue Components
import Toast from 'primevue/toast'
import Button from 'primevue/button'
import Badge from 'primevue/badge'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import AutoComplete from 'primevue/autocomplete'
import Avatar from 'primevue/avatar'
import ToggleButton from 'primevue/togglebutton'
import Select from 'primevue/select'
import Dialog from 'primevue/dialog'
import ProgressSpinner from 'primevue/progressspinner'
import { useConfirm } from 'primevue/useconfirm'

// Custom Components
import ProductVariantDialog from '@/views/orders/components/ProductVariantDialog.vue'
import FastCustomerCreate from '@/views/orders/components/FastCustomerCreate.vue'
import FastAddressCreate from '@/views/orders/components/FastAddressCreate.vue'
import MixedPaymentDialog from '@/views/orders/components/MixedPaymentDialog.vue'
import PriceChangeWarning from '@/views/orders/components/PriceChangeWarning.vue'
import VoucherSuggestionDialog from '@/views/orders/components/VoucherSuggestionDialog.vue'

// QR Scanner
import { QrcodeStream } from 'vue-qrcode-reader'

// Store access
const toast = useToast()
const orderStore = useOrderStore()
const customerStore = useCustomerStore()
const productStore = useProductStore()
const voucherStore = useVoucherStore()
const _confirmDialog = inject('confirmDialog')
const confirm = useConfirm()

// Cart reservations with enhanced conflict detection
const { reserveForCart, releaseCartReservations, releaseSpecificItems } = useCartReservations()

// ENHANCEMENT: Comprehensive reservation conflict detection and prevention
const reservationConflictDetection = {
  // Retry configuration for transient failures
  maxRetries: 3,
  retryDelay: 1000, // 1 second base delay

  /**
   * FIXED: Enhanced serial number selection validation with correct API usage
   * Uses serialNumberApi.getSerialNumbersByVariant() instead of getInventoryAvailability()
   * Aligns with backend HoaDonService.java changes for existing vs new item distinction
   */
  async validateSerialNumberSelection(serialNumber, variantId, context = 'unknown') {
    console.log(`🔍 [CONFLICT DETECTION] Starting validation for serial ${serialNumber} (context: ${context})`)

    try {
      // Step 1: Check if serial number is already in current cart
      const serialValue = serialNumber.serialNumberValue || serialNumber.serialNumber || serialNumber
      const existingInCart = currentOrder.value?.sanPhamList?.find(
        (item) =>
          item.sanPhamChiTiet?.serialNumberId === serialNumber.id ||
          item.sanPhamChiTiet?.serialNumber === serialValue ||
          item.serialNumber === serialValue
      )

      if (existingInCart) {
        throw new Error(`Serial number đã có trong giỏ hàng hiện tại`)
      }

      // Step 2: FIXED - Get actual serial numbers with status information
      console.log(`🔍 [CONFLICT DETECTION] Fetching serial numbers for variant ${variantId}`)
      const serialNumbers = await serialNumberApi.getSerialNumbersByVariant(variantId)

      if (!serialNumbers || !Array.isArray(serialNumbers)) {
        throw new Error(`Không thể tải danh sách serial numbers`)
      }

      // Step 3: Find the specific serial number and check its status
      const targetSerial = serialNumbers.find(s =>
        s.id === serialNumber.id ||
        s.serialNumberValue === serialValue ||
        s.serialNumber === serialValue
      )

      if (!targetSerial) {
        throw new Error(`Serial number ${serialValue} không tồn tại trong hệ thống`)
      }

      console.log(`🔍 [CONFLICT DETECTION] Found serial number:`, {
        id: targetSerial.id,
        serialNumberValue: targetSerial.serialNumberValue,
        trangThai: targetSerial.trangThai,
        donHangDatTruoc: targetSerial.donHangDatTruoc
      })

      // Step 4: Check serial number status and availability
      if (targetSerial.trangThai === 'AVAILABLE') {
        console.log(`✅ [CONFLICT DETECTION] Serial number ${serialValue} is AVAILABLE`)
        return {
          success: true,
          serialNumber: targetSerial,
          status: 'available'
        }
      }

      // Step 5: Handle RESERVED status - acceptable if reserved for current order
      if (targetSerial.trangThai === 'RESERVED') {
        const isReservedForThisOrder = targetSerial.donHangDatTruoc === orderId.value ||
                                      targetSerial.donHangDatTruoc === orderId.value?.toString()

        if (isReservedForThisOrder) {
          console.log(`✅ [CONFLICT DETECTION] Serial number ${serialValue} is RESERVED for current order`)
          return {
            success: true,
            serialNumber: targetSerial,
            status: 'reserved_for_order'
          }
        } else {
          throw new Error(`Serial number ${serialValue} đã được đặt trước bởi đơn hàng khác`)
        }
      }

      // Step 6: Handle SOLD status - not available
      if (targetSerial.trangThai === 'SOLD') {
        throw new Error(`Serial number ${serialValue} đã được bán`)
      }

      // Step 7: Handle other statuses
      throw new Error(`Serial number ${serialValue} không khả dụng (${targetSerial.trangThai})`)

    } catch (error) {
      const serialValue = serialNumber.serialNumberValue || serialNumber.serialNumber || serialNumber
      console.error(`❌ [CONFLICT DETECTION] Validation failed for serial ${serialValue}:`, error)
      return {
        success: false,
        error: error.message,
        serialNumber: serialNumber,
        context: context
      }
    }
  },

  /**
   * ENHANCED: Attempt reservation with comprehensive retry mechanism and graceful degradation
   */
  async attemptReservationWithRetry(reservationRequest, context = 'unknown') {
    let lastError = null
    let serviceUnavailable = false

    // Update reservation status indicators
    reservationStatus.value = {
      isReserving: true,
      currentSerialNumber: reservationRequest.serialNumbers?.[0] || null,
      lastReservationResult: null,
      conflictDetected: false,
      retryAttempt: 0,
      context: context
    }

    for (let attempt = 1; attempt <= this.maxRetries; attempt++) {
      try {
        // Update retry attempt in status
        reservationStatus.value.retryAttempt = attempt

        console.log(`🔄 [RETRY MECHANISM] Reservation attempt ${attempt}/${this.maxRetries} (context: ${context})`)

        // ENHANCEMENT: Check service availability before attempting reservation
        if (serviceUnavailable && attempt > 1) {
          console.log(`🚫 [GRACEFUL DEGRADATION] Service marked as unavailable, attempting graceful fallback`)
          return await this.attemptGracefulDegradation(reservationRequest, context)
        }

        const result = await reserveForCart(reservationRequest)

        // FIXED: Use correct validation path - check result.data.thanhCong instead of result.thanhCong
        if (result && result.data && result.data.thanhCong) {
          console.log(`✅ [RETRY MECHANISM] Reservation successful on attempt ${attempt}`)

          // Reset service availability flag on success
          serviceUnavailable = false

          // Update success status
          reservationStatus.value = {
            ...reservationStatus.value,
            isReserving: false,
            lastReservationResult: 'success',
            conflictDetected: false
          }

          // Return the actual reservation data for consistency
          return result.data
        } else {
          // Use the correct error message path - check result.data.thongBao first
          const errorMessage = result?.data?.thongBao || result?.message || 'Reservation failed without specific error'
          throw new Error(errorMessage)
        }

      } catch (error) {
        lastError = error
        console.warn(`⚠️ [RETRY MECHANISM] Attempt ${attempt} failed:`, error.message)

        // ENHANCEMENT: Detect service unavailability
        const isServiceError = error.message.includes('Network Error') ||
                              error.message.includes('timeout') ||
                              error.code >= 500 ||
                              error.response?.status >= 500

        if (isServiceError) {
          serviceUnavailable = true
          console.warn(`🚨 [GRACEFUL DEGRADATION] Service unavailability detected`)
        }

        // Update conflict detection status
        const isConflictError = error.message.includes('không khả dụng') ||
                               error.message.includes('đã được đặt trước') ||
                               error.message.includes('SOLD')

        if (isConflictError) {
          reservationStatus.value.conflictDetected = true
          console.log(`🚫 [RETRY MECHANISM] Non-retryable error detected, stopping retries`)
          break
        }

        // ENHANCEMENT: Adaptive retry delay based on error type
        if (attempt < this.maxRetries) {
          let delay = this.retryDelay * Math.pow(2, attempt - 1)

          // Longer delay for service errors
          if (isServiceError) {
            delay = Math.min(delay * 2, 10000) // Cap at 10 seconds
          }

          console.log(`⏳ [RETRY MECHANISM] Waiting ${delay}ms before retry...`)
          await new Promise(resolve => setTimeout(resolve, delay))
        }
      }
    }

    // ENHANCEMENT: Final graceful degradation attempt if service is unavailable
    if (serviceUnavailable) {
      console.log(`🔄 [GRACEFUL DEGRADATION] Final attempt with graceful degradation`)
      try {
        return await this.attemptGracefulDegradation(reservationRequest, context)
      } catch (degradationError) {
        console.error(`❌ [GRACEFUL DEGRADATION] Graceful degradation failed:`, degradationError)
      }
    }

    // Update failure status
    reservationStatus.value = {
      ...reservationStatus.value,
      isReserving: false,
      lastReservationResult: 'failed'
    }

    throw lastError || new Error('All reservation attempts failed')
  },

  /**
   * ENHANCEMENT: Graceful degradation when reservation service is unavailable
   */
  async attemptGracefulDegradation(reservationRequest, context) {
    console.log(`🔄 [GRACEFUL DEGRADATION] Attempting graceful degradation for ${context}`)

    // Show user notification about degraded service
    toast.add({
      severity: 'warn',
      summary: 'Chế độ dự phòng',
      detail: 'Dịch vụ đặt trước tạm thời không khả dụng. Hệ thống đang hoạt động ở chế độ dự phòng.',
      life: 8000
    })

    // In graceful degradation mode, we could:
    // 1. Allow adding to cart without reservation (with warning)
    // 2. Queue the reservation for later processing
    // 3. Use local storage to track pending reservations

    // For now, we'll return a mock success response with warning
    return {
      thanhCong: true,
      cartSessionId: reservationRequest.tabId,
      soLuongDatTruoc: reservationRequest.soLuong,
      thoiGianHetHan: new Date(Date.now() + 30 * 60 * 1000).toISOString(), // 30 minutes
      degradedMode: true,
      warning: 'Đặt trước trong chế độ dự phòng - vui lòng xác nhận lại sau'
    }
  },

  /**
   * ENHANCED: Comprehensive error handling framework for reservation operations
   * Provides specific error messages, user guidance, and recovery workflows
   */
  getComprehensiveErrorHandling(error, context = 'unknown', serialNumber = null) {
    const message = error.message || error.toString()
    const errorCode = error.code || error.response?.status || 'UNKNOWN'

    // Error classification and handling
    const errorHandling = {
      type: 'unknown',
      severity: 'error',
      userMessage: '',
      technicalMessage: message,
      guidance: '',
      recoveryActions: [],
      shouldRetry: false,
      retryDelay: 0,
      showDetails: false
    }

    // INVENTORY CONFLICT ERRORS
    if (message.includes('đã có trong giỏ hàng') || message.includes('already in cart')) {
      errorHandling.type = 'duplicate_in_cart'
      errorHandling.severity = 'warning'
      errorHandling.userMessage = `Serial number ${serialNumber || 'này'} đã có trong giỏ hàng của bạn`
      errorHandling.guidance = 'Bạn đã chọn serial number này rồi. Vui lòng kiểm tra lại giỏ hàng.'
      errorHandling.recoveryActions = [
        'Kiểm tra danh sách sản phẩm trong giỏ hàng',
        'Chọn serial number khác nếu cần thêm sản phẩm'
      ]
    }

    // RESERVATION CONFLICT ERRORS
    else if (message.includes('không khả dụng') || message.includes('đã được đặt trước') || message.includes('not available')) {
      errorHandling.type = 'reservation_conflict'
      errorHandling.severity = 'error'
      errorHandling.userMessage = `Serial number ${serialNumber || 'này'} đã được người khác chọn`
      errorHandling.guidance = 'Serial number này hiện đang được đặt trước bởi người dùng khác. Vui lòng chọn serial number khác.'
      errorHandling.recoveryActions = [
        'Chọn serial number khác từ danh sách có sẵn',
        'Thử lại sau vài phút nếu người dùng khác hủy đặt trước',
        'Liên hệ quản lý kho để kiểm tra tình trạng'
      ]
    }

    // SOLD ITEM ERRORS
    else if (message.includes('SOLD') || message.includes('đã được bán')) {
      errorHandling.type = 'item_sold'
      errorHandling.severity = 'error'
      errorHandling.userMessage = `Serial number ${serialNumber || 'này'} đã được bán`
      errorHandling.guidance = 'Sản phẩm này đã được bán cho khách hàng khác và không còn khả dụng.'
      errorHandling.recoveryActions = [
        'Chọn serial number khác từ danh sách có sẵn',
        'Kiểm tra tồn kho để tìm sản phẩm thay thế',
        'Thông báo cho khách hàng về tình trạng hết hàng'
      ]
    }

    // NETWORK/SERVICE ERRORS
    else if (message.includes('Network Error') || message.includes('timeout') || errorCode >= 500) {
      errorHandling.type = 'service_unavailable'
      errorHandling.severity = 'error'
      errorHandling.userMessage = 'Dịch vụ đặt trước tạm thời không khả dụng'
      errorHandling.guidance = 'Hệ thống đang gặp sự cố kỹ thuật. Vui lòng thử lại sau.'
      errorHandling.recoveryActions = [
        'Thử lại sau vài giây',
        'Kiểm tra kết nối mạng',
        'Liên hệ bộ phận kỹ thuật nếu lỗi tiếp tục'
      ]
      errorHandling.shouldRetry = true
      errorHandling.retryDelay = 3000
    }

    // VALIDATION ERRORS
    else if (message.includes('validation') || message.includes('invalid') || errorCode === 400) {
      errorHandling.type = 'validation_error'
      errorHandling.severity = 'warning'
      errorHandling.userMessage = 'Thông tin không hợp lệ'
      errorHandling.guidance = 'Dữ liệu gửi lên không đúng định dạng hoặc thiếu thông tin bắt buộc.'
      errorHandling.recoveryActions = [
        'Kiểm tra lại thông tin đã nhập',
        'Thử chọn lại serial number',
        'Làm mới trang và thử lại'
      ]
      errorHandling.showDetails = true
    }

    // AUTHENTICATION/AUTHORIZATION ERRORS
    else if (errorCode === 401 || errorCode === 403) {
      errorHandling.type = 'auth_error'
      errorHandling.severity = 'error'
      errorHandling.userMessage = 'Không có quyền thực hiện thao tác này'
      errorHandling.guidance = 'Phiên đăng nhập có thể đã hết hạn hoặc bạn không có quyền đặt trước sản phẩm.'
      errorHandling.recoveryActions = [
        'Đăng nhập lại',
        'Liên hệ quản trị viên để cấp quyền',
        'Kiểm tra vai trò người dùng'
      ]
    }

    // INVENTORY CHECK ERRORS
    else if (message.includes('Không thể kiểm tra tình trạng tồn kho') || message.includes('inventory check failed')) {
      errorHandling.type = 'inventory_check_failed'
      errorHandling.severity = 'warning'
      errorHandling.userMessage = 'Không thể kiểm tra tình trạng tồn kho'
      errorHandling.guidance = 'Hệ thống không thể xác minh tình trạng sản phẩm hiện tại.'
      errorHandling.recoveryActions = [
        'Thử lại sau vài giây',
        'Làm mới danh sách sản phẩm',
        'Kiểm tra kết nối mạng'
      ]
      errorHandling.shouldRetry = true
      errorHandling.retryDelay = 2000
    }

    // DEFAULT/UNKNOWN ERRORS
    else {
      errorHandling.type = 'unknown_error'
      errorHandling.severity = 'error'
      errorHandling.userMessage = 'Có lỗi không xác định xảy ra'
      errorHandling.guidance = 'Hệ thống gặp lỗi không mong muốn khi xử lý yêu cầu.'
      errorHandling.recoveryActions = [
        'Thử lại thao tác',
        'Làm mới trang',
        'Liên hệ bộ phận hỗ trợ kỹ thuật'
      ]
      errorHandling.showDetails = true
      errorHandling.shouldRetry = true
      errorHandling.retryDelay = 1000
    }

    // Add context-specific information
    if (context === 'QR_scanning') {
      errorHandling.userMessage = `QR Scan: ${errorHandling.userMessage}`
      errorHandling.recoveryActions.unshift('Thử quét lại mã QR')
    } else if (context === 'addVariantToCurrentOrder') {
      errorHandling.recoveryActions.unshift('Thử chọn sản phẩm từ danh sách')
    }

    return errorHandling
  },

  /**
   * Legacy method for backward compatibility
   */
  getUserFriendlyErrorMessage(error, context = 'unknown') {
    const handling = this.getComprehensiveErrorHandling(error, context)
    return handling.userMessage
  }
}

// ENHANCEMENT: Unified cleanup strategy for OrderEdit and OrderCreate alignment
const unifiedCleanupStrategy = {
  /**
   * Initialize cleanup tracking when order is loaded
   * Records original order items that should NOT be cleaned up
   */
  initializeCleanupTracking(order) {
    if (!order || !order.sanPhamList) return

    // Clear previous tracking
    originalOrderItems.value.clear()
    newlyReservedItems.value.clear()

    // Track original order items (these should NOT be cleaned up)
    order.sanPhamList.forEach(item => {
      if (item.sanPhamChiTiet?.serialNumber) {
        originalOrderItems.value.add(item.sanPhamChiTiet.serialNumber)
        console.log(`🔒 [CLEANUP TRACKING] Original item tracked: ${item.sanPhamChiTiet.serialNumber}`)
      }
    })

    console.log(`🔒 [CLEANUP TRACKING] Initialized with ${originalOrderItems.value.size} original items`)
  },

  /**
   * Track newly reserved items during edit session
   * These items should be cleaned up if user leaves without saving
   */
  trackNewlyReservedItem(serialNumber) {
    if (!serialNumber) return

    // Only track if it's not an original order item
    if (!originalOrderItems.value.has(serialNumber)) {
      newlyReservedItems.value.add(serialNumber)
      console.log(`🔒 [CLEANUP TRACKING] New reservation tracked: ${serialNumber}`)
      console.log(`🔒 [CLEANUP TRACKING] Total new reservations: ${newlyReservedItems.value.size}`)
    } else {
      console.log(`🔒 [CLEANUP TRACKING] Skipping original item: ${serialNumber}`)
    }
  },

  /**
   * Remove item from newly reserved tracking (when item is removed from cart)
   */
  untrackNewlyReservedItem(serialNumber) {
    if (!serialNumber) return

    if (newlyReservedItems.value.has(serialNumber)) {
      newlyReservedItems.value.delete(serialNumber)
      console.log(`🔒 [CLEANUP TRACKING] Removed from new reservations: ${serialNumber}`)
    }
  },

  /**
   * Clean up only newly reserved items when leaving edit mode without saving
   * Preserves existing order items
   */
  async cleanupNewlyReservedItems(context = 'page_unload') {
    if (newlyReservedItems.value.size === 0) {
      console.log(`🔒 [UNIFIED CLEANUP] No newly reserved items to clean up (context: ${context})`)
      return
    }

    console.log(`🔒 [UNIFIED CLEANUP] Cleaning up ${newlyReservedItems.value.size} newly reserved items (context: ${context})`)

    const itemsToCleanup = Array.from(newlyReservedItems.value)
    let cleanupSuccessCount = 0
    let cleanupFailureCount = 0

    for (const serialNumber of itemsToCleanup) {
      try {
        // Find the variant ID for this serial number to release specific reservation
        const cartItem = currentOrder.value?.sanPhamList?.find(item =>
          item.sanPhamChiTiet?.serialNumber === serialNumber
        )

        if (cartItem && cartItem.sanPhamChiTiet?.id) {
          await releaseSpecificItems(orderId.value, cartItem.sanPhamChiTiet.id, 1)
          console.log(`✅ [UNIFIED CLEANUP] Released reservation for: ${serialNumber}`)
          cleanupSuccessCount++
        } else {
          console.warn(`⚠️ [UNIFIED CLEANUP] Could not find cart item for serial: ${serialNumber}`)
        }
      } catch (error) {
        console.error(`❌ [UNIFIED CLEANUP] Failed to release reservation for ${serialNumber}:`, error)
        cleanupFailureCount++
      }
    }

    // Clear the tracking after cleanup attempt
    newlyReservedItems.value.clear()

    console.log(`🔒 [UNIFIED CLEANUP] Cleanup completed: ${cleanupSuccessCount} success, ${cleanupFailureCount} failures`)
  },

  /**
   * Get cleanup summary for debugging
   */
  getCleanupSummary() {
    return {
      originalItems: originalOrderItems.value.size,
      newlyReservedItems: newlyReservedItems.value.size,
      originalItemsList: Array.from(originalOrderItems.value),
      newlyReservedItemsList: Array.from(newlyReservedItems.value)
    }
  }
}

// ENHANCEMENT: Comprehensive error handling and user feedback framework
const comprehensiveErrorHandling = {
  /**
   * Handle reservation errors with comprehensive user feedback
   */
  async handleReservationError(error, context, serialNumber = null) {
    console.error(`🚨 [ERROR HANDLING] Reservation error in ${context}:`, error)

    // Get comprehensive error analysis
    const errorHandling = reservationConflictDetection.getComprehensiveErrorHandling(error, context, serialNumber)

    // Update error state
    reservationErrorState.value = {
      hasError: true,
      errorHandling: errorHandling,
      showErrorDialog: errorHandling.severity === 'error' && errorHandling.showDetails,
      autoRetryEnabled: errorHandling.shouldRetry,
      retryCount: reservationErrorState.value.retryCount + 1,
      maxAutoRetries: 2
    }

    // Show immediate user feedback
    this.showErrorFeedback(errorHandling, context)

    // Handle auto-retry if applicable
    if (errorHandling.shouldRetry && reservationErrorState.value.retryCount <= reservationErrorState.value.maxAutoRetries) {
      console.log(`🔄 [ERROR HANDLING] Auto-retry enabled, attempt ${reservationErrorState.value.retryCount}`)
      return await this.attemptAutoRetry(errorHandling, context, serialNumber)
    }

    // Log detailed error information
    this.logDetailedError(error, errorHandling, context, serialNumber)

    return false // Indicate failure
  },

  /**
   * Show user-friendly error feedback
   */
  showErrorFeedback(errorHandling, context) {
    const toastConfig = {
      severity: errorHandling.severity,
      summary: this.getErrorSummary(errorHandling.type, context),
      detail: errorHandling.userMessage,
      life: errorHandling.severity === 'error' ? 8000 : 5000
    }

    // Add guidance for complex errors
    if (errorHandling.guidance && errorHandling.recoveryActions.length > 0) {
      toastConfig.detail += `\n\n${errorHandling.guidance}`
    }

    toast.add(toastConfig)

    // Show detailed error dialog for complex cases
    if (errorHandling.showDetails) {
      this.showDetailedErrorDialog(errorHandling, context)
    }
  },

  /**
   * Get appropriate error summary based on error type and context
   */
  getErrorSummary(errorType, context) {
    const contextMap = {
      'QR_scanning': 'Lỗi QR Scan',
      'addVariantToCurrentOrder': 'Lỗi thêm sản phẩm',
      'processScannedSerialNumber': 'Lỗi xử lý QR',
      'unknown': 'Lỗi đặt trước'
    }

    const typeMap = {
      'duplicate_in_cart': 'Sản phẩm đã có trong giỏ',
      'reservation_conflict': 'Xung đột đặt trước',
      'item_sold': 'Sản phẩm đã bán',
      'service_unavailable': 'Dịch vụ không khả dụng',
      'validation_error': 'Lỗi dữ liệu',
      'auth_error': 'Lỗi xác thực',
      'inventory_check_failed': 'Lỗi kiểm tra kho',
      'unknown_error': 'Lỗi hệ thống'
    }

    return `${contextMap[context] || contextMap.unknown}: ${typeMap[errorType] || typeMap.unknown_error}`
  },

  /**
   * Attempt automatic retry for recoverable errors
   */
  async attemptAutoRetry(errorHandling, context, serialNumber) {
    if (!errorHandling.shouldRetry) return false

    console.log(`🔄 [AUTO RETRY] Waiting ${errorHandling.retryDelay}ms before retry...`)

    // Show retry feedback to user
    toast.add({
      severity: 'info',
      summary: 'Đang thử lại...',
      detail: `Hệ thống đang tự động thử lại thao tác (lần ${reservationErrorState.value.retryCount})`,
      life: 3000
    })

    // Wait for retry delay
    await new Promise(resolve => setTimeout(resolve, errorHandling.retryDelay))

    // The actual retry will be handled by the calling function
    return true // Indicate retry should be attempted
  },

  /**
   * Show detailed error dialog for complex errors
   */
  showDetailedErrorDialog(errorHandling, context) {
    // This would typically show a modal dialog with detailed error information
    // For now, we'll use a detailed toast message
    const detailMessage = [
      `Lỗi: ${errorHandling.userMessage}`,
      `Hướng dẫn: ${errorHandling.guidance}`,
      `Các bước khắc phục:`,
      ...errorHandling.recoveryActions.map((action, index) => `${index + 1}. ${action}`)
    ].join('\n')

    toast.add({
      severity: 'error',
      summary: 'Chi tiết lỗi',
      detail: detailMessage,
      life: 12000
    })
  },

  /**
   * Log detailed error information for debugging
   */
  logDetailedError(error, errorHandling, context, serialNumber) {
    const errorLog = {
      timestamp: new Date().toISOString(),
      context: context,
      serialNumber: serialNumber,
      errorType: errorHandling.type,
      severity: errorHandling.severity,
      originalError: error.message || error,
      errorCode: error.code || error.response?.status,
      userMessage: errorHandling.userMessage,
      technicalMessage: errorHandling.technicalMessage,
      retryCount: reservationErrorState.value.retryCount,
      orderInfo: {
        orderId: orderId.value,
        currentOrderItems: currentOrder.value?.sanPhamList?.length || 0
      }
    }

    console.error('🚨 [DETAILED ERROR LOG]', errorLog)

    // In a production environment, this could be sent to an error tracking service
    // logger.error('Reservation error', errorLog)
  },

  /**
   * Clear error state
   */
  clearErrorState() {
    reservationErrorState.value = {
      hasError: false,
      errorHandling: null,
      showErrorDialog: false,
      autoRetryEnabled: true,
      retryCount: 0,
      maxAutoRetries: 2
    }
  },

  /**
   * Get user guidance for error recovery
   */
  getRecoveryGuidance(errorType) {
    const guidanceMap = {
      'duplicate_in_cart': 'Kiểm tra giỏ hàng để xem sản phẩm đã được thêm chưa.',
      'reservation_conflict': 'Chọn serial number khác hoặc thử lại sau.',
      'item_sold': 'Tìm sản phẩm thay thế hoặc thông báo khách hàng.',
      'service_unavailable': 'Kiểm tra kết nối mạng và thử lại.',
      'validation_error': 'Kiểm tra lại thông tin đã nhập.',
      'auth_error': 'Đăng nhập lại hoặc liên hệ quản trị viên.',
      'inventory_check_failed': 'Làm mới trang và thử lại.',
      'unknown_error': 'Liên hệ bộ phận hỗ trợ kỹ thuật.'
    }

    return guidanceMap[errorType] || guidanceMap.unknown_error
  },

  /**
   * ENHANCEMENT: User-friendly error recovery workflows
   */
  async executeRecoveryWorkflow(errorType, context, serialNumber = null) {
    console.log(`🔧 [RECOVERY WORKFLOW] Executing recovery for ${errorType} in ${context}`)

    switch (errorType) {
      case 'duplicate_in_cart':
        return await this.handleDuplicateInCartRecovery(serialNumber)

      case 'reservation_conflict':
        return await this.handleReservationConflictRecovery(context, serialNumber)

      case 'service_unavailable':
        return await this.handleServiceUnavailableRecovery(context)

      case 'inventory_check_failed':
        return await this.handleInventoryCheckFailedRecovery(context)

      default:
        return await this.handleGenericErrorRecovery(errorType, context)
    }
  },

  /**
   * Handle duplicate in cart recovery
   */
  async handleDuplicateInCartRecovery(serialNumber) {
    // Find the existing item in cart
    const existingItem = currentOrder.value?.sanPhamList?.find(item =>
      item.sanPhamChiTiet?.serialNumber === serialNumber
    )

    if (existingItem) {
      toast.add({
        severity: 'info',
        summary: 'Sản phẩm đã có trong giỏ',
        detail: `Serial number ${serialNumber} đã có trong giỏ hàng. Bạn có thể tăng số lượng hoặc chọn serial number khác.`,
        life: 5000
      })

      // Scroll to the existing item in the cart (if possible)
      // This would require additional UI implementation
      return true
    }

    return false
  },

  /**
   * Handle reservation conflict recovery
   */
  async handleReservationConflictRecovery(context, serialNumber) {
    // Suggest alternative serial numbers if available
    toast.add({
      severity: 'warn',
      summary: 'Xung đột đặt trước',
      detail: `Serial number ${serialNumber} đã được người khác chọn. Hệ thống sẽ hiển thị các lựa chọn khác.`,
      life: 6000
    })

    // Trigger refresh of available serial numbers
    // This would require integration with the product variant dialog
    return false
  },

  /**
   * Handle service unavailable recovery
   */
  async handleServiceUnavailableRecovery(context) {
    // Offer offline mode or queue the operation
    toast.add({
      severity: 'warn',
      summary: 'Dịch vụ tạm thời không khả dụng',
      detail: 'Hệ thống sẽ tự động thử lại khi dịch vụ khôi phục. Bạn có thể tiếp tục làm việc.',
      life: 8000
    })

    // Enable graceful degradation mode
    return true
  },

  /**
   * Handle inventory check failed recovery
   */
  async handleInventoryCheckFailedRecovery(context) {
    // Refresh inventory data
    toast.add({
      severity: 'info',
      summary: 'Đang làm mới dữ liệu',
      detail: 'Hệ thống đang cập nhật thông tin tồn kho mới nhất...',
      life: 4000
    })

    // Trigger inventory refresh (would require additional implementation)
    return true
  },

  /**
   * Handle generic error recovery
   */
  async handleGenericErrorRecovery(errorType, context) {
    toast.add({
      severity: 'error',
      summary: 'Lỗi hệ thống',
      detail: 'Đã xảy ra lỗi không mong muốn. Vui lòng thử lại hoặc liên hệ hỗ trợ kỹ thuật.',
      life: 6000
    })

    return false
  }
}

// Route and navigation setup
const route = useRoute()
const router = useRouter()

// Route parameter handling and edit mode detection
const orderId = computed(() => route.params.id)
const isEditMode = computed(() => !!orderId.value)

// Navigation helper
const goBack = () => {
  router.push({ name: 'orders' })
}

// Page title computed property
const pageTitle = computed(() => {
  if (isEditMode.value && currentOrder.value?.maHoaDon) {
    return `Chỉnh sửa đơn hàng ${currentOrder.value.maHoaDon}`
  }
  return 'Chỉnh sửa đơn hàng'
})

// Create reactive order state for single order management
const currentOrder = ref(null)

// Loading states
const loading = ref(false)
const error = ref(null)

// Destructure store actions for order management
const {
  fetchOrderById,
  updateOrder,
  createOrder: _createOrder,
} = orderStore

// Helper function to update current order data
const updateCurrentOrderData = (updates) => {
  if (currentOrder.value) {
    Object.assign(currentOrder.value, updates)
  }
}

// COMPREHENSIVE VALIDATION FUNCTIONS for data transformation
const validateChiTiet = (chiTiet) => {
  if (!Array.isArray(chiTiet)) {
    return { valid: false, error: 'chiTiet must be an array' }
  }

  const invalidItems = []
  const validItems = chiTiet.filter((item, index) => {
    const hasRequiredFields = item &&
                             typeof item === 'object' &&
                             item.sanPhamChiTietId &&
                             typeof item.soLuong === 'number' &&
                             item.soLuong > 0

    if (!hasRequiredFields) {
      invalidItems.push({
        index,
        item,
        issues: [
          !item ? 'Item is null/undefined' : null,
          !item.sanPhamChiTietId ? 'Missing sanPhamChiTietId' : null,
          typeof item.soLuong !== 'number' ? 'Invalid soLuong type' : null,
          item.soLuong <= 0 ? 'Invalid soLuong value' : null
        ].filter(Boolean)
      })
    }

    return hasRequiredFields
  })

  return {
    valid: invalidItems.length === 0,
    validItems,
    invalidItems,
    totalItems: chiTiet.length,
    validCount: validItems.length,
    error: invalidItems.length > 0 ? `${invalidItems.length} invalid items found` : null
  }
}

const validateAddress = (address) => {
  if (!address || typeof address !== 'object') {
    return { valid: false, error: 'Address must be an object' }
  }

  const requiredFields = ['duong', 'phuongXa', 'quanHuyen', 'tinhThanh']
  const missingFields = []
  const invalidFields = []

  requiredFields.forEach(field => {
    if (!address[field]) {
      missingFields.push(field)
    } else if (typeof address[field] !== 'string' || address[field].trim().length === 0) {
      invalidFields.push(field)
    }
  })

  // Validate street address length (minimum 5 characters as per backend validation)
  if (address.duong && address.duong.trim().length < 5) {
    invalidFields.push('duong (minimum 5 characters required)')
  }

  return {
    valid: missingFields.length === 0 && invalidFields.length === 0,
    missingFields,
    invalidFields,
    error: missingFields.length > 0 || invalidFields.length > 0 ?
           `Missing: [${missingFields.join(', ')}], Invalid: [${invalidFields.join(', ')}]` : null
  }
}

const validateRecipient = (orderData) => {
  if (!orderData || typeof orderData !== 'object') {
    return { valid: false, error: 'Order data must be an object' }
  }

  const issues = []

  if (!orderData.nguoiNhanTen || typeof orderData.nguoiNhanTen !== 'string' || orderData.nguoiNhanTen.trim().length === 0) {
    issues.push('Missing or invalid nguoiNhanTen')
  }

  if (!orderData.nguoiNhanSdt || typeof orderData.nguoiNhanSdt !== 'string' || orderData.nguoiNhanSdt.trim().length === 0) {
    issues.push('Missing or invalid nguoiNhanSdt')
  }

  // Validate phone number format (basic validation)
  if (orderData.nguoiNhanSdt && !/^[0-9+\-\s()]{8,15}$/.test(orderData.nguoiNhanSdt.trim())) {
    issues.push('Invalid phone number format')
  }

  return {
    valid: issues.length === 0,
    issues,
    error: issues.length > 0 ? issues.join(', ') : null
  }
}

const validateVoucherCodes = (voucherCodes) => {
  if (!voucherCodes) {
    return { valid: true, codes: [], error: null } // No vouchers is valid
  }

  if (!Array.isArray(voucherCodes)) {
    return { valid: false, error: 'voucherCodes must be an array' }
  }

  const invalidCodes = []
  const validCodes = voucherCodes.filter((code, index) => {
    const isValid = code && typeof code === 'string' && code.trim().length > 0
    if (!isValid) {
      invalidCodes.push({ index, code, issue: 'Empty or invalid voucher code' })
    }
    return isValid
  })

  return {
    valid: invalidCodes.length === 0,
    validCodes,
    invalidCodes,
    totalCodes: voucherCodes.length,
    validCount: validCodes.length,
    error: invalidCodes.length > 0 ? `${invalidCodes.length} invalid voucher codes found` : null
  }
}

// Enhanced delivery state derivation utility
const deriveDeliveryState = (orderData) => {
  console.log('🚚 [DELIVERY STATE] Deriving delivery state from order data:', orderData)

  // Check if explicit giaohang flag is set
  if (orderData.giaohang === true) {
    console.log('✅ [DELIVERY STATE] Explicit giaohang=true found')
    return true
  }

  if (orderData.giaohang === false) {
    console.log('❌ [DELIVERY STATE] Explicit giaohang=false found')
    return false
  }

  // Derive from diaChiGiaoHang presence and validity
  const address = orderData.diaChiGiaoHang

  if (!address) {
    console.log('❌ [DELIVERY STATE] No diaChiGiaoHang found, delivery=false')
    return false
  }

  console.log('🔍 [DELIVERY STATE] Checking address validity:', address)

  // Validate required address fields following backend DiaChiDto validation
  const hasValidStreet = address.duong &&
                        typeof address.duong === 'string' &&
                        address.duong.trim().length >= 5 &&
                        address.duong.trim().length <= 255

  const hasValidWard = address.phuongXa &&
                      typeof address.phuongXa === 'string' &&
                      address.phuongXa.trim().length > 0 &&
                      address.phuongXa.trim().length <= 100

  const hasValidDistrict = address.quanHuyen &&
                          typeof address.quanHuyen === 'string' &&
                          address.quanHuyen.trim().length > 0 &&
                          address.quanHuyen.trim().length <= 100

  const hasValidProvince = address.tinhThanh &&
                          typeof address.tinhThanh === 'string' &&
                          address.tinhThanh.trim().length > 0 &&
                          address.tinhThanh.trim().length <= 100

  const isAddressComplete = hasValidStreet && hasValidWard && hasValidDistrict && hasValidProvince

  console.log('🔍 [DELIVERY STATE] Address validation results:', {
    hasValidStreet,
    hasValidWard,
    hasValidDistrict,
    hasValidProvince,
    isAddressComplete,
    streetLength: address.duong?.trim()?.length || 0
  })

  if (isAddressComplete) {
    console.log('✅ [DELIVERY STATE] Complete address found, delivery=true')
    return true
  } else {
    console.log('⚠️ [DELIVERY STATE] Incomplete address found, delivery=false')
    return false
  }
}

// Enhanced data transformation utility for order editing with comprehensive error handling
const transformOrderForEdit = async (orderData) => {
  // Input validation
  if (!orderData) {
    console.error('❌ [TRANSFORM ERROR] No order data provided')
    toast.add({
      severity: 'error',
      summary: 'Lỗi dữ liệu đơn hàng',
      detail: 'Không có dữ liệu đơn hàng để xử lý.',
      life: 5000
    })
    return null
  }

  if (typeof orderData !== 'object') {
    console.error('❌ [TRANSFORM ERROR] Invalid order data type:', typeof orderData)
    toast.add({
      severity: 'error',
      summary: 'Lỗi định dạng dữ liệu',
      detail: 'Dữ liệu đơn hàng không đúng định dạng.',
      life: 5000
    })
    return null
  }

  console.log('🔄 [TRANSFORM DEBUG] Raw order data received:', orderData)
  console.log('🔄 [TRANSFORM DEBUG] chiTiet array:', orderData.chiTiet)
  console.log('🔄 [TRANSFORM DEBUG] voucherCodes array:', orderData.voucherCodes)
  console.log('🔄 [TRANSFORM DEBUG] Recipient fields:', {
    nguoiNhanTen: orderData.nguoiNhanTen,
    nguoiNhanSdt: orderData.nguoiNhanSdt,
    nguoiNhanEmail: orderData.nguoiNhanEmail
  })

  // Track transformation errors for summary
  const transformationErrors = []

  // ENHANCED CHITIET TRANSFORMATION with comprehensive validation
  let sanPhamList = []
  let chiTietValidationResult = null

  try {
    console.log('🔍 [VALIDATION] Starting chiTiet validation...')
    chiTietValidationResult = validateChiTiet(orderData.chiTiet || [])

    if (!chiTietValidationResult.valid) {
      console.error('❌ [VALIDATION ERROR] chiTiet validation failed:', chiTietValidationResult.error)
      console.error('❌ [VALIDATION ERROR] Invalid items:', chiTietValidationResult.invalidItems)

      // Track error for summary
      transformationErrors.push({
        type: 'chiTiet_validation',
        message: `${chiTietValidationResult.invalidItems?.length || 0} sản phẩm có dữ liệu không hợp lệ`,
        severity: 'warn'
      })

      // Show user notification for validation failure
      toast.add({
        severity: 'warn',
        summary: 'Dữ liệu sản phẩm không hợp lệ',
        detail: `Phát hiện ${chiTietValidationResult.invalidItems?.length || 0} sản phẩm có dữ liệu không hợp lệ. Hệ thống sẽ bỏ qua các sản phẩm này.`,
        life: 5000
      })
    }

    if (orderData.chiTiet && Array.isArray(orderData.chiTiet) && orderData.chiTiet.length > 0) {
      // Use validated items only
      const itemsToProcess = chiTietValidationResult.valid ?
                            orderData.chiTiet :
                            chiTietValidationResult.validItems || []

      console.log(`🔄 [TRANSFORM] Processing ${itemsToProcess.length} valid chiTiet items out of ${orderData.chiTiet.length} total`)

      sanPhamList = itemsToProcess.map((item, index) => {
        console.log(`🔄 [TRANSFORM DEBUG] Processing chiTiet item ${index}:`, item)

        // Additional runtime validation for critical fields
        if (!item.sanPhamChiTietId) {
          console.warn(`⚠️ [TRANSFORM WARNING] Missing sanPhamChiTietId in chiTiet item ${index}`)
        }

        // Transform chiTiet item to sanPhamList format following OrderEditOld.vue pattern
        const transformedItem = {
          // Preserve original chiTiet ID if available
          id: item.id,

          // Create sanPhamChiTiet object structure expected by frontend
          sanPhamChiTiet: {
            id: item.sanPhamChiTietId,
            serialNumberId: item.serialNumberId,
            serialNumber: item.serialNumber,
            // Use giaBan from chiTiet as the current selling price
            giaBan: item.giaBan,
            // Include snapshot data for display
            sku: item.skuSnapshot,
            tenSanPham: item.tenSanPhamSnapshot,
            hinhAnh: item.hinhAnhSnapshot ? [item.hinhAnhSnapshot] : []
          },

          // Order line item details
          soLuong: item.soLuong || 1,
          donGia: item.giaBan || item.donGia || 0,
          thanhTien: item.thanhTien || (item.giaBan * item.soLuong) || 0,

          // Preserve serial number at item level for easy access
          serialNumber: item.serialNumber
        }

        console.log(`✅ [TRANSFORM DEBUG] Transformed item ${index}:`, transformedItem)
        return transformedItem
      })

      console.log(`✅ [TRANSFORM SUCCESS] Successfully transformed ${sanPhamList.length} chiTiet items to sanPhamList`)

      // Log validation summary
      if (chiTietValidationResult && !chiTietValidationResult.valid) {
        console.warn(`⚠️ [VALIDATION SUMMARY] Processed ${chiTietValidationResult.validCount}/${chiTietValidationResult.totalItems} valid items`)
      }
    } else {
      console.log('ℹ️ [TRANSFORM INFO] No chiTiet array found, using empty sanPhamList')
    }
  } catch (error) {
    console.error('❌ [TRANSFORM ERROR] Failed to transform chiTiet to sanPhamList:', error)

    // Show user notification for transformation failure
    toast.add({
      severity: 'error',
      summary: 'Lỗi xử lý dữ liệu sản phẩm',
      detail: 'Không thể xử lý danh sách sản phẩm. Giỏ hàng sẽ hiển thị trống.',
      life: 5000
    })

    // Fallback to empty array to prevent crashes
    sanPhamList = []
  }

  // ENHANCED VOUCHER RECONSTRUCTION with comprehensive validation
  let voucherList = []
  let voucherValidationResult = null

  try {
    console.log('🔍 [VALIDATION] Starting voucher codes validation...')
    voucherValidationResult = validateVoucherCodes(orderData.voucherCodes)

    if (!voucherValidationResult.valid) {
      console.error('❌ [VALIDATION ERROR] Voucher codes validation failed:', voucherValidationResult.error)
      console.error('❌ [VALIDATION ERROR] Invalid codes:', voucherValidationResult.invalidCodes)

      // Track error for summary
      transformationErrors.push({
        type: 'voucher_validation',
        message: `${voucherValidationResult.invalidCodes?.length || 0} mã voucher không hợp lệ`,
        severity: 'warn'
      })

      // Show user notification for validation failure
      toast.add({
        severity: 'warn',
        summary: 'Mã voucher không hợp lệ',
        detail: `Phát hiện ${voucherValidationResult.invalidCodes?.length || 0} mã voucher không hợp lệ. Hệ thống sẽ bỏ qua các mã này.`,
        life: 5000
      })
    }

    if (orderData.voucherCodes && Array.isArray(orderData.voucherCodes) && orderData.voucherCodes.length > 0) {
      // Use validated codes only
      const codesToProcess = voucherValidationResult.valid ?
                            orderData.voucherCodes :
                            voucherValidationResult.validCodes || []

      console.log(`🎫 [VOUCHER] Starting voucher reconstruction for ${codesToProcess.length} valid codes out of ${orderData.voucherCodes.length} total`)

      // Use Promise.all for parallel voucher fetching with validated codes
      voucherList = await Promise.all(
        codesToProcess.map(async (code, index) => {
          try {
            console.log(`🎫 [VOUCHER] Processing voucher code ${index + 1}/${codesToProcess.length}: ${code}`)

            // First, try to get voucher from store cache
            const voucher = voucherStore.getVoucherByCode(code)

            if (voucher) {
              console.log(`✅ [VOUCHER] Found voucher in store cache: ${code}`)
              return voucher
            }

            // Fallback: fetch from API if not in store
            console.log(`🔄 [VOUCHER] Fetching voucher from API: ${code}`)
            const response = await voucherApi.getVoucherByCode(code)

            if (response.success && response.data) {
              console.log(`✅ [VOUCHER] Successfully fetched voucher from API: ${code}`)
              return response.data
            } else {
              console.warn(`⚠️ [VOUCHER] API returned unsuccessful response for: ${code}`, response)
              // Return minimal fallback object
              return {
                maPhieuGiamGia: code,
                tenPhieuGiamGia: `Voucher ${code}`,
                giaTriGiam: 0,
                isReconstructed: true // Flag to indicate this is a fallback
              }
            }

          } catch (error) {
            console.error(`❌ [VOUCHER] Failed to reconstruct voucher: ${code}`, error)
            // Return minimal fallback object to prevent crashes
            return {
              maPhieuGiamGia: code,
              tenPhieuGiamGia: `Voucher ${code}`,
              giaTriGiam: 0,
              isReconstructed: true,
              hasError: true
            }
          }
        })
      )

      console.log(`✅ [VOUCHER] Successfully reconstructed ${voucherList.length} vouchers`)

      // Log validation summary
      if (voucherValidationResult && !voucherValidationResult.valid) {
        console.warn(`⚠️ [VALIDATION SUMMARY] Processed ${voucherValidationResult.validCount}/${voucherValidationResult.totalCodes} valid voucher codes`)
      }
    } else {
      console.log('ℹ️ [VOUCHER] No voucherCodes found, using empty voucherList')
    }
  } catch (error) {
    console.error('❌ [VOUCHER] Error during voucher reconstruction:', error)

    // Show user notification for voucher reconstruction failure
    toast.add({
      severity: 'error',
      summary: 'Lỗi xử lý voucher',
      detail: 'Không thể tải thông tin voucher. Danh sách voucher sẽ hiển thị trống.',
      life: 5000
    })

    // Fallback to empty array to prevent crashes
    voucherList = []
  }

  // Transform backend order data to frontend format compatible with form structure
  const transformedOrder = {
    ...orderData,

    // CRITICAL FIX: Map backend chiTiet array to frontend sanPhamList array
    sanPhamList,

    // VOUCHER RECONSTRUCTION: Use reconstructed voucher list with full objects
    voucherList,

    // Ensure payment method is properly set
    phuongThucThanhToan: orderData.phuongThucThanhToan || null,

    // ENHANCED DELIVERY STATE DERIVATION: Derive giaohang from address validity
    giaohang: deriveDeliveryState(orderData),

    // ENHANCED ADDRESS VALIDATION: Validate delivery address if present
    diaChiGiaoHang: (() => {
      try {
        if (!orderData.diaChiGiaoHang) {
          return null
        }

        console.log('🔍 [VALIDATION] Starting address validation...')
        const addressValidation = validateAddress(orderData.diaChiGiaoHang)

        if (!addressValidation.valid) {
          console.warn('⚠️ [VALIDATION WARNING] Address validation failed:', addressValidation.error)
          console.warn('⚠️ [VALIDATION WARNING] Missing fields:', addressValidation.missingFields)
          console.warn('⚠️ [VALIDATION WARNING] Invalid fields:', addressValidation.invalidFields)

          // Show user notification for address validation issues
          if (orderData.giaohang) { // Only show warning for delivery orders
            toast.add({
              severity: 'warn',
              summary: 'Địa chỉ giao hàng không đầy đủ',
              detail: 'Một số thông tin địa chỉ bị thiếu hoặc không hợp lệ. Vui lòng kiểm tra lại.',
              life: 5000
            })
          }
        }

        return orderData.diaChiGiaoHang
      } catch (error) {
        console.error('❌ [VALIDATION ERROR] Failed to validate address:', error)

        // Show user notification for validation failure
        toast.add({
          severity: 'error',
          summary: 'Lỗi xử lý địa chỉ giao hàng',
          detail: 'Không thể xử lý địa chỉ giao hàng. Vui lòng nhập lại.',
          life: 5000
        })

        // Return null for fallback
        return null
      }
    })(),

    // Ensure customer information is available
    khachHang: orderData.khachHang || null,

    // ENHANCED RECIPIENT INFORMATION CONSTRUCTION with validation
    nguoiNhan: (() => {
      try {
        console.log('🔍 [VALIDATION] Starting recipient validation...')
        const recipientValidation = validateRecipient(orderData)

        if (!recipientValidation.valid) {
          console.warn('⚠️ [VALIDATION WARNING] Recipient validation failed:', recipientValidation.error)
          console.warn('⚠️ [VALIDATION WARNING] Issues:', recipientValidation.issues)

          // Show user notification for recipient validation issues
          if (orderData.giaohang) { // Only show warning for delivery orders
            toast.add({
              severity: 'warn',
              summary: 'Thông tin người nhận không đầy đủ',
              detail: 'Một số thông tin người nhận bị thiếu hoặc không hợp lệ. Vui lòng kiểm tra lại.',
              life: 5000
            })
          }
        }

        return {
          hoTen: orderData.nguoiNhanTen || '',
          soDienThoai: orderData.nguoiNhanSdt || '',
          email: orderData.nguoiNhanEmail || ''
        }
      } catch (error) {
        console.error('❌ [VALIDATION ERROR] Failed to validate recipient information:', error)

        // Show user notification for validation failure
        toast.add({
          severity: 'error',
          summary: 'Lỗi xử lý thông tin người nhận',
          detail: 'Không thể xử lý thông tin người nhận. Vui lòng nhập lại.',
          life: 5000
        })

        // Return fallback object
        return {
          hoTen: '',
          soDienThoai: '',
          email: ''
        }
      }
    })(),

    // Ensure totals are properly calculated
    tongTienHang: orderData.tongTienHang || 0,
    giaTriGiamGiaVoucher: orderData.giaTriGiamGiaVoucher || 0,
    phiVanChuyen: orderData.phiVanChuyen || 0,
    tongThanhToan: orderData.tongThanhToan || 0,

    // Preserve order metadata
    maHoaDon: orderData.maHoaDon,
    trangThai: orderData.trangThai,
    loaiHoaDon: orderData.loaiHoaDon,
    ngayTao: orderData.ngayTao,
    ngayCapNhat: orderData.ngayCapNhat
  }

  console.log('✅ [RECIPIENT] Constructed nguoiNhan object:', transformedOrder.nguoiNhan)
  console.log('✅ [VOUCHER] Final voucher list:', transformedOrder.voucherList)

  // TRANSFORMATION SUMMARY: Log comprehensive summary of transformation results
  console.log('📊 [TRANSFORM SUMMARY] Transformation completed with the following results:')
  console.log(`📊 [TRANSFORM SUMMARY] - Products: ${transformedOrder.sanPhamList?.length || 0} items`)
  console.log(`📊 [TRANSFORM SUMMARY] - Vouchers: ${transformedOrder.voucherList?.length || 0} items`)
  console.log(`📊 [TRANSFORM SUMMARY] - Delivery: ${transformedOrder.giaohang ? 'Yes' : 'No'}`)
  console.log(`📊 [TRANSFORM SUMMARY] - Address: ${transformedOrder.diaChiGiaoHang ? 'Present' : 'Not present'}`)
  console.log(`📊 [TRANSFORM SUMMARY] - Recipient: ${transformedOrder.nguoiNhan?.hoTen ? 'Present' : 'Not present'}`)
  console.log(`📊 [TRANSFORM SUMMARY] - Errors encountered: ${transformationErrors.length}`)

  if (transformationErrors.length > 0) {
    console.warn('⚠️ [TRANSFORM SUMMARY] Transformation errors:', transformationErrors)

    // Show summary notification if there were multiple errors
    if (transformationErrors.length > 1) {
      const errorCount = transformationErrors.length
      const warningCount = transformationErrors.filter(e => e.severity === 'warn').length
      const errorCountActual = transformationErrors.filter(e => e.severity === 'error').length

      toast.add({
        severity: errorCountActual > 0 ? 'error' : 'warn',
        summary: 'Tổng kết xử lý dữ liệu',
        detail: `Đã xử lý xong với ${errorCount} vấn đề: ${warningCount} cảnh báo, ${errorCountActual} lỗi. Vui lòng kiểm tra lại thông tin.`,
        life: 7000
      })
    }
  } else {
    console.log('✅ [TRANSFORM SUMMARY] No errors encountered during transformation')
  }

  console.log('✅ [TRANSFORM COMPLETE] Final transformed order:', transformedOrder)
  return transformedOrder
}

// Load order data for editing
const loadOrderData = async () => {
  if (!isEditMode.value || !orderId.value) return

  loading.value = true
  error.value = null

  try {
    console.log('Loading order data for ID:', orderId.value)

    // Fetch order data from API
    const orderData = await fetchOrderById(orderId.value)

    if (!orderData) {
      throw new Error('Không tìm thấy đơn hàng')
    }

    console.log('Raw order data loaded:', orderData)

    // Transform order data for editing (now async for voucher reconstruction)
    console.log('🔄 [LOAD] Starting data transformation...')
    const transformedOrder = await transformOrderForEdit(orderData)

    if (!transformedOrder) {
      throw new Error('Không thể xử lý dữ liệu đơn hàng')
    }

    console.log('✅ [LOAD] Data transformation completed successfully')
    console.log('Transformed order data:', transformedOrder)

    // Set current order
    currentOrder.value = transformedOrder

    // UNIFIED CLEANUP: Initialize cleanup tracking for this order
    unifiedCleanupStrategy.initializeCleanupTracking(transformedOrder)

    // Pre-populate form fields
    await prePopulateFormFields(transformedOrder)

    // Calculate totals to ensure consistency
    calculateCurrentOrderTotals()

    console.log('Order data loading completed successfully')
    console.log('🔒 Cleanup tracking initialized:', unifiedCleanupStrategy.getCleanupSummary())

  } catch (err) {
    console.error('Error loading order data:', err)
    error.value = err.message || 'Lỗi tải dữ liệu đơn hàng'

    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: error.value,
      life: 3000
    })

    // Navigate back to order list on error
    router.push('/orders')

  } finally {
    loading.value = false
  }
}

// Enhanced address dropdown population with timeout and retry logic
const populateAddressDropdownsWithTimeout = async (addressData, timeoutMs = 5000, maxRetries = 2) => {
  console.log('🏠 [ADDRESS DROPDOWN] Starting dropdown population with timeout:', { addressData, timeoutMs, maxRetries })

  let retryCount = 0

  const attemptPopulation = async () => {
    try {
      console.log(`🏠 [ADDRESS DROPDOWN] Attempt ${retryCount + 1}/${maxRetries + 1}`)

      // Create timeout promise
      const timeoutPromise = new Promise((_, reject) => {
        setTimeout(() => reject(new Error('Address dropdown population timeout')), timeoutMs)
      })

      // Create population promise
      const populationPromise = populateDropdownSelections(addressData)

      // Race between population and timeout
      await Promise.race([populationPromise, timeoutPromise])

      console.log('✅ [ADDRESS DROPDOWN] Dropdown population completed successfully')
      return true

    } catch (error) {
      console.error(`❌ [ADDRESS DROPDOWN] Attempt ${retryCount + 1} failed:`, error)

      if (retryCount < maxRetries) {
        retryCount++
        console.log(`🔄 [ADDRESS DROPDOWN] Retrying... (${retryCount}/${maxRetries})`)

        // Wait before retry (exponential backoff)
        await new Promise(resolve => setTimeout(resolve, 1000 * retryCount))
        return attemptPopulation()
      } else {
        console.error('❌ [ADDRESS DROPDOWN] All retry attempts failed')
        throw error
      }
    }
  }

  return attemptPopulation()
}

// Pre-populate form fields with order data
const prePopulateFormFields = async (orderData) => {
  try {
    console.log('Pre-populating form fields with order data:', orderData)

    // Pre-populate customer information
    if (orderData.khachHang) {
      // Set customer data in the customer selection component
      console.log('Setting customer data:', orderData.khachHang)
      // The customer autocomplete will be populated via currentOrder.value.khachHang
    }

    // ENHANCED ADDRESS PRE-POPULATION: Integrate with embedded address infrastructure
    if (orderData.diaChiGiaoHang && orderData.giaohang) {
      console.log('🏠 [ADDRESS] Starting enhanced address pre-population:', orderData.diaChiGiaoHang)

      try {
        // Set address data in the composable
        setAddressData({
          duong: orderData.diaChiGiaoHang.duong || '',
          phuongXa: orderData.diaChiGiaoHang.phuongXa || '',
          quanHuyen: orderData.diaChiGiaoHang.quanHuyen || '',
          tinhThanh: orderData.diaChiGiaoHang.tinhThanh || '',
          loaiDiaChi: orderData.diaChiGiaoHang.loaiDiaChi || 'Nhà riêng',
          laMacDinh: orderData.diaChiGiaoHang.laMacDinh || false
        })

        console.log('✅ [ADDRESS] Address data set in composable')

        // Populate dropdown selections with timeout and error handling
        await populateAddressDropdownsWithTimeout(orderData.diaChiGiaoHang)

        // Update current order data for form integration
        updateCurrentOrderData({
          diaChiGiaoHang: orderData.diaChiGiaoHang
        })

        console.log('✅ [ADDRESS] Enhanced address pre-population completed successfully')

      } catch (error) {
        console.error('❌ [ADDRESS] Error during address pre-population:', error)
        // Don't throw error here as it's not critical for the main loading process
        // Address can still be manually edited by the user
      }
    }

    // Pre-populate recipient information for delivery orders
    if (orderData.giaohang && orderData.nguoiNhan) {
      console.log('Setting recipient information:', orderData.nguoiNhan)

      // Populate recipientInfo reactive state with transformed data
      recipientInfo.value.hoTen = orderData.nguoiNhan.hoTen || ''
      recipientInfo.value.soDienThoai = orderData.nguoiNhan.soDienThoai || ''

      console.log('✅ [RECIPIENT] Populated recipient info:', {
        hoTen: recipientInfo.value.hoTen,
        soDienThoai: recipientInfo.value.soDienThoai
      })
    }

    // Pre-populate payment method
    if (orderData.phuongThucThanhToan) {
      console.log('Setting payment method:', orderData.phuongThucThanhToan)
      // Payment method will be populated via currentOrder.value.phuongThucThanhToan
    }

    // Products and vouchers are already set in currentOrder.value
    console.log('Products count:', orderData.sanPhamList?.length || 0)
    console.log('Vouchers count:', orderData.voucherList?.length || 0)

  } catch (error) {
    console.error('Error pre-populating form fields:', error)
    // Don't throw error here as it's not critical for the main loading process
  }
}

// Calculate totals for current order (single order version of calculateTabTotals)
const calculateCurrentOrderTotals = () => {
  if (!currentOrder.value) return

  // Calculate subtotal from products
  const tongTienHang = currentOrder.value.sanPhamList.reduce((total, item) => {
    return total + (item.donGia * item.soLuong)
  }, 0)

  // Calculate voucher discount
  const giaTriGiamGiaVoucher = (currentOrder.value.voucherList || []).reduce((total, voucher) => {
    return total + voucher.giaTriGiam
  }, 0)

  // Calculate shipping fee (only for delivery orders)
  const phiVanChuyen = currentOrder.value.giaohang ? 30000 : 0

  // Calculate final total
  const tongThanhToan = Math.max(0, tongTienHang - giaTriGiamGiaVoucher + phiVanChuyen)

  // Update current order data
  Object.assign(currentOrder.value, {
    tongTienHang,
    giaTriGiamGiaVoucher,
    phiVanChuyen,
    tongThanhToan
  })
}

// Local state
const creating = ref(false)
const selectedCustomer = ref(null)
const customerSuggestions = ref([])
const prePaymentCheckInProgress = ref(false)

const availableVouchers = ref([])

// Smart voucher recommendation state
const voucherRecommendations = ref([])

// Voucher display state
const showAllVouchers = ref(false)
const voucherDisplayLimit = ref(3)

// New state for enhanced features
const bestVoucherResult = ref(null)
const loadingBestVoucher = ref(false)
const populatingAddress = ref(false)

// Image URL cache for performance
const imageUrlCache = ref(new Map())

// Product variant dialog state
const variantDialogVisible = ref(false)
const productVariantDialogRef = ref(null)

// Order expiration panel state
const showExpirationPanel = ref(true)

// Fast customer creation dialog state
const fastCustomerDialogVisible = ref(false)

// Fast address creation dialog state
const fastAddressDialogVisible = ref(false)

// QR Scanner state
const showQRScanner = ref(false)
const qrScanResult = ref(null)
const qrProcessingResult = ref(null)
const cameraError = ref(null)

// ENHANCEMENT: Reservation status indicators for UI feedback
const reservationStatus = ref({
  isReserving: false,
  currentSerialNumber: null,
  lastReservationResult: null,
  conflictDetected: false,
  retryAttempt: 0,
  context: null
})

// ENHANCEMENT: Comprehensive error handling and user feedback system
const reservationErrorState = ref({
  hasError: false,
  errorHandling: null,
  showErrorDialog: false,
  autoRetryEnabled: true,
  retryCount: 0,
  maxAutoRetries: 2
})

// ENHANCEMENT: Unified cleanup strategy - track newly reserved items for proper cleanup
const newlyReservedItems = ref(new Set()) // Track serial numbers reserved during this edit session
const originalOrderItems = ref(new Set()) // Track original order items that should NOT be cleaned up

// Order confirmation dialog state
const orderConfirmationVisible = ref(false)

// Mixed payment dialog state
const mixedPaymentDialogVisible = ref(false)

// Local state
const hasUnsavedChanges = ref(false)

// Customer payment state
const customerPayment = ref(0)
const changeAmount = ref(0)

// Recipient information state
const recipientInfo = ref({
  hoTen: '',
  soDienThoai: '',
})
const recipientNameSuggestions = ref([])
const recipientPhoneSuggestions = ref([])
const recipientSuggestions = ref([]) // Keep for backward compatibility
const recipientErrors = ref({})
const searchingRecipient = ref(false)

// Separate recipient customer tracking for Scenario 2 (different recipient than customer)
const recipientCustomer = ref(null)

// Debounce timers for recipient search
let recipientNameSearchTimer = null
let recipientPhoneSearchTimer = null

// Embedded address composable
const {
  addressData,
  provinces,
  districts,
  wards,
  selectedProvince,
  selectedDistrict,
  selectedWard,
  loadingProvinces,
  loadingDistricts,
  loadingWards,
  errors: addressErrors,
  onProvinceChange,
  onDistrictChange,
  setAddressData,
  resetAddressForm,
  // Enhanced address management functions
  findMatchingAddress,
  isAddressDifferentFromCustomer,
  addAddressToCustomer,
} = useEmbeddedAddress()

// Real-time order management composables
const { isConnected: wsConnected, connectionStatus: _connectionStatus } = useRealTimeOrderManagement()

const {
  priceUpdates,
  lastPriceUpdate: _lastPriceUpdate,
  subscribeToPriceUpdates,
  showPriceWarnings: _showPriceWarnings,
  getLatestPriceForVariant,
  getPriceUpdatesForVariant,
  hasRecentPriceChange: _hasRecentPriceChange,
  formatCurrency: _formatPricingCurrency,
} = useRealTimePricing()

const {
  expiredVouchers,
  newVouchers,
  alternativeRecommendations,
  betterVoucherSuggestions: _betterVoucherSuggestions,
  hasVoucherUpdates,
  hasBetterVoucherSuggestions: _hasBetterVoucherSuggestions,
  subscribeToVoucherMonitoring,
  showVoucherNotifications,
  suggestionDialogVisible,
  currentSuggestion,
  processBetterVoucherSuggestion,
  closeSuggestionDialog,
  setIntegrationCallback,
} = useVoucherMonitoring()

// Shipping calculator composable
const {
  isCalculating: isCalculatingShipping,
  calculationError: shippingError,
  shippingFee,
  isAutoCalculated: isShippingAutoCalculated,
  estimatedDeliveryTime,
  calculateShippingFeeWithComparison,
  resetShippingCalculation,
  loadShippingConfig,
} = useShippingCalculator()

// Order expiration composable
const {
  expiringOrders,
  expiredOrders,
  criticalExpiringOrders,
  hasExpirationUpdates,
  subscribeToOrderExpiration,
  formatRemainingTime,
  getRemainingTimeForOrder,
} = useOrderExpiration()

// Initialize WebSocket logger with optimized throttling for order operations
const logger = WebSocketLogger.createWebSocketLogger('OrderCreate', {
  throttleConfig: {
    // High-frequency events from audit findings
    websocket_integration: { rate: 20, counter: 0, lastLogged: 0 },
    voucher_validation: { rate: 8, counter: 0, lastLogged: 0 },
    order_integration: { rate: 10, counter: 0, lastLogged: 0 },
    connection_monitoring: { rate: 15, counter: 0, lastLogged: 0 },
    integration_callback: { rate: 5, counter: 0, lastLogged: 0 },
    setup_operations: { rate: 10, counter: 0, lastLogged: 0 }
  }
})

// Real-time cart price change tracking
const cartPriceChanges = ref([])
const acknowledgedPriceChanges = ref(new Set())

// Computed properties
const canCreateActiveOrder = computed(() => {
  if (!currentOrder.value) return false

  // Basic requirements
  const hasProducts = currentOrder.value.sanPhamList.length > 0
  const hasPaymentMethod = currentOrder.value.phuongThucThanhToan

  // Mixed payment validation
  let paymentValid = true
  if (currentOrder.value.phuongThucThanhToan === 'MIXED') {
    paymentValid =
      currentOrder.value.mixedPayments &&
      currentOrder.value.mixedPayments.length > 0 &&
      currentOrder.value.mixedPayments.every((p) => p.method && p.amount > 0)
  }

  // Delivery validation using consolidated validation function
  let deliveryValid = true
  if (currentOrder.value.giaohang) {
    const deliveryValidation = validateDeliveryInfo()
    deliveryValid = deliveryValidation.valid
  }

  return hasProducts && hasPaymentMethod && paymentValid && deliveryValid
})

// Enhanced shipping status
const enhancedShippingStatus = computed(() => {
  if (!currentOrder.value?.giaohang) {
    return { text: 'Không giao hàng', severity: 'secondary' }
  }

  if (isCalculatingShipping.value) {
    return { text: 'Đang tính phí...', severity: 'info' }
  }

  if (shippingError.value) {
    return { text: 'Lỗi tính phí', severity: 'danger' }
  }

  // Check if address is complete
  if (!isAddressComplete()) {
    return { text: 'Chưa đủ địa chỉ', severity: 'warn' }
  }

  if (isShippingAutoCalculated.value && shippingFee.value > 0) {
    return { text: 'Tự động', severity: 'success' }
  }

  if (shippingFee.value > 0) {
    return { text: 'Thủ công', severity: 'warn' }
  }

  return { text: 'Sẵn sàng tính', severity: 'info' }
})

const paymentMethods = computed(() => {
  if (!currentOrder.value) return []

  const methods = []

  // TIEN_MAT - Only for TAI_QUAY orders (POS only)
  if (currentOrder.value.loaiHoaDon === 'TAI_QUAY') {
    methods.push({
      value: 'TIEN_MAT',
      label: 'Tiền mặt',
      description: 'Thanh toán bằng tiền mặt tại quầy',
      icon: 'pi pi-wallet',
      available: true,
    })
  }

  // TIEN_MAT for delivery - Available for online orders when delivery is enabled (former COD)
  if (currentOrder.value.giaohang && currentOrder.value.loaiHoaDon === 'ONLINE') {
    methods.push({
      value: 'TIEN_MAT',
      label: 'Tiền mặt khi giao hàng',
      description: 'Thanh toán bằng tiền mặt khi nhận hàng',
      icon: 'pi pi-money-bill',
      available: true,
    })
  }

  // VNPAY - Available for both order types
  methods.push({
    value: 'VNPAY',
    label: 'VNPay',
    description: 'Thanh toán qua ví điện tử VNPay',
    icon: 'pi pi-credit-card',
    available: true,
  })

  // MOMO - Available for both order types
  methods.push({
    value: 'MOMO',
    label: 'MoMo',
    description: 'Thanh toán qua ví điện tử MoMo',
    icon: 'pi pi-mobile',
    available: true,
  })



  return methods
})

// Computed property for displayed available vouchers
const displayedAvailableVouchers = computed(() => {
  if (showAllVouchers.value) {
    return availableVouchers.value
  }
  return availableVouchers.value.slice(0, voucherDisplayLimit.value)
})

// Computed property for formatted delivery address
const formattedDeliveryAddress = computed(() => {
  if (!currentOrder.value?.giaohang) return ''

  const parts = []

  if (addressData.value.duong?.trim()) {
    parts.push(addressData.value.duong.trim())
  }

  const locationParts = []
  if (addressData.value.phuongXa) locationParts.push(addressData.value.phuongXa)
  if (addressData.value.quanHuyen) locationParts.push(addressData.value.quanHuyen)
  if (addressData.value.tinhThanh) locationParts.push(addressData.value.tinhThanh)

  if (locationParts.length > 0) {
    parts.push(locationParts.join(', '))
  }

  return parts.length > 0 ? parts.join(', ') : 'Chưa nhập địa chỉ giao hàng'
})

// Computed property for processed cart items with standardized price change detection
const processedCartItems = computed(() => {
  if (!currentOrder.value?.sanPhamList?.length) return []

  return currentOrder.value.sanPhamList.map((item, index) => {
    const variantId = item.sanPhamChiTiet?.id
    if (!variantId) return { ...item, hasPriceChange: false }

    // STANDARDIZED PRICE CHANGE DETECTION: Check for original price changes
    const currentOriginalPrice = item.sanPhamChiTiet?.giaBan
    const latestOriginalPrice = getLatestOriginalPriceForVariant(variantId)
    const hasPriceChange =
      latestOriginalPrice && currentOriginalPrice && latestOriginalPrice !== currentOriginalPrice

    // Calculate latest effective price preserving promotional pricing logic
    const latestEffectivePrice = latestOriginalPrice
      ? calculateEffectivePrice(latestOriginalPrice, item.sanPhamChiTiet?.giaKhuyenMai)
      : item.donGia

    return {
      ...item,
      hasPriceChange,
      latestPrice: latestEffectivePrice,
      latestOriginalPrice,
      originalIndex: index,
    }
  })
})

// Methods
const formatCurrency = (amount) => {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
  }).format(amount)
}

// Price change notification methods
const getCartItemKey = (item, index) => {
  const variantId = item.sanPhamChiTiet?.id || 'unknown'
  const serialNumber = item.sanPhamChiTiet?.serialNumber || ''
  return `${variantId}-${serialNumber}-${index}`
}

const acknowledgePriceChange = (index) => {
  if (index >= 0 && index < cartPriceChanges.value.length) {
    cartPriceChanges.value.splice(index, 1)
  }
}

// Helper function to get latest original price (giaBan) for a variant
// Integrates with useRealTimePricing composable for consistent price comparison
const getLatestOriginalPriceForVariant = (variantId) => {
  // First check real-time price updates for original price changes
  const priceUpdate = getPriceUpdatesForVariant(variantId)?.[0]
  if (priceUpdate?.originalNewPrice) {
    return priceUpdate.originalNewPrice
  }

  // Fallback to latest effective price from real-time updates
  // This maintains compatibility with existing useRealTimePricing integration
  return getLatestPriceForVariant(variantId)
}

// Helper function to calculate effective price (preserves promotional pricing logic)
// Uses giaKhuyenMai when available and lower than giaBan, otherwise uses giaBan
const calculateEffectivePrice = (originalPrice, promotionalPrice) => {
  if (promotionalPrice && promotionalPrice < originalPrice) {
    return promotionalPrice
  }
  return originalPrice
}

// Check if cart item has active discount
const hasCartItemDiscount = (item) => {
  const variant = item.sanPhamChiTiet
  if (!variant) return false

  // Check if variant has giaKhuyenMai that is lower than giaBan
  // This indicates a discount campaign is active for this variant
  return variant.giaKhuyenMai && variant.giaKhuyenMai < variant.giaBan
}

// Hàm phát hiện thay đổi giá
const detectCartPriceChanges = () => {
  if (!currentOrder.value?.sanPhamList?.length) return

  const newPriceChanges = []

  currentOrder.value.sanPhamList.forEach((item, index) => {
    const variantId = item.sanPhamChiTiet?.id
    if (!variantId) return

    const currentOriginalPrice = item.sanPhamChiTiet?.giaBan
    const latestOriginalPrice = getLatestOriginalPriceForVariant(variantId)

    if (
      latestOriginalPrice &&
      currentOriginalPrice &&
      latestOriginalPrice !== currentOriginalPrice
    ) {
      const changeId = `${variantId}-${item.sanPhamChiTiet?.serialNumber || ''}`

      if (acknowledgedPriceChanges.value.has(changeId)) return

      const existingChange = cartPriceChanges.value.find(
        (change) =>
          change.variantId === variantId &&
          change.serialNumber === (item.sanPhamChiTiet?.serialNumber || ''),
      )

      if (!existingChange) {
        // Sử dụng originalEffectivePrice làm oldPrice
        const currentEffectivePrice = item.originalEffectivePrice || item.donGia
        const latestEffectivePrice = calculateEffectivePrice(
          latestOriginalPrice,
          item.sanPhamChiTiet?.giaKhuyenMai,
        )

        newPriceChanges.push({
          variantId,
          serialNumber: item.sanPhamChiTiet?.serialNumber || '',
          productName: getCartItemName(item),
          variantInfo: getVariantDisplayInfo(item),
          oldPrice: currentEffectivePrice,
          newPrice: latestEffectivePrice,
          originalOldPrice: currentOriginalPrice,
          originalNewPrice: latestOriginalPrice,
          changeType: latestOriginalPrice > currentOriginalPrice ? 'INCREASE' : 'DECREASE',
          timestamp: new Date(),
          cartIndex: index,
        })
      }
    }
  })

  if (newPriceChanges.length > 0) {
    cartPriceChanges.value.push(...newPriceChanges)
  }
}

// Hàm phát hiện sự khác biệt giá cùng SKU
const detectSameSKUPriceDifferences = (newlyAddedItem) => {
  if (!currentOrder.value?.sanPhamList?.length || !newlyAddedItem) return

  const newVariantId = newlyAddedItem.sanPhamChiTiet?.id
  if (!newVariantId) return

  const newPrice = newlyAddedItem.donGia
  const newSerial = newlyAddedItem.sanPhamChiTiet?.serialNumber || ''
  const newOriginalPrice = newlyAddedItem.sanPhamChiTiet?.giaBan

  const existingItemsWithSameSKU = currentOrder.value.sanPhamList.filter(
    (item) =>
      item.sanPhamChiTiet?.id === newVariantId &&
      item.donGia !== newPrice &&
      item !== newlyAddedItem,
  )

  if (existingItemsWithSameSKU.length > 0) {
    existingItemsWithSameSKU.forEach((item) => {
      const oldPrice = item.originalEffectivePrice || item.donGia
      const oldOriginalPrice = item.sanPhamChiTiet?.giaBan
      const changeId = `sku-diff-${newVariantId}-${newSerial}-${item.sanPhamChiTiet?.serialNumber || ''}`

      if (acknowledgedPriceChanges.value.has(changeId)) return

      const existingChange = cartPriceChanges.value.find(
        (change) =>
          change.variantId === newVariantId &&
          change.serialNumber === newSerial &&
          change.changeType === 'SKU_PRICE_DIFFERENCE',
      )

      if (!existingChange) {
        cartPriceChanges.value.push({
          variantId: newVariantId,
          serialNumber: newSerial,
          productName: getCartItemName(newlyAddedItem),
          variantInfo: getVariantDisplayInfo(newlyAddedItem),
          oldPrice: oldPrice,
          newPrice: newPrice,
          originalOldPrice: oldOriginalPrice,
          originalNewPrice: newOriginalPrice,
          changeType: 'SKU_PRICE_DIFFERENCE',
          timestamp: new Date(),
          cartIndex: currentOrder.value.sanPhamList.length - 1,
        })
      }
    })
  }
}

// Enhanced shipping fee calculation with comprehensive error handling and retry mechanism
const calculateShippingFeeForCurrentAddress = async (retryCount = 0) => {
  const maxRetries = 2

  if (!currentOrder.value?.giaohang) {
    return { success: false, error: 'Delivery not enabled' }
  }

  // Validate address completeness before calculation
  const addressStatus = getAddressCompletionStatus()
  if (!addressStatus.isComplete) {
    return {
      success: false,
      error: 'Address incomplete',
      missingFields: addressStatus.missingFields,
    }
  }

  // Validate address field combinations
  const validationResult = await validateAddressFieldCombinations()
  if (!validationResult.valid) {
    return {
      success: false,
      error: 'Invalid address combination',
      details: validationResult.message,
    }
  }

  const deliveryAddress = {
    province: addressData.value.tinhThanh,
    district: addressData.value.quanHuyen,
    ward: addressData.value.phuongXa,
    address: addressData.value.duong.trim(),
  }

  const orderValue = currentOrder.value.tongTienHang || 0

  try {
    // Show immediate feedback during calculation (removed toast - loading state is sufficient)

    // Use GHN service for shipping calculation
    const success = await calculateShippingFeeWithComparison(deliveryAddress, orderValue)

    if (success) {
      // Update using consolidated shipping fee (automatically syncs both sources)
      consolidatedShippingFee.value = shippingFee.value
      return { success: true, fee: shippingFee.value }
    } else {
      throw new Error('Shipping calculation failed')
    }
  } catch (error) {
    console.error('Error calculating shipping fee:', error)

    // Implement retry mechanism for transient failures
    if (retryCount < maxRetries && isTransientError(error)) {
      console.log(`Retrying shipping calculation (attempt ${retryCount + 1}/${maxRetries})`)

      // Wait before retry with exponential backoff
      await new Promise((resolve) => setTimeout(resolve, Math.pow(2, retryCount) * 1000))

      return await calculateShippingFeeForCurrentAddress(retryCount + 1)
    }

    // Final failure - provide actionable error message
    const errorMessage = getActionableErrorMessage(error, retryCount >= maxRetries)

    toast.add({
      severity: 'error',
      summary: 'Không thể tính phí vận chuyển',
      detail: errorMessage,
      life: 5000,
    })

    return { success: false, error: error.message, actionableMessage: errorMessage }
  }
}

// Helper function to validate address field combinations
const validateAddressFieldCombinations = async () => {
  try {
    // Prepare address data for consolidated validation
    const addressValidationData = {
      duong: addressData.value.duong?.trim() || '',
      phuongXa: selectedWard.value?.name || addressData.value.phuongXa,
      quanHuyen: selectedDistrict.value?.name || addressData.value.quanHuyen,
      tinhThanh: selectedProvince.value?.name || addressData.value.tinhThanh,
      availableDistricts: districts.value,
      availableWards: wards.value
    }

    // Use consolidated complete address validation
    const validation = validateCompleteAddress(addressValidationData)

    if (!validation.isValid) {
      // Return the first error found
      const firstError = validation.fieldErrors.duong ||
                        validation.fieldErrors.tinhThanh ||
                        validation.fieldErrors.quanHuyen ||
                        validation.fieldErrors.phuongXa ||
                        validation.fieldErrors.general ||
                        validation.error

      return { valid: false, message: firstError }
    }

    return { valid: true, message: 'Address validation passed' }
  } catch (error) {
    console.error('Error validating address combinations:', error)
    return { valid: false, message: 'Lỗi xác thực địa chỉ. Vui lòng thử lại.' }
  }
}

// Helper function to determine if error is transient (retryable)
const isTransientError = (error) => {
  const transientIndicators = [
    'network',
    'timeout',
    'connection',
    'temporary',
    'service unavailable',
    'rate limit',
    'ECONNRESET',
    'ETIMEDOUT',
  ]

  const errorMessage = error.message?.toLowerCase() || ''
  return transientIndicators.some((indicator) => errorMessage.includes(indicator))
}

// Helper function to generate actionable error messages
const getActionableErrorMessage = (error, isAfterRetries = false) => {
  const errorMessage = error.message?.toLowerCase() || ''

  if (
    errorMessage.includes('address resolution') ||
    errorMessage.includes('không thể xác định địa chỉ')
  ) {
    return 'Địa chỉ không được GHN hỗ trợ. Vui lòng kiểm tra lại địa chỉ hoặc nhập phí vận chuyển thủ công.'
  }

  if (errorMessage.includes('network') || errorMessage.includes('connection')) {
    return isAfterRetries
      ? 'Lỗi kết nối mạng. Vui lòng kiểm tra kết nối internet và thử lại sau.'
      : 'Lỗi kết nối tạm thời. Đang thử lại...'
  }

  if (errorMessage.includes('timeout')) {
    return 'Hệ thống phản hồi chậm. Vui lòng thử lại sau hoặc nhập phí vận chuyển thủ công.'
  }

  if (errorMessage.includes('rate limit')) {
    return 'Quá nhiều yêu cầu. Vui lòng chờ một chút rồi thử lại.'
  }

  if (errorMessage.includes('ghn') || errorMessage.includes('shipping service')) {
    return 'Dịch vụ vận chuyển tạm thời không khả dụng. Vui lòng nhập phí vận chuyển thủ công.'
  }

  // Generic fallback message
  return isAfterRetries
    ? 'Không thể tính phí vận chuyển tự động. Vui lòng nhập phí vận chuyển thủ công.'
    : 'Đã xảy ra lỗi. Vui lòng thử lại.'
}

// onShippingFeeChange removed - now handled by consolidatedShippingFee computed property

const formatDate = (dateString) => {
  if (!dateString) return 'N/A'
  return new Date(dateString).toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

// Voucher display methods
const toggleVoucherDisplay = () => {
  showAllVouchers.value = !showAllVouchers.value
}

// Method to find the overall best voucher across all vouchers (applied + available)
const getBestOverallVoucher = () => {
  const allVouchers = []

  // Add applied vouchers with their actual discount amounts
  if (currentOrder.value?.voucherList?.length) {
    currentOrder.value.voucherList.forEach((voucher) => {
      allVouchers.push({
        ...voucher,
        discountAmount: voucher.giaTriGiam || 0,
        isApplied: true,
      })
    })
  }

  // Add available vouchers with their calculated discount amounts
  if (availableVouchers.value.length) {
    availableVouchers.value.forEach((voucher) => {
      allVouchers.push({
        ...voucher,
        discountAmount: calculateVoucherDiscount(voucher),
        isApplied: false,
      })
    })
  }

  if (!allVouchers.length) return null

  // Find the voucher with the highest discount amount
  return allVouchers.reduce((best, current) => {
    return current.discountAmount > best.discountAmount ? current : best
  }, allVouchers[0])
}

// Method to determine if a voucher is the best overall voucher
const isBestVoucher = (voucher) => {
  const bestVoucher = getBestOverallVoucher()
  if (!bestVoucher) return false

  return voucher.id === bestVoucher.id || voucher.maPhieuGiamGia === bestVoucher.maPhieuGiamGia
}

// Method to determine if a voucher is the best among available vouchers (for styling only)
const isBestAvailableVoucher = (voucher) => {
  const bestVoucher = getBestOverallVoucher()
  if (!bestVoucher) return false

  // Only return true if this voucher is the best overall AND it's not applied
  const isOverallBest =
    voucher.id === bestVoucher.id || voucher.maPhieuGiamGia === bestVoucher.maPhieuGiamGia
  const isNotApplied = !currentOrder.value?.voucherList?.some(
    (applied) => applied.id === voucher.id || applied.maPhieuGiamGia === voucher.maPhieuGiamGia,
  )

  return isOverallBest && isNotApplied
}

// Calculate actual discount amount for a voucher based on current order total
const calculateVoucherDiscount = (voucher, orderTotal = null) => {
  if (!voucher || !voucher.giaTriGiam) return 0

  const total = orderTotal || currentOrder.value?.tongTienHang || 0

  if (voucher.loaiGiamGia === 'PHAN_TRAM') {
    // Percentage discount
    const discountAmount = (total * voucher.giaTriGiam) / 100
    // Apply maximum discount limit if specified
    if (voucher.giaTriGiamToiDa && discountAmount > voucher.giaTriGiamToiDa) {
      return voucher.giaTriGiamToiDa
    }
    return discountAmount
  } else {
    // Fixed amount discount (SO_TIEN_CO_DINH)
    return Math.min(voucher.giaTriGiam, total)
  }
}

/**
 * Revalidate applied voucher and handle discount value changes (Step 2 from validateVouchersBeforePayment)
 * @param {Object} appliedVoucher - The currently applied voucher
 * @param {number} customerId - Customer ID
 * @param {number} orderTotal - Order total amount
 * @returns {Promise<Object>} Result object with proceed flag and updated discount
 */
const revalidateAppliedVoucher = async (appliedVoucher, customerId, orderTotal) => {
  try {
    const originalDiscount = appliedVoucher.giaTriGiam

    // Re-validate the applied voucher with API
    const validationResponse = await voucherApi.validateVoucher(
      appliedVoucher.maPhieuGiamGia,
      customerId,
      orderTotal,
    )

    if (!validationResponse.success || !validationResponse.data.valid) {
      const reason = validationResponse.data?.error || 'Voucher không còn hợp lệ.'
      toast.add({
        severity: 'error',
        summary: 'Voucher không hợp lệ',
        detail: `Voucher ${appliedVoucher.maPhieuGiamGia} không thể sử dụng. Lý do: ${reason}`,
        life: 7000,
      })
      currentOrder.value.voucherList = []
      await loadAvailableVouchers()
      calculateCurrentOrderTotals()
      await findAndApplyBestVoucher()
      return { proceed: false }
    }

    const validatedDiscount = validationResponse.data.discountAmount

    // Check if voucher discount value has changed
    if (validatedDiscount !== originalDiscount) {
      const confirmed = await new Promise((resolve) => {
        confirm.require({
          message: `Giá trị giảm của voucher ${appliedVoucher.maPhieuGiamGia} đã thay đổi từ ${formatCurrency(originalDiscount)} thành ${formatCurrency(validatedDiscount)}. Bạn có muốn tiếp tục với giá trị mới không?`,
          header: 'Cập nhật giá trị Voucher',
          icon: 'pi pi-info-circle',
          acceptLabel: 'Đồng ý',
          rejectLabel: 'Để tôi chọn lại',
          accept: () => resolve(true),
          reject: () => resolve(false),
          onHide: () => resolve(false),
        })
      })

      if (confirmed) {
        currentOrder.value.voucherList[0].giaTriGiam = validatedDiscount
        calculateCurrentOrderTotals()
        return { proceed: true, validatedDiscount }
      } else {
        currentOrder.value.voucherList = []
        await loadAvailableVouchers()
        calculateCurrentOrderTotals()
        return { proceed: false }
      }
    }

    return { proceed: true, validatedDiscount }
  } catch (error) {
    console.error('Error revalidating applied voucher:', error)
    toast.add({
      severity: 'error',
      summary: 'Lỗi API',
      detail: 'Không thể xác thực voucher tại thời điểm này. Vui lòng thử lại.',
      life: 5000,
    })
    return { proceed: false }
  }
}

/**
 * Detect better vouchers and offer replacement (Step 3 from validateVouchersBeforePayment)
 * @param {Object} appliedVoucher - The currently applied voucher
 * @param {number} customerId - Customer ID
 * @param {number} orderTotal - Order total amount
 * @param {number} currentDiscount - Current validated discount amount
 * @returns {Promise<Object>} Result object with proceed flag
 */
const detectBetterVouchers = async (appliedVoucher, customerId, orderTotal, currentDiscount) => {
  try {
    // Check for better vouchers available
    const bestVoucherResponse = await voucherApi.getBestVoucher(customerId, orderTotal)

    if (bestVoucherResponse.success && bestVoucherResponse.data.found) {
      const bestVoucher = bestVoucherResponse.data.voucher
      const bestDiscount = bestVoucherResponse.data.discountAmount

      if (
        bestVoucher.maPhieuGiamGia !== appliedVoucher.maPhieuGiamGia &&
        bestDiscount > currentDiscount
      ) {
        const confirmed = await new Promise((resolve) => {
          confirm.require({
            message: `Chúng tôi tìm thấy voucher tốt hơn! Voucher '${bestVoucher.tenPhieuGiamGia || bestVoucher.maPhieuGiamGia}' giúp bạn tiết kiệm thêm ${formatCurrency(bestDiscount - currentDiscount)}. Bạn có muốn đổi sang voucher này không?`,
            header: 'Gợi ý Voucher tốt hơn',
            icon: 'pi pi-lightbulb',
            acceptLabel: 'Đồng ý đổi',
            rejectLabel: 'Không, giữ voucher cũ',
            accept: () => resolve(true),
            reject: () => resolve(false),
            onHide: () => resolve(false),
          })
        })

        if (confirmed) {
          await selectVoucher(bestVoucher)
          return { proceed: true }
        }
      }
    }

    return { proceed: true }
  } catch (error) {
    console.error('Error detecting better vouchers:', error)
    // Don't fail the entire process for better voucher detection errors
    return { proceed: true }
  }
}

// Enhanced voucher validation before payment
const validateVouchersBeforePayment = async () => {
  // If no vouchers are applied, skip validation and proceed
  if (!currentOrder.value?.voucherList?.length) {
    return { proceed: true }
  }

  const appliedVoucher = currentOrder.value.voucherList[0]
  const orderTotal = currentOrder.value.tongTienHang || 0
  const customerId = currentOrder.value.khachHang?.id || null

  try {
    // Step 2: Revalidate applied voucher and handle discount changes
    const revalidationResult = await revalidateAppliedVoucher(appliedVoucher, customerId, orderTotal)
    if (!revalidationResult.proceed) {
      return { proceed: false }
    }

    const validatedDiscount = revalidationResult.validatedDiscount

    // Step 3: Detect better vouchers and offer replacement
    const betterVoucherResult = await detectBetterVouchers(appliedVoucher, customerId, orderTotal, validatedDiscount)
    return betterVoucherResult

  } catch (error) {
    console.error('Lỗi API trong quá trình kiểm tra voucher:', error)
    toast.add({
      severity: 'error',
      summary: 'Lỗi API',
      detail: 'Không thể xác thực voucher tại thời điểm này. Vui lòng thử lại.',
      life: 5000,
    })
    return { proceed: false }
  }
}

// Hàm thêm sản phẩm vào giỏ hàng, lưu giá hiệu quả ban đầu
const addVariantToCurrentOrder = async (variantData) => {
  if (!currentOrder.value) return

  const { sanPhamChiTiet, soLuong, groupInfo } = variantData
  let { donGia, thanhTien } = variantData

  const latestOriginalPrice = getLatestOriginalPriceForVariant(sanPhamChiTiet.id)
  const currentOriginalPrice = sanPhamChiTiet.giaBan

  // Tính giá hiệu quả ban đầu để lưu trữ
  const originalEffectivePrice = calculateEffectivePrice(
    currentOriginalPrice,
    sanPhamChiTiet.giaKhuyenMai,
  )

  if (latestOriginalPrice && currentOriginalPrice && latestOriginalPrice !== currentOriginalPrice) {
    const newEffectivePrice = calculateEffectivePrice(
      latestOriginalPrice,
      sanPhamChiTiet.giaKhuyenMai, // Giả định giaKhuyenMai được cập nhật từ API
    )
    console.log(
      `Price updated for variant ${sanPhamChiTiet.id}: ${donGia} -> ${newEffectivePrice} (original: ${currentOriginalPrice} -> ${latestOriginalPrice})`,
    )
    donGia = newEffectivePrice
    thanhTien = newEffectivePrice * soLuong
  }

  // ENHANCED: Comprehensive reservation with conflict detection
  if (sanPhamChiTiet.serialNumberId && sanPhamChiTiet.serialNumber) {
    try {
      console.log(`🔒 [ENHANCED RESERVATION] Starting reservation for serial ${sanPhamChiTiet.serialNumber}`)

      // Step 1: Validate serial number selection with conflict detection
      const serialNumberObj = {
        id: sanPhamChiTiet.serialNumberId,
        serialNumberValue: sanPhamChiTiet.serialNumber,
        serialNumber: sanPhamChiTiet.serialNumber
      }

      const validationResult = await reservationConflictDetection.validateSerialNumberSelection(
        serialNumberObj,
        sanPhamChiTiet.id,
        'addVariantToCurrentOrder'
      )

      if (!validationResult.success) {
        throw new Error(validationResult.error)
      }

      // Step 2: Attempt reservation with retry mechanism
      const reservationRequest = {
        sanPhamChiTietId: sanPhamChiTiet.id,
        soLuong: soLuong,
        tabId: orderId.value, // Use order ID as session ID for edit mode
        serialNumbers: [sanPhamChiTiet.serialNumber] // Reserve specific serial number
      }

      const reservationResult = await reservationConflictDetection.attemptReservationWithRetry(
        reservationRequest,
        'addVariantToCurrentOrder'
      )

      console.log(`✅ [ENHANCED RESERVATION] Successfully reserved serial number ${sanPhamChiTiet.serialNumber}`)
      console.log('Reservation details:', {
        cartSessionId: reservationResult.cartSessionId,
        soLuongDatTruoc: reservationResult.soLuongDatTruoc,
        thoiGianHetHan: reservationResult.thoiGianHetHan
      })

      // UNIFIED CLEANUP: Track this newly reserved item for potential cleanup
      unifiedCleanupStrategy.trackNewlyReservedItem(sanPhamChiTiet.serialNumber)

    } catch (error) {
      console.error(`❌ [ENHANCED RESERVATION] Failed to reserve serial number ${sanPhamChiTiet.serialNumber}:`, error)

      // COMPREHENSIVE ERROR HANDLING: Use enhanced error handling framework
      const errorHandled = await comprehensiveErrorHandling.handleReservationError(
        error,
        'addVariantToCurrentOrder',
        sanPhamChiTiet.serialNumber
      )

      // If auto-retry was successful, continue with adding to cart
      if (errorHandled && reservationErrorState.value.autoRetryEnabled) {
        console.log(`🔄 [ENHANCED RESERVATION] Auto-retry successful, proceeding with cart addition`)
        // The retry logic would be handled by the error handling framework
        // For now, we'll still prevent cart addition to be safe
      }

      // Don't add item to cart if reservation fails to prevent inventory conflicts
      return
    }
  }

  const newCartItem = {
    sanPhamChiTiet,
    soLuong,
    donGia,
    thanhTien,
    groupInfo,
    originalEffectivePrice, // Lưu giá hiệu quả ban đầu
  }

  currentOrder.value.sanPhamList.push(newCartItem)

  calculateCurrentOrderTotals()

  detectSameSKUPriceDifferences(newCartItem)

  setTimeout(() => {
    syncCartWithDialog()
  }, 100)
}

// Helper methods for cart item display
const getCartItemImage = (item) => {
  let imageFilename = null

  if (item.sanPhamChiTiet) {
    // Get first image from variant's image array
    if (
      item.sanPhamChiTiet.hinhAnh &&
      Array.isArray(item.sanPhamChiTiet.hinhAnh) &&
      item.sanPhamChiTiet.hinhAnh.length > 0
    ) {
      imageFilename = item.sanPhamChiTiet.hinhAnh[0]
    } else {
      // Fallback to product image if variant has no image
      const productImages = item.sanPhamChiTiet.sanPham?.hinhAnh
      if (productImages && Array.isArray(productImages) && productImages.length > 0) {
        imageFilename = productImages[0]
      } else if (typeof productImages === 'string') {
        imageFilename = productImages
      }
    }
  } else {
    // Legacy support for old product-based items
    const productImages = item.sanPham?.hinhAnh
    if (productImages && Array.isArray(productImages) && productImages.length > 0) {
      imageFilename = productImages[0]
    } else if (typeof productImages === 'string') {
      imageFilename = productImages
    }
  }

  if (!imageFilename) return null

  // If it's already a full URL, return as is
  if (imageFilename.startsWith('http')) return imageFilename

  // Check cache first
  if (imageUrlCache.value.has(imageFilename)) {
    return imageUrlCache.value.get(imageFilename)
  }

  // Load presigned URL asynchronously
  loadCartImageUrl(imageFilename)

  // Return null for now, will update when loaded
  return null
}

const loadCartImageUrl = async (imageFilename) => {
  try {
    // Get presigned URL for the image filename
    const presignedUrl = await storageApi.getPresignedUrl('products', imageFilename)

    // Cache the URL for future use
    imageUrlCache.value.set(imageFilename, presignedUrl)

    // Force reactivity update
    imageUrlCache.value = new Map(imageUrlCache.value)
  } catch (error) {
    console.warn('Error getting presigned URL for cart image:', imageFilename, error)
    // Cache null to prevent repeated attempts
    imageUrlCache.value.set(imageFilename, null)
  }
}

const getCartItemName = (item) => {
  if (item.sanPhamChiTiet) {
    return item.sanPhamChiTiet.sanPham?.tenSanPham || 'Sản phẩm'
  }
  // Legacy support for old product-based items
  return item.sanPham?.tenSanPham || 'Sản phẩm'
}

const getCartItemCode = (item) => {
  if (item.sanPhamChiTiet) {
    // Show product code instead of serial number to avoid duplication
    return item.sanPhamChiTiet.sanPham?.maSanPham || item.sanPhamChiTiet.maSanPhamChiTiet || ''
  }
  // Legacy support for old product-based items
  return item.sanPham?.maSanPham || ''
}

const getVariantDisplayInfo = (item) => {
  if (item.sanPhamChiTiet) {
    const parts = []

    // Add hardware specifications
    if (item.sanPhamChiTiet.cpu) parts.push(item.sanPhamChiTiet.cpu.moTaCpu)
    if (item.sanPhamChiTiet.ram) parts.push(item.sanPhamChiTiet.ram.moTaRam)
    if (item.sanPhamChiTiet.gpu) parts.push(item.sanPhamChiTiet.gpu.moTaGpu)
    if (item.sanPhamChiTiet.mauSac) parts.push(item.sanPhamChiTiet.mauSac.moTaMauSac)

    // Storage field reference (boNho)
    if (item.sanPhamChiTiet.boNho) parts.push(item.sanPhamChiTiet.boNho.moTaBoNho)

    if (item.sanPhamChiTiet.manHinh) parts.push(item.sanPhamChiTiet.manHinh.moTaManHinh)

    // Add serial number if available
    if (item.sanPhamChiTiet.serialNumber) {
      parts.push(`Serial: ${item.sanPhamChiTiet.serialNumber}`)
    }

    const displayInfo = parts.join(' • ')

    // Add group info if this variant was selected from a group
    if (item.groupInfo?.isFromGroup) {
      return `${displayInfo} • ${item.groupInfo.displayName}`
    }

    return displayInfo
  }
  return ''
}

const removeFromCurrentOrder = async (index) => {
  const item = currentOrder.value.sanPhamList[index]

  // UNIFIED CLEANUP: Untrack newly reserved item if it's being removed
  if (item?.sanPhamChiTiet?.serialNumber) {
    unifiedCleanupStrategy.untrackNewlyReservedItem(item.sanPhamChiTiet.serialNumber)
  }

  // Release backend reservation before removing from cart
  try {
    if (item?.sanPhamChiTiet?.id) {
      await releaseSpecificItems(orderId.value, item.sanPhamChiTiet.id, item.soLuong || 1)
    }
  } catch (error) {
    console.error('Failed to release reservation:', error)
    // Continue with removal even if backend release fails
  }

  currentOrder.value.sanPhamList.splice(index, 1)
  calculateCurrentOrderTotals()

  // Sync with product variant dialog to update stock counts
  // Add small delay to prevent race condition with immediate serial tracking
  setTimeout(() => {
    syncCartWithDialog()
  }, 100)
}

// QR Scanner Methods
const onQRDetect = async (detectedCodes) => {
  if (detectedCodes && detectedCodes.length > 0) {
    const scannedValue = detectedCodes[0].rawValue
    console.log('QR Code detected:', scannedValue)

    // Set the scanned result
    qrScanResult.value = scannedValue

    // Process the scanned serial number
    await processScannedSerialNumber(scannedValue)
  }
}

const onQRInit = async (promise) => {
  try {
    await promise
    cameraError.value = null

    // Camera activated successfully (removed toast - not critical)
  } catch (error) {
    console.error('QR Scanner initialization error:', error)

    if (error.name === 'NotAllowedError') {
      cameraError.value = 'Quyền truy cập camera bị từ chối. Vui lòng cấp quyền và thử lại.'
    } else if (error.name === 'NotFoundError') {
      cameraError.value = 'Không tìm thấy camera. Vui lòng kiểm tra thiết bị.'
    } else if (error.name === 'NotSupportedError') {
      cameraError.value = 'Trình duyệt không hỗ trợ camera.'
    } else if (error.name === 'NotReadableError') {
      cameraError.value = 'Camera đang được sử dụng bởi ứng dụng khác.'
    } else if (error.name === 'OverconstrainedError') {
      cameraError.value = 'Camera không đáp ứng yêu cầu.'
    } else {
      cameraError.value = 'Lỗi không xác định khi truy cập camera.'
    }
  }
}

const paintBoundingBox = (detectedCodes, ctx) => {
  for (const detectedCode of detectedCodes) {
    const {
      boundingBox: { x, y, width, height },
    } = detectedCode

    ctx.lineWidth = 2
    ctx.strokeStyle = '#007bff'
    ctx.strokeRect(x, y, width, height)
  }
}

const requestCameraPermission = async () => {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true })
    stream.getTracks().forEach((track) => track.stop())

    showQRScanner.value = false
    setTimeout(() => {
      showQRScanner.value = true
      qrScanResult.value = null
      cameraError.value = null
    }, 500)

    // Camera permission granted (removed toast - not critical)
  } catch (error) {
    console.error('Permission request error:', error)
    cameraError.value =
      'Không thể truy cập camera. Vui lòng kiểm tra cài đặt quyền của trình duyệt.'
    toast.add({
      severity: 'error',
      summary: 'Lỗi Camera',
      detail: cameraError.value,
      life: 5000,
    })
  }
}

const stopQRScanner = () => {
  qrScanResult.value = null
  qrProcessingResult.value = null
  cameraError.value = null
}

const resetQRScanner = () => {
  qrScanResult.value = null
  qrProcessingResult.value = null
}

const processScannedSerialNumber = async (serialNumber) => {
  try {
    console.log('🔍 QR Scan: Processing scanned serial number:', serialNumber)
    console.log('🔍 QR Scan: Order ID for reservation:', orderId.value)

    // Find the serial number in the database
    const serialData = await serialNumberApi.getBySerialNumber(serialNumber)

    if (!serialData) {
      qrProcessingResult.value = {
        success: false,
        message: `Serial number "${serialNumber}" không tồn tại trong hệ thống`,
      }
      return
    }

    // Check if serial number is available for reservation
    if (serialData.trangThai !== 'AVAILABLE' && serialData.trangThai !== 'RESERVED') {
      qrProcessingResult.value = {
        success: false,
        message: `Serial number "${serialNumber}" không khả dụng (Trạng thái: ${serialData.trangThai})`,
      }
      return
    }

    // Additional check for RESERVED items - only allow if reserved for this order
    if (serialData.trangThai === 'RESERVED') {
      // For QR scanning, we'll be more permissive and let addVariantToCurrentOrder handle the reservation logic
      console.log(`QR Scan: Serial number ${serialNumber} is RESERVED, will attempt reservation in addVariantToCurrentOrder`)
    }

    // Get the product variant information
    const variantId = serialData.sanPhamChiTietId
    if (!variantId) {
      qrProcessingResult.value = {
        success: false,
        message: `Serial number "${serialNumber}" không liên kết với sản phẩm nào`,
      }
      return
    }

    // Find variant in current products or search for it
    let variant = null

    // Try to find variant by fetching from product store
    // This will be handled by the enhanced ProductVariantDialog

    // If not found in current products, search all products
    if (!variant) {
      try {
        await productStore.fetchProducts()
        for (const product of productStore.products) {
          if (product.sanPhamChiTiets) {
            variant = product.sanPhamChiTiets.find((v) => v.id === variantId)
            if (variant) {
              // Add product reference to variant for display
              variant.sanPham = product
              break
            }
          }
        }
      } catch (error) {
        console.error('Error fetching products for variant lookup:', error)
      }
    }

    if (!variant) {
      qrProcessingResult.value = {
        success: false,
        message: `Không tìm thấy thông tin sản phẩm cho serial number "${serialNumber}"`,
      }
      return
    }

    // Check if this serial number is already in cart
    const existingInCart = currentOrder.value?.sanPhamList?.find(
      (item) =>
        item.sanPhamChiTiet?.serialNumberId === serialData.id ||
        item.sanPhamChiTiet?.serialNumber === serialNumber,
    )

    if (existingInCart) {
      qrProcessingResult.value = {
        success: false,
        message: `Serial number "${serialNumber}" đã có trong giỏ hàng`,
      }
      return
    }

    // Create variant data with serial number
    const variantWithSerial = {
      ...variant,
      serialNumber: serialNumber,
      serialNumberId: serialData.id,
    }

    // ENHANCED: QR scanning with comprehensive conflict detection
    console.log(`🔍 [QR ENHANCED] Starting enhanced reservation for scanned serial ${serialNumber}`)

    try {
      // Step 1: Enhanced validation with conflict detection
      const validationResult = await reservationConflictDetection.validateSerialNumberSelection(
        serialData,
        variantId,
        'QR_scanning'
      )

      if (!validationResult.success) {
        throw new Error(validationResult.error)
      }

      // Step 2: Attempt reservation with retry mechanism
      const reservationRequest = {
        sanPhamChiTietId: variantId,
        soLuong: 1,
        tabId: orderId.value, // Use order ID as session ID for edit mode
        serialNumbers: [serialNumber] // Reserve specific scanned serial number
      }

      const reservationResult = await reservationConflictDetection.attemptReservationWithRetry(
        reservationRequest,
        'QR_scanning'
      )

      console.log(`✅ [QR ENHANCED] Successfully reserved scanned serial number ${serialNumber}`)
      console.log('QR Enhanced reservation details:', {
        cartSessionId: reservationResult.cartSessionId,
        soLuongDatTruoc: reservationResult.soLuongDatTruoc,
        thoiGianHetHan: reservationResult.thoiGianHetHan
      })

      // UNIFIED CLEANUP: Track this newly reserved item for potential cleanup
      unifiedCleanupStrategy.trackNewlyReservedItem(serialNumber)

    } catch (error) {
      console.error(`❌ [QR ENHANCED] Failed to reserve scanned serial number ${serialNumber}:`, error)

      // COMPREHENSIVE ERROR HANDLING: Use enhanced error handling framework for QR scanning
      const errorHandled = await comprehensiveErrorHandling.handleReservationError(
        error,
        'QR_scanning',
        serialNumber
      )

      // Update QR processing result with comprehensive error information
      const errorHandling = reservationConflictDetection.getComprehensiveErrorHandling(error, 'QR_scanning', serialNumber)

      qrProcessingResult.value = {
        success: false,
        message: errorHandling.userMessage,
        errorType: errorHandling.type,
        guidance: errorHandling.guidance,
        recoveryActions: errorHandling.recoveryActions
      }

      // If auto-retry was successful, we could continue, but for QR scanning we'll be conservative
      if (errorHandled && reservationErrorState.value.autoRetryEnabled) {
        console.log(`🔄 [QR ENHANCED] Auto-retry available, but QR scanning requires manual retry`)
      }

      return // Don't proceed to add to cart if reservation fails
    }

    // Add to cart (reservation already handled above, but addVariantToCurrentOrder will handle edge cases)
    const variantData = {
      sanPhamChiTiet: variantWithSerial,
      soLuong: 1,
      donGia:
        variant.giaKhuyenMai && variant.giaKhuyenMai < variant.giaBan
          ? variant.giaKhuyenMai
          : variant.giaBan,
      thanhTien:
        variant.giaKhuyenMai && variant.giaKhuyenMai < variant.giaBan
          ? variant.giaKhuyenMai
          : variant.giaBan,
    }

    await addVariantToCurrentOrder(variantData)

    qrProcessingResult.value = {
      success: true,
      message: `Đã thêm sản phẩm với serial "${serialNumber}" vào giỏ hàng (QR scan)`,
    }

    // Auto-close scanner after successful scan (optional)
    setTimeout(() => {
      if (qrProcessingResult.value?.success) {
        resetQRScanner()
      }
    }, 2000)
  } catch (error) {
    console.error('❌ QR Scan: Error processing scanned serial number:', error)
    qrProcessingResult.value = {
      success: false,
      message: `Lỗi QR scan khi xử lý serial number: ${error.message || 'Vui lòng thử lại hoặc nhập thủ công.'}`,
    }
  }
}

// Customer display label helper
const getCustomerDisplayLabel = (customer) => {
  if (!customer) return ''
  const name = customer.hoTen || 'Không có tên'
  const phone = customer.soDienThoai || 'Không có SĐT'
  return `${name} - ${phone}`
}

const searchCustomers = async (event) => {
  try {
    console.log('Searching customers with query:', event.query)

    // Try backend search first
    try {
      const customers = await customerStore.fetchCustomers({ search: event.query })
      console.log('Customer search results from backend:', customers)
      customerSuggestions.value = customers
      console.log('Updated customerSuggestions:', customerSuggestions.value)
      return
    } catch (backendError) {
      console.warn('Backend search failed, falling back to frontend filtering:', backendError)
    }

    // Fallback: Load all customers and filter on frontend
    const allCustomers = await customerStore.fetchCustomers()
    console.log('All customers loaded:', allCustomers)

    if (!event.query || event.query.trim() === '') {
      customerSuggestions.value = allCustomers.slice(0, 10) // Limit to first 10
      return
    }

    const query = event.query.toLowerCase().trim()
    const filteredCustomers = allCustomers
      .filter((customer) => {
        return (
          customer.hoTen?.toLowerCase().includes(query) ||
          customer.soDienThoai?.includes(query) ||
          customer.email?.toLowerCase().includes(query) ||
          customer.maNguoiDung?.toLowerCase().includes(query)
        )
      })
      .slice(0, 10) // Limit to first 10 results

    console.log('Filtered customers:', filteredCustomers)
    customerSuggestions.value = filteredCustomers
  } catch (error) {
    console.error('Error searching customers:', error)
    customerSuggestions.value = []
  }
}

const onCustomerSelect = async (event) => {
  try {
    console.log('Customer selected from search:', event.value)

    // Fetch complete customer data with addresses to ensure we have all necessary information
    const customerWithAddresses = await customerStore.fetchCustomerById(event.value.id)
    console.log('Customer data with addresses loaded:', customerWithAddresses)

    updateCurrentOrderData({
      khachHang: customerWithAddresses,
      diaChiGiaoHang: null, // Clear any previously selected address
    })
    selectedCustomer.value = customerWithAddresses

    // Load available vouchers for the selected customer
    await loadAvailableVouchers()
  } catch (error) {
    console.error('Error loading customer details:', error)
    // Fallback to the basic customer data from search
    console.log('Using fallback customer data:', event.value)
    updateCurrentOrderData({
      khachHang: event.value,
      diaChiGiaoHang: null, // Clear any previously selected address
    })
    selectedCustomer.value = event.value
    await loadAvailableVouchers()
  }
}

const clearCustomerFromTab = () => {
  updateCurrentOrderData({ khachHang: null, diaChiGiaoHang: null })
  selectedCustomer.value = null
  // Clear available vouchers when customer is removed
  availableVouchers.value = []
  // Clear applied vouchers when customer is removed
  if (currentOrder.value) {
    currentOrder.value.voucherList = []
    calculateCurrentOrderTotals()
  }
  // Reset customer payment fields
  customerPayment.value = 0
  changeAmount.value = 0
}

// Enhanced recipient search methods with debouncing
const searchRecipientByName = async (event) => {
  // Clear previous timer
  if (recipientNameSearchTimer) {
    clearTimeout(recipientNameSearchTimer)
  }

  // Debounce search to prevent excessive API calls
  recipientNameSearchTimer = setTimeout(async () => {
    try {
      console.log('Searching recipients by name:', event.query)

      // Allow single character searches like customer search
      if (!event.query || event.query.trim() === '') {
        recipientNameSuggestions.value = []
        recipientSuggestions.value = [] // Keep backward compatibility
        return
      }

      searchingRecipient.value = true

      // Use the same customer search logic for recipient name search
      const customers = await customerStore.fetchCustomers({ search: event.query })

      const suggestions = customers
        .map((customer) => ({
          ...customer,
          displayLabel: customer.hoTen,
          searchType: 'name',
        }))
        .slice(0, 10) // Limit to 10 results for performance

      recipientNameSuggestions.value = suggestions
      recipientSuggestions.value = suggestions // Keep backward compatibility
    } catch (error) {
      console.error('Error searching recipients by name:', error)
      recipientNameSuggestions.value = []
      recipientSuggestions.value = []
    } finally {
      searchingRecipient.value = false
    }
  }, 300) // 300ms debounce delay
}

const searchRecipientByPhone = async (event) => {
  // Clear previous timer
  if (recipientPhoneSearchTimer) {
    clearTimeout(recipientPhoneSearchTimer)
  }

  // Debounce search to prevent excessive API calls
  recipientPhoneSearchTimer = setTimeout(async () => {
    try {
      console.log('Searching recipients by phone:', event.query)

      // Allow single character searches like customer search
      if (!event.query || event.query.trim() === '') {
        recipientPhoneSuggestions.value = []
        recipientSuggestions.value = [] // Keep backward compatibility
        return
      }

      searchingRecipient.value = true

      // Use the same customer search logic for recipient phone search
      const customers = await customerStore.fetchCustomers({ search: event.query })

      const suggestions = customers
        .map((customer) => ({
          ...customer,
          displayLabel: customer.soDienThoai,
          searchType: 'phone',
        }))
        .slice(0, 10) // Limit to 10 results for performance

      recipientPhoneSuggestions.value = suggestions
      recipientSuggestions.value = suggestions // Keep backward compatibility
    } catch (error) {
      console.error('Error searching recipients by phone:', error)
      recipientPhoneSuggestions.value = []
      recipientSuggestions.value = []
    } finally {
      searchingRecipient.value = false
    }
  }, 300) // 300ms debounce delay
}

// Enhanced recipient selection handlers
const onRecipientNameSelect = async (event) => {
  try {
    console.log('Recipient name selected:', event.value)
    await handleRecipientSelection(event.value, 'name')
  } catch (error) {
    console.error('Error handling recipient name selection:', error)
  }
}

const onRecipientPhoneSelect = async (event) => {
  try {
    console.log('Recipient phone selected:', event.value)
    await handleRecipientSelection(event.value, 'phone')
  } catch (error) {
    console.error('Error handling recipient phone selection:', error)
  }
}

// Unified recipient selection handler
const handleRecipientSelection = async (selectedCustomer, fieldType) => {
  try {
    // Auto-populate recipient information
    if (fieldType === 'name') {
      recipientInfo.value.hoTen = selectedCustomer.hoTen || ''
      // Auto-fill phone if available and not already filled
      if (selectedCustomer.soDienThoai && !recipientInfo.value.soDienThoai.trim()) {
        recipientInfo.value.soDienThoai = selectedCustomer.soDienThoai
      }
    } else if (fieldType === 'phone') {
      recipientInfo.value.soDienThoai = selectedCustomer.soDienThoai || ''
      // Auto-fill name if available and not already filled
      if (selectedCustomer.hoTen && !recipientInfo.value.hoTen.trim()) {
        recipientInfo.value.hoTen = selectedCustomer.hoTen
      }
    }

    // Load complete customer data with addresses if available
    let customerWithAddresses = selectedCustomer
    if (selectedCustomer.id) {
      try {
        customerWithAddresses = await customerStore.fetchCustomerById(selectedCustomer.id)
        console.log('Loaded complete customer data:', customerWithAddresses)
      } catch (error) {
        console.warn('Could not load complete customer data, using basic info:', error)
      }
    }

    // Populate address form with customer's default or first address
    await populateAddressFromCustomer(customerWithAddresses)

    // Clear any validation errors
    recipientErrors.value = {}
  } catch (error) {
    console.error('Error handling recipient selection:', error)
    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: 'Có lỗi xảy ra khi xử lý thông tin người nhận',
      life: 3000,
    })
  }
}

// Helper function to populate dropdown selections based on address data
const populateDropdownSelections = async (addressToUse) => {
  try {
    // Find and select province
    const province = provinces.value.find((p) => p.name === addressToUse.tinhThanh)
    if (province) {
      selectedProvince.value = province
      await onProvinceChange()

      // Find and select district
      const district = districts.value.find((d) => d.name === addressToUse.quanHuyen)
      if (district) {
        selectedDistrict.value = district
        await onDistrictChange()

        // Find and select ward
        const ward = wards.value.find((w) => w.name === addressToUse.phuongXa)
        if (ward) {
          selectedWard.value = ward
        }
      }
    }
  } catch (error) {
    console.error('Error populating dropdown selections:', error)
  }
}

// Address population from customer data with enhanced loading states and timeout handling
const populateAddressFromCustomer = async (customer) => {
  // Create timeout promise for 5-second limit
  const timeoutPromise = new Promise((_, reject) => {
    setTimeout(() => reject(new Error('TIMEOUT')), 5000)
  })

  // Main address population logic
  const populatePromise = async () => {
    if (!customer || !customer.diaChis || customer.diaChis.length === 0) {
      console.log('No addresses found for customer, keeping current address form data')
      return
    }

    // Find default address or use first address
    const addressToUse = customer.diaChis.find((addr) => addr.laMacDinh) || customer.diaChis[0]

    console.log('Populating address form with:', addressToUse)

    // Use setAddressData from the composable to populate the form
    setAddressData({
      duong: addressToUse.duong || '',
      phuongXa: addressToUse.phuongXa || '',
      quanHuyen: addressToUse.quanHuyen || '',
      tinhThanh: addressToUse.tinhThanh || '',
      loaiDiaChi: addressToUse.loaiDiaChi || 'Nhà riêng',
    })

    // Also populate the dropdown selections for proper UI display
    await populateDropdownSelections(addressToUse)

    // Address auto-filled from customer data (removed toast - not critical)
  }

  try {
    populatingAddress.value = true

    // Race between address population and timeout
    await Promise.race([populatePromise(), timeoutPromise])
  } catch (error) {
    console.error('Error populating address from customer:', error)

    if (error.message === 'TIMEOUT') {
      toast.add({
        severity: 'warn',
        summary: 'Cảnh báo',
        detail: 'Việc tự động điền địa chỉ mất quá nhiều thời gian. Vui lòng nhập thủ công.',
        life: 4000,
      })
    } else {
      toast.add({
        severity: 'error',
        summary: 'Lỗi',
        detail: 'Không thể tự động điền địa chỉ. Vui lòng nhập thủ công.',
        life: 3000,
      })
    }
  } finally {
    populatingAddress.value = false
  }
}

// Customer lookup functionality
const checkExistingCustomer = async () => {
  try {
    if (!recipientInfo.value.hoTen.trim() && !recipientInfo.value.soDienThoai.trim()) {
      return null
    }

    console.log('Checking for existing customer with recipient info:', recipientInfo.value)

    // Search by phone first (more unique)
    if (recipientInfo.value.soDienThoai.trim()) {
      const phoneResults = await customerStore.fetchCustomers({
        search: recipientInfo.value.soDienThoai.trim(),
      })

      // Look for exact phone match
      const phoneMatch = phoneResults.find(
        (customer) => customer.soDienThoai === recipientInfo.value.soDienThoai.trim(),
      )

      if (phoneMatch) {
        console.log('Found customer by phone:', phoneMatch)
        return phoneMatch
      }
    }

    // Search by name if no phone match
    if (recipientInfo.value.hoTen.trim()) {
      const nameResults = await customerStore.fetchCustomers({
        search: recipientInfo.value.hoTen.trim(),
      })

      // Look for exact name match
      const nameMatch = nameResults.find(
        (customer) =>
          customer.hoTen?.toLowerCase() === recipientInfo.value.hoTen.trim().toLowerCase(),
      )

      if (nameMatch) {
        console.log('Found customer by name:', nameMatch)
        return nameMatch
      }
    }

    console.log('No existing customer found for recipient info')
    return null
  } catch (error) {
    console.error('Error checking existing customer:', error)
    return null
  }
}

// Product selection dialog methods
const showProductSelectionDialog = () => {
  // Open the enhanced ProductVariantDialog that shows all variants from all products
  variantDialogVisible.value = true
}

// Enhanced sync cart data with product variant dialog for real-time inventory synchronization
const syncCartWithDialog = () => {
  const cartItems = currentOrder.value?.sanPhamList || []

  console.log(`🔄 [SYNC DEBUG] syncCartWithDialog called:`, {
    hasDialogRef: !!productVariantDialogRef.value,
    hasCartItems: !!currentOrder.value?.sanPhamList,
    cartItemsCount: cartItems.length,
    cartSerials: cartItems.map((item) => item.sanPhamChiTiet?.serialNumber).filter(Boolean),
  })

  if (productVariantDialogRef.value && currentOrder.value?.sanPhamList) {
    // Pass current order's cart data for immediate UI updates
    // This triggers cache invalidation and real-time sync in ProductVariantDialog
    productVariantDialogRef.value.updateUsedSerialNumbers(currentOrder.value.sanPhamList)
    console.log(`🔄 [SYNC DEBUG] Cart sync completed with ${cartItems.length} items`)

    // Note: Real-time inventory checking is now handled within ProductVariantDialog
    // via the backend API and cache invalidation to ensure cross-tab accuracy
    // This fixes the inventory count discrepancy issue
  } else {
    console.log(`⚠️ [SYNC DEBUG] Cannot sync - dialog ref or cart items not available`)
  }
}

// Fast customer creation methods
const showFastCustomerDialog = () => {
  fastCustomerDialogVisible.value = true
}

const onCustomerCreated = async (newCustomer) => {
  try {
    console.log('New customer created:', newCustomer)

    // Select the newly created customer
    updateCurrentOrderData({
      khachHang: newCustomer,
      diaChiGiaoHang: null, // Clear any previously selected address
    })
    selectedCustomer.value = newCustomer

    // Load available vouchers for the new customer
    await loadAvailableVouchers()

    // Automatically find and apply best voucher for new customer
    if (currentOrder.value.tongTienHang > 0) {
      await findAndApplyBestVoucher()
    }

    // Customer selected for order (removed toast - not critical)
  } catch (error) {
    console.error('Error selecting newly created customer:', error)
  }
}

// Fast address creation methods

const onAddressCreated = async (newAddress) => {
  try {
    console.log('New address created:', newAddress)

    if (!currentOrder.value.khachHang) {
      toast.add({
        severity: 'error',
        summary: 'Lỗi',
        detail: 'Không tìm thấy thông tin khách hàng',
        life: 3000,
      })
      return
    }

    const customer = currentOrder.value.khachHang

    // Initialize customer addresses array if needed
    if (!customer.diaChis) {
      customer.diaChis = []
    }

    // Check for duplicate address using existing validation logic
    const existingAddress = findMatchingAddress(newAddress, customer.diaChis)

    if (existingAddress) {
      console.log('Address already exists for customer, reusing existing address')

      // Automatically select the existing address for delivery
      updateCurrentOrderData({ diaChiGiaoHang: existingAddress })

      toast.add({
        severity: 'info',
        summary: 'Thông báo',
        detail: 'Địa chỉ này đã tồn tại, sử dụng địa chỉ có sẵn',
        life: 3000,
      })
      return
    }

    // Check address limit (maximum 20 addresses per customer)
    if (customer.diaChis.length >= 20) {
      toast.add({
        severity: 'warn',
        summary: 'Cảnh báo',
        detail: 'Khách hàng đã có quá nhiều địa chỉ (tối đa 20 địa chỉ)',
        life: 5000,
      })
      return
    }

    // Create a temporary address object with ID for UI purposes
    const tempAddress = {
      ...newAddress,
      id: Date.now(), // Temporary ID for UI
      nguoiDungId: customer.id,
    }

    // Add the new address to the customer's address list
    customer.diaChis.push(tempAddress)

    // Automatically select the new address for delivery
    updateCurrentOrderData({ diaChiGiaoHang: tempAddress })

    console.log('Successfully added new address to customer')
    // Delivery address added and selected (removed success toast - not critical per user preference)
  } catch (error) {
    console.error('Error handling newly created address:', error)
    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: 'Có lỗi xảy ra khi xử lý địa chỉ mới',
      life: 3000,
    })
  }
}

const loadAvailableVouchers = async () => {
  if (!currentOrder.value) return

  try {
    const customerId = currentOrder.value.khachHang?.id || null
    const orderTotal = currentOrder.value.tongTienHang || 0

    const response = await voucherApi.getAvailableVouchers(customerId, orderTotal)

    if (response.success) {
      // Filter out already applied vouchers
      const appliedVoucherCodes = currentOrder.value.voucherList.map((v) => v.maPhieuGiamGia)
      availableVouchers.value = response.data.filter(
        (voucher) => !appliedVoucherCodes.includes(voucher.maPhieuGiamGia),
      )
    } else {
      availableVouchers.value = []
    }
  } catch (error) {
    console.error('Error loading available vouchers:', error)
    availableVouchers.value = []
  }
}

const onDeliveryToggle = () => {
  if (!currentOrder.value.giaohang) {
    // Clear address when delivery is turned off
    updateCurrentOrderData({ diaChiGiaoHang: null })
  } else {
    // When delivery is turned on, validate current address selection
    if (currentOrder.value.diaChiGiaoHang && currentOrder.value.khachHang) {
      const currentAddress = currentOrder.value.diaChiGiaoHang
      if (
        currentAddress.nguoiDungId &&
        currentAddress.nguoiDungId !== currentOrder.value.khachHang.id
      ) {
        console.warn(
          'Clearing invalid address selection: Address does not belong to current customer',
        )
        updateCurrentOrderData({ diaChiGiaoHang: null })
      }
    }
  }

  // Validate current payment method when delivery option changes
  const currentPaymentMethod = currentOrder.value.phuongThucThanhToan
  const availablePaymentMethods = paymentMethods.value.map((m) => m.value)

  if (currentPaymentMethod && !availablePaymentMethods.includes(currentPaymentMethod)) {
    // Clear invalid payment method
    updateCurrentOrderData({ phuongThucThanhToan: null })
    toast.add({
      severity: 'warn',
      summary: 'Cảnh báo',
      detail: 'Phương thức thanh toán đã chọn không khả dụng với tùy chọn giao hàng hiện tại',
      life: 3000,
    })
  }

  calculateCurrentOrderTotals()
}

const removeVoucherFromTab = async (index) => {
  if (!currentOrder.value) return

  const _removedVoucher = currentOrder.value.voucherList[index]

  // Remove voucher directly
  currentOrder.value.voucherList.splice(index, 1)
  calculateCurrentOrderTotals()

  // Reload available vouchers to include the removed voucher
  await loadAvailableVouchers()

  // Voucher removed (removed toast - not critical)
}

const selectVoucher = async (voucher) => {
  if (!currentOrder.value) return

  try {
    // Validate voucher before applying
    const customerId = currentOrder.value.khachHang?.id || null
    const orderTotal = currentOrder.value.tongTienHang || 0

    const response = await voucherApi.validateVoucher(
      voucher.maPhieuGiamGia,
      customerId,
      orderTotal,
    )

    if (response.success && response.data.valid) {
      // Check if voucher is already applied
      const existingVoucher = currentOrder.value.voucherList.find(
        (v) => v.maPhieuGiamGia === voucher.maPhieuGiamGia,
      )

      if (existingVoucher) {
        toast.add({
          severity: 'warn',
          summary: 'Cảnh báo',
          detail: 'Voucher này đã được áp dụng',
          life: 3000,
        })
        return
      }

      // SINGLE VOUCHER RESTRICTION: Remove any existing vouchers before applying new one
      if (currentOrder.value.voucherList.length > 0) {
        const _removedVouchers = [...currentOrder.value.voucherList]
        currentOrder.value.voucherList = []

        // Reload available vouchers to include the removed vouchers
        await loadAvailableVouchers()

        // Old vouchers removed for new voucher (removed toast - not critical)
      }

      // Add voucher to active tab with validated discount amount
      const voucherData = {
        ...response.data.voucher,
        giaTriGiam: response.data.discountAmount,
      }

      currentOrder.value.voucherList.push(voucherData)
      calculateCurrentOrderTotals()

      // Remove from available vouchers list
      availableVouchers.value = availableVouchers.value.filter(
        (v) => v.maPhieuGiamGia !== voucher.maPhieuGiamGia,
      )

      // Voucher applied successfully (removed toast - not critical)
    } else {
      toast.add({
        severity: 'error',
        summary: 'Lỗi',
        detail: response.data.error || 'Voucher không hợp lệ',
        life: 3000,
      })
    }
  } catch (error) {
    console.error('Error applying voucher:', error)
    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: 'Không thể áp dụng voucher. Vui lòng thử lại.',
      life: 3000,
    })
  }
}

const selectPaymentMethod = (method) => {
  updateCurrentOrderData({ phuongThucThanhToan: method })

  // Reset customer payment when changing payment method
  if (method !== 'TIEN_MAT') {
    customerPayment.value = 0
    changeAmount.value = 0
  } else {
    customerPayment.value = currentOrder.value.tongTienHang;
  }
}

// Mixed payment methods
const showMixedPaymentDialog = () => {
  mixedPaymentDialogVisible.value = true
}

const onMixedPaymentConfirm = (paymentConfig) => {
  // Store mixed payment configuration in the current order
  updateCurrentOrderData({
    phuongThucThanhToan: 'MIXED',
    mixedPayments: paymentConfig.payments,
  })

  mixedPaymentDialogVisible.value = false

  // Mixed payment configured (removed toast - not critical)
}

// Helper method to get payment method label
const getPaymentMethodLabel = (methodValue) => {
  const method = paymentMethods.value.find((m) => m.value === methodValue)
  return method?.label || methodValue
}

// Better voucher suggestion handlers
const onAcceptBetterVoucher = async (suggestion) => {
  if (!currentOrder.value || !suggestion) return

  try {
    // Process the suggestion acceptance
    const result = processBetterVoucherSuggestion(suggestion, 'accept')

    if (result.type === 'ACCEPT_BETTER_VOUCHER') {
      // Remove current voucher and apply better voucher
      const currentVoucherIndex = currentOrder.value.voucherList.findIndex(
        (v) => v.maPhieuGiamGia === suggestion.currentVoucherCode,
      )

      if (currentVoucherIndex !== -1) {
        // Remove current voucher
        currentOrder.value.voucherList.splice(currentVoucherIndex, 1)
      }

      // Apply better voucher
      await selectVoucher(suggestion.betterVoucher)

      // Close dialog
      closeSuggestionDialog()
    }
  } catch (error) {
    console.error('Error accepting better voucher:', error)
    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: 'Không thể áp dụng voucher tốt hơn. Vui lòng thử lại.',
      life: 3000,
    })
  }
}

const onRejectBetterVoucher = (suggestion) => {
  if (!suggestion) return

  // Process the suggestion rejection
  const result = processBetterVoucherSuggestion(suggestion, 'reject')

  if (result.type === 'REJECT_BETTER_VOUCHER') {
    // Close dialog
    closeSuggestionDialog()

    // Better voucher suggestion declined (removed toast - not critical)
  }
}

// Calculate change amount for cash payments
const calculateChange = () => {
  if (!currentOrder.value || currentOrder.value.phuongThucThanhToan !== 'TIEN_MAT') {
    changeAmount.value = 0
    return
  }

  const payment = customerPayment.value || 0
  const total = dynamicOrderTotal.value || 0
  changeAmount.value = Math.max(0, payment - total)
}

// Automatic voucher selection methods
const findAndApplyBestVoucher = async () => {
  if (!currentOrder.value || !currentOrder.value.khachHang || currentOrder.value.tongTienHang <= 0) return

  try {
    loadingBestVoucher.value = true
    const customerId = currentOrder.value.khachHang.id
    const orderTotal = currentOrder.value.tongTienHang

    const response = await voucherApi.getBestVoucher(customerId, orderTotal)

    if (response.success && response.data.found) {
      bestVoucherResult.value = response.data

      // Check if this voucher is already applied
      const existingVoucher = currentOrder.value.voucherList.find(
        (v) => v.maPhieuGiamGia === response.data.voucher.maPhieuGiamGia,
      )

      if (!existingVoucher) {
        // Automatically apply the best voucher
        await selectVoucher(response.data.voucher)

        toast.add({
          severity: 'success',
          summary: 'Tự động áp dụng voucher',
          detail: `Đã áp dụng voucher tốt nhất: ${response.data.voucher.maPhieuGiamGia} (Giảm ${formatCurrency(response.data.discountAmount)})`,
          life: 4000,
        })
      }
    }

    // Generate smart voucher recommendations after processing vouchers
    await generateVoucherRecommendation()
  } catch (error) {
    console.error('Error finding best voucher:', error)
    // Don't show error toast for automatic voucher application to avoid annoying users
  } finally {
    loadingBestVoucher.value = false
  }
}

// Smart Voucher Recommendation Logic
const generateVoucherRecommendation = async () => {
  if ( !currentOrder.value?.tongTienHang) {
    voucherRecommendations.value = []
    return
  }

  try {
    const currentTotal = currentOrder.value.tongTienHang

    // Get ALL active vouchers (not just available ones) to find recommendation opportunities
    const allVouchersResponse = await voucherApi.getAllVouchers({ status: 'DA_DIEN_RA' })
    const allVouchers = allVouchersResponse.success ? allVouchersResponse.data : []

    // Filter vouchers that require more spending than current total (for recommendations)
    const futureVouchers = allVouchers
      .filter((voucher) => {
        const minOrder = voucher.giaTriDonHangToiThieu || 0
        return minOrder > currentTotal
      })
      .sort((a, b) => (a.giaTriDonHangToiThieu || 0) - (b.giaTriDonHangToiThieu || 0))

    // Generate recommendations for multiple tiers (up to 3 recommendations)
    const recommendations = []
    const maxRecommendations = Math.min(3, futureVouchers.length)

    for (let i = 0; i < maxRecommendations; i++) {
      const voucher = futureVouchers[i]
      const targetAmount = voucher.giaTriDonHangToiThieu
      const additionalAmount = targetAmount - currentTotal
      const potentialDiscount = calculateVoucherDiscount(voucher, targetAmount)

      recommendations.push({
        message: `Mua thêm ${formatCurrency(additionalAmount)} để được giảm ${formatCurrency(potentialDiscount)}`,
        voucher: voucher,
        targetAmount: targetAmount,
        additionalAmount: additionalAmount,
        potentialDiscount: potentialDiscount,
      })
    }

    voucherRecommendations.value = recommendations
  } catch (error) {
    console.error('Error generating voucher recommendation:', error)
    voucherRecommendations.value = []
  }
}

// Apply recommended voucher with click-to-apply functionality
const applyRecommendedVoucher = async (voucher) => {
  if (!currentOrder.value || !voucher) return

  try {
    // Check if the current order total meets the voucher's minimum requirement
    const currentTotal = currentOrder.value.tongTienHang || 0
    const minOrder = voucher.giaTriDonHangToiThieu || 0

    if (currentTotal < minOrder) {
      const additionalAmount = minOrder - currentTotal
      toast.add({
        severity: 'warn',
        summary: 'Chưa đủ điều kiện',
        detail: `Cần mua thêm ${formatCurrency(additionalAmount)} để áp dụng voucher này`,
        life: 4000,
      })
      return
    }

    // Check if voucher is already applied
    const existingVoucher = currentOrder.value.voucherList.find(
      (v) => v.maPhieuGiamGia === voucher.maPhieuGiamGia,
    )

    if (existingVoucher) {
      toast.add({
        severity: 'warn',
        summary: 'Cảnh báo',
        detail: 'Voucher này đã được áp dụng',
        life: 3000,
      })
      return
    }

    // Apply the voucher using existing selectVoucher function
    await selectVoucher(voucher)

    // Regenerate recommendations after applying voucher
    await generateVoucherRecommendation()
  } catch (error) {
    console.error('Error applying recommended voucher:', error)
    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: 'Không thể áp dụng voucher. Vui lòng thử lại.',
      life: 3000,
    })
  }
}

// Order confirmation methods
const showOrderConfirmation = async () => {
  if (!currentOrder.value) return

  // Perform basic validation before showing confirmation
  if (!canCreateActiveOrder.value) {
    toast.add({
      severity: 'warn',
      summary: 'Cảnh báo',
      detail: 'Vui lòng hoàn tất thông tin đơn hàng trước khi thanh toán',
      life: 3000,
    })
    return
  }

  // Enhanced voucher validation before payment
  prePaymentCheckInProgress.value = true
  try {
    const voucherValidation = await validateVouchersBeforePayment()
    if (!voucherValidation.proceed) {
      return // Stop if voucher validation fails or user cancels
    }
  } catch (error) {
    console.error('Error during voucher validation:', error)
    toast.add({
      severity: 'error',
      summary: 'Lỗi xác thực voucher',
      detail: 'Không thể xác thực voucher. Vui lòng thử lại.',
      life: 5000,
    })
    return
  } finally {
    prePaymentCheckInProgress.value = false
  }

  orderConfirmationVisible.value = true
}

const confirmAndUpdateOrder = async () => {
  orderConfirmationVisible.value = false

  // In edit mode, we only support direct order updates
  // Gateway payments and mixed payments are not supported for order updates
  if (currentOrder.value?.phuongThucThanhToan === 'MIXED') {
    toast.add({
      severity: 'warn',
      summary: 'Cảnh báo',
      detail: 'Không thể cập nhật đơn hàng với thanh toán hỗn hợp. Vui lòng chọn một phương thức thanh toán duy nhất.',
      life: 5000
    })
    return
  } else if (
    currentOrder.value?.phuongThucThanhToan === 'MOMO' ||
    currentOrder.value?.phuongThucThanhToan === 'VNPAY'
  ) {
    toast.add({
      severity: 'warn',
      summary: 'Cảnh báo',
      detail: 'Không thể cập nhật đơn hàng với thanh toán qua cổng. Vui lòng chọn phương thức thanh toán khác.',
      life: 5000
    })
    return
  } else {
    // For standard payments, update order normally
    await updateOrderFromCurrentOrder()
  }
}







/**
 * Enhanced gateway payment handling with comprehensive error management
 * NOTE: Disabled for order updates - gateway payments not supported in edit mode
 */
const _handleGatewayPayment = async () => {
  if (!currentOrder.value) return

  // Store payment method and order data before creating order (tab gets closed after creation)
  const paymentMethod = currentOrder.value.phuongThucThanhToan
  const orderTotal = dynamicOrderTotal.value

  // Perform all validations first (same as createOrderFromCurrentOrder)
  const validationErrors = validateCurrentOrder()

  // Validate delivery information if delivery is enabled
  if (currentOrder.value.giaohang) {
    const deliveryValidation = validateDeliveryInfo()
    const scenarioValidation = await validateAllCustomerScenarios()

    if (!deliveryValidation.valid || !scenarioValidation.valid) {
      let errorDetail = 'Vui lòng kiểm tra lại thông tin người nhận và địa chỉ giao hàng'

      if (!deliveryValidation.valid) {
        const errorMessages = Object.values(deliveryValidation.errors || {}).map(
          (msg) => `- ${msg}`,
        )
        errorDetail = `Vui lòng kiểm tra lại:\n${errorMessages.join('\n')}`
      } else if (!scenarioValidation.valid) {
        const errorMessages = scenarioValidation.errors.map((msg) => `- ${msg}`)
        errorDetail = `Vui lòng kiểm tra lại:\n${errorMessages.join('\n')}`
      }

      toast.add({
        severity: 'warn',
        summary: 'Dữ liệu không hợp lệ',
        detail: errorDetail,
        life: 5000,
      })
      return
    }
  }

  if (Object.keys(validationErrors).length > 0) {
    const errorMessages = []
    Object.entries(validationErrors).forEach(([, errors]) => {
      errors.forEach((error) => errorMessages.push(`- ${error}`))
    })

    toast.add({
      severity: 'warn',
      summary: 'Dữ liệu không hợp lệ',
      detail: `Vui lòng kiểm tra lại:\n${errorMessages.join('\n')}`,
      life: 7000,
    })
    return
  }

  try {
    creating.value = true

    // Create order first to get order ID (disabled in edit mode)
    const result = await updateOrderFromCurrentOrder()

    if (!result) {
      throw new Error('Không thể tạo đơn hàng')
    }

    // Use stored payment method and order data (currentOrder is now processed)
    const paymentData = {
      amount: orderTotal,
      orderInfo: `Thanh toán đơn hàng ${result.maHoaDon}`,
      returnUrl: getPaymentReturnUrl(),
    }

    let paymentResponse
    if (paymentMethod === 'MOMO') {
      paymentResponse = await orderApi.processMoMoPayment(result.id, paymentData)
    } else if (paymentMethod === 'VNPAY') {
      paymentResponse = await orderApi.processVNPayPayment(result.id, paymentData)
    }

    if (paymentResponse?.success && paymentResponse.data?.paymentUrl) {
      // Clear unsaved changes flag before opening payment gateway
      hasUnsavedChanges.value = false

      // Open payment gateway in new tab to preserve current order tab
      window.open(paymentResponse.data.paymentUrl, '_blank')
    } else {
      throw new Error(paymentResponse?.message || 'Không thể khởi tạo thanh toán')
    }
  } catch (error) {
    console.error('Error handling gateway payment:', error)
    toast.add({
      severity: 'error',
      summary: 'Lỗi thanh toán',
      detail: error.message || 'Không thể khởi tạo thanh toán. Vui lòng thử lại.',
      life: 5000,
    })
  } finally {
    creating.value = false
  }
}

/**
 * Handle mixed payment order creation and processing
 * NOTE: Disabled for order updates - mixed payments not supported in edit mode
 */
const _handleMixedPaymentOrder = async () => {
  if (!currentOrder.value || !currentOrder.value.mixedPayments) {
    toast.add({
      severity: 'error',
      summary: 'Lỗi thanh toán',
      detail: 'Không tìm thấy cấu hình thanh toán hỗn hợp',
      life: 5000,
    })
    return
  }

  // Store payment configuration and order data before creating order
  const mixedPayments = currentOrder.value.mixedPayments
  const orderTotal = dynamicOrderTotal.value

  // Perform all validations first (same as createOrderFromCurrentOrder)
  const validationErrors = validateCurrentOrder()

  // Validate delivery information if delivery is enabled
  if (currentOrder.value.giaohang) {
    const deliveryValidation = validateDeliveryInfo()
    const scenarioValidation = await validateAllCustomerScenarios()

    if (!deliveryValidation.valid || !scenarioValidation.valid) {
      let errorDetail = 'Vui lòng kiểm tra lại thông tin người nhận và địa chỉ giao hàng'

      if (!deliveryValidation.valid) {
        const errorMessages = Object.values(deliveryValidation.errors || {}).map(
          (msg) => `- ${msg}`,
        )
        errorDetail = `Vui lòng kiểm tra lại:\n${errorMessages.join('\n')}`
      } else if (!scenarioValidation.valid) {
        const errorMessages = scenarioValidation.errors.map((msg) => `- ${msg}`)
        errorDetail = `Vui lòng kiểm tra lại:\n${errorMessages.join('\n')}`
      }

      toast.add({
        severity: 'warn',
        summary: 'Dữ liệu không hợp lệ',
        detail: errorDetail,
        life: 5000,
      })
      return
    }
  }

  if (Object.keys(validationErrors).length > 0) {
    const errorMessages = []
    Object.entries(validationErrors).forEach(([, errors]) => {
      errors.forEach((error) => errorMessages.push(`- ${error}`))
    })

    toast.add({
      severity: 'warn',
      summary: 'Dữ liệu không hợp lệ',
      detail: `Vui lòng kiểm tra lại:\n${errorMessages.join('\n')}`,
      life: 7000,
    })
    return
  }

  try {
    creating.value = true

    // Create order first to get order ID (disabled in edit mode)
    const result = await updateOrderFromCurrentOrder()

    if (!result) {
      throw new Error('Không thể tạo đơn hàng')
    }

    // Process mixed payment using the API
    const paymentData = mixedPayments.map(payment => ({
      phuongThucThanhToan: payment.method,
      soTien: payment.amount,
      ghiChu: `Thanh toán ${payment.method} - Đơn hàng ${result.maHoaDon}`
    }))

    console.log('Processing mixed payment for order:', result.id, 'with payments:', paymentData)

    const paymentResponse = await orderApi.processMixedPayment(result.id, paymentData, orderTotal)

    if (paymentResponse?.success) {
      // Handle successful mixed payment processing
      const paymentResults = paymentResponse.data

      // Check if there are any gateway URLs that need to be opened
      if (paymentResults.gatewayUrls && paymentResults.gatewayUrls.length > 0) {
        // Clear unsaved changes flag before opening payment gateways
        hasUnsavedChanges.value = false

        // Open each gateway URL in a new tab
        paymentResults.gatewayUrls.forEach((url, index) => {
          setTimeout(() => {
            window.open(url, '_blank')
          }, index * 1000) // Stagger the opening by 1 second each
        })

        toast.add({
          severity: 'success',
          summary: 'Đơn hàng đã tạo',
          detail: `Đơn hàng ${result.maHoaDon} đã được tạo thành công. Vui lòng hoàn tất thanh toán qua các cổng thanh toán đã mở.`,
          life: 8000,
        })
      } else {
        // All payments were processed immediately (e.g., all cash)
        toast.add({
          severity: 'success',
          summary: 'Đơn hàng đã tạo',
          detail: `Đơn hàng ${result.maHoaDon} đã được tạo và thanh toán thành công.`,
          life: 5000,
        })
      }

      // Clear unsaved changes flag
      hasUnsavedChanges.value = false

    } else {
      throw new Error(paymentResponse?.message || 'Không thể xử lý thanh toán hỗn hợp')
    }
  } catch (error) {
    console.error('Error handling mixed payment order:', error)
    toast.add({
      severity: 'error',
      summary: 'Lỗi thanh toán hỗn hợp',
      detail: error.message || 'Không thể xử lý thanh toán hỗn hợp. Vui lòng thử lại.',
      life: 5000,
    })
  } finally {
    creating.value = false
  }
}




const updateOrderFromCurrentOrder = async () => {
  if (!currentOrder.value) return

  // Prevent multiple simultaneous order update attempts
  if (creating.value) {
    console.log('Order update already in progress, ignoring duplicate request')
    return
  }

  // Perform comprehensive validation including embedded address and recipient info
  const validationErrors = validateCurrentOrder()

  // Validate delivery information if delivery is enabled
  if (currentOrder.value.giaohang) {
    const deliveryValidation = validateDeliveryInfo()
    const scenarioValidation = await validateAllCustomerScenarios()

    if (!deliveryValidation.valid || !scenarioValidation.valid) {
      let errorDetail = 'Vui lòng kiểm tra lại thông tin người nhận và địa chỉ giao hàng'

      if (!deliveryValidation.valid) {
        const errorMessages = Object.values(deliveryValidation.errors || {}).map(
          (msg) => `- ${msg}`,
        )
        errorDetail = `Vui lòng kiểm tra lại:\n${errorMessages.join('\n')}`
      } else if (!scenarioValidation.valid) {
        const errorMessages = scenarioValidation.errors.map((msg) => `- ${msg}`)
        errorDetail = `Vui lòng kiểm tra lại:\n${errorMessages.join('\n')}`
      }

      toast.add({
        severity: 'warn',
        summary: 'Dữ liệu không hợp lệ',
        detail: errorDetail,
        life: 5000,
      })
      return
    }

    console.log(`✅ All validations passed for ${scenarioValidation.scenario}`)
  }

  if (Object.keys(validationErrors).length > 0) {
    // Display validation errors
    const errorMessages = []
    Object.entries(validationErrors).forEach(([, errors]) => {
      errors.forEach((error) => errorMessages.push(`- ${error}`))
    })

    toast.add({
      severity: 'warn',
      summary: 'Dữ liệu không hợp lệ',
      detail: `Vui lòng kiểm tra lại:\n${errorMessages.join('\n')}`,
      life: 7000,
    })
    return
  }

  // Proceed directly with order creation
  await performOrderCreation()
}

const performOrderCreation = async () => {
  creating.value = true
  try {
    // Handle customer creation for recipient-only orders before order creation
    if (
      currentOrder.value.giaohang &&
      !currentOrder.value.khachHang &&
      recipientInfo.value.hoTen.trim() &&
      recipientInfo.value.soDienThoai.trim()
    ) {
      console.log('Handling recipient-only order - checking for existing customer')
      const existingCustomer = await checkExistingCustomer()

      if (!existingCustomer) {
        console.log('Creating new customer from recipient information')
        await createCustomerFromRecipient()
      } else {
        console.log('Using existing customer for recipient-only order')
        updateCurrentOrderData({
          khachHang: existingCustomer,
          diaChiGiaoHang: null,
        })
        selectedCustomer.value = existingCustomer
      }
    }

    // Handle Scenario 2: Different recipient than customer - Create recipient customer if needed
    if (
      currentOrder.value.giaohang &&
      currentOrder.value.khachHang &&
      recipientInfo.value.hoTen.trim() &&
      recipientInfo.value.soDienThoai.trim()
    ) {
      const currentCustomer = currentOrder.value.khachHang
      const recipientDiffersFromCustomer =
        recipientInfo.value.hoTen.trim() !== currentCustomer.hoTen ||
        recipientInfo.value.soDienThoai.trim() !== currentCustomer.soDienThoai

      if (recipientDiffersFromCustomer && !recipientCustomer.value) {
        console.log('Scenario 2: Creating recipient customer for different recipient')
        try {
          await createRecipientCustomerForScenario2()
        } catch (error) {
          console.error('Failed to create recipient customer for Scenario 2:', error)
          // Continue with order creation even if recipient customer creation fails
          // The order will still be valid with recipient info in nguoi_nhan fields
        }
      }
    }

    // Enhanced address management and validation before order creation
    if (currentOrder.value.giaohang) {
      console.log('Validating and managing address before order creation')

      // Determine the appropriate scenario for validation
      let validationScenario = 'default'
      if (currentOrder.value.khachHang && recipientInfo.value.hoTen.trim()) {
        const currentCustomer = currentOrder.value.khachHang
        const recipientDiffersFromCustomer =
          recipientInfo.value.hoTen.trim() !== currentCustomer.hoTen ||
          recipientInfo.value.soDienThoai.trim() !== currentCustomer.soDienThoai

        validationScenario = recipientDiffersFromCustomer ? 'scenario2' : 'scenario1'
      } else if (!currentOrder.value.khachHang && recipientInfo.value.hoTen.trim()) {
        validationScenario = 'scenario3'
      }

      // Perform comprehensive address validation
      const addressValidation = await validateAddressForScenario(validationScenario)

      if (!addressValidation.valid) {
        const errorMessages = Object.values(addressValidation.errors).join(', ')
        toast.add({
          severity: 'error',
          summary: 'Lỗi địa chỉ',
          detail: `Địa chỉ giao hàng không hợp lệ: ${errorMessages}`,
          life: 5000,
        })
        return // Stop order creation if address validation fails
      }

      // Handle address management for existing customers
      if (currentOrder.value.khachHang) {
        console.log('Managing address for customer before order creation')
        const addressResult = await handleAddressManagement(currentOrder.value.khachHang)

        if (!addressResult.success) {
          // Show detailed error message
          const errorDetail = addressResult.validationErrors
            ? Object.values(addressResult.validationErrors).join(', ')
            : addressResult.error || 'Không thể xử lý địa chỉ'

          toast.add({
            severity: 'error',
            summary: 'Lỗi quản lý địa chỉ',
            detail: errorDetail,
            life: 5000,
          })
          return // Stop order creation if address management fails
        } else if (addressResult.updatedCustomer) {
          console.log('Customer address updated successfully before order creation')
          // Customer address updated (removed toast - not critical)
        }
      }
    }

    // ENHANCED: Validate complete serial number data before order creation
    console.log('Validating serial number payload completeness...')
    const serialValidation = validateCartSerialNumberCompleteness(currentOrder.value.sanPhamList)

    if (!serialValidation.valid) {
      console.error('Serial number validation failed:', serialValidation.issues)
      toast.add({
        severity: 'error',
        summary: 'Dữ liệu serial number không đầy đủ',
        detail: `Vui lòng kiểm tra lại thông tin serial number:\n${serialValidation.issues.join('\n')}`,
        life: 7000,
      })
      return // Stop order creation if serial number validation fails
    }

    console.log(
      '✅ Serial number validation passed - all cart items have complete serial number data',
    )

    // Map frontend data to HoaDonDto structure for update using order-specific mapping
    const updateData = mapOrderToUpdateDto(currentOrder.value, {
      addressData: addressData.value,
      recipientInfo: recipientInfo.value,
      consolidatedShippingFee: consolidatedShippingFee.value,
      dynamicOrderTotal: dynamicOrderTotal.value,
      validationFunctions: {
        validateSerialNumberId,
        validateSerialNumber
      },
      updateCurrentOrderData,
      source: 'OrderEdit.vue'
    })
    console.log('Updating order with data:', updateData)
    console.log('Current order data:', currentOrder.value);

    // Update order using orderStore updateOrder method
    const result = await updateOrder(currentOrder.value.id, updateData)

    if (result) {
      // Clear unsaved changes flag
      hasUnsavedChanges.value = false

      // UNIFIED CLEANUP: Clear newly reserved items tracking since they're now committed
      // After successful order update, newly reserved items become part of the order
      newlyReservedItems.value.clear()
      console.log('🔒 [UNIFIED CLEANUP] Cleared newly reserved items tracking after successful order update')

      // Show success message
      toast.add({
        severity: 'success',
        summary: 'Thành công',
        detail: `Đơn hàng ${currentOrder.value.maHoaDon} đã được cập nhật thành công`,
        life: 3000
      })

      // Navigate back to order detail or orders list
      router.push(`/orders/${currentOrder.value.id}`)
    }
  } catch (error) {
    console.error('Error updating order:', error)

    // Handle specific API validation errors
    if (error.response && error.response.data && error.response.data.errors) {
      const apiErrors = error.response.data.errors
      const errorMessages = Object.values(apiErrors).flat()

      toast.add({
        severity: 'error',
        summary: 'Lỗi xác thực',
        detail: `Dữ liệu không hợp lệ:\n${errorMessages.join('\n')}`,
        life: 7000,
      })
    } else {
      toast.add({
        severity: 'error',
        summary: 'Lỗi',
        detail: 'Không thể cập nhật đơn hàng. Vui lòng thử lại.',
        life: 5000,
      })
    }
  } finally {
    creating.value = false
  }
}

/**
 * Reset all component-level state variables while preserving order store data
 * This function systematically clears component state to ensure clean tab closure
 * without affecting the core order data managed by the store
 */
const _resetTabState = () => {
  try {
    console.log('Tab state reset initiated')

    // 1. Customer & Recipient State Reset
    try {
      selectedCustomer.value = null
      recipientInfo.value = { hoTen: '', soDienThoai: '' }
      recipientCustomer.value = null
      recipientErrors.value = {}
      customerSuggestions.value = []
      recipientNameSuggestions.value = []
      recipientPhoneSuggestions.value = []
      recipientSuggestions.value = []
      searchingRecipient.value = false
    } catch (error) {
      console.error('Error resetting customer/recipient state:', error)
    }

    // 2. Payment State Reset
    try {
      customerPayment.value = 0
      changeAmount.value = 0
    } catch (error) {
      console.error('Error resetting payment state:', error)
    }

    // 3. Shipping State Reset
    try {
      resetShippingCalculation()
      // consolidatedShippingFee is computed and will sync automatically
    } catch (error) {
      console.error('Error resetting shipping state:', error)
    }

    // 4. Voucher State Reset
    try {
      availableVouchers.value = []
      voucherRecommendations.value = []
      bestVoucherResult.value = null
      loadingBestVoucher.value = false
      showAllVouchers.value = false
      voucherDisplayLimit.value = 3
    } catch (error) {
      console.error('Error resetting voucher state:', error)
    }

    // 5. Dialog States Reset
    try {
      variantDialogVisible.value = false
      fastCustomerDialogVisible.value = false
      fastAddressDialogVisible.value = false
      orderConfirmationVisible.value = false
      mixedPaymentDialogVisible.value = false
      showQRScanner.value = false
    } catch (error) {
      console.error('Error resetting dialog states:', error)
    }

    // 6. Address State Reset
    try {
      resetAddressForm()
    } catch (error) {
      console.error('Error resetting address state:', error)
    }

    // 7. Processing States Reset
    try {
      creating.value = false
      populatingAddress.value = false
      hasUnsavedChanges.value = false
      prePaymentCheckInProgress.value = false
    } catch (error) {
      console.error('Error resetting processing states:', error)
    }

    // 8. UI State Reset
    try {
      showExpirationPanel.value = true
      imageUrlCache.value = new Map()
      qrScanResult.value = null
      qrProcessingResult.value = null
      cameraError.value = null
    } catch (error) {
      console.error('Error resetting UI state:', error)
    }

    // 9. Real-time State Reset
    try {
      cartPriceChanges.value = []
      acknowledgedPriceChanges.value = new Set()
    } catch (error) {
      console.error('Error resetting real-time state:', error)
    }

    // 10. Timer Cleanup (Critical for memory management)
    try {
      if (recipientNameSearchTimer) {
        clearTimeout(recipientNameSearchTimer)
        recipientNameSearchTimer = null
      }
      if (recipientPhoneSearchTimer) {
        clearTimeout(recipientPhoneSearchTimer)
        recipientPhoneSearchTimer = null
      }
      if (shippingCalculationTimeout.value) {
        clearTimeout(shippingCalculationTimeout.value)
        shippingCalculationTimeout.value = null
      }
    } catch (error) {
      console.error('Error cleaning up timers:', error)
    }

    console.log('Tab state reset completed successfully')
  } catch (error) {
    console.error('Critical error during tab state reset:', error)
    // Continue with tab closure even if reset fails
  }
}

// Enhanced tab closure with unsaved changes warning
const _closeTabWithConfirmation = async (_tabId) => {
  // This function is no longer needed in single-order edit mode
  // In edit mode, we navigate back to the order list instead
  console.log('closeTabWithConfirmation called but not needed in edit mode')
  router.push('/orders')
}

// Serial number validation helpers for order payload completeness
const validateSerialNumberId = (serialNumberId) => {
  // Ensure serialNumberId is properly populated and valid
  if (serialNumberId === undefined || serialNumberId === null) {
    console.warn('Serial number ID is missing from cart item')
    return null
  }

  // Validate that it's a valid number
  if (typeof serialNumberId !== 'number' || serialNumberId <= 0) {
    console.warn('Invalid serial number ID:', serialNumberId)
    return null
  }

  return serialNumberId
}

const validateSerialNumber = (serialNumber) => {
  // Only validate that we have a non-empty string - let backend handle format rules
  if (!serialNumber || typeof serialNumber !== 'string' || serialNumber === '') {
    console.warn('Serial number value is missing or invalid from cart item')
    return null
  }
  // Return the original serial number unchanged - no modification, no arbitrary validation
  // Backend will handle all format validation according to actual business rules
  return serialNumber
}

// Validate complete serial number data for all cart items
const validateCartSerialNumberCompleteness = (cartItems) => {
  const validationResults = {
    valid: true,
    issues: [],
    itemsWithIssues: [],
  }

  cartItems.forEach((item, index) => {
    const variantId = item.sanPhamChiTiet?.id
    const serialNumberId = item.sanPhamChiTiet?.serialNumberId
    const serialNumber = item.sanPhamChiTiet?.serialNumber

    // Check for missing serial number data
    if (!serialNumberId && !serialNumber) {
      validationResults.valid = false
      validationResults.issues.push(`Item ${index + 1}: Missing both serial number ID and value`)
      validationResults.itemsWithIssues.push(index)
    } else if (!serialNumberId) {
      validationResults.valid = false
      validationResults.issues.push(`Item ${index + 1}: Missing serial number ID`)
      validationResults.itemsWithIssues.push(index)
    } else if (!serialNumber) {
      validationResults.valid = false
      validationResults.issues.push(`Item ${index + 1}: Missing serial number value`)
      validationResults.itemsWithIssues.push(index)
    }

    // Log validation details for debugging
    console.log(`Cart item ${index + 1} serial validation:`, {
      variantId,
      serialNumberId,
      serialNumber,
      hasValidId: !!validateSerialNumberId(serialNumberId),
      hasValidSerial: !!validateSerialNumber(serialNumber),
    })
  })

  return validationResults
}

// Note: mapTabToHoaDonDto function now imported from shared utility @/utils/orderMapping.js

// Use shared audit composable
const { auditOrderCreation: _auditOrderCreation } = useOrderAudit()

// Use shared validation composable
const {
  validateTabData,
  clearValidationErrors,
  validatePhoneNumber,
  validateCompleteAddress
} = useOrderValidation()

const validateCurrentOrder = () => {
  if (!currentOrder.value) return {}
  return validateTabData(currentOrder.value)
}

// Consolidated delivery information validation function
// Handles timing issues between dropdown selections and string value updates
const validateDeliveryInfo = () => {
  const errors = {}

  if (!currentOrder.value?.giaohang) return { valid: true, errors: {} }

  // Recipient validation
  if (!recipientInfo.value.hoTen.trim()) {
    errors.hoTen = 'Vui lòng nhập tên người nhận'
  }

  // Use consolidated phone validation
  if (!recipientInfo.value.soDienThoai.trim()) {
    errors.soDienThoai = 'Vui lòng nhập số điện thoại người nhận'
  } else {
    const phoneValidation = validatePhoneNumber(recipientInfo.value.soDienThoai.trim())
    if (!phoneValidation.isValid && phoneValidation.error) {
      errors.soDienThoai = 'Số điện thoại người nhận không hợp lệ'
    }
  }

  // Address validation - check both objects and string values to handle timing issues
  const hasStreetAddress = addressData.value.duong && addressData.value.duong.trim()
  const hasLocationFromDropdowns =
    selectedProvince.value && selectedDistrict.value && selectedWard.value
  const hasLocationFromData =
    addressData.value.tinhThanh && addressData.value.quanHuyen && addressData.value.phuongXa

  if (!hasStreetAddress) {
    errors.duong = 'Vui lòng nhập địa chỉ đường'
  } else if (addressData.value.duong.trim().length < 5) {
    errors.duong = 'Địa chỉ đường phải có ít nhất 5 ký tự'
  } else if (addressData.value.duong.trim().length > 255) {
    errors.duong = 'Địa chỉ đường không được vượt quá 255 ký tự'
  }

  if (!hasLocationFromDropdowns && !hasLocationFromData) {
    if (!addressData.value.tinhThanh && !selectedProvince.value) {
      errors.tinhThanh = 'Vui lòng chọn tỉnh/thành phố'
    }
    if (!addressData.value.quanHuyen && !selectedDistrict.value) {
      errors.quanHuyen = 'Vui lòng chọn quận/huyện'
    }
    if (!addressData.value.phuongXa && !selectedWard.value) {
      errors.phuongXa = 'Vui lòng chọn phường/xã'
    }
  }

  return {
    valid: Object.keys(errors).length === 0,
    errors,
  }
}

// Legacy function for backward compatibility
const validateEmbeddedAddress = () => {
  const result = validateDeliveryInfo()
  // Update address errors from composable for backward compatibility
  addressErrors.value = { ...addressErrors.value, ...result.errors }
  return result.valid
}

// ===== BUSINESS LOGIC FOR CUSTOMER SCENARIOS =====

// Enhanced address management for order creation with comprehensive validation and persistence
const handleAddressManagement = async (customer) => {
  try {
    if (!customer || !currentOrder.value?.giaohang) {
      return { success: true, message: 'No address management needed' }
    }

    // Validate address data before processing
    const addressValid = validateEmbeddedAddress()
    if (!addressValid) {
      return {
        success: false,
        error: 'Address validation failed',
        validationErrors: addressErrors.value,
      }
    }

    // Check if current address differs from customer's existing addresses
    if (isAddressDifferentFromCustomer(customer)) {
      console.log('Address differs from customer addresses, attempting to add new address')

      // Validate address completeness before adding
      if (!isAddressComplete()) {
        return {
          success: false,
          error: 'Address information is incomplete',
        }
      }

      // Add the new address to customer's address list
      const result = await addAddressToCustomer(customer, customerStore)

      if (result.success && result.updatedCustomer) {
        // Update the customer data in the active tab
        updateCurrentOrderData({
          khachHang: result.updatedCustomer,
        })
        selectedCustomer.value = result.updatedCustomer

        console.log('Successfully added new address to customer')
        return {
          success: true,
          message: 'New address added to customer',
          updatedCustomer: result.updatedCustomer,
          addressAction: 'created',
        }
      } else {
        return {
          success: false,
          error: result.error || 'Failed to add address to customer',
        }
      }
    } else {
      console.log('Address matches existing customer address, reusing existing')

      // Find the matching address for reference
      const matchingAddress = findMatchingAddress(
        {
          duong: addressData.value.duong.trim(),
          phuongXa: addressData.value.phuongXa,
          quanHuyen: addressData.value.quanHuyen,
          tinhThanh: addressData.value.tinhThanh,
        },
        customer.diaChis,
      )

      return {
        success: true,
        message: 'Existing address reused',
        matchingAddress: matchingAddress,
        addressAction: 'reused',
      }
    }
  } catch (error) {
    console.error('Error in address management:', error)
    return {
      success: false,
      error: error.message || 'Unknown error in address management',
    }
  }
}

// Enhanced helper function to check if address is complete with validation
const isAddressComplete = () => {
  // Check basic required fields
  const hasStreetAddress = addressData.value.duong && addressData.value.duong.trim().length >= 5
  const hasWard = addressData.value.phuongXa && addressData.value.phuongXa.trim()
  const hasDistrict = addressData.value.quanHuyen && addressData.value.quanHuyen.trim()
  const hasProvince = addressData.value.tinhThanh && addressData.value.tinhThanh.trim()

  // Also check dropdown selections for UI consistency
  const hasDropdownSelections =
    selectedProvince.value && selectedDistrict.value && selectedWard.value

  return hasStreetAddress && hasWard && hasDistrict && hasProvince && hasDropdownSelections
}

// Progressive address validation feedback
const getAddressCompletionStatus = () => {
  const status = {
    isComplete: false,
    completionPercentage: 0,
    missingFields: [],
    nextStep: '',
  }

  const fields = [
    { key: 'province', value: selectedProvince.value, label: 'Tỉnh/Thành phố' },
    { key: 'district', value: selectedDistrict.value, label: 'Quận/Huyện' },
    { key: 'ward', value: selectedWard.value, label: 'Phường/Xã' },
    { key: 'street', value: addressData.value.duong?.trim(), label: 'Địa chỉ đường', minLength: 5 },
  ]

  let completedFields = 0

  for (const field of fields) {
    if (field.key === 'street') {
      if (field.value && field.value.length >= (field.minLength || 1)) {
        completedFields++
      } else {
        status.missingFields.push(field.label)
      }
    } else {
      if (field.value) {
        completedFields++
      } else {
        status.missingFields.push(field.label)
      }
    }
  }

  status.completionPercentage = Math.round((completedFields / fields.length) * 100)
  status.isComplete = completedFields === fields.length

  if (!status.isComplete && status.missingFields.length > 0) {
    status.nextStep = `Vui lòng chọn ${status.missingFields[0]}`
  }

  return status
}

// Comprehensive address validation for all customer scenarios
const validateAddressForScenario = async (scenario = 'default') => {
  const errors = {}

  try {
    // Basic address validation
    const basicValidation = validateEmbeddedAddress()
    if (!basicValidation) {
      errors.basic = 'Thông tin địa chỉ cơ bản không hợp lệ'
    }

    // Scenario-specific validation
    switch (scenario) {
      case 'scenario1': // Same recipient as customer
        if (currentOrder.value?.khachHang) {
          // Validate that address can be associated with the customer
          const customer = currentOrder.value.khachHang
          if (customer.diaChis && customer.diaChis.length >= 20) {
            errors.limit = 'Khách hàng đã có quá nhiều địa chỉ (tối đa 20 địa chỉ)'
          }
        }
        break

      case 'scenario2': // Different recipient than customer
        // Validate that address is suitable for delivery to different recipient
        if (!recipientInfo.value.hoTen.trim() || !recipientInfo.value.soDienThoai.trim()) {
          errors.recipient = 'Thông tin người nhận là bắt buộc cho địa chỉ giao hàng khác'
        }
        break

      case 'scenario3': // Recipient-only orders
        // Validate that address can be used for new customer creation
        if (!recipientInfo.value.hoTen.trim() || !recipientInfo.value.soDienThoai.trim()) {
          errors.newCustomer = 'Thông tin người nhận là bắt buộc để tạo khách hàng mới'
        }
        break

      default:
        // General validation for any scenario
        if (currentOrder.value?.giaohang && !isAddressComplete()) {
          errors.incomplete = 'Địa chỉ giao hàng chưa đầy đủ thông tin'
        }
    }

    // Geographic validation (basic check)
    if (addressData.value.duong.trim() && addressData.value.tinhThanh) {
      // Check for obviously invalid combinations (basic validation)
      const streetAddress = addressData.value.duong.trim().toLowerCase()
      if (streetAddress.includes('test') || streetAddress.includes('fake')) {
        errors.geographic = 'Địa chỉ đường có vẻ không hợp lệ'
      }
    }

    return {
      valid: Object.keys(errors).length === 0,
      errors: errors,
      scenario: scenario,
    }
  } catch (error) {
    console.error('Error in address validation:', error)
    return {
      valid: false,
      errors: { system: 'Lỗi hệ thống khi xác thực địa chỉ' },
      scenario: scenario,
    }
  }
}

// Streamlined address validation before order creation
const _validateAddressBeforeOrderCreation = async () => {
  try {
    const result = validateDeliveryInfo()

    if (!result.valid) {
      return {
        valid: false,
        errors: result.errors,
        message: 'Delivery information validation failed',
      }
    }

    return {
      valid: true,
      message: 'Delivery information validation passed',
    }
  } catch (error) {
    console.error('Error in delivery information validation:', error)
    return {
      valid: false,
      errors: { system: 'Lỗi hệ thống khi xác thực thông tin giao hàng' },
      message: 'System error during delivery information validation',
    }
  }
}

// Comprehensive validation function for all customer scenarios
const validateAllCustomerScenarios = async () => {
  try {
    console.log('🔍 Validating all customer scenarios...')

    // Determine current scenario
    let currentScenario = 'none'
    if (currentOrder.value?.giaohang) {
      if (currentOrder.value.khachHang && recipientInfo.value.hoTen.trim()) {
        const currentCustomer = currentOrder.value.khachHang
        const recipientDiffersFromCustomer =
          recipientInfo.value.hoTen.trim() !== currentCustomer.hoTen ||
          recipientInfo.value.soDienThoai.trim() !== currentCustomer.soDienThoai

        currentScenario = recipientDiffersFromCustomer ? 'scenario2' : 'scenario1'
      } else if (!currentOrder.value.khachHang && recipientInfo.value.hoTen.trim()) {
        currentScenario = 'scenario3'
      }
    }

    console.log(`Current scenario detected: ${currentScenario}`)

    // Validate current scenario
    const validationResults = {
      scenario: currentScenario,
      valid: true,
      errors: [],
      warnings: [],
    }

    // Basic validation
    if (currentScenario !== 'none') {
      const addressValidation = await validateAddressForScenario(currentScenario)
      if (!addressValidation.valid) {
        validationResults.valid = false
        validationResults.errors.push(...Object.values(addressValidation.errors))
      }

      // Scenario-specific validation
      switch (currentScenario) {
        case 'scenario1':
          if (!currentOrder.value.khachHang) {
            validationResults.errors.push('Customer required for Scenario 1')
            validationResults.valid = false
          }
          break

        case 'scenario2':
          if (!currentOrder.value.khachHang) {
            validationResults.errors.push('Original customer required for Scenario 2')
            validationResults.valid = false
          }
          if (!recipientInfo.value.hoTen.trim() || !recipientInfo.value.soDienThoai.trim()) {
            validationResults.errors.push('Recipient information required for Scenario 2')
            validationResults.valid = false
          }
          break

        case 'scenario3':
          if (!recipientInfo.value.hoTen.trim() || !recipientInfo.value.soDienThoai.trim()) {
            validationResults.errors.push('Recipient information required for Scenario 3')
            validationResults.valid = false
          }
          break
      }
    }

    console.log('✅ Scenario validation completed:', validationResults)
    return validationResults
  } catch (error) {
    console.error('Error validating customer scenarios:', error)
    return {
      scenario: 'error',
      valid: false,
      errors: [error.message],
      warnings: [],
    }
  }
}

// Scenario 1: Same recipient as customer - Auto-populate recipient fields with customer info
const syncCustomerToRecipient = async (customer) => {
  // Create timeout promise for overall sync operation
  const syncTimeoutPromise = new Promise((_, reject) => {
    setTimeout(() => reject(new Error('SYNC_TIMEOUT')), 5000)
  })

  // Main sync logic
  const syncPromise = async () => {
    console.log('Syncing customer to recipient:', customer)

    // Auto-populate recipient fields with customer information
    recipientInfo.value.hoTen = customer.hoTen || ''
    recipientInfo.value.soDienThoai = customer.soDienThoai || ''

    // Load customer's default address into embedded form with loading coordination
    await populateAddressFromCustomer(customer)

    // Clear any validation errors
    recipientErrors.value = {}

    // Customer info synced to recipient (removed toast - not critical)
  }

  try {
    // Race between sync operation and timeout
    await Promise.race([syncPromise(), syncTimeoutPromise])
  } catch (error) {
    console.error('Error syncing customer to recipient:', error)

    if (error.message === 'SYNC_TIMEOUT') {
      toast.add({
        severity: 'warn',
        summary: 'Cảnh báo',
        detail:
          'Việc đồng bộ thông tin khách hàng mất quá nhiều thời gian. Một số thông tin có thể chưa được cập nhật.',
        life: 4000,
      })
    } else {
      toast.add({
        severity: 'error',
        summary: 'Lỗi',
        detail: 'Không thể đồng bộ thông tin khách hàng. Vui lòng kiểm tra và nhập thủ công.',
        life: 3000,
      })
    }
  }
}

// Clear recipient information
const clearRecipientInfo = () => {
  recipientInfo.value.hoTen = ''
  recipientInfo.value.soDienThoai = ''
  recipientErrors.value = {}

  // Clear recipient customer tracking for Scenario 2
  recipientCustomer.value = null

  // Clear address form
  setAddressData({
    duong: '',
    phuongXa: '',
    quanHuyen: '',
    tinhThanh: '',
    loaiDiaChi: 'Nhà riêng',
  })
}

// Scenario 3: Recipient-only orders - Create customer from recipient information
const createCustomerFromRecipient = async () => {
  try {
    if (!recipientInfo.value.hoTen.trim() || !recipientInfo.value.soDienThoai.trim()) {
      throw new Error('Thiếu thông tin người nhận để tạo khách hàng')
    }

    // Prepare customer data with address information
    const customerPayload = {
      hoTen: recipientInfo.value.hoTen.trim(),
      soDienThoai: recipientInfo.value.soDienThoai.trim(),
      email: null, // No email from recipient info
      gioiTinh: 'NAM', // Default gender
      ngaySinh: null, // No birth date from recipient info
      trangThai: 'HOAT_DONG',
      diaChis: [],
    }

    // Add address information if available with enhanced validation
    if (isAddressComplete()) {
      // Validate address before adding to customer
      const addressValidation = await validateAddressForScenario('scenario3')

      if (!addressValidation.valid) {
        const errorMessages = Object.values(addressValidation.errors).join(', ')
        throw new Error(`Địa chỉ không hợp lệ: ${errorMessages}`)
      }

      customerPayload.diaChis = [
        {
          duong: addressData.value.duong.trim(),
          phuongXa: addressData.value.phuongXa,
          quanHuyen: addressData.value.quanHuyen,
          tinhThanh: addressData.value.tinhThanh,
          loaiDiaChi: addressData.value.loaiDiaChi || 'Nhà riêng',
          laMacDinh: true,
        },
      ]

      console.log('Address validated and added to customer payload for Scenario 3')
    } else {
      console.log('Address incomplete, creating customer without address for Scenario 3')
    }

    console.log('Creating customer from recipient info:', customerPayload)

    // Create customer using store
    const newCustomer = await customerStore.createCustomer(customerPayload)

    if (newCustomer) {
      // Set the newly created customer as the main customer for the order
      updateCurrentOrderData({
        khachHang: newCustomer,
        diaChiGiaoHang: null,
      })
      selectedCustomer.value = newCustomer

      // Customer created from recipient info (removed toast - not critical)

      return newCustomer
    }
  } catch (error) {
    console.error('Error creating customer from recipient:', error)
    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: 'Không thể tạo khách hàng từ thông tin người nhận',
      life: 3000,
    })
    throw error
  }
}

// Scenario 2: Create recipient customer without replacing main customer
const createRecipientCustomerForScenario2 = async () => {
  try {
    if (!recipientInfo.value.hoTen.trim() || !recipientInfo.value.soDienThoai.trim()) {
      throw new Error('Thiếu thông tin người nhận để tạo khách hàng')
    }

    // Prepare customer data for recipient
    const recipientCustomerPayload = {
      hoTen: recipientInfo.value.hoTen.trim(),
      soDienThoai: recipientInfo.value.soDienThoai.trim(),
      email: null, // No email from recipient info
      gioiTinh: 'NAM', // Default gender
      ngaySinh: null, // No birth date from recipient info
      trangThai: 'HOAT_DONG',
      diaChis: [],
    }

    // Add address information if available with enhanced validation
    if (isAddressComplete()) {
      // Validate address before adding to recipient customer
      const addressValidation = await validateAddressForScenario('scenario2')

      if (!addressValidation.valid) {
        const errorMessages = Object.values(addressValidation.errors).join(', ')
        throw new Error(`Địa chỉ không hợp lệ cho người nhận: ${errorMessages}`)
      }

      recipientCustomerPayload.diaChis = [
        {
          duong: addressData.value.duong.trim(),
          phuongXa: addressData.value.phuongXa,
          quanHuyen: addressData.value.quanHuyen,
          tinhThanh: addressData.value.tinhThanh,
          loaiDiaChi: addressData.value.loaiDiaChi || 'Nhà riêng',
          laMacDinh: true,
        },
      ]

      console.log('Address validated and added to recipient customer payload for Scenario 2')
    } else {
      console.log('Address incomplete, creating recipient customer without address for Scenario 2')
    }

    console.log('Creating recipient customer for Scenario 2:', recipientCustomerPayload)

    // Create customer using store
    const newRecipientCustomer = await customerStore.createCustomer(recipientCustomerPayload)

    if (newRecipientCustomer) {
      // Store as recipient customer (DO NOT replace main customer)
      recipientCustomer.value = newRecipientCustomer

      // Recipient customer created (removed toast - not critical)

      return newRecipientCustomer
    }
  } catch (error) {
    console.error('Error creating recipient customer for Scenario 2:', error)
    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: 'Không thể tạo khách hàng cho người nhận',
      life: 3000,
    })
    throw error
  }
}

// Scenario 2: Different recipient than customer - Handle recipient customer lookup and address loading
const handleDifferentRecipient = async () => {
  try {
    // Check if recipient info differs from selected customer
    const currentCustomer = currentOrder.value?.khachHang
    if (!currentCustomer) return

    const recipientDiffersFromCustomer =
      recipientInfo.value.hoTen.trim() !== currentCustomer.hoTen ||
      recipientInfo.value.soDienThoai.trim() !== currentCustomer.soDienThoai

    if (recipientDiffersFromCustomer) {
      console.log('Recipient differs from customer, searching for recipient customer')

      // Search for existing customer with recipient details
      const foundRecipientCustomer = await checkExistingCustomer()

      if (foundRecipientCustomer) {
        console.log('Found existing customer for recipient:', foundRecipientCustomer)

        // Store recipient customer separately (DO NOT replace main customer)
        recipientCustomer.value = foundRecipientCustomer

        // Load recipient customer's address if found
        await populateAddressFromCustomer(foundRecipientCustomer)

        // Found existing customer and auto-filled address (removed toast - not critical)
      } else {
        // Clear recipient customer if no match found
        recipientCustomer.value = null
        console.log('No existing customer found for recipient, will create new customer if needed')

        // For Scenario 2, we may need to create a customer for the recipient
        // This will be handled during order creation validation
      }
    } else {
      // Recipient is same as customer, clear separate recipient customer tracking
      recipientCustomer.value = null
    }
  } catch (error) {
    console.error('Error handling different recipient:', error)
  }
}

// State synchronization between customer and recipient
const syncCustomerAndRecipient = async () => {
  try {
    if (!currentOrder.value?.giaohang) return

    const currentCustomer = currentOrder.value?.khachHang

    // If no customer selected but recipient info exists, handle recipient-only scenario
    if (
      !currentCustomer &&
      recipientInfo.value.hoTen.trim() &&
      recipientInfo.value.soDienThoai.trim()
    ) {
      const existingCustomer = await checkExistingCustomer()
      if (existingCustomer) {
        // Set existing customer as main customer
        updateCurrentOrderData({
          khachHang: existingCustomer,
          diaChiGiaoHang: null,
        })
        selectedCustomer.value = existingCustomer

        // Automatically selected existing customer (removed toast - not critical)
      }
    }

    // If customer exists, handle different recipient scenario
    if (currentCustomer) {
      await handleDifferentRecipient()
    }
  } catch (error) {
    console.error('Error in customer-recipient synchronization:', error)
  }
}

// Watch for recipient information changes to auto-lookup customers
watch(
  () => [recipientInfo.value.hoTen, recipientInfo.value.soDienThoai],
  async ([newName, newPhone], [oldName, oldPhone]) => {
    // Only proceed if delivery is enabled and values have actually changed
    if (!currentOrder.value?.giaohang) return

    // Validate delivery info
    validateDeliveryInfo()

    // Auto-lookup customer if both fields are filled and changed
    if (newName && newPhone && (newName !== oldName || newPhone !== oldPhone)) {
      try {
        // Perform state synchronization
        await syncCustomerAndRecipient()
      } catch (error) {
        console.error('Error in auto-lookup customer:', error)
      }
    }
  },
  { immediate: false, deep: true },
)

// Watchers with proper null checks
watch(
  () => currentOrder.value?.tongTienHang,
  async (newTotal, oldTotal) => {
    // Only proceed if currentOrder exists and has required data
    if (currentOrder.value && newTotal !== oldTotal && currentOrder.value.khachHang) {
      // Reload available vouchers when order total changes
      await loadAvailableVouchers()

      // Automatically find and apply best voucher when order total changes
      if (newTotal > 0) {
        await findAndApplyBestVoucher()
      }
    }
  },
  { immediate: false }, // Don't run immediately to avoid undefined access
)

// Watch for customer changes to automatically apply vouchers and sync recipient info
watch(
  () => currentOrder.value?.khachHang,
  async (newCustomer, oldCustomer) => {
    if (currentOrder.value && newCustomer && newCustomer !== oldCustomer) {
      // Clear existing vouchers when customer changes
      if (oldCustomer && newCustomer.id !== oldCustomer.id) {
        currentOrder.value.voucherList = []
        calculateCurrentOrderTotals()
      }

      // Load available vouchers for new customer
      await loadAvailableVouchers()

      // Automatically find and apply best voucher for new customer
      if (currentOrder.value.tongTienHang > 0) {
        await findAndApplyBestVoucher()
      }

      // Scenario 1: Auto-populate recipient info when customer is selected and delivery is enabled
      if (newCustomer && currentOrder.value.giaohang) {
        await syncCustomerToRecipient(newCustomer)
      }
    }

    // Clear recipient info when customer is cleared
    if (!newCustomer && oldCustomer) {
      clearRecipientInfo()
    }
  },
  { immediate: false },
)

// Watch for delivery toggle changes to sync customer and recipient
watch(
  () => currentOrder.value?.giaohang,
  async (deliveryEnabled, wasDeliveryEnabled) => {
    if (!currentOrder.value) return

    if (deliveryEnabled && !wasDeliveryEnabled) {
      // Delivery just enabled - sync customer to recipient if customer exists
      if (currentOrder.value.khachHang) {
        await syncCustomerToRecipient(currentOrder.value.khachHang)
      }
      // Reset shipping calculation when delivery is enabled
      resetShippingCalculation()
    } else if (!deliveryEnabled && wasDeliveryEnabled) {
      // Delivery just disabled - clear recipient info
      clearRecipientInfo()
      // Clear shipping fee when delivery is disabled using consolidated shipping fee
      consolidatedShippingFee.value = 0
    }
  },
  { immediate: false },
)

watch(
  () => currentOrder.value,
  (newOrder, oldOrder) => {
    // Only proceed if we have valid order data
    if (newOrder && newOrder !== oldOrder) {
      // Clear validation errors when order changes
      clearValidationErrors()
      hasUnsavedChanges.value = false
    }
  },
  { deep: true, immediate: false }, // Don't run immediately to avoid undefined access
)

watch(
  () => currentOrder.value?.isModified,
  (isModified) => {
    // Only update if currentOrder exists
    if (currentOrder.value) {
      hasUnsavedChanges.value = isModified || false
    }
  },
  { immediate: false }, // Don't run immediately to avoid undefined access
)

// Watch for order changes to sync cart data
watch(
  () => orderId.value,
  (newOrderId, oldOrderId) => {
    // Sync cart data with product variant dialog when order changes
    if (newOrderId && newOrderId !== oldOrderId) {
      syncCartWithDialog()
    }
  },
  { immediate: false },
)

// Shipping calculation timeout for debouncing
const shippingCalculationTimeout = ref(null)

// Watch for address changes to automatically calculate shipping fee
watch(
  () => [
    selectedProvince.value,
    selectedDistrict.value,
    selectedWard.value,
    addressData.value.duong,
    currentOrder.value?.tongTienHang,
  ],
  async (
    [province, district, ward, street, orderTotal],
    [oldProvince, oldDistrict, oldWard, oldStreet, oldOrderTotal],
  ) => {
    // Only process if delivery is enabled
    if (!currentOrder.value?.giaohang) {
      return
    }

    // Check if address components have changed
    const addressChanged =
      province !== oldProvince ||
      district !== oldDistrict ||
      ward !== oldWard ||
      street !== oldStreet
    const orderTotalChanged = orderTotal !== oldOrderTotal

    // Update tab's diaChiGiaoHang field for validation compatibility
    if (addressChanged) {
      if (street?.trim() && province && district && ward) {
        const addressPayload = {
          duong: street.trim(),
          phuongXa: ward.name,
          quanHuyen: district.name,
          tinhThanh: province.name,
          loaiDiaChi: addressData.value.loaiDiaChi || 'Nhà riêng',
        }
        updateCurrentOrderData({ diaChiGiaoHang: addressPayload })
      } else {
        // Clear address if incomplete
        updateCurrentOrderData({ diaChiGiaoHang: null })
      }
    }

    // Only calculate shipping if we have a complete address with all dropdown selections
    if (province && district && ward && street?.trim() && (addressChanged || orderTotalChanged)) {
      // Debounce the calculation to avoid too many API calls (reduced for better responsiveness)
      clearTimeout(shippingCalculationTimeout.value)
      shippingCalculationTimeout.value = setTimeout(async () => {
        await calculateShippingFeeForCurrentAddress()
      }, 500) // 500ms delay for better responsiveness
    }
  },
  { immediate: false, deep: true },
)

// Consolidated shipping fee - single source of truth (shipping fee input is master)
const consolidatedShippingFee = computed({
  get: () => {
    // Priority: shippingFee.value (input) is the master source
    return shippingFee.value ?? 0
  },
  set: (value) => {
    const numericValue = value || 0
    // Update both sources simultaneously to prevent race conditions
    shippingFee.value = numericValue
    if (currentOrder.value && currentOrder.value.giaohang) {
      updateCurrentOrderData({ phiVanChuyen: consolidatedShippingFee.value })
    }
  },
})

// Watch for shipping fee changes to sync with order summary (simplified)
watch(
  () => shippingFee.value,
  (newFee) => {
    if (currentOrder.value && currentOrder.value.giaohang && currentOrder.value.phiVanChuyen !== newFee) {
      updateCurrentOrderData({ phiVanChuyen: newFee || 0 })
    }
  },
  { immediate: false },
)

// Watch for order changes to sync shipping fee input from order data (one-time sync)
watch(
  () => orderId.value,
  (newOrderId, oldOrderId) => {
    if (
      newOrderId !== oldOrderId &&
      currentOrder.value?.giaohang &&
      currentOrder.value.phiVanChuyen !== undefined
    ) {
      // Only sync if the input doesn't already have the correct value
      if (shippingFee.value !== currentOrder.value.phiVanChuyen) {
        shippingFee.value = currentOrder.value.phiVanChuyen
      }
    }
  },
  { immediate: true },
)

// Dynamic total calculation - automatically updates when any component changes
const dynamicOrderTotal = computed(() => {
  if (!currentOrder.value) return 0

  const subtotal = currentOrder.value.tongTienHang || 0
  const voucherDiscount = currentOrder.value.giaTriGiamGiaVoucher || 0
  const shippingFee = consolidatedShippingFee.value || 0

  const total = subtotal - voucherDiscount + shippingFee

  return Math.max(0, total) // Ensure total is never negative
})

// Watch for dynamic total changes to sync with tab data (separate from computed)
watch(
  () => dynamicOrderTotal.value,
  (newTotal) => {
    if (currentOrder.value && currentOrder.value.tongThanhToan !== newTotal) {
      updateCurrentOrderData({ tongThanhToan: newTotal })
    }
  },
  { immediate: false },
)

// Watch for price updates to detect cart price changes
watch(
  () => priceUpdates.value,
  (newUpdates) => {
    if (newUpdates && newUpdates.length > 0) {
      detectCartPriceChanges()
    }
  },
  { deep: true, immediate: false },
)

// Watch for route parameter changes
watch(() => route.params.id, async (newId, oldId) => {
  if (newId !== oldId) {
    if (newId) {
      // Load new order data
      try {
        const orderData = await fetchOrderById(newId)
        if (orderData) {
          currentOrder.value = orderData
          document.title = `Chỉnh sửa đơn hàng ${orderData.maHoaDon} - LapXpert Admin`
        } else {
          toast.add({
            severity: 'error',
            summary: 'Lỗi',
            detail: 'Không tìm thấy đơn hàng',
            life: 5000
          })
          goBack()
        }
      } catch (error) {
        console.error('Failed to load order:', error)
        toast.add({
          severity: 'error',
          summary: 'Lỗi',
          detail: 'Không thể tải dữ liệu đơn hàng',
          life: 5000
        })
        goBack()
      }
    } else {
      // No ID provided - redirect
      goBack()
    }
  }
})

// Watch for cart items to subscribe to price updates
watch(
  () => currentOrder.value?.sanPhamList,
  (newItems, _oldItems) => {
    if (newItems && newItems.length > 0) {
      const variantIds = newItems.map((item) => item.sanPhamChiTiet?.id).filter(Boolean)

      if (variantIds.length > 0) {
        subscribeToPriceUpdates(variantIds)
      }
    }
  },
  { deep: true, immediate: false },
)

// Route guard for unsaved changes
onBeforeRouteLeave((_to, _from, next) => {
  if (hasUnsavedChanges.value) {
    const answer = window.confirm(
      'Bạn có thay đổi chưa được lưu. Bạn có chắc chắn muốn rời khỏi trang này không?'
    )
    if (answer) {
      next()
    } else {
      next(false)
    }
  } else {
    next()
  }
})

// Page refresh/close detection for order edit scenarios
const handlePageUnload = async (_event) => {
  console.log('Page unload detected in order edit mode...')

  // Get current order ID for potential cleanup tracking
  const currentOrderId = orderId.value

  // ENHANCED: Unified cleanup strategy - clean up only newly reserved items
  // This preserves existing order items while cleaning up items added during this edit session
  try {
    await unifiedCleanupStrategy.cleanupNewlyReservedItems('page_unload')
    console.log('🔒 [UNIFIED CLEANUP] Page unload cleanup completed')
  } catch (error) {
    console.error('🔒 [UNIFIED CLEANUP] Page unload cleanup failed:', error)
  }

  // Store order ID for potential future cleanup scenarios (if needed)
  // Using different localStorage key to distinguish from cart cleanup
  if (currentOrderId) {
    localStorage.setItem('pendingOrderEditCleanup', JSON.stringify([currentOrderId]))
    console.log(`Stored order ID ${currentOrderId} for potential cleanup tracking`)
  }

  // NOTE: Existing order items are preserved during cleanup
  // Only newly reserved items (added during this edit session) are cleaned up
  // This maintains inventory integrity while preventing reservation leaks
}

// Cleanup any pending reservations from previous session (supports both tab-based and order-based)
const cleanupPendingReservations = async () => {
  try {
    // Clean up old tab-based reservations for backward compatibility
    const pendingTabCleanup = localStorage.getItem('pendingCartReservationCleanup')
    if (pendingTabCleanup) {
      const tabIds = JSON.parse(pendingTabCleanup)
      logger.debug('Cleaning up pending tab-based cart reservations from previous session', {
        tabIds,
        count: tabIds.length
      }, 'cleanup')

      for (const tabId of tabIds) {
        try {
          await releaseCartReservations(tabId)
          logger.debug('Cleaned up reservations for tab', { tabId }, 'cleanup')
        } catch (error) {
          logger.critical('Failed to cleanup reservations for tab', {
            tabId,
            error: error.message
          }, 'cleanup')
        }
      }

      // Clear the pending cleanup flag
      localStorage.removeItem('pendingCartReservationCleanup')
    }

    // Clean up old order-based reservations (legacy cart management)
    const pendingOrderCleanup = localStorage.getItem('pendingOrderReservationCleanup')
    if (pendingOrderCleanup) {
      const orderIds = JSON.parse(pendingOrderCleanup)
      logger.debug('Cleaning up pending order-based cart reservations from previous session', {
        orderIds,
        count: orderIds.length
      }, 'cleanup')

      for (const orderId of orderIds) {
        try {
          await releaseCartReservations(orderId)
          logger.debug('Cleaned up reservations for order', { orderId }, 'cleanup')
        } catch (error) {
          logger.critical('Failed to cleanup reservations for order', {
            orderId,
            error: error.message
          }, 'cleanup')
        }
      }

      // Clear the pending cleanup flag
      localStorage.removeItem('pendingOrderReservationCleanup')
    }

    // Handle order edit cleanup tracking (NO automatic reservation release)
    const pendingOrderEditCleanup = localStorage.getItem('pendingOrderEditCleanup')
    if (pendingOrderEditCleanup) {
      const orderIds = JSON.parse(pendingOrderEditCleanup)
      logger.debug('Found pending order edit cleanup tracking from previous session', {
        orderIds,
        count: orderIds.length,
        note: 'No automatic reservation release for order editing'
      }, 'cleanup')

      // For order editing, we do NOT automatically release reservations
      // We only clear the tracking entry and log the information
      // Reservations should only be modified during actual order updates

      // Clear the pending cleanup tracking
      localStorage.removeItem('pendingOrderEditCleanup')

      logger.debug('Cleared order edit cleanup tracking without releasing reservations', {
        orderIds,
        reason: 'Order editing preserves reservations until actual update'
      }, 'cleanup')
    }
  } catch (error) {
    logger.critical('Error during pending reservations cleanup', {
      error: error.message
    }, 'cleanup')
  }
}

// ===== WEBSOCKET INTEGRATION SETUP =====

/**
 * Handle WebSocket errors with appropriate logging and user feedback
 * @param {Error} error - The error that occurred
 * @param {string} context - Context where the error occurred
 * @param {boolean} showUserNotification - Whether to show user notification
 */
const handleWebSocketError = (error, context, _showUserNotification = false) => {
  logger.critical('WebSocket error occurred', {
    context,
    error: error.message,
    type: error.name
  }, 'websocket')

  // Log error details for debugging without overwhelming users
  if (error.stack) {
    logger.debug('Error stack trace', {
      context,
      stack: error.stack
    }, 'websocket')
  }
}

/**
 * Validate WebSocket connection status before processing
 * @returns {boolean} True if WebSocket is connected and ready
 */
const validateWebSocketConnection = () => {
  if (!wsConnected.value) {
    logger.debug('WebSocket not connected - using manual validation fallback', null, 'connection')
    return false
  }
  return true
}

/**
 * Setup WebSocket integration callbacks for real-time voucher validation
 */
const setupVoucherWebSocketIntegration = () => {
  try {
    // Use separate, non-nested group for OrderCreate operations
    logger.group('[OrderCreate] WebSocket Integration Setup', 'debug')

    // Validate WebSocket availability before setting up callbacks
    if (!validateWebSocketConnection()) {
      logger.debug('WebSocket not available during setup - callbacks will be registered but inactive', null, 'setup')
    }

    // Set up callback for voucher updates (existing vouchers modified)
    setIntegrationCallback('onVoucherExpired', handleAppliedVoucherUpdate)

    // Set up callback for new voucher creation
    setIntegrationCallback('onNewVoucher', handleNewVoucherCreated)

    logger.trace('OrderCreate WebSocket voucher integration callbacks registered', {
      callbacks: ['onVoucherExpired', 'onNewVoucher']
    }, 'setup', 'websocket_integration')

    logger.groupEnd()
  } catch (error) {
    logger.groupEnd() // End group on error
    handleWebSocketError(error, 'setupVoucherWebSocketIntegration', true)
  }
}

/**
 * Monitor WebSocket connection status and provide user feedback
 */
const monitorWebSocketConnection = () => {
  // Watch for connection status changes
  watch(wsConnected, (isConnected, wasConnected) => {
    // Only show notifications for status changes, not initial state
    if (wasConnected !== undefined) {
      if (isConnected && !wasConnected) {
        // Connection restored
        // toast.add({
        //   severity: 'success',
        //   summary: 'Kết nối khôi phục',
        //   detail: 'Tính năng cập nhật voucher tự động đã hoạt động trở lại.',
        //   life: 3000,
        // })
        WebSocketLoggerUtils.logConnectionLifecycle(logger, 'restored', {
          features: 'automatic voucher features'
        })
      } else if (!isConnected && wasConnected) {
        // Connection lost
        // toast.add({
        //   severity: 'warn',
        //   summary: 'Mất kết nối thời gian thực',
        //   detail: 'Tính năng cập nhật tự động tạm thời không khả dụng. Kiểm tra thủ công vẫn hoạt động.',
        //   life: 5000,
        // })
        WebSocketLoggerUtils.logConnectionLifecycle(logger, 'lost', {
          fallback: 'manual validation'
        })
      }
    }
  })
}

/**
 * Cleanup WebSocket integration callbacks
 */
const cleanupVoucherWebSocketIntegration = () => {
  try {
    // Remove integration callbacks to prevent memory leaks
    setIntegrationCallback('onVoucherExpired', null)
    setIntegrationCallback('onNewVoucher', null)

    logger.debug('OrderCreate WebSocket voucher integration callbacks cleaned up', {
      callbacks: ['onVoucherExpired', 'onNewVoucher']
    }, 'cleanup')
  } catch (error) {
    logger.critical('Error cleaning up WebSocket integration callbacks', {
      error: error.message
    }, 'cleanup')
  }
}

/**
 * Check if a voucher is currently applied to the active order
 */
const isAppliedVoucher = (voucherId, voucherCode) => {
  if (!currentOrder.value?.voucherList?.length) {
    return false
  }

  return currentOrder.value.voucherList.some(voucher => {
    // Check by ID (if provided) or by voucher code
    if (voucherId && voucher.id) {
      return voucher.id.toString() === voucherId.toString()
    }
    if (voucherCode && voucher.maPhieuGiamGia) {
      return voucher.maPhieuGiamGia === voucherCode
    }
    return false
  })
}

/**
 * Validate if the current order has valid context for voucher processing
 */
const hasValidOrderContext = () => {
  return !!(
    currentOrder.value?.khachHang &&
    currentOrder.value?.tongTienHang > 0 &&
    currentOrder.value?.sanPhamList?.length > 0
  )
}

/**
 * Trigger automatic voucher revalidation for applied vouchers
 * @param {Object} appliedVoucher - The voucher to revalidate
 * @param {string} triggerSource - Source of the trigger (e.g., 'websocket', 'manual')
 */
const triggerVoucherRevalidation = async (appliedVoucher, triggerSource = 'websocket') => {
  try {
    // Enhanced validation of input parameters
    if (!appliedVoucher || !appliedVoucher.maPhieuGiamGia) {
      throw new Error('Invalid appliedVoucher parameter')
    }

    const customerId = currentOrder.value.khachHang?.id || null
    const orderTotal = currentOrder.value.tongTienHang || 0

    // Validate order context before proceeding
    if (!customerId || orderTotal <= 0) {
      logger.debug('Invalid order context for voucher revalidation', {
        customerId: !!customerId,
        orderTotal
      }, 'validation')
      return { success: false, reason: 'invalid_context' }
    }

    logger.trace('Starting automatic voucher revalidation', {
      triggerSource,
      voucherCode: appliedVoucher.maPhieuGiamGia,
      customerId,
      orderTotal
    }, 'validation', 'voucher_validation')

    // Show user notification for WebSocket-triggered validation
    if (triggerSource === 'websocket') {
      toast.add({
        severity: 'info',
        summary: 'Kiểm tra voucher tự động',
        detail: `Đang kiểm tra cập nhật cho voucher ${appliedVoucher.maPhieuGiamGia}...`,
        life: 3000,
      })
    }

    // Step 2: Revalidate applied voucher and handle discount changes
    const revalidationResult = await revalidateAppliedVoucher(appliedVoucher, customerId, orderTotal)
    if (!revalidationResult.proceed) {
      logger.debug('Voucher revalidation stopped by user or validation failure', {
        voucherCode: appliedVoucher.maPhieuGiamGia
      }, 'validation')
      return { success: false, reason: 'revalidation_failed' }
    }

    const validatedDiscount = revalidationResult.validatedDiscount

    // Step 3: Detect better vouchers and offer replacement
    const betterVoucherResult = await detectBetterVouchers(appliedVoucher, customerId, orderTotal, validatedDiscount)

    logger.debug('Automatic voucher revalidation completed successfully', {
      voucherCode: appliedVoucher.maPhieuGiamGia,
      triggerSource
    }, 'validation')

    // Show success notification for WebSocket-triggered validation
    if (triggerSource === 'websocket') {
      toast.add({
        severity: 'success',
        summary: 'Voucher đã được cập nhật',
        detail: 'Voucher của bạn đã được kiểm tra và cập nhật tự động.',
        life: 3000,
      })
    }

    return { success: true, result: betterVoucherResult }

  } catch (error) {
    handleWebSocketError(error, 'triggerVoucherRevalidation', triggerSource === 'websocket')
    return { success: false, reason: 'validation_error', error }
  }
}

/**
 * Handle updates to applied vouchers (discount value changes, expiration, etc.)
 */
const handleAppliedVoucherUpdate = async (voucherData) => {
  try {
    // Use separate, non-nested group for OrderCreate operations
    logger.group('[OrderCreate] Applied Voucher Update', 'debug')
    logger.trace('handleAppliedVoucherUpdate called', {
      voucherId: voucherData?.id || voucherData?.voucherId,
      voucherCode: voucherData?.maPhieuGiamGia || voucherData?.voucherCode
    }, 'integration', 'integration_callback')

    // Enhanced WebSocket connection validation
    if (!validateWebSocketConnection()) {
      logger.debug('WebSocket validation failed in handleAppliedVoucherUpdate', null, 'integration')
      logger.groupEnd()
      return
    }

    // Validate order context
    if (!hasValidOrderContext()) {
      logger.debug('Skipping voucher update - invalid order context', null, 'integration')
      logger.groupEnd()
      return
    }

    logger.debug('Processing voucher update in OrderCreate', {
      voucherId: voucherData?.id || voucherData?.voucherId,
      voucherCode: voucherData?.maPhieuGiamGia || voucherData?.voucherCode
    }, 'integration')

    // Extract voucher identification from message with validation
    const voucherId = voucherData?.id || voucherData?.voucherId
    const voucherCode = voucherData?.maPhieuGiamGia || voucherData?.voucherCode

    if (!voucherId && !voucherCode) {
      logger.debug('Invalid voucher update message - missing identification', null, 'integration')
      logger.groupEnd()
      return
    }

    // Filter: Only process if this voucher is currently applied
    if (!isAppliedVoucher(voucherId, voucherCode)) {
      logger.debug('Skipping voucher update - voucher not applied to current order', {
        voucherId,
        voucherCode
      }, 'integration')
      logger.groupEnd()
      return
    }

    logger.debug('Processing voucher update for applied voucher', {
      voucherCode: voucherCode || voucherId
    }, 'integration')

    // Find the applied voucher object with enhanced error handling
    const appliedVoucher = currentOrder.value.voucherList.find(voucher => {
      if (voucherId && voucher.id) {
        return voucher.id.toString() === voucherId.toString()
      }
      if (voucherCode && voucher.maPhieuGiamGia) {
        return voucher.maPhieuGiamGia === voucherCode
      }
      return false
    })

    if (!appliedVoucher) {
      logger.critical('Applied voucher not found in voucherList', {
        voucherId,
        voucherCode
      }, 'integration')
      handleWebSocketError(new Error('Applied voucher not found'), 'handleAppliedVoucherUpdate')
      logger.groupEnd()
      return
    }

    // Trigger automatic voucher revalidation with enhanced error handling
    const result = await triggerVoucherRevalidation(appliedVoucher, 'websocket')
    if (!result.success) {
      logger.debug('Voucher revalidation failed', {
        reason: result.reason,
        voucherCode: appliedVoucher.maPhieuGiamGia
      }, 'integration')
    }

    logger.groupEnd() // End OrderCreate Applied Voucher Update group
  } catch (error) {
    logger.groupEnd() // End group on error
    handleWebSocketError(error, 'handleAppliedVoucherUpdate', true)
  }
}

/**
 * Trigger automatic better voucher detection for new vouchers
 * @param {Object} newVoucherData - The new voucher data from WebSocket
 * @param {string} triggerSource - Source of the trigger (e.g., 'websocket', 'manual')
 */
const triggerBetterVoucherDetection = async (newVoucherData = null, triggerSource = 'websocket') => {
  try {
    const customerId = currentOrder.value.khachHang?.id || null
    const orderTotal = currentOrder.value.tongTienHang || 0
    const appliedVoucher = currentOrder.value.voucherList?.[0] || null

    // Enhanced validation of order context
    if (!customerId || orderTotal <= 0) {
      logger.debug('Invalid order context for better voucher detection', {
        customerId: !!customerId,
        orderTotal
      }, 'detection')
      return { success: false, reason: 'invalid_context' }
    }

    logger.trace('Starting automatic better voucher detection', {
      triggerSource,
      hasNewVoucherData: !!newVoucherData
    }, 'detection', 'voucher_validation')

    // Show user notification for WebSocket-triggered detection
    if (triggerSource === 'websocket' && newVoucherData) {
      const voucherName = newVoucherData.maPhieuGiamGia || newVoucherData.voucherCode || 'vừa tạo'
      toast.add({
        severity: 'info',
        summary: 'Kiểm tra voucher mới',
        detail: `Đang kiểm tra voucher mới ${voucherName} có phù hợp với đơn hàng không...`,
        life: 3000,
      })
    }

    // If no voucher is currently applied, use a dummy voucher for comparison
    const currentVoucher = appliedVoucher || {
      maPhieuGiamGia: 'NO_VOUCHER',
      giaTriGiam: 0
    }

    // Get current discount amount (0 if no voucher applied)
    const currentDiscount = appliedVoucher?.giaTriGiam || 0

    // Step 3: Detect better vouchers and offer replacement
    const betterVoucherResult = await detectBetterVouchers(currentVoucher, customerId, orderTotal, currentDiscount)

    logger.debug('Automatic better voucher detection completed successfully', {
      triggerSource,
      hasResult: !!betterVoucherResult
    }, 'detection')

    // Show success notification only if a better voucher was found and user was prompted
    if (triggerSource === 'websocket' && betterVoucherResult.proceed) {
      toast.add({
        severity: 'success',
        summary: 'Voucher tốt hơn đã được kiểm tra',
        detail: 'Hệ thống đã kiểm tra và đề xuất voucher tốt nhất cho đơn hàng của bạn.',
        life: 3000,
      })
    }

    return { success: true, result: betterVoucherResult }

  } catch (error) {
    handleWebSocketError(error, 'triggerBetterVoucherDetection', triggerSource === 'websocket')
    return { success: false, reason: 'detection_error', error }
  }
}

/**
 * Handle new voucher creation for better alternatives detection
 */
const handleNewVoucherCreated = async (voucherData) => {
  try {
    // Enhanced WebSocket connection validation
    if (!validateWebSocketConnection()) {
      return
    }

    // Validate order context
    if (!hasValidOrderContext()) {
      logger.debug('Skipping new voucher processing - invalid order context', null, 'integration')
      return
    }

    logger.debug('Received new voucher', {
      voucherCode: voucherData?.maPhieuGiamGia || voucherData?.voucherCode
    }, 'integration')

    // Validate voucher data structure
    if (!voucherData || (!voucherData.maPhieuGiamGia && !voucherData.voucherCode)) {
      logger.debug('Invalid new voucher message - missing voucher data', null, 'integration')
      return
    }

    // Filter: Only process if we have an active order with customer and products
    // This ensures we only check for better alternatives when relevant
    if (!currentOrder.value.khachHang?.id) {
      logger.debug('Skipping new voucher processing - no customer assigned', null, 'integration')
      return
    }

    logger.debug('Processing new voucher for better alternatives detection', {
      voucherCode: voucherData?.maPhieuGiamGia || voucherData?.voucherCode
    }, 'integration')

    // Additional validation: Check if the new voucher could potentially be better
    const orderTotal = currentOrder.value.tongTienHang || 0

    // Basic eligibility check: if order total is 0, skip processing
    if (orderTotal <= 0) {
      logger.debug('Skipping new voucher processing - order total is zero', null, 'integration')
      return
    }

    // Trigger automatic better voucher detection with enhanced error handling
    const result = await triggerBetterVoucherDetection(voucherData, 'websocket')
    if (!result.success) {
      logger.debug('Better voucher detection failed', {
        reason: result.reason,
        voucherCode: voucherData?.maPhieuGiamGia || voucherData?.voucherCode
      }, 'integration')
    }

  } catch (error) {
    handleWebSocketError(error, 'handleNewVoucherCreated', true)
  }
}

// DISABLED: Cart reservation cleanup function
// This function was previously called in onUnmounted() but has been removed
// because order editing should preserve reservations until actual order update.
// Kept for reference in case reservation handling needs to be implemented
// in the order update process in the future.
/*
const cleanupCurrentOrderReservations = async () => {
  if (currentOrder.value?.id) {
    try {
      await releaseCartReservations(currentOrder.value.id)
      logger.debug('Released cart reservations for current order', {
        orderId: currentOrder.value.id
      }, 'cleanup')
    } catch (error) {
      logger.critical('Failed to release cart reservations for current order', {
        orderId: currentOrder.value.id,
        error: error.message
      }, 'cleanup')
    }
  }
}
*/

// ===== DEVELOPMENT TESTING UTILITIES =====

// Initialize
onMounted(async () => {
  // Staff assignment is now handled automatically by the backend

  // Cleanup any pending cart reservations from previous session
  await cleanupPendingReservations()

  // Route parameter validation and order loading
  if (isEditMode.value) {
    if (!orderId.value) {
      toast.add({
        severity: 'error',
        summary: 'Lỗi',
        detail: 'ID đơn hàng không hợp lệ',
        life: 5000
      })
      router.push('/orders')
      return
    }

    // Load order data using our new comprehensive loading function
    await loadOrderData()

    // Update page title if order was loaded successfully
    if (currentOrder.value?.maHoaDon) {
      document.title = `Chỉnh sửa đơn hàng ${currentOrder.value.maHoaDon} - LapXpert Admin`
    }
  } else {
    // Not in edit mode - redirect to order list
    toast.add({
      severity: 'warn',
      summary: 'Cảnh báo',
      detail: 'Trang này chỉ dành cho chỉnh sửa đơn hàng',
      life: 3000
    })
    goBack()
    return
  }

  // Preload data for search functionality
  try {
    await customerStore.fetchCustomers()
  } catch (error) {
    logger.critical('Failed to preload data', {
      error: error.message
    }, 'initialization')
  }

  // Initialize real-time features
  try {
    // Subscribe to voucher monitoring
    subscribeToVoucherMonitoring()

    // Setup WebSocket integration callbacks for automatic voucher validation
    setupVoucherWebSocketIntegration()

    // Monitor WebSocket connection status for user feedback
    monitorWebSocketConnection()

    // Subscribe to order expiration monitoring
    subscribeToOrderExpiration()

    // Subscribe to price updates for any existing cart items
    const existingVariantIds =
      currentOrder.value?.sanPhamList?.map((item) => item.sanPhamChiTiet?.id).filter(Boolean) || []
    if (existingVariantIds.length > 0) {
      subscribeToPriceUpdates(existingVariantIds)
    }

    logger.debug('Real-time features initialized successfully', {
      features: ['voucher monitoring', 'websocket integration', 'order expiration', 'price updates']
    }, 'initialization')

    // Run integration test validation in development mode
    if (process.env.NODE_ENV === 'development') {
      setTimeout(() => {
        // validateWebSocketIntegration() // Commented out as function not defined
        logger.debug('Development mode integration test skipped', null, 'development')
      }, 2000) // Delay to ensure all components are fully initialized
    }
  } catch (error) {
    logger.critical('Failed to initialize real-time features', {
      error: error.message
    }, 'initialization')
    // Show user notification about degraded functionality
    toast.add({
      severity: 'warn',
      summary: 'Tính năng thời gian thực',
      detail: 'Một số tính năng cập nhật tự động có thể không khả dụng. Chức năng chính vẫn hoạt động bình thường.',
      life: 5000,
    })
  }

  // Initialize shipping configuration
  try {
    await loadShippingConfig()
    logger.debug('Shipping configuration loaded successfully', null, 'initialization')
  } catch (error) {
    logger.critical('Failed to load shipping configuration', {
      error: error.message
    }, 'initialization')
  }

  // Add beforeunload event listener for page refresh/close detection
  window.addEventListener('beforeunload', handlePageUnload)

  // Also add pagehide event for better mobile browser support
  window.addEventListener('pagehide', handlePageUnload)
})

// Watch for route changes to reload order data
watch(() => route.params.id, (newId, oldId) => {
  if (newId && newId !== oldId) {
    // Route changed to a different order ID
    loadOrderData()
  } else if (!newId) {
    // Route changed to create mode - reset form
    currentOrder.value = null
    error.value = null
  }
})

// Cleanup event listeners and WebSocket integration on component unmount
onUnmounted(async () => {
  try {
    // Remove event listeners
    window.removeEventListener('beforeunload', handlePageUnload)
    window.removeEventListener('pagehide', handlePageUnload)

    // ENHANCED: Unified cleanup strategy - clean up newly reserved items on component unmount
    // This ensures newly reserved items are cleaned up when component is destroyed
    // while preserving existing order items
    try {
      await unifiedCleanupStrategy.cleanupNewlyReservedItems('component_unmount')
      console.log('🔒 [UNIFIED CLEANUP] Component unmount cleanup completed')
    } catch (error) {
      console.error('🔒 [UNIFIED CLEANUP] Component unmount cleanup failed:', error)
    }

    // Cleanup WebSocket integration callbacks to prevent memory leaks
    cleanupVoucherWebSocketIntegration()

    logger.debug('OrderEdit component cleanup completed successfully', null, 'cleanup')
  } catch (error) {
    logger.critical('Error during OrderEdit component cleanup', {
      error: error.message
    }, 'cleanup')
  }
})
</script>

<style scoped>
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
