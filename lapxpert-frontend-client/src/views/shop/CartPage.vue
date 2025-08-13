<script setup>
import { ref, computed } from 'vue';
import { useCartStore } from '@/stores/cartStore';
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import DataView from 'primevue/dataview';
import InputNumber from 'primevue/inputnumber';
import Button from 'primevue/button';
import Toast from 'primevue/toast';
import SelectButton from 'primevue/selectbutton';

const cartStore = useCartStore();
const router = useRouter();
const toast = useToast();

const cartItems = computed(() => cartStore.items);
const selectedCartItems = ref([]);
const layout = ref('list');
const layoutOptions = ref(['list', 'grid']);

const formatCurrency = (value) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(value);
};

const selectedTotalPrice = computed(() => {
    return selectedCartItems.value.reduce((total, item) => total + (item.price * item.quantity), 0);
});

const updateQuantity = (variantId, quantity) => {
    cartStore.updateQuantity(variantId, quantity);
};

const removeItem = (variantId) => {
    cartStore.removeItem(variantId);
    const index = selectedCartItems.value.findIndex(i => i.variantId === variantId);
    if (index > -1) {
        selectedCartItems.value.splice(index, 1);
    }
    toast.add({ severity: 'info', summary: 'Thông báo', detail: 'Đã xóa sản phẩm khỏi giỏ hàng.', life: 3000 });
};

const toggleSelectItem = (item) => {
    const index = selectedCartItems.value.findIndex(i => i.variantId === item.variantId);
    if (index > -1) {
        selectedCartItems.value.splice(index, 1);
    } else {
        selectedCartItems.value.push(item);
    }
};

const isItemSelected = (item) => {
    return selectedCartItems.value.some(i => i.variantId === item.variantId);
};

const proceedToCheckout = () => {
    if (selectedCartItems.value.length === 0) {
        toast.add({ severity: 'warn', summary: 'Cảnh báo', detail: 'Vui lòng chọn ít nhất một sản phẩm để thanh toán.', life: 3000 });
        return;
    }

    cartStore.setSelectedItemsForCheckout(selectedCartItems.value);
    router.push('/shop/checkout');
};

const isCheckoutButtonDisabled = computed(() => selectedCartItems.value.length === 0);

const continueShopping = () => {
    router.push('/shop/products');
};
</script>

<template>
    <div class="container mx-auto px-4 py-6 max-w-6xl">
        <Toast />
        <h1 class="text-3xl font-bold mb-6 flex items-center gap-2">
            <i class="pi pi-shopping-cart text-primary"></i>
            Giỏ hàng của bạn
        </h1>
        <div class="card">
            <DataView v-if="cartItems.length > 0" :value="cartItems" :layout="layout">
                <template #header>
                    <div class="flex justify-end mb-4">
                        <SelectButton v-model="layout" :options="layoutOptions" :allowEmpty="false">
                            <template #option="{ option }">
                                <i :class="[option === 'list' ? 'pi pi-bars' : 'pi pi-table']" />
                            </template>
                        </SelectButton>
                    </div>
                </template>
                <template #list="slotProps">
                    <div class="flex flex-col">
                        <div
                            v-for="(item, index) in slotProps.items"
                            :key="item.variantId"
                            class="flex flex-col sm:flex-row sm:items-center p-6 gap-4 cursor-pointer transition-all duration-200 hover:bg-surface-hover"
                            :class="{ 'border-t border-surface-200': index !== 0, 'bg-primary-50 border-2 border-primary': isItemSelected(item) }"
                            @click="toggleSelectItem(item)"
                        >
                            <!-- Product Image -->
                            <div class="w-32 sm:w-40 relative">
                                <img
                                    :src="item.image || 'https://via.placeholder.com/150'"
                                    :alt="item.name"
                                    class="w-full h-32 sm:h-40 object-contain rounded border border-surface-200"
                                />
                            </div>
                            <!-- Product Details -->
                            <div class="flex flex-col sm:flex-row justify-between sm:items-center flex-1 gap-4 sm:gap-6">
                                <div class="flex flex-col justify-between gap-2">
                                    <div>
                                        <div class="text-lg font-semibold text-gray-900 line-clamp-2">{{ item.name }}</div>
                                        <span class="text-sm text-surface-500">Đơn giá: {{ formatCurrency(item.price) }}</span>
                                    </div>
                                    <InputNumber
                                        v-model="item.quantity"
                                        @input="updateQuantity(item.variantId, $event.value)"
                                        showButtons
                                        buttonLayout="horizontal"
                                        :min="1"
                                        class="w-32"
                                        inputClass="text-base"
                                        @click.stop
                                    />
                                </div>
                                <div class="flex flex-col sm:items-end gap-4">
                                    <span class="text-xl font-semibold text-primary">{{ formatCurrency(item.price * item.quantity) }}</span>
                                    <Button
                                        icon="pi pi-trash"
                                        severity="danger"
                                        text
                                        rounded
                                        @click.stop="removeItem(item.variantId)"
                                        class="hover:scale-110 transition-transform duration-200"
                                    />
                                </div>
                            </div>
                        </div>
                    </div>
                </template>
                <template #grid="slotProps">
                    <div class="grid grid-cols-12 gap-4">
                        <div
                            v-for="(item, index) in slotProps.items"
                            :key="item.variantId"
                            class="col-span-12 sm:col-span-6 md:col-span-4 p-2"
                        >
                            <div
                                class="p-4 border border-surface-200 bg-surface-0 rounded flex flex-col cursor-pointer transition-all duration-200 hover:bg-surface-hover min-h-[300px] h-full"
                                :class="{ 'bg-primary-50 border-2 border-primary': isItemSelected(item) }"
                                @click="toggleSelectItem(item)"
                            >
                                <div class="bg-surface-50 flex justify-center rounded p-4">
                                    <div class="relative mx-auto">
                                        <img
                                            :src="item.image || 'https://via.placeholder.com/150'"
                                            :alt="item.name"
                                            class="rounded w-full h-40 object-contain"
                                        />
                                    </div>
                                </div>
                                <div class="pt-4 flex flex-col flex-grow">
                                    <div class="flex flex-row justify-between items-start gap-2">
                                        <div>
                                            <div class="text-lg font-semibold text-gray-900 line-clamp-2">{{ item.name }}</div>
                                            <span class="text-sm text-surface-500">Đơn giá: {{ formatCurrency(item.price) }}</span>
                                        </div>
                                    </div>
                                    <div class="flex flex-col gap-4 mt-4 flex-grow justify-between">
                                        <div class="flex items-center gap-2">
                                            <InputNumber
                                                v-model="item.quantity"
                                                @input="updateQuantity(item.variantId, $event.value)"
                                                showButtons
                                                buttonLayout="horizontal"
                                                :min="1"
                                                class="w-32"
                                                inputClass="text-base"
                                                @click.stop
                                            />
                                            <Button
                                                icon="pi pi-trash"
                                                severity="danger"
                                                text
                                                rounded
                                                @click.stop="removeItem(item.variantId)"
                                                class="hover:scale-110 transition-transform duration-200"
                                            />
                                        </div>
                                        <span class="text-xl font-semibold text-primary">{{ formatCurrency(item.price * item.quantity) }}</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </template>
            </DataView>
            <div v-if="cartItems.length > 0" class="flex justify-end items-center gap-4 mt-4 p-4 border-t border-surface-200">
                <span class="text-lg font-semibold">Tổng cộng: <span class="text-primary">{{ formatCurrency(selectedTotalPrice) }}</span></span>
                <Button
                    label="Tiếp tục mua sắm"
                    severity="secondary"
                    outlined
                    @click="continueShopping"
                    class="hover:scale-105 transition-transform duration-200"
                />
                <Button
                    label="Tiến hành thanh toán"
                    icon="pi pi-check"
                    :disabled="isCheckoutButtonDisabled"
                    @click="proceedToCheckout"
                    class="p-button-raised hover:scale-105 transition-transform duration-200"
                />
            </div>
            <div v-else class="text-center p-8">
                <i class="pi pi-shopping-cart text-6xl text-surface-400 mb-4"></i>
                <p class="text-lg text-surface-600 mb-4">Giỏ hàng của bạn đang trống.</p>
                <Button
                    label="Bắt đầu mua sắm"
                    icon="pi pi-arrow-right"
                    @click="continueShopping"
                    class="p-button-raised p-button-info hover:scale-105 transition-transform duration-200"
                />
            </div>
        </div>
    </div>
</template>

<style scoped>
.card {
    background: var(--surface-card);
    padding: 1.5rem;
    border-radius: 6px;
    box-shadow: 0 2px 1px -1px rgba(0,0,0,.2), 0 1px 1px 0 rgba(0,0,0,.14), 0 1px 3px 0 rgba(0,0,0,.12);
}

.line-clamp-2 {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.p-button {
    transition: transform 0.2s, box-shadow 0.2s;
}

.p-button:hover {
    box-shadow: 0 4px 8px rgba(0,0,0,0.1);
}
</style>