<template>
  <div class="settings-view" v-loading="loading">
    <div class="settings-grid">
      <!-- Playback Settings -->
      <el-card class="settings-card">
        <template #header>
          <div class="card-header">
            <el-icon><VideoPlay /></el-icon>
            <span>Playback Settings</span>
          </div>
        </template>

        <el-form label-position="top" class="settings-form">
          <el-form-item label="Playback Mode">
            <el-radio-group v-model="config.playbackMode" class="mode-group">
              <el-radio-button value="loop">
                <el-icon><RefreshRight /></el-icon>
                Loop
              </el-radio-button>
              <el-radio-button value="sequential">
                <el-icon><Sort /></el-icon>
                Sequential
              </el-radio-button>
              <el-radio-button value="random">
                <el-icon><Switch /></el-icon>
                Random
              </el-radio-button>
            </el-radio-group>
            <div class="form-hint">
              {{ playbackModeHint }}
            </div>
          </el-form-item>

          <el-form-item label="Volume">
            <div class="volume-control">
              <el-icon><Mute /></el-icon>
              <el-slider
                v-model="config.volume"
                :min="0"
                :max="100"
                :step="1"
                :show-tooltip="true"
                class="volume-slider"
              />
              <el-icon><Bell /></el-icon>
              <span class="volume-value">{{ config.volume }}%</span>
            </div>
          </el-form-item>

          <el-form-item label="Image Duration (seconds)">
            <el-input-number
              v-model="config.imageDuration"
              :min="1"
              :max="3600"
              :step="1"
            />
            <div class="form-hint">Default display duration for image items without custom duration.</div>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- Interstitial Settings -->
      <el-card class="settings-card">
        <template #header>
          <div class="card-header">
            <el-icon><Clock /></el-icon>
            <span>Interstitial Settings</span>
          </div>
        </template>

        <el-form label-position="top" class="settings-form">
          <el-form-item label="Enable Interstitial Mode">
            <div class="toggle-row">
              <el-switch v-model="config.interstitialEnabled" />
              <span class="toggle-label">
                {{ config.interstitialEnabled ? 'Enabled' : 'Disabled' }}
              </span>
            </div>
            <div class="form-hint">Display content only during specified hours.</div>
          </el-form-item>

          <template v-if="config.interstitialEnabled">
            <el-form-item label="Active Start Hour">
              <el-time-picker
                v-model="config.interstitialStart"
                placeholder="Start time"
                format="HH:mm"
                :disabled-hours="() => []"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="Active End Hour">
              <el-time-picker
                v-model="config.interstitialEnd"
                placeholder="End time"
                format="HH:mm"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="Off-Screen Message">
              <el-input
                v-model="config.offScreenMessage"
                placeholder="Message shown outside active hours"
                clearable
              />
            </el-form-item>
          </template>
        </el-form>
      </el-card>

      <!-- Device Settings -->
      <el-card class="settings-card">
        <template #header>
          <div class="card-header">
            <el-icon><Monitor /></el-icon>
            <span>Device Settings</span>
          </div>
        </template>

        <el-form label-position="top" class="settings-form">
          <el-form-item label="Device Name">
            <el-input v-model="config.deviceName" placeholder="Enter device name" clearable />
          </el-form-item>

          <el-form-item label="Screen Orientation">
            <el-radio-group v-model="config.orientation">
              <el-radio-button value="landscape">Landscape</el-radio-button>
              <el-radio-button value="portrait">Portrait</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="Idle Timeout (minutes, 0 = never)">
            <el-input-number
              v-model="config.idleTimeout"
              :min="0"
              :max="480"
              :step="5"
            />
          </el-form-item>
        </el-form>
      </el-card>

      <!-- Network Info -->
      <el-card class="settings-card">
        <template #header>
          <div class="card-header">
            <el-icon><Connection /></el-icon>
            <span>Network Information</span>
          </div>
        </template>

        <div class="network-info" v-loading="networkLoading">
          <div class="info-row">
            <span class="info-label">IP Address</span>
            <span class="info-value mono">{{ networkInfo.ip || '--' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">Gateway</span>
            <span class="info-value mono">{{ networkInfo.gateway || '--' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">DNS</span>
            <span class="info-value mono">{{ networkInfo.dns || '--' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">SSID</span>
            <span class="info-value mono">{{ networkInfo.ssid || '--' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">Signal Strength</span>
            <span class="info-value">{{ networkInfo.signal || '--' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">MAC Address</span>
            <span class="info-value mono">{{ networkInfo.mac || '--' }}</span>
          </div>
        </div>
      </el-card>
    </div>

    <!-- Save Button -->
    <div class="save-bar">
      <el-button type="primary" size="large" :icon="Check" :loading="saving" @click="saveSettings" class="save-button">
        Save Settings
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoPlay, Clock, Monitor, Connection, RefreshRight, Sort, Switch, Mute, Bell, Check } from '@element-plus/icons-vue'
import { getStatus, getConfig, updateConfig } from '../api'

const loading = ref(false)
const saving = ref(false)
const networkLoading = ref(false)
const networkInfo = ref({})

const config = reactive({
  playbackMode: 'loop',
  volume: 50,
  imageDuration: 10,
  interstitialEnabled: false,
  interstitialStart: null,
  interstitialEnd: null,
  offScreenMessage: '',
  deviceName: 'ScreenPulse Player',
  orientation: 'landscape',
  idleTimeout: 0
})

const playbackModeHint = computed(() => {
  const hints = {
    loop: 'Repeat the playlist continuously',
    sequential: 'Play through once and stop',
    random: 'Shuffle and play randomly'
  }
  return hints[config.playbackMode] || ''
})

const fetchSettings = async () => {
  loading.value = true
  try {
    const [statusRes, configRes] = await Promise.allSettled([getStatus(), getConfig()])

    if (statusRes.status === 'fulfilled' && statusRes.value) {
      const s = statusRes.value
      networkInfo.value = {
        ip: s.ip,
        gateway: s.gateway,
        dns: s.dns,
        ssid: s.ssid,
        signal: s.signal,
        mac: s.mac
      }
    }

    if (configRes.status === 'fulfilled' && configRes.value) {
      const c = configRes.value
      if (c.playbackMode) config.playbackMode = c.playbackMode
      if (c.volume !== undefined) config.volume = c.volume
      if (c.imageDuration) config.imageDuration = c.imageDuration
      if (c.interstitialEnabled !== undefined) config.interstitialEnabled = c.interstitialEnabled
      if (c.interstitialStart) config.interstitialStart = c.interstitialStart
      if (c.interstitialEnd) config.interstitialEnd = c.interstitialEnd
      if (c.offScreenMessage) config.offScreenMessage = c.offScreenMessage
      if (c.deviceName) config.deviceName = c.deviceName
      if (c.orientation) config.orientation = c.orientation
      if (c.idleTimeout !== undefined) config.idleTimeout = c.idleTimeout
    }
  } catch (e) {
    console.error('Failed to load settings:', e)
  } finally {
    loading.value = false
  }
}

const saveSettings = async () => {
  saving.value = true
  try {
    await updateConfig({ ...config })
    ElMessage.success('Settings saved successfully')
  } catch (e) {
    ElMessage.error('Failed to save settings')
  } finally {
    saving.value = false
  }
}

onMounted(fetchSettings)
</script>

<style scoped>
.settings-view {
  max-width: 1200px;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
  gap: 20px;
}

.settings-card {
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

.settings-form {
  padding-top: 8px;
}

.settings-form :deep(.el-form-item__label) {
  color: #B0BEC5;
  font-weight: 500;
}

.form-hint {
  font-size: 12px;
  color: #78909C;
  margin-top: 6px;
}

.mode-group {
  display: flex;
  gap: 0;
}

.mode-group :deep(.el-radio-button__inner) {
  display: flex;
  align-items: center;
  gap: 6px;
}

.toggle-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toggle-label {
  font-size: 13px;
  color: #B0BEC5;
}

.volume-control {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.volume-control .el-icon {
  color: #78909C;
}

.volume-slider {
  flex: 1;
}

.volume-value {
  min-width: 48px;
  text-align: right;
  font-size: 14px;
  font-weight: 600;
  color: #448AFF;
}

.network-info {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
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

.info-value.mono {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}

.save-bar {
  margin-top: 32px;
  display: flex;
  justify-content: center;
}

.save-button {
  min-width: 200px;
  height: 44px;
  font-size: 15px;
  border-radius: 10px;
}

@media (max-width: 768px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
</style>
