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
    meta: { title: '播放列表' }
  },
  {
    path: '/media',
    name: 'MediaLibrary',
    component: () => import('../views/MediaLibrary.vue'),
    meta: { title: '媒体库' }
  },
  {
    path: '/schedule',
    name: 'Schedule',
    component: () => import('../views/Schedule.vue'),
    meta: { title: '定时任务' }
  },
  {
    path: '/music',
    name: 'Music',
    component: () => import('../views/Music.vue'),
    meta: { title: '背景音乐' }
  },
  {
    path: '/group',
    name: 'Group',
    component: () => import('../views/Group.vue'),
    meta: { title: '分组管理' }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('../views/Settings.vue'),
    meta: { title: '设置' }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router