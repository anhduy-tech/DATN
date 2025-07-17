<template>
    <footer class="bg-gray-800 text-white p-6 mt-8">
        <div class="container mx-auto flex flex-col md:flex-row justify-between items-center">
            <div class="mb-4 md:mb-0">
                <p class="text-lg font-semibold">LapXpert</p>
                <p class="text-sm">&copy; 2025 LapXpert. All rights reserved.</p>
            </div>
            <div class="flex space-x-6">
                <router-link to="/shop/support" class="hover:underline">Hỗ trợ</router-link>
                <router-link to="/shop/products" class="hover:underline">Sản phẩm</router-link>
                <router-link to="/shop/cart" class="hover:underline">Giỏ hàng</router-link>
            </div>
        </div>
    </footer>
</template>
