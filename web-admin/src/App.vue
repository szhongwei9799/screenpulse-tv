<template>
  <el-container class="app-container">
    <!-- Sidebar -->
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="app-aside">
      <div class="logo-area">
        <div class="logo-icon">S</div>
        <transition name="fade">
          <span v-if="!isCollapsed" class="logo-text">ScreenPulse</span>
        </transition>
      </div>

      <el-menu
        :default-active="currentRoute"
        :collapse="isCollapsed"
        :collapse-transition="true"
        router
        class="app-menu"
        background-color="transparent"
        text-color="#B0BEC5"
        active-text-color="#448AFF"
      >
        <el-menu-item index="/playlist">
          <el-icon><List /></el-icon>
          <template #title>Playlist</template>
        </el-menu-item>

        <el-menu-item index="/media">
          <el-icon><FolderOpened /></el-icon>
          <template #title>Media Library</template>
        </el-menu-item>

        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <template #title>Settings</template>
        </el-menu-item>
      </el-menu>

      <div class="aside-footer">
        <el-button
          :icon="isCollapsed ? Expand : Fold"
          circle
          size="small"
          text
          @click="isCollapsed = !isCollapsed"
        />
      </div>
    </el-aside>

    <!-- Main Content -->
    <el-container class="app-main-container">
      <el-header class="app-header" height="56px">
        <div class="header-left">
          <h2 class="page-title">{{ pageTitle }}</h2>
        </div>
        <div class="header-right">
          <el-tag type="success" effect="dark" size="small" class="status-tag">
            <el-icon class="tag-icon"><CircleCheck /></el-icon>
            Connected
          </el-tag>
          <el-button :icon="Refresh" circle size="small" @click="refreshPage" />
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="slide-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { List, FolderOpened, Setting, Fold, Expand, Refresh, CircleCheck } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const isCollapsed = ref(false)

const currentRoute = computed(() => route.path)

const pageTitles = {
  '/playlist': 'Playlist Management',
  '/media': 'Media Library',
  '/settings': 'Device Settings'
}

const pageTitle = computed(() => pageTitles[route.path] || 'Playlist')

const refreshPage = () => {
  router.go(0)
}
</script>

<style scoped>
.app-container {
  height: 100vh;
  background: #0D1117;
  color: #E0E0E0;
  overflow: hidden;
}

.app-aside {
  background: #161B22;
  border-right: 1px solid #21262D;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
  overflow: hidden;
}

.logo-area {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid #21262D;
  gap: 10px;
  flex-shrink: 0;
}

.logo-icon {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #1A237E, #448AFF);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
  color: #fff;
  flex-shrink: 0;
}

.logo-text {
  font-size: 16px;
  font-weight: 700;
  color: #E0E0E0;
  white-space: nowrap;
  background: linear-gradient(135deg, #448AFF, #82B1FF);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.app-menu {
  border-right: none;
  flex: 1;
  padding-top: 8px;
}

.app-menu .el-menu-item {
  margin: 2px 8px;
  border-radius: 8px;
  height: 44px;
  line-height: 44px;
}

.app-menu .el-menu-item:hover {
  background: rgba(68, 138, 255, 0.08) !important;
}

.app-menu .el-menu-item.is-active {
  background: rgba(68, 138, 255, 0.15) !important;
  color: #448AFF !important;
}

.aside-footer {
  padding: 12px;
  border-top: 1px solid #21262D;
  display: flex;
  justify-content: center;
  flex-shrink: 0;
}

.app-main-container {
  flex: 1;
  overflow: hidden;
}

.app-header {
  background: #161B22;
  border-bottom: 1px solid #21262D;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #E0E0E0;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-tag {
  border: none;
}

.tag-icon {
  margin-right: 4px;
}

.app-main {
  background: #0D1117;
  padding: 24px;
  overflow-y: auto;
  overflow-x: hidden;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-fade-enter-active {
  transition: all 0.25s ease-out;
}
.slide-fade-leave-active {
  transition: all 0.2s ease-in;
}
.slide-fade-enter-from {
  transform: translateY(10px);
  opacity: 0;
}
.slide-fade-leave-to {
  transform: translateY(-10px);
  opacity: 0;
}
</style>
