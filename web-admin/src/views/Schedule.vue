<template>
  <div class="coming-soon">
    <el-icon :size="80" class="icon"><Timer /></el-icon>
    <h2>定时任务</h2>
    <p>功能开发中，敬请期待</p>
  </div>
</template>

<script setup>
import { Timer } from '@element-plus/icons-vue'
</script>

<style scoped>
.coming-soon {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #78909C;
  gap: 16px;
}
.icon {
  color: #448AFF;
}
h2 {
  margin: 0;
  font-size: 24px;
  color: #E0E0E0;
}
p {
  margin: 0;
  font-size: 14px;
}
</style>