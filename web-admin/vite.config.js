import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  base: './',
  build: {
    outDir: resolve(__dirname, '../app/src/main/assets/web-admin'),
    emptyOutDir: true,
    assetsDir: 'assets',
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus', '@element-plus/icons-vue'],
          'vendor': ['vue', 'vue-router', 'axios']
        }
      }
    }
  },
  server: {
    port: 8081,
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true
      }
    }
  }
})
