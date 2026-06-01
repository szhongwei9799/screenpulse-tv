<template>
  <div class="status-card" :style="cardStyle">
    <div class="card-icon-wrapper" :style="iconWrapperStyle">
      <el-icon :size="24"><component :is="icon" /></el-icon>
    </div>
    <div class="card-content">
      <div class="card-label">{{ label }}</div>
      <div class="card-value">{{ displayValue }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  icon: { type: [String, Object], required: true },
  label: { type: String, required: true },
  value: { type: [String, Number], default: '--' },
  color: { type: String, default: '#448AFF' }
})

const displayValue = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return '--'
  return props.value
})

const cardStyle = computed(() => ({
  '--card-color': props.color
}))

const iconWrapperStyle = computed(() => ({
  background: `${props.color}15`,
  color: props.color
}))
</script>

<style scoped>
.status-card {
  background: #161B22;
  border: 1px solid #21262D;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all 0.3s ease;
}

.status-card:hover {
  border-color: var(--card-color);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3), 0 0 0 1px var(--card-color);
  transform: translateY(-2px);
}

.card-icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.3s ease;
}

.status-card:hover .card-icon-wrapper {
  transform: scale(1.05);
}

.card-content {
  flex: 1;
  min-width: 0;
}

.card-label {
  font-size: 13px;
  color: #78909C;
  font-weight: 500;
  margin-bottom: 4px;
}

.card-value {
  font-size: 20px;
  font-weight: 700;
  color: #E0E0E0;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
