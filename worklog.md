---
Task ID: 1
Agent: Main Agent
Task: Fix script loading failure + checkbox selection + media library persistence + build pipeline

Work Log:
- Analyzed project structure: two modules (app/ with WebServer.kt + android/ with ApiHandler.kt)
- Identified root cause of script loading failure: large JS files served as Strings may fail with AAPT compressed assets
- Modified WebServer.kt to serve ALL files as byte arrays (removed text/binary distinction)
- Fixed build.gradle by reverting to original (removed aaptOptions that caused dataBinding error)
- Attempted 6+ CI builds, all failed due to Google Maven (dl.google.com) being unreachable from GitHub Actions runners
- Synchronized all fixes to Vite source code (web-admin/src/) since CI runs Vite build which overwrites assets
- Fixed Playlist.vue: row-key changed from "id" to composite key "(row) => row.id + '_' + row.title"
- Fixed MediaLibrary.vue: added auto-scan on mount when list is empty
- Fixed web-admin/index.html: added script load retry with cache-bust mechanism
- Fixed app/src/main/assets/web-admin/index.html: same fixes (directly in assets)

Stage Summary:
- All code fixes pushed to main branch
- CI blocked by Google Maven infrastructure issue (not code issue)
- 3 key files modified: WebServer.kt, Playlist.vue, MediaLibrary.vue (+ 2 index.html files)
- Build will auto-trigger when GitHub Actions infrastructure recovers
- User warned about Vite build overwriting assets directory
