<script setup>
import ShopHeader from '@/components/shop/Header.vue';
import ShopFooter from '@/components/shop/Footer.vue';
</script>

<template>
    <div class="min-h-screen bg-gray-100 flex flex-col">
        <ShopHeader />
        <main class="p-4 flex-grow">
            <RouterView />
        </main>
        <ShopFooter />
    </div>
</template>
