<template>
  <el-container class="app-container">
    <!-- Sidebar -->
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="app-aside">
      <div class="logo-area">
        <div class="logo-icon">S</div>
        <transition name="fade">
          <div v-if="!isCollapsed" class="logo-text-wrapper">
            <span class="logo-version">v{{ appVersion }}</span>
            <span class="logo-text">ScreenPulse</span>
          </div>
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
          <template #title>播放列表</template>
        </el-menu-item>

        <el-menu-item index="/media">
          <el-icon><FolderOpened /></el-icon>
          <template #title>媒体库</template>
        </el-menu-item>

        <el-menu-item index="/music">
          <el-icon><Headset /></el-icon>
          <template #title>背景音乐</template>
        </el-menu-item>

        <el-menu-item index="/group">
          <el-icon><Folder /></el-icon>
          <template #title>分组管理</template>
        </el-menu-item>

        <el-menu-item index="/schedule">
          <el-icon><Timer /></el-icon>
          <template #title>定时任务</template>
        </el-menu-item>

        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <template #title>设置</template>
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
          <el-dropdown trigger="click">
            <span class="user-avatar">
              <el-icon><User /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="changePassword">
                  <el-icon><Lock /></el-icon>
                  <span>修改密码</span>
                </el-dropdown-item>
                <el-dropdown-item divided @click="logout">
                  <el-icon><ArrowRight /></el-icon>
                  <span>登出</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
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
import { List, FolderOpened, Setting, Fold, Expand, Timer, Headset, Folder, User, Lock, ArrowRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const isCollapsed = ref(false)

// App version - in production this would be injected at build time
const appVersion = '1.2.0'

const currentRoute = computed(() => route.path)

const pageTitles = {
  '/playlist': '播放列表',
  '/media': '媒体库',
  '/music': '背景音乐',
  '/group': '分组管理',
  '/schedule': '定时任务',
  '/settings': '设置'
}

const pageTitle = computed(() => pageTitles[route.path] || '播放列表')

const changePassword = () => {
  ElMessage.info('修改密码功能开发中')
}

const logout = () => {
  ElMessage.info('登出功能开发中')
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

.logo-text-wrapper {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.logo-version {
  font-size: 11px;
  font-weight: 600;
  color: #448AFF;
  letter-spacing: 0.5px;
  text-transform: uppercase;
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
}

.user-avatar {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #1A237E, #448AFF);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.user-avatar:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(68, 138, 255, 0.4);
}

.user-avatar .el-icon {
  font-size: 18px;
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
