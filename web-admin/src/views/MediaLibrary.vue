<template>
  <div class="media-library" v-loading="loading">
    <!-- Upload Area -->
    <el-card class="upload-card">
      <template #header>
        <div class="card-header">
          <el-icon><UploadFilled /></el-icon>
          <span>Upload Media Files</span>
        </div>
      </template>

      <el-upload
        ref="uploadRef"
        :action="uploadUrl"
        :auto-upload="false"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        :on-exceed="handleExceed"
        :file-list="fileList"
        :accept="acceptTypes"
        drag
        multiple
        class="upload-area"
      >
        <div class="upload-content">
          <el-icon :size="48" class="upload-icon"><UploadFilled /></el-icon>
          <div class="upload-text">Drop files here or click to upload</div>
          <div class="upload-hint">
            Supports: Videos (MP4, MKV, AVI), Images (JPG, PNG, GIF, BMP), Presentations (PPT, PPTX)
          </div>
        </div>
      </el-upload>

      <div class="upload-actions">
        <el-button type="primary" :icon="UploadFilled" @click="uploadFiles" :loading="uploading" :disabled="!fileList.length">
          Upload {{ fileList.length }} file{{ fileList.length !== 1 ? 's' : '' }}
        </el-button>
        <el-button :icon="Refresh" @click="scanMediaFiles">
          Scan Local Storage
        </el-button>
      </div>
    </el-card>

    <!-- File List -->
    <el-card class="files-card" style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <el-icon><FolderOpened /></el-icon>
          <span>Media Files</span>
          <el-tag effect="dark" size="small" class="count-tag">{{ mediaFiles.length }} files</el-tag>
        </div>
      </template>

      <el-table :data="mediaFiles" empty-text="No media files found" class="files-table">
        <el-table-column label="File" min-width="300">
          <template #default="{ row }">
            <div class="file-cell">
              <div class="file-icon-wrapper" :class="getFileType(row.name)">
                <el-icon :size="20">
                  <VideoPlay v-if="isVideo(row.name)" />
                  <Picture v-else-if="isImage(row.name)" />
                  <Document v-else />
                </el-icon>
              </div>
              <div class="file-info">
                <div class="file-name">{{ row.name }}</div>
                <div class="file-path" v-if="row.path">{{ row.path }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="Type" width="100" align="center">
          <template #default="{ row }">
            <MediaTypeBadge :type="getMediaType(row.name)" />
          </template>
        </el-table-column>

        <el-table-column label="Size" width="120" align="right">
          <template #default="{ row }">
            <span class="file-size">{{ formatSize(row.size) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="Date" width="160" align="center">
          <template #default="{ row }">
            <span class="file-date">{{ row.modifiedAt || row.date || '--' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="Actions" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button :icon="CopyDocument" size="small" text type="primary" @click="copyPath(row)" title="Copy path" />
              <el-button :icon="Delete" size="small" text type="danger" @click="confirmDelete(row)" />
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, FolderOpened, VideoPlay, Picture, Document, CopyDocument, Delete, Refresh } from '@element-plus/icons-vue'
import MediaTypeBadge from '../components/MediaTypeBadge.vue'
import { uploadFile, scanMedia } from '../api'

const uploadUrl = `${window.location.origin}/api/upload`
const loading = ref(false)
const uploading = ref(false)
const fileList = ref([])
const mediaFiles = ref([])

const acceptTypes = '.mp4,.mkv,.avi,.mov,.wmv,.flv,.jpg,.jpeg,.png,.gif,.bmp,.webp,.ppt,.pptx'

const isVideo = (name) => /\.(mp4|mkv|avi|mov|wmv|flv|webm)$/i.test(name)
const isImage = (name) => /\.(jpg|jpeg|png|gif|bmp|webp)$/i.test(name)
const isPPT = (name) => /\.(ppt|pptx)$/i.test(name)

const getFileType = (name) => {
  if (isVideo(name)) return 'video'
  if (isImage(name)) return 'image'
  return 'document'
}

const getMediaType = (name) => {
  if (isVideo(name)) return 'VIDEO'
  if (isImage(name)) return 'IMAGE'
  if (isPPT(name)) return 'PPT'
  return 'VIDEO'
}

const formatSize = (bytes) => {
  if (!bytes && bytes !== 0) return '--'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

const handleFileChange = (file, files) => {
  fileList.value = files
}

const handleFileRemove = (file, files) => {
  fileList.value = files
}

const handleExceed = () => {
  ElMessage.warning('Maximum files reached')
}

const uploadFiles = async () => {
  if (!fileList.value.length) return
  uploading.value = true

  let successCount = 0
  let failCount = 0

  for (const file of fileList.value) {
    try {
      const formData = new FormData()
      formData.append('file', file.raw)
      await uploadFile(formData)
      successCount++
    } catch (e) {
      failCount++
    }
  }

  uploading.value = false

  if (failCount === 0) {
    ElMessage.success(`${successCount} file(s) uploaded successfully`)
    fileList.value = []
  } else {
    ElMessage.warning(`${successCount} uploaded, ${failCount} failed`)
  }

  fetchMediaFiles()
}

const scanMediaFiles = async () => {
  try {
    const result = await scanMedia()
    if (result?.files) {
      mediaFiles.value = result.files
    } else if (Array.isArray(result)) {
      mediaFiles.value = result
    }
    ElMessage.success('Media scan complete')
  } catch (e) {
    ElMessage.error('Media scan failed')
  }
}

const copyPath = (row) => {
  const path = row.path || row.name
  navigator.clipboard?.writeText(path).then(() => {
    ElMessage.success('Path copied')
  }).catch(() => {
    ElMessage.info(path)
  })
}

const confirmDelete = (row) => {
  ElMessageBox.confirm(
    `Delete "${row.name}"?`,
    'Confirm Delete',
    { confirmButtonText: 'Delete', cancelButtonText: 'Cancel', type: 'warning' }
  ).then(async () => {
    try {
      // Try delete via API - the endpoint may vary
      const api = (await import('../api')).default
      await api.delete(`/api/media/${encodeURIComponent(row.name)}`)
      ElMessage.success('File deleted')
      fetchMediaFiles()
    } catch (e) {
      ElMessage.error('Failed to delete file')
    }
  }).catch(() => {})
}

const fetchMediaFiles = async () => {
  loading.value = true
  try {
    const result = await scanMedia()
    if (result?.files) {
      mediaFiles.value = result.files
    } else if (Array.isArray(result)) {
      mediaFiles.value = result
    }
  } catch (e) {
    console.error('Failed to fetch media files:', e)
  } finally {
    loading.value = false
  }
}

onMounted(fetchMediaFiles)
</script>

<style scoped>
.media-library {
  max-width: 1400px;
}

.upload-card,
.files-card {
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

.count-tag {
  margin-left: auto;
}

.upload-area {
  width: 100%;
}

.upload-area :deep(.el-upload-dragger) {
  padding: 40px;
}

.upload-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.upload-icon {
  color: #30363D;
}

.upload-text {
  font-size: 16px;
  color: #B0BEC5;
  font-weight: 500;
}

.upload-hint {
  font-size: 12px;
  color: #78909C;
}

.upload-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.file-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-icon-wrapper {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-icon-wrapper.video {
  background: rgba(68, 138, 255, 0.15);
  color: #448AFF;
}

.file-icon-wrapper.image {
  background: rgba(102, 187, 106, 0.15);
  color: #66BB6A;
}

.file-icon-wrapper.document {
  background: rgba(255, 152, 0, 0.15);
  color: #FF9800;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-weight: 500;
  color: #E0E0E0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-path {
  font-size: 12px;
  color: #78909C;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  font-size: 13px;
  color: #B0BEC5;
  font-family: 'JetBrains Mono', monospace;
}

.file-date {
  font-size: 13px;
  color: #78909C;
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 2px;
}

@media (max-width: 768px) {
  .upload-area :deep(.el-upload-dragger) {
    padding: 24px;
  }
}
</style>
