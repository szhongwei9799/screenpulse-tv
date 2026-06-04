<template>
  <div class="dashboard" v-loading="loading">
    <!-- Status Cards Row -->
    <div class="status-grid">
      <StatusCard icon="Monitor" label="Device Status" :value="status.online ? 'Online' : 'Offline'" :color="status.online ? '#4CAF50' : '#F44336'" />
      <StatusCard icon="Connection" label="IP Address" :value="status.ip || '--'" color="#448AFF" />
      <StatusCard icon="Timer" label="Uptime" :value="status.uptime || '--'" color="#FF9800" />
      <StatusCard icon="VideoPlay" label="Total Media" :value="status.mediaCount || 0" color="#AB47BC" />
      <StatusCard icon="Film" label="Playlist Items" :value="status.playlistCount || 0" color="#26C6DA" />
      <StatusCard icon="Coin" label="Storage Used" :value="status.storageUsed || '--'" color="#66BB6A" />
    </div>

    <!-- Info Row -->
    <div class="info-grid">
      <!-- Current Playing -->
      <el-card class="info-card">
        <template #header>
          <div class="card-header">
            <el-icon><VideoPlay /></el-icon>
            <span>Now Playing</span>
            <el-tag v-if="currentItem" type="success" effect="dark" size="small" class="live-tag">
              <span class="live-dot"></span> LIVE
            </el-tag>
          </div>
        </template>
        <div v-if="currentItem" class="now-playing-content">
          <div class="playing-thumbnail">
            <el-icon :size="40"><VideoPlay /></el-icon>
          </div>
          <div class="playing-info">
            <div class="playing-title">{{ currentItem.title }}</div>
            <div class="playing-meta">
              <MediaTypeBadge :type="currentItem.type" />
              <span class="playing-url">{{ truncateUrl(currentItem.url) }}</span>
            </div>
            <div class="playing-duration" v-if="currentItem.duration">
              {{ currentItem.duration === 'auto' ? 'Auto (video length)' : `${currentItem.duration}s` }}
            </div>
          </div>
        </div>
        <div v-else class="empty-state">
          <el-icon :size="32" class="empty-icon"><VideoPause /></el-icon>
          <span>No media currently playing</span>
        </div>
      </el-card>

      <!-- Playlist Summary -->
      <el-card class="info-card">
        <template #header>
          <div class="card-header">
            <el-icon><List /></el-icon>
            <span>Playlist Summary</span>
          </div>
        </template>
        <div v-if="playlistSummary.length" class="summary-list">
          <div v-for="item in playlistSummary" :key="item.type" class="summary-row">
            <MediaTypeBadge :type="item.type" />
            <div class="summary-bar-wrapper">
              <div class="summary-bar" :style="{ width: `${item.percentage}%`, background: item.color }"></div>
            </div>
            <span class="summary-count">{{ item.count }} items</span>
            <span class="summary-pct">{{ item.percentage }}%</span>
          </div>
        </div>
        <div v-else class="empty-state">
          <el-icon :size="32" class="empty-icon"><Document /></el-icon>
          <span>Playlist is empty</span>
        </div>
      </el-card>

      <!-- Device Info -->
      <el-card class="info-card">
        <template #header>
          <div class="card-header">
            <el-icon><InfoFilled /></el-icon>
            <span>Device Information</span>
          </div>
        </template>
        <div class="device-info">
          <div class="info-row">
            <span class="info-label">Device Name</span>
            <span class="info-value">{{ status.deviceName || 'ScreenPulse Player' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">Model</span>
            <span class="info-value">{{ status.model || 'Android TV' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">Android Version</span>
            <span class="info-value">{{ status.androidVersion || '--' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">App Version</span>
            <span class="info-value">{{ status.appVersion || '1.0.0' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">MAC Address</span>
            <span class="info-value">{{ status.mac || '--' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">Resolution</span>
            <span class="info-value">{{ status.resolution || '--' }}</span>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { Monitor, Connection, Timer, VideoPlay, VideoPause, List, Coin, InfoFilled, Film, Document } from '@element-plus/icons-vue'
import StatusCard from '../components/StatusCard.vue'
import MediaTypeBadge from '../components/MediaTypeBadge.vue'
import { getStatus, getPlaylist } from '../api'

const loading = ref(true)
const status = ref({})
const playlist = ref([])

const currentItem = computed(() => {
  if (!playlist.value.length) return null
  const enabled = playlist.value.filter(p => p.enabled)
  return enabled[0] || null
})

const playlistSummary = computed(() => {
  if (!playlist.value.length) return []
  const types = {}
  playlist.value.forEach(item => {
    const t = item.type || 'VIDEO'
    types[t] = (types[t] || 0) + 1
  })
  const colors = { VIDEO: '#448AFF', IMAGE: '#66BB6A', PPT: '#FF9800', IPTV: '#AB47BC', STREAM: '#EF5350', WEB: '#26C6DA' }
  return Object.entries(types).map(([type, count]) => ({
    type,
    count,
    percentage: Math.round((count / playlist.value.length) * 100),
    color: colors[type] || '#448AFF'
  })).sort((a, b) => b.count - a.count)
})

const truncateUrl = (url) => {
  if (!url) return ''
  return url.length > 50 ? url.substring(0, 50) + '...' : url
}

const fetchData = async () => {
  loading.value = true
  try {
    const [statusRes, playlistRes] = await Promise.allSettled([
      getStatus(),
      getPlaylist()
    ])
    if (statusRes.status === 'fulfilled') {
      status.value = statusRes.value || {}
    }
    if (playlistRes.status === 'fulfilled') {
      playlist.value = Array.isArray(playlistRes.value) ? playlistRes.value : (playlistRes.value?.items || [])
    }
  } catch (e) {
    console.error('Failed to load dashboard data:', e)
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)

// Auto-refresh every 30 seconds
const interval = setInterval(fetchData, 30000)
</script>

<style scoped>
.dashboard {
  max-width: 1400px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 20px;
}

.info-card {
  border-radius: 12px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}

.card-header .el-icon {
  color: #448AFF;
}

.live-tag {
  margin-left: auto;
}

.live-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  background: #4CAF50;
  border-radius: 50%;
  margin-right: 4px;
  animation: pulse 1.5s ease-in-out infinite;
}

.now-playing-content {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.playing-thumbnail {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #1A237E, #448AFF);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.playing-info {
  flex: 1;
  min-width: 0;
}

.playing-title {
  font-size: 16px;
  font-weight: 600;
  color: #E0E0E0;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.playing-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.playing-url {
  font-size: 12px;
  color: #78909C;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.playing-duration {
  font-size: 12px;
  color: #448AFF;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px;
  color: #78909C;
}

.empty-icon {
  color: #30363D;
}

.summary-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.summary-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.summary-bar-wrapper {
  flex: 1;
  height: 6px;
  background: #21262D;
  border-radius: 3px;
  overflow: hidden;
}

.summary-bar {
  height: 100%;
  border-radius: 3px;
  transition: width 0.6s ease;
}

.summary-count {
  font-size: 12px;
  color: #B0BEC5;
  min-width: 60px;
  text-align: right;
}

.summary-pct {
  font-size: 12px;
  color: #78909C;
  min-width: 35px;
  text-align: right;
}

.device-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #21262D;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 13px;
  color: #78909C;
}

.info-value {
  font-size: 13px;
  color: #E0E0E0;
  font-weight: 500;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

@media (max-width: 768px) {
  .status-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .info-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 480px) {
  .status-grid {
    grid-template-columns: 1fr;
  }
}
</style>
