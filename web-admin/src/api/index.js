import axios from 'axios'

const api = axios.create({
  baseURL: '/',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Response interceptor for error handling
api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error.response?.data?.message || error.message || 'Request failed'
    console.error('API Error:', message)
    return Promise.reject(error)
  }
)

// Device status
export const getStatus = () => api.get('/api/status')

// Playlist
export const getPlaylist = () => api.get('/api/playlist')
export const addPlaylistItem = (item) => api.post('/api/playlist', item)
export const updatePlaylistItem = (id, item) => api.put(`/api/playlist/${id}`, item)
export const deletePlaylistItem = (id) => api.delete(`/api/playlist/${id}`)
export const updatePlaylist = (playlist) => api.put('/api/playlist', playlist)

// Config
export const getConfig = () => api.get('/api/config')
export const updateConfig = (config) => api.put('/api/config', config)

// Upload
export const uploadFile = (formData) =>
  api.post('/api/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })

// Media scan
export const scanMedia = () => api.get('/api/scan')

export default api
