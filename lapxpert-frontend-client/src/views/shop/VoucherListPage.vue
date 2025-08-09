<template>
    <div class="container mx-auto p-4">
        <h1 class="text-3xl font-bold mb-6">Vouchers</h1>
        <div v-if="loading" class="text-center">
            <p>Loading vouchers...</p>
        </div>
        <div v-else-if="error" class="text-center text-red-500">
            <p>{{ error }}</p>
        </div>
        <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            <div v-for="voucher in vouchers" :key="voucher.id" class="bg-white rounded-lg shadow-md p-6 border border-gray-200">
                <div class="flex justify-between items-center mb-4">
                    <h2 class="text-xl font-semibold text-gray-800">{{ voucher.maPhieuGiamGia }}</h2>
                    <span class="px-3 py-1 text-sm font-medium text-white bg-green-500 rounded-full">{{ voucher.loaiGiamGia === 'PHAN_TRAM' ? `${voucher.giaTriGiam}%` : `${voucher.giaTriGiam}đ` }} OFF</span>
                </div>
                <p class="text-gray-600 mb-2">Tối thiểu: {{ formatCurrency(voucher.giaTriDonHangToiThieu) }}</p>
                <p class="text-gray-600 mb-4">Hiệu lực đến: {{ new Date(voucher.ngayKetThuc).toLocaleDateString() }}</p>
                <button @click="copyVoucher(voucher.maPhieuGiamGia)" class="w-full bg-blue-500 hover:bg-blue-600 text-white font-bold py-2 px-4 rounded">
                    Copy Code
                </button>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useVoucherStore } from '@/stores/voucherStore';
import { storeToRefs } from 'pinia';

const voucherStore = useVoucherStore();
const { vouchers, loading, error } = storeToRefs(voucherStore);

onMounted(() => {
    voucherStore.fetchVouchers();
});

const formatCurrency = (value) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
};

const copyVoucher = (code) => {
    navigator.clipboard.writeText(code).then(() => {
        alert(`Voucher code "${code}" copied to clipboard!`);
    }).catch(err => {
        console.error('Failed to copy: ', err);
    });
};
</script>

<style scoped>
.container {
    max-width: 1200px;
}
</style>
