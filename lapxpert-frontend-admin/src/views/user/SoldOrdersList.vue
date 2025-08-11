<template>
    <div class="card">
        <DataTable
            :value="orders"
            paginator
            :rows="10"
            :rowsPerPageOptions="[5, 10, 20, 50]"
            tableStyle="min-width: 50rem"
            :loading="loading"
            :totalRecords="totalRecords"
            lazy
            @page="onPage"
            dataKey="id"
        >
            <Column field="maHoaDon" header="Mã đơn hàng"></Column>
            <Column field="ngayTao" header="Ngày tạo">
                <template #body="slotProps">
                    {{ formatDate(slotProps.data.ngayTao) }}
                </template>
            </Column>
            <Column field="tongTien" header="Tổng tiền">
                <template #body="slotProps">
                    {{ formatCurrency(slotProps.data.tongTien) }}
                </template>
            </Column>
            <Column field="trangThaiDonHang" header="Trạng thái">
                <template #body="slotProps">
                    <Badge :value="formatOrderStatus(slotProps.data.trangThaiDonHang)" :severity="getOrderSeverity(slotProps.data.trangThaiDonHang)" />
                </template>
            </Column>
            <Column field="loaiHoaDon" header="Loại đơn hàng">
                <template #body="slotProps">
                    {{ slotProps.data.loaiHoaDon === 'ONLINE' ? 'Trực tuyến' : 'Tại quầy' }}
                </template>
            </Column>
            <Column field="khachHang.hoTen" header="Khách hàng"></Column>
            <Column header="Hành động">
                <template #body="slotProps">
                    <Button icon="pi pi-eye" severity="info" text rounded @click="viewOrder(slotProps.data.id)" v-tooltip.top="'Xem chi tiết'" />
                </template>
            </Column>
            <template #empty>
                Không có đơn hàng nào được tìm thấy.
            </template>
            <template #loading>
                Đang tải đơn hàng, vui lòng chờ...
            </template>
        </DataTable>
    </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Badge from 'primevue/badge';
import Button from 'primevue/button';
import orderApi from '@/apis/orderApi';
import { useRouter } from 'vue-router';

// No props needed anymore
// const props = defineProps({
//     userId: {
//         type: Number,
//         required: true
//     },
//     staffMaNguoiDung: {
//         type: String,
//         required: false
//     }
// });

const router = useRouter();
const orders = ref([]);
const loading = ref(false);
const totalRecords = ref(0);
const lazyParams = ref({
    page: 0,
    rows: 10,
    sortField: null,
    sortOrder: null,
    filters: {}
});

const loadOrders = async () => {
    loading.value = true;
    try {
        // Call the new getMyOrders API
        const response = await orderApi.getMyOrders();
        if (response.success) {
            // The /me endpoint returns a List<HoaDonDto>, not a paginated response
            orders.value = response.data;
            totalRecords.value = response.data.length;
        } else {
            console.error('Failed to load orders:', response.message);
            orders.value = [];
            totalRecords.value = 0;
        }
    } catch (error) {
        console.error('Error loading orders:', error);
        orders.value = [];
        totalRecords.value = 0;
    } finally {
        loading.value = false;
    }
};

// Pagination is not directly supported by /me endpoint, so we'll handle it client-side if needed
// For now, just load all and let DataTable handle display pagination
const onPage = (event) => {
    // No server-side pagination for /me endpoint
    // DataTable will handle client-side pagination based on `orders.value`
};

const formatDate = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString('vi-VN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
};

const formatCurrency = (value) => {
    if (value === null || value === undefined) return '';
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
};

const formatOrderStatus = (status) => {
    switch (status) {
        case 'PENDING': return 'Chờ xử lý';
        case 'PROCESSING': return 'Đang xử lý';
        case 'SHIPPED': return 'Đã giao hàng';
        case 'DELIVERED': return 'Đã hoàn thành';
        case 'CANCELLED': return 'Đã hủy';
        case 'RETURNED': return 'Đã trả hàng';
        default: return status;
    }
};

const getOrderSeverity = (status) => {
    switch (status) {
        case 'PENDING': return 'warning';
        case 'PROCESSING': return 'info';
        case 'SHIPPED': return 'primary';
        case 'DELIVERED': return 'success';
        case 'CANCELLED': return 'danger';
        case 'RETURNED': return 'danger';
        default: return null;
    }
};

const viewOrder = (orderId) => {
    router.push({ name: 'OrderDetail', params: { id: orderId } });
};

onMounted(() => {
    loadOrders();
});

// No need to watch props anymore as they are not used
// watch(() => props.userId, () => {
//     loadOrders();
// });
// watch(() => props.staffMaNguoiDung, () => {
//     loadOrders();
// });
</script>

<style scoped>
/* Add any specific styles for this component here */
</style>
