<template>
  <span class="media-type-badge" :class="typeClass">
    {{ label }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: {
    type: String,
    required: true,
    validator: (v) => ['VIDEO', 'IMAGE', 'PPT', 'IPTV', 'STREAM', 'WEB'].includes(v)
  }
})

const typeConfig = {
  VIDEO:  { label: 'Video',  color: '#448AFF', bg: 'rgba(68,138,255,0.15)' },
  IMAGE:  { label: 'Image',  color: '#66BB6A', bg: 'rgba(102,187,106,0.15)' },
  PPT:    { label: 'PPT',    color: '#FF9800', bg: 'rgba(255,152,0,0.15)' },
  IPTV:   { label: 'IPTV',   color: '#AB47BC', bg: 'rgba(171,71,188,0.15)' },
  STREAM: { label: 'Stream', color: '#EF5350', bg: 'rgba(239,83,80,0.15)' },
  WEB:    { label: 'Web',    color: '#26C6DA', bg: 'rgba(38,198,218,0.15)' }
}

const config = computed(() => typeConfig[props.type] || typeConfig.VIDEO)
const label = computed(() => config.value.label)
const typeClass = computed(() => props.type.toLowerCase())
</script>

<style scoped>
.media-type-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  white-space: nowrap;
}

.media-type-badge.video {
  color: #448AFF;
  background: rgba(68,138,255,0.15);
}

.media-type-badge.image {
  color: #66BB6A;
  background: rgba(102,187,106,0.15);
}

.media-type-badge.ppt {
  color: #FF9800;
  background: rgba(255,152,0,0.15);
}

.media-type-badge.iptv {
  color: #AB47BC;
  background: rgba(171,71,188,0.15);
}

.media-type-badge.stream {
  color: #EF5350;
  background: rgba(239,83,80,0.15);
}

.media-type-badge.web {
  color: #26C6DA;
  background: rgba(38,198,218,0.15);
}
</style>
