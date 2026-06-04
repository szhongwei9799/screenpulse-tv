import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { title: 'Dashboard' }
  },
  {
    path: '/playlist',
    name: 'Playlist',
    component: () => import('../views/Playlist.vue'),
    meta: { title: 'Playlist Management' }
  },
  {
    path: '/media',
    name: 'MediaLibrary',
    component: () => import('../views/MediaLibrary.vue'),
    meta: { title: 'Media Library' }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('../views/Settings.vue'),
    meta: { title: 'Device Settings' }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
