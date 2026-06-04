import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/playlist'
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
