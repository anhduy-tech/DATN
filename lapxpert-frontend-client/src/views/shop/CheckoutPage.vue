<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useCartStore } from '@/stores/cartStore';
import { useProductStore } from '@/stores/productstore';
import { useOrderStore } from '@/stores/orderStore';
import { useRoute, useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { useEmbeddedAddress } from '@/composables/useEmbeddedAddress';
import { useShippingCalculator } from '@/composables/useShippingCalculator';
import orderApi from '@/apis/orderApi';
import customerApi from '@/apis/user';
import voucherApi from '@/apis/voucherApi';
import AuthService from '@/apis/auth';
import storageApi from '@/apis/storage';

// PrimeVue Components
import Card from 'primevue/card';
import InputText from 'primevue/inputtext';
import RadioButton from 'primevue/radiobutton';
import Button from 'primevue/button';
import Toast from 'primevue/toast';
import Select from 'primevue/select';
import InputNumber from 'primevue/inputnumber';

const cartStore = useCartStore();
const productStore = useProductStore();
const orderStore = useOrderStore();
const route = useRoute();
const router = useRouter();
const toast = useToast();

const isBuyNowFlow = ref(false);
const singleCheckoutItem = ref(null);
const localCheckoutItems = ref([]);
const imageUrlCache = ref(new Map());

// Voucher state
const voucherCode = ref('');
const appliedVoucher = ref(null);
const voucherError = ref('');
const isVoucherLoading = ref(false);

// Auto-applied voucher state
const autoAppliedVoucher = ref(null);
const isAutoApplyingVoucher = ref(false);

// Customer address states
const customerAddresses = ref([]);
const selectedExistingAddressId = ref(null);

// Use composables for address and shipping
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
} = useEmbeddedAddress();

const {
    isCalculating: isCalculatingShipping,
    calculationError: shippingError,
    shippingFee,
    isAutoCalculated: isShippingAutoCalculated,
    estimatedDeliveryTime,
    calculateShippingFeeWithComparison,
    resetShippingCalculation,
    loadShippingConfig,
} = useShippingCalculator();

const customerInfo = ref({
    hoTen: '',
    soDienThoai: '',
    email: '',
});

const paymentMethod = ref('COD');
const isSubmitting = ref(false);
const consolidatedShippingFee = ref(0);

const checkoutItems = computed(() => {
    if (localCheckoutItems.value.length > 0) {
        return localCheckoutItems.value;
    }
    if (singleCheckoutItem.value) {
        return [singleCheckoutItem.value];
    }
    return [];
});

const itemsTotal = computed(() => {
    return checkoutItems.value.reduce((sum, item) => sum + (item.price * item.quantity), 0);
});

const discountAmount = computed(() => {
    if (appliedVoucher.value && appliedVoucher.value.discountAmount > 0) {
        return appliedVoucher.value.discountAmount;
    }
    return 0;
});

const totalPrice = computed(() => {
    const finalPrice = itemsTotal.value + consolidatedShippingFee.value - discountAmount.value;
    return finalPrice > 0 ? finalPrice : 0;
});

const formatCurrency = (value) => {
    if (typeof value !== 'number' || isNaN(value)) {
        return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(0);
    }
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(value);
};

const applyVoucher = async () => {
    if (!voucherCode.value.trim()) {
        voucherError.value = 'Vui lòng nhập mã giảm giá.';
        return;
    }
    isVoucherLoading.value = true;
    voucherError.value = '';
    appliedVoucher.value = null;
    autoAppliedVoucher.value = null;

    try {
        const response = await voucherApi.validateVoucher(voucherCode.value, AuthService.isAuthenticated() ? AuthService.getUser().id : null, itemsTotal.value);
        if (response.success && response.data.valid) {
            appliedVoucher.value = response.data;
            toast.add({ severity: 'success', summary: 'Thành công', detail: `Áp dụng voucher thành công! Bạn được giảm ${formatCurrency(response.data.discountAmount)}.`, life: 3000 });
        } else {
            voucherError.value = response.data.error || 'Mã giảm giá không hợp lệ hoặc không thể áp dụng.';
            toast.add({ severity: 'error', summary: 'Lỗi', detail: voucherError.value, life: 3000 });
        }
    } catch (err) {
        voucherError.value = 'Có lỗi xảy ra khi kiểm tra mã giảm giá. Vui lòng thử lại.';
        toast.add({ severity: 'error', summary: 'Lỗi', detail: voucherError.value, life: 3000 });
    } finally {
        isVoucherLoading.value = false;
    }
};

const removeVoucher = () => {
    appliedVoucher.value = null;
    voucherCode.value = '';
    voucherError.value = '';
    toast.add({ severity: 'info', summary: 'Thông báo', detail: 'Đã gỡ bỏ mã giảm giá.', life: 3000 });
};

const confirmRemoveItem = (variantId) => {
    
    const index = localCheckoutItems.value.findIndex(i => i.variantId === variantId);
    if (index > -1) {
        localCheckoutItems.value.splice(index, 1);
        toast.add({ severity: 'info', summary: 'Thông báo', detail: 'Đã xóa sản phẩm khỏi giỏ hàng.', life: 3000 });
    }
    toast.removeGroup('confirm');
};

const onExistingAddressSelect = () => {
    const selectedAddress = customerAddresses.value.find(addr => addr.id === selectedExistingAddressId.value);
    if (selectedAddress) {
        setAddressData({
            duong: selectedAddress.duong,
            phuongXa: selectedAddress.phuongXa,
            quanHuyen: selectedAddress.quanHuyen,
            tinhThanh: selectedAddress.tinhThanh,
            loaiDiaChi: selectedAddress.loaiDiaChi,
            laMacDinh: selectedAddress.laMacDinh,
        });
        selectedProvince.value = provinces.value.find(p => p.name === selectedAddress.tinhThanh);
        if (selectedProvince.value) {
            onProvinceChange();
            selectedDistrict.value = districts.value.find(d => d.name === selectedAddress.quanHuyen);
            if (selectedDistrict.value) {
                onDistrictChange();
                selectedWard.value = wards.value.find(w => w.name === selectedAddress.phuongXa);
            }
        }
    }
};

const getProductImageUrl = (image) => {
  if (!image || !image.length) return null

  // If it's already a full URL, return as is
  if (image.startsWith('http')) return image

  // Check cache first
  if (imageUrlCache.value.has(image)) {
    return imageUrlCache.value.get(image)
  }

  // Load presigned URL asynchronously
  loadImageUrl(image)

  // Return null for now, will update when loaded
  return null
}

const loadImageUrl = async (imageFilename) => {
  try {
    // Get presigned URL for the image filename
    const presignedUrl = await storageApi.getPresignedUrl('products', imageFilename)

    // Cache the URL for future use
    imageUrlCache.value.set(imageFilename, presignedUrl)

    // Force reactivity update
    imageUrlCache.value = new Map(imageUrlCache.value)
  } catch (error) {
    console.warn('Error getting presigned URL for image:', imageFilename, error)
    // Cache null to prevent repeated attempts
    imageUrlCache.value.set(imageFilename, null)
  }
}

const clearSelectedAddress = () => {
    selectedExistingAddressId.value = null;
    resetAddressForm();
};

const applyBestVoucherAutomatically = async () => {
    if (!AuthService.isAuthenticated() || itemsTotal.value === 0) {
        autoAppliedVoucher.value = null;
        return;
    }

    const user = AuthService.getUser();
    if (!user || !user.id) {
        autoAppliedVoucher.value = null;
        return;
    }

    isAutoApplyingVoucher.value = true;
    try {
        const response = await voucherApi.getBestVoucher(user.id, itemsTotal.value);
        if (response.success && response.data.found) {
            const bestVoucher = response.data.voucher;
            const bestDiscountAmount = response.data.discountAmount;

            if (!appliedVoucher.value || bestDiscountAmount > appliedVoucher.value.discountAmount) {
                autoAppliedVoucher.value = {
                    voucher: bestVoucher,
                    discountAmount: bestDiscountAmount,
                    message: response.data.message
                };
                appliedVoucher.value = autoAppliedVoucher.value;
                voucherCode.value = bestVoucher.maPhieuGiamGia;
                toast.add({ severity: 'success', summary: 'Voucher tự động', detail: `Đã áp dụng voucher tốt nhất: ${bestVoucher.maPhieuGiamGia} - Giảm ${formatCurrency(bestDiscountAmount)}.`, life: 5000 });
            } else {
                autoAppliedVoucher.value = null;
            }
        } else {
            autoAppliedVoucher.value = null;
        }
    } catch (err) {
        console.error("Error auto-applying best voucher:", err);
        autoAppliedVoucher.value = null;
    } finally {
        isAutoApplyingVoucher.value = false;
    }
};

watch(
    () => [
        selectedProvince.value,
        selectedDistrict.value,
        selectedWard.value,
        addressData.value.duong,
        checkoutItems.value,
    ],
    async () => {
        if (
            selectedProvince.value &&
            selectedDistrict.value &&
            selectedWard.value &&
            addressData.value.duong.trim() &&
            checkoutItems.value.length > 0
        ) {
            const deliveryAddressForCalc = {
                province: selectedProvince.value.name,
                district: selectedDistrict.value.name,
                ward: selectedWard.value.name,
                address: addressData.value.duong.trim(),
            };
            await calculateShippingFeeWithComparison(deliveryAddressForCalc, itemsTotal.value);
            consolidatedShippingFee.value = shippingFee.value;
        } else {
            consolidatedShippingFee.value = 0;
        }
    },
    { deep: true }
);

watch(() => [itemsTotal.value, AuthService.isAuthenticated()], () => {
    applyBestVoucherAutomatically();
}, { immediate: true });

onMounted(async () => {
    await loadShippingConfig();
    if (cartStore.selectedItemsForCheckout.length > 0) {
        isBuyNowFlow.value = false;
        localCheckoutItems.value = cartStore.selectedItemsForCheckout;
        toast.add({ severity: 'info', summary: 'Giỏ hàng', detail: `Đã tải ${localCheckoutItems.value.length} loại sản phẩm từ giỏ hàng.`, life: 3000 });
        cartStore.clearSelectedItemsForCheckout();
    }

    if (AuthService.isAuthenticated()) {
        const user = AuthService.getUser();
        if (user && user.id) {
            customerInfo.value.hoTen = user.hoTen || '';
            customerInfo.value.soDienThoai = user.soDienThoai || '';
            customerInfo.value.email = user.email || '';

            try {
                const response = await customerApi.getCustomerById(user.id);
                if (response.data && response.data.diaChis) {
                    customerAddresses.value = response.data.diaChis;
                    const defaultAddress = customerAddresses.value.find(addr => addr.laMacDinh);
                    if (defaultAddress) {
                        selectedExistingAddressId.value = defaultAddress.id;
                        setAddressData({
                            duong: defaultAddress.duong,
                            phuongXa: defaultAddress.phuongXa,
                            quanHuyen: defaultAddress.quanHuyen,
                            tinhThanh: defaultAddress.tinhThanh,
                            loaiDiaChi: defaultAddress.loaiDiaChi,
                            laMacDinh: defaultAddress.laMacDinh,
                        });
                        selectedProvince.value = provinces.value.find(p => p.name === defaultAddress.tinhThanh);
                        if (selectedProvince.value) {
                            await onProvinceChange();
                            selectedDistrict.value = districts.value.find(d => d.name === defaultAddress.quanHuyen);
                            if (selectedDistrict.value) {
                                await onDistrictChange();
                                selectedWard.value = wards.value.find(w => w.name === defaultAddress.phuongXa);
                            }
                        }
                    }
                }
            } catch (err) {
                console.error("Error fetching customer addresses:", err);
                toast.add({ severity: 'error', summary: 'Lỗi', detail: 'Không thể tải địa chỉ của bạn.', life: 3000 });
            }
        }
    }

    const productId = route.query.productId;
    const variantId = route.query.variantId;
    const quantity = parseInt(route.query.quantity || 1);

    if (productId && variantId) {
        isBuyNowFlow.value = true;
        try {
            const product = await productStore.fetchProductById(productId);
            if (product) {
                const variant = product.sanPhamChiTiets.find(v => v.id == variantId);
                if (variant) {
                    singleCheckoutItem.value = {
                        productId: product.id,
                        variantId: variant.id,
                        name: product.tenSanPham + ' - ' + (variant.sku || 'Variant'),
                        image: product.hinhAnh && product.hinhAnh.length > 0 ? product.hinhAnh[0] : null,
                        price: variant.giaKhuyenMai && variant.giaKhuyenMai < variant.giaBan ? variant.giaKhuyenMai : variant.giaBan,
                        quantity: quantity,
                        sanPhamChiTiet: variant,
                        sanPham: product,
                    };
                    toast.add({ severity: 'info', summary: 'Mua ngay', detail: `Sản phẩm "${singleCheckoutItem.value.name}" đã sẵn sàng thanh toán.`, life: 3000 });
                } else {
                    throw new Error('Không tìm thấy biến thể sản phẩm.');
                }
            } else {
                throw new Error('Không tìm thấy sản phẩm.');
            }
        } catch (err) {
            toast.add({ severity: 'error', summary: 'Lỗi', detail: err.message || 'Không thể tải thông tin sản phẩm.', life: 5000 });
            isBuyNowFlow.value = false;
        }
    }
});

const placeOrder = async () => {
    const itemsToOrder = checkoutItems.value;

    if (itemsToOrder.length === 0) {
        toast.add({ severity: 'error', summary: 'Lỗi', detail: 'Không có sản phẩm nào để đặt hàng.', life: 3000 });
        return;
    }

    if (!customerInfo.value.hoTen || !customerInfo.value.soDienThoai) {
        toast.add({ severity: 'error', summary: 'Lỗi', detail: 'Vui lòng điền đầy đủ thông tin người nhận.', life: 3000 });
        return;
    }

    if (!selectedProvince.value || !selectedDistrict.value || !selectedWard.value || !addressData.value.duong.trim()) {
        toast.add({ severity: 'error', summary: 'Lỗi', detail: 'Vui lòng điền đầy đủ địa chỉ giao hàng.', life: 3000 });
        return;
    }

    isSubmitting.value = true;
    try {
        let response;
        const effectivePaymentMethod = paymentMethod.value === 'COD' ? 'TIEN_MAT' : paymentMethod.value;

        const orderData = {
            maHoaDon: `ONLINE_${Date.now()}`,
            loaiHoaDon: 'ONLINE',
            phuongThucThanhToan: effectivePaymentMethod,
            tongTienHang: itemsTotal.value,
            giaTriGiamGiaVoucher: discountAmount.value,
            phiVanChuyen: consolidatedShippingFee.value,
            tongThanhToan: totalPrice.value,
            trangThaiDonHang: 'CHO_XAC_NHAN',
            trangThaiThanhToan: 'CHUA_THANH_TOAN',
            isMixedPayment: false,
            mixedPayments: null,
            voucherCodes: appliedVoucher.value ? [appliedVoucher.value.voucher.maPhieuGiamGia] : [],
            khachHangId: AuthService.isAuthenticated() ? AuthService.getUser().id : null,
            nguoiNhanTen: customerInfo.value.hoTen,
            nguoiNhanSdt: customerInfo.value.soDienThoai,
            nguoiNhanEmail: customerInfo.value.email || null,
            diaChiGiaoHang: {
                duong: addressData.value.duong.trim(),
                phuongXa: selectedWard.value.name,
                quanHuyen: selectedDistrict.value.name,
                tinhThanh: selectedProvince.value.name,
                loaiDiaChi: 'Nhà riêng'
            },
            chiTiet: itemsToOrder.map(item => ({
                sanPhamChiTietId: item.variantId,
                soLuong: item.quantity,
                giaGoc: item.price,
                giaBan: item.price,
                thanhTien: item.price * item.quantity,
                tenSanPhamSnapshot: item.name.split(' - ')[0],
                skuSnapshot: item.name.split(' - ')[1] || item.name,
                hinhAnhSnapshot: item.image,
                serialNumberId: null,
                serialNumber: null,
            })),
            nhanVienId: null,
        };

        const finalOrderData = JSON.parse(JSON.stringify(orderData));

        if (effectivePaymentMethod === 'MOMO') {
            response = await orderApi.processMoMoPayment(null, {
                amount: totalPrice.value,
                orderInfo: `Thanh toán đơn hàng online`,
                returnUrl: window.location.origin + '/shop/checkout/payment-return',
                orderDetails: finalOrderData.chiTiet,
                customerInfo: { hoTen: finalOrderData.nguoiNhanTen, soDienThoai: finalOrderData.nguoiNhanSdt },
                deliveryAddress: finalOrderData.diaChiGiaoHang,
                loaiHoaDon: 'ONLINE',
                phiVanChuyen: consolidatedShippingFee.value,
            });
            if (response?.success && response.data?.paymentUrl) {
                window.location.href = response.data.paymentUrl;
            } else {
                throw new Error(response?.message || 'Không thể khởi tạo thanh toán MoMo.');
            }
        } else if (effectivePaymentMethod === 'VNPAY') {
            response = await orderApi.processVNPayPayment(null, {
                amount: totalPrice.value,
                orderInfo: `Thanh toán đơn hàng online`,
                returnUrl: window.location.origin + '/shop/checkout/payment-return',
                orderDetails: finalOrderData.chiTiet,
                customerInfo: { hoTen: finalOrderData.nguoiNhanTen, soDienThoai: finalOrderData.nguoiNhanSdt },
                deliveryAddress: finalOrderData.diaChiGiaoHang,
                loaiHoaDon: 'ONLINE',
                phiVanChuyen: consolidatedShippingFee.value,
            });
            if (response?.success && response.data?.paymentUrl) {
                window.location.href = response.data.paymentUrl;
            } else {
                throw new Error(response?.message || 'Không thể khởi tạo thanh toán VNPay.');
            }
        } else {
            response = await orderStore.createOrder(finalOrderData);
            toast.add({ severity: 'success', summary: 'Đặt hàng thành công', detail: `Đơn hàng của bạn (#${response.maHoaDon}) đã được tạo.`, life: 5000 });
            if (!isBuyNowFlow.value) {
                cartStore.clearCart();
            }
            router.push({ name: 'shop-order-detail', params: { id: response.data.id } });
        }
    } catch (error) {
        console.error("Lỗi khi đặt hàng:", error);
        toast.add({ severity: 'error', summary: 'Đặt hàng thất bại', detail: error.response?.data?.message || 'Có lỗi xảy ra, vui lòng thử lại.', life: 5000 });
    } finally {
        isSubmitting.value = false;
    }
};
</script>

<template>
    <div class="card max-w-7xl mx-auto px-4 py-8">
        <Toast position="top-right" group="confirm" />
        
        <!-- Progress Indicator -->
        <div class="mb-8">
            <div class="flex items-center justify-between">
                <div class="flex-1 text-center">
                    <span class="inline-flex items-center justify-center w-8 h-8 rounded-full bg-primary text-white">1</span>
                    <p class="mt-2 text-sm font-medium text-surface-600">Giỏ hàng</p>
                </div>
                <div class="flex-1 border-t-2 border-primary"></div>
                <div class="flex-1 text-center">
                    <span class="inline-flex items-center justify-center w-8 h-8 rounded-full bg-primary text-white">2</span>
                    <p class="mt-2 text-sm font-medium text-surface-600">Thanh toán</p>
                </div>
                <div class="flex-1 border-t-2 border-surface-200"></div>
                <div class="flex-1 text-center">
                    <span class="inline-flex items-center justify-center w-8 h-8 rounded-full bg-surface-200 text-surface-600">3</span>
                    <p class="mt-2 text-sm font-medium text-surface-500">Hoàn tất</p>
                </div>
            </div>
        </div>

        <h1 class="text-3xl font-bold mb-8 text-surface-800">Thanh toán đơn hàng</h1>

        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <!-- Left Column: Order Details, Delivery, Voucher, Payment -->
            <div class="lg:col-span-2 space-y-6">
                <!-- Order Summary -->
                <Card class="card shadow-md border-surface-border">
                    <template #title>
                        <div class="flex items-center gap-2">
                            <i class="pi pi-shopping-cart text-xl text-primary"></i>
                            <span class="text-xl font-semibold">Tóm tắt đơn hàng</span>
                        </div>
                    </template>
                    <template #content>
                        <div v-if="checkoutItems.length > 0" class="space-y-4">
                            <div v-for="item in checkoutItems" :key="item.variantId" class="flex items-center gap-4 py-3 border-b border-surface-200 last:border-b-0 min-h-[80px]">
                                <div class="w-16 h-16 flex items-center justify-center">
                                    <img v-if="item.image" :src="getProductImageUrl(item.image)" :alt="getProductImageUrl(item.image)" class="w-16 h-16 object-cover rounded-md border border-surface-border" />
                                    <i v-else class="pi pi-image text-2xl text-surface-400"></i>
                                </div>
                                <div class="flex-1">
                                    <p class="font-medium text-surface-800 line-clamp-2">{{ item.name }}</p>
                                    <p class="text-sm text-surface-500">Số lượng: {{ item.quantity }}</p>
                                </div>
                                <div class="flex items-center gap-4">
                                    <p class="font-semibold text-surface-800">{{ formatCurrency(item.price * item.quantity) }}</p>
                                    <Button
                                        v-if="!isBuyNowFlow && localCheckoutItems.length > 1"
                                        icon="pi pi-trash"
                                        severity="danger"
                                        text
                                        rounded
                                        @click="confirmRemoveItem(item.variantId)"
                                        v-tooltip.top="'Xóa sản phẩm'"
                                        class="hover:scale-110 transition-transform duration-200"
                                    />
                                </div>
                            </div>
                            <div class="pt-4 space-y-3 border-t border-surface-200">
                                <div class="flex justify-between items-center">
                                    <span class="text-surface-600">Tạm tính</span>
                                    <span>{{ formatCurrency(itemsTotal) }}</span>
                                </div>
                                <div class="flex justify-between items-center">
                                    <span class="text-surface-600">Phí vận chuyển</span>
                                    <span>{{ formatCurrency(consolidatedShippingFee) }}</span>
                                </div>
                                <div v-if="discountAmount > 0" class="flex justify-between items-center text-green-600">
                                    <span>Giảm giá</span>
                                    <span>-{{ formatCurrency(discountAmount) }}</span>
                                </div>
                                <div class="flex justify-between items-center pt-3 border-t border-surface-200">
                                    <span class="text-lg font-semibold text-surface-800">Tổng cộng</span>
                                    <span class="text-xl font-bold text-primary">{{ formatCurrency(totalPrice) }}</span>
                                </div>
                            </div>
                        </div>
                        <div v-else class="text-center py-6 text-surface-500">
                            Giỏ hàng trống. Vui lòng thêm sản phẩm để thanh toán.
                        </div>
                    </template>
                </Card>

                <!-- Delivery Information -->
                <Card class="card shadow-md border-surface-border">
                    <template #title>
                        <div class="flex items-center gap-2">
                            <i class="pi pi-map-marker text-xl text-primary"></i>
                            <span class="text-xl font-semibold">Thông tin giao hàng</span>
                        </div>
                    </template>
                    <template #content>
                        <div v-if="customerAddresses.length > 0" class="mb-6">
                            <label for="existingAddress" class="block mb-2 font-medium text-surface-700">Chọn địa chỉ đã lưu</label>
                            <Select
                                id="existingAddress"
                                v-model="selectedExistingAddressId"
                                :options="customerAddresses"
                                optionLabel="duong"
                                optionValue="id"
                                placeholder="Chọn địa chỉ đã lưu"
                                class="w-full"
                                @change="onExistingAddressSelect"
                            >
                                <template #option="slotProps">
                                    <div class="flex flex-col py-2">
                                        <span class="font-medium text-surface-800">{{ slotProps.option.duong }}</span>
                                        <span class="text-sm text-surface-500">{{ slotProps.option.phuongXa }}, {{ slotProps.option.quanHuyen }}, {{ slotProps.option.tinhThanh }}</span>
                                    </div>
                                </template>
                            </Select>
                            <Button v-if="selectedExistingAddressId" label="Sử dụng địa chỉ mới" icon="pi pi-plus" text class="mt-3 text-primary" @click="clearSelectedAddress" />
                        </div>

                        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div class="space-y-2">
                                <label for="hoTen" class="block text-sm font-medium text-surface-700">Họ và tên</label>
                                <InputText id="hoTen" v-model="customerInfo.hoTen" class="w-full" required />
                            </div>
                            <div class="space-y-2">
                                <label for="soDienThoai" class="block text-sm font-medium text-surface-700">Số điện thoại</label>
                                <InputText id="soDienThoai" v-model="customerInfo.soDienThoai" class="w-full" required />
                            </div>
                            <div class="space-y-2 md:col-span-2">
                                <label for="email" class="block text-sm font-medium text-surface-700">Email (Tùy chọn)</label>
                                <InputText id="email" v-model="customerInfo.email" class="w-full" />
                            </div>
                            <div class="space-y-2 md:col-span-2">
                                <label for="duong" class="block text-sm font-medium text-surface-700">Địa chỉ đường (Số nhà, tên đường)</label>
                                <InputText id="duong" v-model="addressData.duong" :class="{ 'p-invalid': addressErrors.duong }" class="w-full" required />
                                <small v-if="addressErrors.duong" class="text-red-500">{{ addressErrors.duong }}</small>
                            </div>
                            <div class="space-y-2">
                                <label for="tinhThanh" class="block text-sm font-medium text-surface-700">Tỉnh/Thành phố</label>
                                <Select
                                    id="tinhThanh"
                                    v-model="selectedProvince"
                                    :options="provinces"
                                    optionLabel="name"
                                    placeholder="Chọn tỉnh/thành phố"
                                    :class="{ 'p-invalid': addressErrors.tinhThanh }"
                                    class="w-full"
                                    @change="onProvinceChange"
                                    :loading="loadingProvinces"
                                    required
                                />
                                <small v-if="addressErrors.tinhThanh" class="text-red-500">{{ addressErrors.tinhThanh }}</small>
                            </div>
                            <div class="space-y-2">
                                <label for="quanHuyen" class="block text-sm font-medium text-surface-700">Quận/Huyện</label>
                                <Select
                                    id="quanHuyen"
                                    v-model="selectedDistrict"
                                    :options="districts"
                                    optionLabel="name"
                                    placeholder="Chọn quận/huyện"
                                    :class="{ 'p-invalid': addressErrors.quanHuyen }"
                                    class="w-full"
                                    @change="onDistrictChange"
                                    :disabled="!selectedProvince"
                                    :loading="loadingDistricts"
                                    required
                                />
                                <small v-if="addressErrors.quanHuyen" class="text-red-500">{{ addressErrors.quanHuyen }}</small>
                            </div>
                            <div class="space-y-2 md:col-span-2">
                                <label for="phuongXa" class="block text-sm font-medium text-surface-700">Phường/Xã</label>
                                <Select
                                    id="phuongXa"
                                    v-model="selectedWard"
                                    :options="wards"
                                    optionLabel="name"
                                    placeholder="Chọn phường/xã"
                                    :class="{ 'p-invalid': addressErrors.phuongXa }"
                                    class="w-full"
                                    :disabled="!selectedDistrict"
                                    :loading="loadingWards"
                                    required
                                />
                                <small v-if="addressErrors.phuongXa" class="text-red-500">{{ addressErrors.phuongXa }}</small>
                            </div>
                            <div class="space-y-2 md:col-span-2">
                                <label for="shippingFee" class="block text-sm font-medium text-surface-700">Phí vận chuyển</label>
                                <InputNumber
                                    id="shippingFee"
                                    v-model="consolidatedShippingFee"
                                    mode="currency"
                                    currency="VND"
                                    locale="vi-VN"
                                    :disabled="isCalculatingShipping"
                                    :class="{ 'p-invalid': shippingError }"
                                    class="w-full"
                                />
                                <small v-if="shippingError" class="text-red-500">{{ shippingError }}</small>
                                <p v-if="estimatedDeliveryTime" class="text-sm text-surface-500 mt-1">
                                    Thời gian giao hàng dự kiến: {{ estimatedDeliveryTime }}
                                </p>
                            </div>
                        </div>
                    </template>
                </Card>

                <!-- Voucher Section -->
                <Card class="card shadow-md border-surface-border">
                    <template #title>
                        <div class="flex items-center gap-2">
                            <i class="pi pi-ticket text-xl text-primary"></i>
                            <span class="text-xl font-semibold">Mã giảm giá</span>
                        </div>
                    </template>
                    <template #content>
                        <div v-if="!appliedVoucher">
                            <div class="flex gap-3 items-center">
                                <InputText v-model="voucherCode" placeholder="Nhập mã giảm giá của bạn" class="flex-grow" @keyup.enter="applyVoucher" />
                                <Button label="Áp dụng" icon="pi pi-check" class="p-button-outlined p-button-info" @click="applyVoucher" :loading="isVoucherLoading" />
                            </div>
                            <small v-if="voucherError" class="text-red-500 mt-2 block">{{ voucherError }}</small>
                        </div>
                        <div v-else>
                            <div class="flex justify-between items-center p-4 bg-green-50 border border-green-200 rounded-lg">
                                <div>
                                    <p class="font-medium text-green-800">Đã áp dụng mã: {{ appliedVoucher.voucher.maPhieuGiamGia }}</p>
                                    <p class="text-sm text-green-700">Bạn được giảm: {{ formatCurrency(appliedVoucher.discountAmount) }}</p>
                                </div>
                                <Button icon="pi pi-times" severity="danger" text rounded @click="removeVoucher" v-tooltip.top="'Gỡ bỏ mã'" />
                            </div>
                        </div>
                        <div v-if="autoAppliedVoucher && appliedVoucher && autoAppliedVoucher.voucher.maPhieuGiamGia === appliedVoucher.voucher.maPhieuGiamGia" class="mt-3 text-sm text-green-600 flex items-center">
                            <i class="pi pi-info-circle mr-2"></i>
                            Voucher này được áp dụng tự động.
                        </div>
                    </template>
                </Card>

                <!-- Payment Method -->
                <Card class="card shadow-md border-surface-border">
                    <template #title>
                        <div class="flex items-center gap-2">
                            <i class="pi pi-credit-card text-xl text-primary"></i>
                            <span class="text-xl font-semibold">Phương thức thanh toán</span>
                        </div>
                    </template>
                    <template #content>
                        <div class="space-y-4">
                            <div class="flex items-center">
                                <RadioButton v-model="paymentMethod" inputId="cod" name="paymentMethod" value="COD" />
                                <label for="cod" class="ml-3 text-surface-700">Thanh toán khi nhận hàng (COD)</label>
                            </div>
                            <div class="flex items-center">
                                <RadioButton v-model="paymentMethod" inputId="momo" name="paymentMethod" value="MOMO" />
                                <label for="momo" class="ml-3 text-surface-700">Thanh toán qua MoMo</label>
                            </div>
                            <div class="flex items-center">
                                <RadioButton v-model="paymentMethod" inputId="vnpay" name="paymentMethod" value="VNPAY" />
                                <label for="vnpay" class="ml-3 text-surface-700">Thanh toán qua VNPay</label>
                            </div>
                        </div>
                    </template>
                </Card>
            </div>

            <!-- Right Column: Order Summary and Place Order -->
            <div class="lg:col-span-1">
                <div class="sticky top-4">
                    <Card class="card shadow-md border-surface-border">
                        <template #title>
                            <div class="flex items-center gap-2">
                                <i class="pi pi-wallet text-xl text-primary"></i>
                                <span class="text-xl font-semibold">Hoàn tất đơn hàng</span>
                            </div>
                        </template>
                        <template #content>
                            <div class="space-y-3 mb-6">
                                <div class="flex justify-between items-center">
                                    <span class="text-surface-600">Tạm tính</span>
                                    <span>{{ formatCurrency(itemsTotal) }}</span>
                                </div>
                                <div class="flex justify-between items-center">
                                    <span class="text-surface-600">Phí vận chuyển</span>
                                    <span>{{ formatCurrency(consolidatedShippingFee) }}</span>
                                </div>
                                <div v-if="discountAmount > 0" class="flex justify-between items-center text-green-600">
                                    <span>Giảm giá</span>
                                    <span>-{{ formatCurrency(discountAmount) }}</span>
                                </div>
                                <hr class="my-3 border-surface-200">
                                <div class="flex justify-between items-center">
                                    <span class="text-lg font-semibold text-surface-800">Tổng cộng</span>
                                    <span class="text-2xl font-bold text-primary">{{ formatCurrency(totalPrice) }}</span>
                                </div>
                            </div>
                            <Button
                                label="Đặt hàng ngay"
                                icon="pi pi-check"
                                class="w-full p-button-raised p-button-success"
                                @click="placeOrder"
                                :loading="isSubmitting"
                                :disabled="checkoutItems.length === 0 || isSubmitting"
                            />
                        </template>
                    </Card>
                </div>
            </div>
        </div>
    </div>
</template>