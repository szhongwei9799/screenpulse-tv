# ScreenPulse TV 变更追踪

> 记录所有需求变更，防止回滚丢失。每条格式：编号 | 时间 | 原始描述 | 结构化理解 | 状态 | 关联文件/提交

---

| 编号 | 时间 | 原始描述 | 结构化理解 | 状态 | 关联文件/提交 |
|------|------|----------|------------|------|---------------|
| CR-001 | 2026-06-08 | 首页右上角的控制栏（播放/暂停/停止/静音/音量/连接状态/刷新）都不要，这些功能没有实际用处 | 移除 Web 管理后台首页右上角的播放控制栏组件：Connected 标签 + 刷新按钮 | 已完成 | web-admin/src/App.vue, app/src/main/assets/web-admin/index.html, .github/workflows/build.yml |
| CR-002 | 2026-06-08 | 左上角 screenpulse 上方要显示版本号，每次编译我要看到版本号的变动 | 在侧边栏 Logo 区域，ScreenPulse 文字上方显示版本号（当前硬编码 v1.2.0，后续可由构建注入） | 代码就绪 | web-admin/src/App.vue, app/src/main/assets/web-admin/index.html, web-admin/src/router/index.js |
| CR-003 | 2026-06-08 | 媒体库左边的图标补上，风格要统一，注意整体效果 | 侧边栏菜单重构：播放列表、媒体库、定时任务、背景音乐、分组管理、设置；媒体库补齐 FolderOpened 图标；新增 3 个占位页面；统一中文标签 | 代码就绪 | web-admin/src/App.vue, app/src/main/assets/web-admin/index.html, web-admin/src/router/index.js, web-admin/src/views/Schedule.vue, Music.vue, Group.vue |
| CR-004 | 2026-06-08 | 右上角电源图标改成下拉菜单，里面包含"修改密码"和"登出" | 将 header 右侧的电源按钮替换为用户下拉菜单（ElDropdown），菜单项：修改密码、登出 | 代码就绪 | web-admin/src/App.vue, app/src/main/assets/web-admin/index.html |
| CR-005 | 2026-06-08 | 定时任务要排在最后第二项 | 调整侧边栏菜单顺序：播放列表、媒体库、背景音乐、分组管理、定时任务、设置 | 代码就绪 | web-admin/src/App.vue, app/src/main/assets/web-admin/index.html, web-admin/src/router/index.js |
