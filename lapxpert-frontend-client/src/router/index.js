import { createRouter, createWebHistory } from 'vue-router'
import CustomerLayout from '@/layout/CustomerLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/auth/LoginDashboard.vue'),
    },
    {
      path: '/',
      redirect: '/shop'
    },
    {
      path: '/:pathMatch(.*)*',
      component: () => import('@/views/auth/Error.vue'),
    },
    {
      path: '/shop',
      component: CustomerLayout,
      children: [
        {
          path: '',
          name: 'shop-home',
          component: () => import('@/views/shop/HomePage.vue'),
        },
        {
          path: 'products',
          name: 'shop-products',
          component: () => import('@/views/shop/ProductListPage.vue'),
        },
        {
          path: 'products/:id',
          name: 'shop-product-detail',
          component: () => import('@/views/shop/ProductDetailPage.vue'),
          props: true,
        },
        {
          path: 'cart',
          name: 'shop-cart',
          component: () => import('@/views/shop/CartPage.vue'),
        },
        {
          path: 'checkout',
          name: 'shop-checkout',
          component: () => import('@/views/shop/CheckoutPage.vue'),
        },
        {
          path: 'support',
          name: 'shop-support',
          component: () => import('@/views/shop/SupportPage.vue'),
        },
        {
          path: 'orders/:id',
          name: 'shop-order-detail',
          component: () => import('@/views/shop/OrderDetailPage.vue'),
          props: true,
        },
        {
          path: 'profile',
          name: 'shop-profile',
          component: () => import('@/views/shop/ProfilePage.vue'),
        },
        { // New route for Order History
          path: 'orders',
          name: 'shop-order-history',
          component: () => import('@/views/shop/OrderHistoryPage.vue'),
          meta: { requiresAuth: true } // Only logged-in users can view their order history
        },
        {
          path: 'vouchers',
          name: 'shop-vouchers',
          component: () => import('@/views/shop/VoucherListPage.vue'),
        },
        { // New route for Favorite List
          path: 'favorites',
          name: 'shop-favorites',
          component: () => import('@/views/shop/FavoriteListPage.vue'),
          meta: { requiresAuth: true } // Ensure only logged-in users can access
        },
        {
          path: '/websocket-test',
          name: 'websocket-test',
          component: () => import('@/views/WebSocketTest.vue'),
        },
      ],
    },
  ],
})

export default router
