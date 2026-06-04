<template>
  <div class="playlist-view" v-loading="loading">
    <!-- Toolbar -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="primary" :icon="Plus" @click="openAddDialog">
          Add Item
        </el-button>
        <el-button :icon="Refresh" @click="fetchPlaylist">Refresh</el-button>
      </div>
      <div class="toolbar-right">
        <template v-if="selectedRows.length > 0">
          <el-button :icon="CircleCheck" type="success" plain @click="bulkToggle(true)">
            Enable ({{ selectedRows.length }})
          </el-button>
          <el-button :icon="CircleClose" type="warning" plain @click="bulkToggle(false)">
            Disable ({{ selectedRows.length }})
          </el-button>
          <el-button :icon="Delete" type="danger" plain @click="batchDelete">
            Delete ({{ selectedRows.length }})
          </el-button>
        </template>
      </div>
    </div>

    <!-- Playlist Table -->
    <el-card class="table-card">
      <el-table
        ref="tableRef"
        :data="playlist"
        row-key="(row) => row.id + '_' + row.title"
        @selection-change="handleSelectionChange"
        empty-text="No items in playlist"
        class="playlist-table"
      >
        <el-table-column type="selection" width="50" />

        <el-table-column label="#" width="60" class-name="drag-handle-col">
          <template #default="{ $index }">
            <div class="drag-handle" draggable="true" @dragstart="onDragStart($event, $index)" @dragover.prevent>
              <el-icon :size="16"><Rank /></el-icon>
              <span>{{ $index + 1 }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="Title" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="title-cell">
              <div class="item-thumb">
                <el-icon :size="18">
                  <VideoPlay v-if="row.type === 'VIDEO'" />
                  <Picture v-else-if="row.type === 'IMAGE'" />
                  <Monitor v-else-if="row.type === 'IPTV' || row.type === 'STREAM'" />
                  <Document v-else />
                </el-icon>
              </div>
              <span class="item-title">{{ row.title || 'Untitled' }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="Type" width="100" align="center">
          <template #default="{ row }">
            <MediaTypeBadge :type="row.type || 'VIDEO'" />
          </template>
        </el-table-column>

        <el-table-column label="URL / Source" min-width="250" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="url-text">{{ row.url || '--' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="Duration" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.duration === 'auto' ? '' : 'info'" effect="plain" round>
              {{ row.duration === 'auto' ? 'Auto' : `${row.duration}s` }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="Enabled" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              @change="toggleEnabled(row)"
              size="small"
            />
          </template>
        </el-table-column>

        <el-table-column label="Actions" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button :icon="Edit" size="small" text @click="openEditDialog(row)" />
              <el-button :icon="Top" size="small" text type="primary" @click="moveUp(row)" :disabled="isFirst(row)" />
              <el-button :icon="Bottom" size="small" text type="primary" @click="moveDown(row)" :disabled="isLast(row)" />
              <el-button :icon="Delete" size="small" text type="danger" @click="confirmDelete(row)" />
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Add/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? 'Edit Playlist Item' : 'Add Playlist Item'"
      width="520px"
      destroy-on-close
      @close="resetForm"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-position="top"
        class="item-form"
      >
        <el-form-item label="Title" prop="title">
          <el-input v-model="formData.title" placeholder="Enter item title" clearable />
        </el-form-item>

        <el-form-item label="URL / Source" prop="url">
          <el-input v-model="formData.url" placeholder="File path, URL, or stream address" clearable />
        </el-form-item>

        <el-form-item label="Media Type" prop="type">
          <el-select v-model="formData.type" placeholder="Select type" style="width: 100%">
            <el-option label="Video" value="VIDEO" />
            <el-option label="Image" value="IMAGE" />
            <el-option label="PPT / Presentation" value="PPT" />
            <el-option label="IPTV Stream" value="IPTV" />
            <el-option label="Web Stream" value="STREAM" />
            <el-option label="Web Page" value="WEB" />
          </el-select>
        </el-form-item>

        <el-form-item label="Duration" prop="duration">
          <div class="duration-field">
            <el-switch
              v-model="isAutoDuration"
              active-text="Auto (video length)"
              inactive-text="Custom (seconds)"
              inline-prompt
              @change="handleDurationToggle"
            />
            <el-input-number
              v-if="!isAutoDuration"
              v-model="formData.duration"
              :min="1"
              :max="3600"
              :step="1"
              placeholder="Seconds"
              style="width: 160px"
            />
          </div>
        </el-form-item>

        <el-form-item label="Enabled" prop="enabled">
          <el-switch v-model="formData.enabled" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="saveItem">
          {{ isEditing ? 'Update' : 'Add' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Edit, Delete, Top, Bottom, Rank, CircleCheck, CircleClose, VideoPlay, Picture, Monitor, Document } from '@element-plus/icons-vue'
import MediaTypeBadge from '../components/MediaTypeBadge.vue'
import { getPlaylist, addPlaylistItem, updatePlaylistItem, deletePlaylistItem, updatePlaylist } from '../api'

const loading = ref(false)
const saving = ref(false)
const playlist = ref([])
const selectedRows = ref([])
const tableRef = ref(null)
const formRef = ref(null)
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref(null)
const isAutoDuration = ref(true)
const dragIndex = ref(null)

const formData = ref({
  title: '',
  url: '',
  type: 'VIDEO',
  duration: 'auto',
  enabled: true
})

const formRules = {
  title: [{ required: true, message: 'Title is required', trigger: 'blur' }],
  url: [{ required: true, message: 'URL or source is required', trigger: 'blur' }],
  type: [{ required: true, message: 'Type is required', trigger: 'change' }]
}

const fetchPlaylist = async () => {
  loading.value = true
  try {
    const data = await getPlaylist()
    playlist.value = Array.isArray(data) ? data : (data?.items || [])
  } catch (e) {
    ElMessage.error('Failed to load playlist')
  } finally {
    loading.value = false
  }
}

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

// Drag & Drop reorder
const onDragStart = (event, index) => {
  dragIndex.value = index
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', index)
}

// Apply drop on the table wrapper (via event delegation on the table card)
const setupDropZone = () => {
  const tableCard = document.querySelector('.table-card .el-table__body-wrapper')
  if (!tableCard) return

  tableCard.addEventListener('dragover', (e) => {
    e.preventDefault()
    e.dataTransfer.dropEffect = 'move'
  })

  tableCard.addEventListener('drop', (e) => {
    e.preventDefault()
    const fromIndex = parseInt(e.dataTransfer.getData('text/plain'))
    const toIndex = parseInt(e.target.closest('tr')?.dataset.index)
    if (isNaN(fromIndex) || isNaN(toIndex) || fromIndex === toIndex) return

    const item = playlist.value.splice(fromIndex, 1)[0]
    playlist.value.splice(toIndex, 0, item)
    saveOrder()
  })
}

const saveOrder = async () => {
  try {
    await updatePlaylist(playlist.value)
    ElMessage.success('Playlist order updated')
  } catch (e) {
    ElMessage.error('Failed to update order')
  }
}

const moveUp = (row) => {
  const idx = playlist.value.findIndex(p => p.id === row.id || p === row)
  if (idx > 0) {
    const item = playlist.value.splice(idx, 1)[0]
    playlist.value.splice(idx - 1, 0, item)
    saveOrder()
  }
}

const moveDown = (row) => {
  const idx = playlist.value.findIndex(p => p.id === row.id || p === row)
  if (idx < playlist.value.length - 1) {
    const item = playlist.value.splice(idx, 1)[0]
    playlist.value.splice(idx + 1, 0, item)
    saveOrder()
  }
}

const isFirst = (row) => {
  const idx = playlist.value.findIndex(p => p.id === row.id || p === row)
  return idx <= 0
}

const isLast = (row) => {
  const idx = playlist.value.findIndex(p => p.id === row.id || p === row)
  return idx >= playlist.value.length - 1 || idx < 0
}

// Dialog operations
const openAddDialog = () => {
  isEditing.value = false
  editingId.value = null
  formData.value = { title: '', url: '', type: 'VIDEO', duration: 'auto', enabled: true }
  isAutoDuration.value = true
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  isEditing.value = true
  editingId.value = row.id
  formData.value = { ...row }
  isAutoDuration.value = formData.value.duration === 'auto'
  dialogVisible.value = true
}

const handleDurationToggle = (val) => {
  formData.value.duration = val ? 'auto' : 10
}

const resetForm = () => {
  formRef.value?.resetFields()
}

const saveItem = async () => {
  try {
    await formRef.value?.validate()
  } catch { return }

  saving.value = true
  try {
    if (isEditing.value) {
      await updatePlaylistItem(editingId.value, formData.value)
      ElMessage.success('Item updated')
    } else {
      await addPlaylistItem(formData.value)
      ElMessage.success('Item added')
    }
    dialogVisible.value = false
    await fetchPlaylist()
  } catch (e) {
    ElMessage.error('Failed to save item')
  } finally {
    saving.value = false
  }
}

const toggleEnabled = async (row) => {
  try {
    await updatePlaylistItem(row.id || row._id, { enabled: row.enabled })
  } catch (e) {
    row.enabled = !row.enabled
    ElMessage.error('Failed to update item')
  }
}

const confirmDelete = (row) => {
  ElMessageBox.confirm(
    `Delete "${row.title || 'Untitled'}" from the playlist?`,
    'Confirm Delete',
    { confirmButtonText: 'Delete', cancelButtonText: 'Cancel', type: 'warning' }
  ).then(async () => {
    try {
      await deletePlaylistItem(row.id || row._id)
      ElMessage.success('Item deleted')
      await fetchPlaylist()
    } catch (e) {
      ElMessage.error('Failed to delete item')
    }
  }).catch(() => {})
}

const bulkToggle = async (enabled) => {
  try {
    for (const row of selectedRows.value) {
      await updatePlaylistItem(row.id || row._id, { enabled })
      row.enabled = enabled
    }
    ElMessage.success(`${enabled ? 'Enabled' : 'Disabled'} ${selectedRows.value.length} items`)
    selectedRows.value = []
  } catch (e) {
    ElMessage.error('Bulk operation failed')
  }
}

const batchDelete = () => {
  ElMessageBox.confirm(
    `Delete ${selectedRows.value.length} selected items?`,
    'Batch Delete',
    { confirmButtonText: 'Delete All', cancelButtonText: 'Cancel', type: 'warning' }
  ).then(async () => {
    try {
      for (const row of selectedRows.value) {
        await deletePlaylistItem(row.id || row._id)
      }
      ElMessage.success('Items deleted')
      await fetchPlaylist()
    } catch (e) {
      ElMessage.error('Batch delete failed')
    }
  }).catch(() => {})
}

onMounted(() => {
  fetchPlaylist()
  setupDropZone()
})
</script>

<style scoped>
.playlist-view {
  max-width: 1400px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-left {
  display: flex;
  gap: 10px;
}

.toolbar-right {
  display: flex;
  gap: 10px;
}

.table-card {
  border-radius: 12px;
}

.playlist-table {
  width: 100%;
}

.drag-handle {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: grab;
  color: #78909C;
  padding: 4px 6px;
  border-radius: 4px;
  transition: all 0.2s ease;
  user-select: none;
}

.drag-handle:hover {
  color: #448AFF;
  background: rgba(68, 138, 255, 0.1);
}

.drag-handle:active {
  cursor: grabbing;
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.item-thumb {
  width: 36px;
  height: 36px;
  background: #21262D;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #448AFF;
  flex-shrink: 0;
}

.item-title {
  font-weight: 500;
  color: #E0E0E0;
}

.url-text {
  font-size: 13px;
  color: #78909C;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 2px;
}

.item-form :deep(.el-form-item__label) {
  color: #B0BEC5;
}

.duration-field {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .toolbar-right {
    flex-wrap: wrap;
  }
}
</style>
