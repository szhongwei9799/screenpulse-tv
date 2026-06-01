package com.screenpulse.tv.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.screenpulse.tv.db.AppDatabase
import com.screenpulse.tv.db.entities.PlaylistEntity
import com.screenpulse.tv.db.entities.ScheduleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * 定时任务调度管理器
 *
 * 使用 AlarmManager + WorkManager 实现定时内容插入功能
 * 支持类 cron 表达式的定时规则，在指定时间插入特殊内容（如广告、通知等）
 *
 * 功能：
 * - 解析简单的 cron-like 定时表达式
 * - 在指定时间中断当前播放，插入定时内容
 * - 定时内容播放完毕后自动恢复原播放列表
 * - 支持一次性定时和重复定时
 */
class ScheduleManager(private val context: Context) {

    companion object {
        private const val TAG = "ScheduleManager"

        /** 定时任务触发广播 Action */
        const val ACTION_SCHEDULE_TRIGGER = "com.screenpulse.tv.action.SCHEDULE_TRIGGER"
        const val EXTRA_SCHEDULE_ID = "schedule_id"

        /** 默认定时内容最大时长（毫秒） */
        private const val MAX_SCHEDULE_DURATION = 300_000L // 5 分钟
    }

    private val database: AppDatabase = AppDatabase.getInstance(context)
    private val scheduleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    /** 定时内容播放完成回调 */
    var onScheduleContentComplete: (() -> Unit)? = null

    /**
     * 添加定时任务
     * @param entity 定时任务实体
     * @return 任务 ID
     */
    fun addSchedule(entity: ScheduleEntity): Long {
        val id = runCatching {
            database.scheduleDao().insert(entity)
        }.getOrDefault(-1)

        if (id > 0) {
            // 注册定时器
            registerAlarm(id, entity)
            Log.d(TAG, "定时任务已添加: id=$id, name=${entity.name}")
        }

        return id
    }

    /**
     * 删除定时任务
     */
    fun removeSchedule(id: Long) {
        cancelAlarm(id)
        scheduleScope.launch {
            database.scheduleDao().deleteById(id)
            Log.d(TAG, "定时任务已删除: id=$id")
        }
    }

    /**
     * 更新定时任务
     */
    fun updateSchedule(entity: ScheduleEntity) {
        scheduleScope.launch {
            database.scheduleDao().update(entity)
            // 先取消旧定时器，再注册新的
            cancelAlarm(entity.id)
            registerAlarm(entity.id, entity)
            Log.d(TAG, "定时任务已更新: id=${entity.id}")
        }
    }

    /**
     * 获取所有定时任务
     */
    suspend fun getAllSchedules(): List<ScheduleEntity> {
        return database.scheduleDao().getAll()
    }

    /**
     * 获取已启用的定时任务
     */
    suspend fun getActiveSchedules(): List<ScheduleEntity> {
        return database.scheduleDao().getActive()
    }

    /**
     * 触发定时任务
     * 由 BroadcastReceiver 在 AlarmManager 触发时调用
     */
    fun triggerSchedule(scheduleId: Long) {
        scheduleScope.launch {
            val schedule = database.scheduleDao().getById(scheduleId) ?: return@launch

            Log.i(TAG, "触发定时任务: ${schedule.name}")

            // 解析定时内容播放列表
            val items = parseScheduleContent(schedule)
            if (items.isEmpty()) {
                Log.w(TAG, "定时任务内容为空，跳过")
                return@launch
            }

            // 通知播放引擎插入定时内容
            // 注意：实际调用需要在 MainActivity/ViewModel 中桥接
            onScheduleTriggered(items, schedule)

            // 如果是一次性任务，执行后删除
            if (schedule.repeat) {
                // 重复任务：注册下一次触发
                val nextTime = calculateNextTriggerTime(schedule)
                if (nextTime > 0) {
                    registerAlarm(scheduleId, schedule)
                }
            } else {
                // 一次性任务：标记为已完成
                database.scheduleDao().updateCompleted(scheduleId, true)
            }
        }
    }

    /**
     * 定时任务触发回调 - 设置由外部注入
     */
    var onScheduleTriggered: (items: List<PlaylistEntity>, schedule: ScheduleEntity) -> Unit = { _, _ ->
        Log.w(TAG, "onScheduleTriggered 未设置，定时内容将被忽略")
    }

    /**
     * 注册 AlarmManager 定时器
     */
    private fun registerAlarm(scheduleId: Long, entity: ScheduleEntity) {
        try {
            val triggerTime = calculateNextTriggerTime(entity)
            if (triggerTime <= 0) {
                Log.w(TAG, "无法计算下次触发时间")
                return
            }

            val intent = Intent(ACTION_SCHEDULE_TRIGGER).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                scheduleId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 根据时间精度选择合适的 AlarmManager 方法
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            val triggerDate = Date(triggerTime)
            Log.d(TAG, "定时器已注册: id=$scheduleId, 触发时间=$triggerDate")
        } catch (e: SecurityException) {
            Log.e(TAG, "注册定时器失败 - 权限不足（需要 SCHEDULE_EXACT_ALARM）", e)
        } catch (e: Exception) {
            Log.e(TAG, "注册定时器失败", e)
        }
    }

    /**
     * 取消 AlarmManager 定时器
     */
    private fun cancelAlarm(scheduleId: Long) {
        try {
            val intent = Intent(ACTION_SCHEDULE_TRIGGER).apply {
                setPackage(context.packageName)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                scheduleId.toInt(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
            Log.d(TAG, "定时器已取消: id=$scheduleId")
        } catch (e: Exception) {
            Log.e(TAG, "取消定时器失败", e)
        }
    }

    /**
     * 计算下次触发时间（毫秒时间戳）
     * 解析简单的 cron-like 表达式
     *
     * 支持格式：
     * - "HH:mm" - 每天（如 "09:30"）
     * - "HH:mm HH:mm" - 时间范围（如 "09:00 18:00"）
     * - "d HH:mm" - 每周指定天（如 "1 09:00" = 每周一 9 点）
     * - 时间戳 - 绝对时间
     */
    private fun calculateNextTriggerTime(entity: ScheduleEntity): Long {
        val cron = entity.cron

        return try {
            when {
                // 绝对时间戳
                cron.matches(Regex("^\\d{10,13}$")) -> {
                    val ts = cron.toLong()
                    if (ts > System.currentTimeMillis() / 1000) {
                        ts * 1000
                    } else {
                        // 已过去的时间戳，如果是重复任务则加一天
                        if (entity.repeat) {
                            ts * 1000 + 24 * 60 * 60 * 1000
                        } else {
                            -1L // 过期的一次性任务
                        }
                    }
                }

                // 每天 HH:mm 格式
                cron.matches(Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) -> {
                    val (hour, minute) = cron.split(":").map { it.toInt() }
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    // 如果今天的时间已过，设置为明天
                    if (calendar.timeInMillis <= System.currentTimeMillis()) {
                        calendar.add(Calendar.DAY_OF_MONTH, 1)
                    }

                    calendar.timeInMillis
                }

                // 每周 d HH:mm 格式 (1-7, 1=周日或周一取决于系统)
                cron.matches(Regex("^[1-7] ([01]?[0-9]|2[0-3]):[0-5][0-9]$")) -> {
                    val parts = cron.split(" ")
                    val dayOfWeek = parts[0].toInt()
                    val (hour, minute) = parts[1].split(":").map { it.toInt() }

                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    // 计算到下周目标日期的天数差
                    val currentDay = get(Calendar.DAY_OF_WEEK)
                    var daysUntil = dayOfWeek - currentDay
                    if (daysUntil <= 0) daysUntil += 7

                    calendar.add(Calendar.DAY_OF_MONTH, daysUntil)

                    // 如果计算出来的时间仍然在过去，加一周
                    if (calendar.timeInMillis <= System.currentTimeMillis()) {
                        calendar.add(Calendar.WEEK_OF_MONTH, 1)
                    }

                    calendar.timeInMillis
                }

                // 时间范围格式 HH:mm HH:mm
                cron.matches(Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9] ([01]?[0-9]|2[0-3]):[0-5][0-9]$")) -> {
                    val parts = cron.split(" ")
                    val (startHour, startMinute) = parts[0].split(":").map { it.toInt() }

                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, startHour)
                        set(Calendar.MINUTE, startMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    if (calendar.timeInMillis <= System.currentTimeMillis()) {
                        calendar.add(Calendar.DAY_OF_MONTH, 1)
                    }

                    calendar.timeInMillis
                }

                else -> {
                    Log.w(TAG, "无法解析 cron 表达式: $cron")
                    -1L
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "计算触发时间失败", e)
            -1L
        }
    }

    /**
     * 解析定时内容
     * 从定时任务的 content JSON 中解析出播放列表项
     */
    private fun parseScheduleContent(schedule: ScheduleEntity): List<PlaylistEntity> {
        val content = schedule.contentJson
        if (content.isNullOrBlank()) return emptyList()

        return try {
            com.google.gson.Gson().fromJson(
                content,
                Array<PlaylistEntity>::class.java
            ).toList()
        } catch (e: Exception) {
            Log.e(TAG, "解析定时内容失败", e)
            emptyList()
        }
    }

    /**
     * 初始化所有定时任务
     * 在应用启动时调用，恢复所有已启用的定时任务
     */
    fun initAllSchedules() {
        scheduleScope.launch {
            val schedules = database.scheduleDao().getActive()
            schedules.forEach { schedule ->
                registerAlarm(schedule.id, schedule)
            }
            Log.d(TAG, "已恢复 ${schedules.size} 个定时任务")
        }
    }

    /**
     * 取消所有定时任务
     */
    fun cancelAllSchedules() {
        scheduleScope.launch {
            val schedules = database.scheduleDao().getActive()
            schedules.forEach { cancelAlarm(it.id) }
            Log.d(TAG, "已取消所有定时任务")
        }
    }
}

/**
 * 开机自启动接收器
 */
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i("BootReceiver", "设备开机，恢复定时任务")
            val scheduleManager = ScheduleManager(context)
            scheduleManager.initAllSchedules()
        }
    }
}
