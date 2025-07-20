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

// Voucher state
const voucherCode = ref('');
const appliedVoucher = ref(null);
const voucherError = ref('');
const isVoucherLoading = ref(false);

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

    try {
        const response = await voucherApi.validateVoucher(voucherCode.value, null, itemsTotal.value);

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

onMounted(async () => {
    await loadShippingConfig();

    if (cartStore.selectedItemsForCheckout.length > 0) {
        isBuyNowFlow.value = false;
        localCheckoutItems.value = cartStore.selectedItemsForCheckout.map(item => ({
            ...item,
            quantity: 1
        }));
        toast.add({ severity: 'info', summary: 'Giỏ hàng', detail: `Đã tải ${localCheckoutItems.value.length} sản phẩm từ giỏ hàng.`, life: 3000 });
    } else {
        const productId = route.query.productId;
        const variantId = route.query.variantId;
        const serialNumberId = route.query.serialNumberId ? parseInt(route.query.serialNumberId) : null;
        const serialNumberValue = route.query.serialNumberValue || '';
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
                            serialId: serialNumberId,
                            name: product.tenSanPham + ' - ' + (variant.sku || 'Variant'),
                            image: product.hinhAnh && product.hinhAnh.length > 0 ? product.hinhAnh[0] : null,
                            price: variant.giaKhuyenMai && variant.giaKhuyenMai < variant.giaBan ? variant.giaKhuyenMai : variant.giaBan,
                            quantity: quantity,
                            sanPhamChiTiet: { ...variant, serialNumberId: serialNumberId, serialNumber: serialNumberValue },
                            sanPham: product
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
    }
    cartStore.clearSelectedItemsForCheckout();
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
            khachHangId: null,
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
            chiTiet: itemsToOrder.map(item => {
                if (item.serialId && !item.sanPhamChiTiet) {
                    return {
                        sanPhamChiTietId: item.variantId,
                        soLuong: 1,
                        giaGoc: item.price,
                        giaBan: item.price,
                        thanhTien: item.price,
                        tenSanPhamSnapshot: item.name.split(' - ')[0],
                        skuSnapshot: item.name.split(' - ')[1] || item.name,
                        hinhAnhSnapshot: item.image,
                        serialNumberId: item.serialId,
                        serialNumber: item.serialId,
                    };
                } else {
                    const sanPhamChiTietPlain = JSON.parse(JSON.stringify(item.sanPhamChiTiet));
                    const sanPhamPlain = JSON.parse(JSON.stringify(item.sanPham));
                    return {
                        sanPhamChiTietId: sanPhamChiTietPlain.id,
                        soLuong: item.quantity,
                        giaGoc: sanPhamChiTietPlain.giaBan,
                        giaBan: item.price,
                        thanhTien: item.price * item.quantity,
                        tenSanPhamSnapshot: sanPhamPlain.tenSanPham,
                        skuSnapshot: sanPhamChiTietPlain.sku,
                        hinhAnhSnapshot: sanPhamPlain.hinhAnh && sanPhamPlain.hinhAnh.length > 0 ? sanPhamPlain.hinhAnh[0] : null,
                        serialNumberId: sanPhamChiTietPlain.serialNumberId || null,
                        serialNumber: sanPhamChiTietPlain.serialNumber || null,
                    };
                }
            }),
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
    <div class="container mx-auto p-4">
        <Toast />
        <h1 class="text-3xl font-bold mb-6">Thanh toán</h1>

        <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
            <div class="lg:col-span-2">
                <Card class="mb-6">
                    <template #title>Tóm tắt đơn hàng</template>
                    <template #content>
                        <div v-if="checkoutItems.length > 0">
                            <div v-for="item in checkoutItems" :key="item.id" class="flex justify-between items-center py-2 border-b last:border-b-0">
                                <div class="flex items-center">
                                    <div>
                                        <p class="font-semibold">{{ item.name }}</p>
                                        <p class="text-sm text-gray-600">Số lượng: {{ item.quantity }}</p>
                                    </div>
                                </div>
                                <p class="font-semibold">{{ formatCurrency(item.price * item.quantity) }}</p>
                            </div>
                            <div class="pt-4 mt-4 border-t space-y-2">
                                <div class="flex justify-between items-center">
                                    <p>Tạm tính</p>
                                    <p>{{ formatCurrency(itemsTotal) }}</p>
                                </div>
                                <div class="flex justify-between items-center">
                                    <p>Phí vận chuyển</p>
                                    <p>{{ formatCurrency(consolidatedShippingFee) }}</p>
                                </div>
                                <div v-if="discountAmount > 0" class="flex justify-between items-center text-green-600">
                                    <p>Giảm giá</p>
                                    <p>-{{ formatCurrency(discountAmount) }}</p>
                                </div>
                                <div class="flex justify-between items-center pt-2 mt-2 border-t">
                                    <p class="text-xl font-bold">Tổng cộng:</p>
                                    <p class="text-xl font-bold text-blue-600">{{ formatCurrency(totalPrice) }}</p>
                                </div>
                            </div>
                        </div>
                        <div v-else class="text-center text-gray-500">
                            Giỏ hàng trống. Vui lòng thêm sản phẩm để thanh toán.
                        </div>
                    </template>
                </Card>

                <Card class="mb-6">
                    <template #title>Thông tin giao hàng</template>
                    <template #content>
                        <div class="p-fluid grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div class="field">
                                <label for="hoTen">Họ và tên</label>
                                <InputText id="hoTen" v-model="customerInfo.hoTen" required />
                            </div>
                            <div class="field">
                                <label for="soDienThoai">Số điện thoại</label>
                                <InputText id="soDienThoai" v-model="customerInfo.soDienThoai" required />
                            </div>
                            <div class="field md:col-span-2">
                                <label for="email">Email (Tùy chọn)</label>
                                <InputText id="email" v-model="customerInfo.email" />
                            </div>
                            <div class="field md:col-span-2">
                                <label for="duong">Địa chỉ đường (Số nhà, tên đường)</label>
                                <InputText id="duong" v-model="addressData.duong" :class="{ 'p-invalid': addressErrors.duong }" required />
                                <small v-if="addressErrors.duong" class="p-error">{{ addressErrors.duong }}</small>
                            </div>
                            <div class="field">
                                <label for="tinhThanh">Tỉnh/Thành phố</label>
                                <Select
                                    id="tinhThanh"
                                    v-model="selectedProvince"
                                    :options="provinces"
                                    optionLabel="name"
                                    placeholder="Chọn tỉnh/thành phố"
                                    :class="{ 'p-invalid': addressErrors.tinhThanh }"
                                    @change="onProvinceChange"
                                    :loading="loadingProvinces"
                                    required
                                />
                                <small v-if="addressErrors.tinhThanh" class="p-error">{{ addressErrors.tinhThanh }}</small>
                            </div>
                            <div class="field">
                                <label for="quanHuyen">Quận/Huyện</label>
                                <Select
                                    id="quanHuyen"
                                    v-model="selectedDistrict"
                                    :options="districts"
                                    optionLabel="name"
                                    placeholder="Chọn quận/huyện"
                                    :class="{ 'p-invalid': addressErrors.quanHuyen }"
                                    @change="onDistrictChange"
                                    :disabled="!selectedProvince"
                                    :loading="loadingDistricts"
                                    required
                                />
                                <small v-if="addressErrors.quanHuyen" class="p-error">{{ addressErrors.quanHuyen }}</small>
                            </div>
                            <div class="field md:col-span-2">
                                <label for="phuongXa">Phường/Xã</label>
                                <Select
                                    id="phuongXa"
                                    v-model="selectedWard"
                                    :options="wards"
                                    optionLabel="name"
                                    placeholder="Chọn phường/xã"
                                    :class="{ 'p-invalid': addressErrors.phuongXa }"
                                    :disabled="!selectedDistrict"
                                    :loading="loadingWards"
                                    required
                                />
                                <small v-if="addressErrors.phuongXa" class="p-error">{{ addressErrors.phuongXa }}</small>
                            </div>
                            <div class="field md:col-span-2">
                                <label for="shippingFee">Phí vận chuyển</label>
                                <InputNumber
                                    id="shippingFee"
                                    v-model="consolidatedShippingFee"
                                    mode="currency"
                                    currency="VND"
                                    locale="vi-VN"
                                    :disabled="isCalculatingShipping"
                                    :class="{ 'p-invalid': shippingError }"
                                />
                                <small v-if="shippingError" class="p-error">{{ shippingError }}</small>
                                <p v-if="estimatedDeliveryTime" class="text-sm text-gray-600 mt-1">
                                    Thời gian giao hàng dự kiến: {{ estimatedDeliveryTime }}
                                </p>
                            </div>
                        </div>
                    </template>
                </Card>

                <!-- Voucher/Discount Code -->
                <Card class="mb-6">
                    <template #title>Mã giảm giá</template>
                    <template #content>
                        <div v-if="!appliedVoucher">
                            <div class="flex gap-2">
                                <InputText v-model="voucherCode" placeholder="Nhập mã giảm giá của bạn" class="flex-grow" @keyup.enter="applyVoucher" />
                                <Button label="Áp dụng" @click="applyVoucher" :loading="isVoucherLoading" />
                            </div>
                            <small v-if="voucherError" class="p-error mt-2 block">{{ voucherError }}</small>
                        </div>
                        <div v-else>
                            <div class="flex justify-between items-center p-3 bg-green-50 border border-green-200 rounded-lg">
                                <div>
                                    <p class="font-semibold text-green-800">Đã áp dụng mã: {{ appliedVoucher.voucher.maPhieuGiamGia }}</p>
                                    <p class="text-sm text-green-700">Bạn được giảm: {{ formatCurrency(appliedVoucher.discountAmount) }}</p>
                                </div>
                                <Button icon="pi pi-times" severity="danger" text rounded @click="removeVoucher" v-tooltip.top="'Gỡ bỏ mã'" />
                            </div>
                        </div>
                    </template>
                </Card>

                <!-- Payment Method -->
                <Card>
                    <template #title>Phương thức thanh toán</template>
                    <template #content>
                        <div class="flex flex-col gap-3">
                            <div class="flex items-center">
                                <RadioButton v-model="paymentMethod" inputId="cod" name="paymentMethod" value="COD" />
                                <label for="cod" class="ml-2">Thanh toán khi nhận hàng (COD)</label>
                            </div>
                            <div class="flex items-center">
                                <RadioButton v-model="paymentMethod" inputId="momo" name="paymentMethod" value="MOMO" />
                                <label for="momo" class="ml-2">Thanh toán qua MoMo</label>
                            </div>
                            <div class="flex items-center">
                                <RadioButton v-model="paymentMethod" inputId="vnpay" name="paymentMethod" value="VNPAY" />
                                <label for="vnpay" class="ml-2">Thanh toán qua VNPay</label>
                            </div>
                        </div>
                    </template>
                </Card>
            </div>

            <!-- Place Order Button -->
            <div class="lg:col-span-1">
                <Card>
                    <template #title>Hoàn tất đơn hàng</template>
                    <template #content>
                        <div class="space-y-2 mb-4">
                            <div class="flex justify-between items-center">
                                <p>Tạm tính:</p>
                                <p>{{ formatCurrency(itemsTotal) }}</p>
                            </div>
                            <div class="flex justify-between items-center">
                                <p>Phí vận chuyển:</p>
                                <p>{{ formatCurrency(consolidatedShippingFee) }}</p>
                            </div>
                            <div v-if="discountAmount > 0" class="flex justify-between items-center text-green-600">
                                <p>Giảm giá:</p>
                                <p>-{{ formatCurrency(discountAmount) }}</p>
                            </div>
                            <hr class="my-2">
                            <div class="flex justify-between items-center">
                                <p class="text-lg font-semibold">Tổng cộng:</p>
                                <p class="text-2xl font-bold text-blue-600">{{ formatCurrency(totalPrice) }}</p>
                            </div>
                        </div>
                        <Button
                            label="Đặt hàng ngay"
                            icon="pi pi-check"
                            class="w-full p-button-success"
                            @click="placeOrder"
                            :loading="isSubmitting"
                            :disabled="checkoutItems.length === 0 || isSubmitting"
                        />
                    </template>
                </Card>
            </div>
        </div>
    </div>
</template>
