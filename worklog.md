---
Task ID: 2
Agent: main
Task: 保留文件名 + 修复照片显示VIDEO + 媒体库删除/重命名

Work Log:
- 分析根因：scan API 返回 MediaItem 对象(title/url/type)，前端期望 name/path/size/date
- row.name 为 undefined 导致 getMediaType(undefined) 默认返回 VIDEO
- 修复上传文件名：不再用正则替换中文，仅过滤路径分隔符和危险字符
- 修复 triggerScan()：返回包含文件系统元数据的完整 JSON
- 新增 DELETE /api/media/:id 端点（删除文件+数据库记录）
- 新增 PUT /api/media/:id 端点（重命名文件+数据库）
- 前端修复类型列使用 row.type，删除改用 row.id，新增重命名对话框
- 第一次编译成功（run 26872369250）

Stage Summary:
- 修改文件：ApiRouter.kt, WebServer.kt, index.html (assets + web-admin)
- APK: https://github.com/szhongwei9799/screenpulse-tv/actions/runs/26872369250
