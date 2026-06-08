// Top-level build file for ScreenPulse TV Player
// 项目顶层构建文件，配置全局插件和仓库

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    id("com.google.devtools.ksp") version "1.9.25-1.0.14" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.7.7" apply false
}

// 清理任务配置
tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
