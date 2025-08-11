<template>
    <div class="min-h-screen bg-[var(--surface-ground)] p-4">
        <div class="max-w-7xl mx-auto">
            <!-- Header Section -->
            <div class="mb-8">
                <div class="flex items-center gap-4 mb-2">
                    <div
                        class="w-2 h-8 bg-gradient-to-b from-[var(--primary-500)] to-[var(--primary-700)] rounded-full">
                    </div>
                    <h1 class="text-3xl font-bold text-[var(--text-color)]">Thông tin cá nhân</h1>
                </div>
                <p class="text-[var(--text-color-secondary)] ml-6">Quản lý thông tin tài khoản và đơn hàng của bạn</p>
            </div>

            <!-- Main Content Grid -->
            <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
                <!-- Left Sidebar - Avatar & Quick Stats -->
                <div class="lg:col-span-4 space-y-6">
                    <!-- Avatar Card -->
                    <div
                        class="card bg-[var(--surface-card)] shadow-lg rounded-xl overflow-hidden transition-transform duration-300 hover:-translate-y-1 hover:shadow-2xl">
                        <div class="relative">
                            <!-- Background Pattern -->
                            <div class="h-32 bg-gradient-to-r from-primary-500 via-indigo-500 to-purple-600 relative overflow-hidden">
                                <div class="absolute inset-0 bg-black bg-opacity-10"></div>
                                <div class="absolute -top-10 -right-10 w-40 h-40 bg-white bg-opacity-10 rounded-full"></div>
                                <div class="absolute -bottom-5 -left-5 w-32 h-32 bg-white bg-opacity-5 rounded-full"></div>
                            </div>

                            <!-- Avatar -->
                            <div class="absolute -bottom-16 left-1/2 -translate-x-1/2">
                                <div
                                    class="w-48 h-48 border-4 border-[var(--surface-0)] rounded-full flex items-center justify-center overflow-hidden bg-[var(--surface-50)] shadow-xl">
                                    <img v-if="avatarUrl" :src="avatarUrl" alt="Avatar"
                                        class="w-full h-full object-cover" />
                                    <i v-else class="pi pi-user text-6xl text-surface-400"></i>
                                </div>
                            </div>
                        </div>

                        <div class="pt-20 pb-6 px-6 text-center">
                            <h2 class="text-2xl font-bold text-[var(--text-color)] mb-1">{{ user.hoTen }}</h2>
                            <p class="text-[var(--primary-color)] font-semibold mb-2">{{ user.vaiTro }}</p>
                            <div class="inline-flex items-center gap-2 bg-[var(--surface-100)] px-3 py-1 rounded-full">
                                <i class="pi pi-id-card text-sm text-[var(--text-color-secondary)]"></i>
                                <span class="text-sm text-[var(--text-color)] font-medium">{{ user.maNguoiDung }}</span>
                            </div>
                        </div>
                    </div>

                    <!-- Quick Contact Card -->
                    <div
                        class="card bg-[var(--surface-card)] shadow-lg rounded-xl transition-transform duration-300 hover:-translate-y-1 hover:shadow-2xl">
                        <div class="p-6">
                            <h3 class="text-lg font-bold text-[var(--text-color)] mb-4 flex items-center gap-2">
                                <i class="pi pi-phone text-[var(--primary-color)]"></i>
                                Thông tin liên hệ
                            </h3>
                            <div class="space-y-3">
                                <div class="flex items-center gap-3 p-3 bg-[var(--surface-50)] rounded-lg">
                                    <i class="pi pi-envelope text-[var(--primary-color)] w-5"></i>
                                    <div class="flex-1">
                                        <p class="text-sm text-[var(--text-color-secondary)]">Email</p>
                                        <p class="font-medium text-[var(--text-color)] break-all">{{ user.email }}</p>
                                    </div>
                                </div>
                                <div class="flex items-center gap-3 p-3 bg-[var(--surface-50)] rounded-lg">
                                    <i class="pi pi-phone text-[var(--primary-color)] w-5"></i>
                                    <div class="flex-1">
                                        <p class="text-sm text-[var(--text-color-secondary)]">Số điện thoại</p>
                                        <p class="font-medium text-[var(--text-color)]">{{ user.soDienThoai }}</p>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Right Content Area -->
                <div class="lg:col-span-8 space-y-6">
                    <!-- Personal Information Card -->
                    <div
                        class="card bg-[var(--surface-card)] shadow-lg rounded-xl transition-transform duration-300 hover:-translate-y-1 hover:shadow-2xl">
                        <div class="p-6">
                            <div class="flex items-center justify-between mb-6">
                                <h3 class="text-xl font-bold text-[var(--text-color)] flex items-center gap-2">
                                    <i class="pi pi-user text-[var(--primary-color)]"></i>
                                    Thông tin cá nhân
                                </h3>
                            </div>

                            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                                <div class="bg-[var(--surface-50)] p-4 rounded-xl border border-[var(--surface-200)]">
                                    <label
                                        class="text-sm font-semibold text-[var(--text-color-secondary)] flex items-center gap-2 mb-2">
                                        <i class="pi pi-calendar text-[var(--primary-color)] text-xs"></i>
                                        Ngày sinh
                                    </label>
                                    <div class="text-lg font-bold text-[var(--text-color)]">{{ formatDate(user.ngaySinh)
                                        }}</div>
                                </div>

                                <div class="bg-[var(--surface-50)] p-4 rounded-xl border border-[var(--surface-200)]">
                                    <label
                                        class="text-sm font-semibold text-[var(--text-color-secondary)] flex items-center gap-2 mb-2">
                                        <i class="pi pi-users text-[var(--primary-color)] text-xs"></i>
                                        Giới tính
                                    </label>
                                    <div class="text-lg font-bold text-[var(--text-color)]">{{ user.gioiTinh === 'NAM' ?
                                        'Nam' : 'Nữ' }}</div>
                                </div>

                                <div class="bg-[var(--surface-50)] p-4 rounded-xl border border-[var(--surface-200)]">
                                    <label
                                        class="text-sm font-semibold text-[var(--text-color-secondary)] flex items-center gap-2 mb-2">
                                        <i class="pi pi-id-card text-[var(--primary-color)] text-xs"></i>
                                        Căn cước công dân
                                    </label>
                                    <div class="text-lg font-bold text-[var(--text-color)]">{{ user.cccd }}</div>
                                </div>

                                <div class="bg-[var(--surface-50)] p-4 rounded-xl border border-[var(--surface-200)]">
                                    <label
                                        class="text-sm font-semibold text-[var(--text-color-secondary)] flex items-center gap-2 mb-2">
                                        <i class="pi pi-shield text-[var(--primary-color)] text-xs"></i>
                                        Vai trò
                                    </label>
                                    <div class="text-lg font-bold text-[var(--primary-color)]">{{ user.vaiTro }}</div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Address Information Card -->
                    <div
                        class="card bg-[var(--surface-card)] shadow-lg rounded-xl transition-transform duration-300 hover:-translate-y-1 hover:shadow-2xl">
                        <div class="p-6">
                            <div class="flex items-center justify-between mb-6">
                                <h3 class="text-xl font-bold text-[var(--text-color)] flex items-center gap-2">
                                    <i class="pi pi-map-marker text-[var(--primary-color)]"></i>
                                    Địa chỉ
                                </h3>
                                <Button icon="pi pi-plus" label="Thêm địa chỉ"
                                    class="p-button-outlined p-button-sm transition-transform duration-300 hover:scale-105" />
                            </div>

                            <div v-if="user.diaChis && user.diaChis.length > 0" class="space-y-4">
                                <div v-for="(address, index) in user.diaChis" :key="index"
                                    class="bg-[var(--surface-50)] border border-[var(--surface-200)] rounded-xl p-5 transition-all duration-300 hover:shadow-md">
                                    <div class="flex items-start justify-between mb-3">
                                        <div class="flex items-center gap-3">
                                            <div
                                                class="w-10 h-10 bg-[var(--surface-100)] rounded-full flex items-center justify-center">
                                                <i class="pi pi-home text-[var(--primary-color)]"></i>
                                            </div>
                                            <div>
                                                <h4 class="font-semibold text-[var(--text-color)]">Địa chỉ {{ index + 1
                                                    }}</h4>
                                                <p v-if="address.loaiDiaChi"
                                                    class="text-sm text-[var(--text-color-secondary)]">{{
                                                        address.loaiDiaChi }}</p>
                                            </div>
                                        </div>
                                        <div class="flex items-center gap-2">
                                            <Badge v-if="address.laMacDinh" value="Mặc định" severity="success" />
                                        </div>
                                    </div>
                                    <div class="ml-12">
                                        <p class="text-[var(--text-color)] leading-relaxed">{{ formatAddress(address) }}
                                        </p>
                                    </div>
                                </div>
                            </div>

                            <div v-else class="text-center py-12">
                                <i class="pi pi-map-marker text-6xl text-[var(--surface-300)] mb-4"></i>
                                <h4 class="text-lg font-semibold text-[var(--text-color-secondary)] mb-2">Chưa có địa
                                    chỉ</h4>
                                <p class="text-[var(--text-color-secondary)] mb-6">Thêm địa chỉ để hoàn thiện thông tin
                                    cá nhân</p>
                                <Button label="Thêm địa chỉ đầu tiên" icon="pi pi-plus"
                                    class="transition-transform duration-300 hover:scale-105" />
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Orders Section -->
            <div class="mt-8">
                <div
                    class="card bg-[var(--surface-card)] shadow-lg rounded-xl transition-transform duration-300 hover:-translate-y-1 hover:shadow-2xl">
                    <TabView class="bg-transparent">
                        <TabPanel>
                            <template #header>
                                <div
                                    class="flex items-center gap-2 px-4 py-2 text-[var(--text-color-secondary)] hover:text-[var(--primary-color)] transition-colors duration-300">
                                    <i class="pi pi-shopping-cart text-lg"></i>
                                    <span class="font-semibold">Đơn hàng đã bán</span>
                                </div>
                            </template>
                            <div class="p-6">
                                <SoldOrdersList />
                            </div>
                        </TabPanel>
                        <TabPanel>
                            <template #header>
                                <div
                                    class="flex items-center gap-2 px-4 py-2 text-[var(--text-color-secondary)] hover:text-[var(--primary-color)] transition-colors duration-300">
                                    <i class="pi pi-chart-line text-lg"></i>
                                    <span class="font-semibold">Thống kê</span>
                                </div>
                            </template>
                            <div class="p-6">
                                <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
                                    <div
                                        class="bg-gradient-to-r from-[var(--primary-500)] to-[var(--primary-600)] text-white p-6 rounded-xl">
                                        <div class="flex items-center justify-between">
                                            <div>
                                                <p class="text-[var(--primary-100)] mb-1">Tổng đơn hàng</p>
                                                <p class="text-3xl font-bold">{{ orderCount }}</p>
                                            </div>
                                            <i class="pi pi-shopping-bag text-4xl text-[var(--primary-200)]"></i>
                                        </div>
                                    </div>
                                    <div
                                        class="bg-gradient-to-r from-[var(--green-500)] to-[var(--green-600)] text-white p-6 rounded-xl">
                                        <div class="flex items-center justify-between">
                                            <div>
                                                <p class="text-[var(--green-100)] mb-1">Đã hoàn thành</p>
                                                <p class="text-3xl font-bold">0</p>
                                            </div>
                                            <i class="pi pi-check-circle text-4xl text-[var(--green-200)]"></i>
                                        </div>
                                    </div>
                                    <div
                                        class="bg-gradient-to-r from-[var(--purple-500)] to-[var(--purple-600)] text-white p-6 rounded-xl">
                                        <div class="flex items-center justify-between">
                                            <div>
                                                <p class="text-[var(--purple-100)] mb-1">Doanh thu</p>
                                                <p class="text-3xl font-bold">0₫</p>
                                            </div>
                                            <i class="pi pi-dollar text-4xl text-[var(--purple-200)]"></i>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </TabPanel>
                    </TabView>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import Button from 'primevue/button';
import Badge from 'primevue/badge';
import TabView from 'primevue/tabview';
import TabPanel from 'primevue/tabpanel';
import storageApi from '@/apis/storage';
import SoldOrdersList from './SoldOrdersList.vue';

const user = ref({});
const avatarUrl = ref(null);
const orderCount = ref(0);
const avatarInput = ref(null);

onMounted(async () => {
    const userData = localStorage.getItem('nguoiDung');
    if (userData) {
        user.value = JSON.parse(userData);
        if (user.value.avatar) {
            try {
                let objectName = user.value.avatar;
                const urlParts = user.value.avatar.split('/');
                const lastPart = urlParts[urlParts.length - 1];
                if (lastPart.includes('?')) {
                    objectName = lastPart.split('?')[0];
                }
                avatarUrl.value = await storageApi.getPresignedUrl('avatars', objectName);
            } catch (error) {
                console.error('Error fetching presigned URL for avatar:', error);
                avatarUrl.value = null;
            }
        } else {
            avatarUrl.value = null;
        }
        orderCount.value = 10; // Giả định số lượng đơn hàng
    }
});

const formatDate = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString('vi-VN');
};

const formatAddress = (address) => {
    if (!address) return '';
    let fullAddress = address.duong;
    if (address.phuongXa) fullAddress += `, ${address.phuongXa}`;
    if (address.quanHuyen) fullAddress += `, ${address.quanHuyen}`;
    if (address.tinhThanh) fullAddress += `, ${address.tinhThanh}`;
    if (address.quocGia) fullAddress += `, ${address.quocGia}`;
    return fullAddress;
};

const triggerAvatarUpload = () => {
    avatarInput.value.click();
};

const handleAvatarUpload = async (event) => {
    const file = event.target.files[0];
    if (file) {
        try {
            const uploadedUrl = await storageApi.uploadAvatar(file);
            avatarUrl.value = uploadedUrl;
            user.value.avatar = uploadedUrl;
            localStorage.setItem('nguoiDung', JSON.stringify(user.value));
        } catch (error) {
            console.error('Error uploading avatar:', error);
        }
    }
};
</script>

<style scoped>
.card {
    transition: all 0.3s ease;
}
</style>