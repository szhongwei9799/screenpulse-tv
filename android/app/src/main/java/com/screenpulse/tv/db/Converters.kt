package com.screenpulse.tv.db

import androidx.room.TypeConverter

/**
 * Room 类型转换器
 *
 * 处理 Room 数据库中自定义类型的序列化和反序列化
 */
class Converters {

    /**
     * Long 转 String（用于可能为 null 的数值字段）
     */
    @TypeConverter
    fun fromLong(value: Long?): String? {
        return value?.toString()
    }

    /**
     * String 转 Long
     */
    @TypeConverter
    fun toLong(value: String?): Long? {
        return value?.toLongOrNull()
    }

    /**
     * Int 转 String
     */
    @TypeConverter
    fun fromInt(value: Int?): String? {
        return value?.toString()
    }

    /**
     * String 转 Int
     */
    @TypeConverter
    fun toInt(value: String?): Int? {
        return value?.toIntOrNull()
    }

    /**
     * Boolean 转 Int（Room 不直接支持 Boolean）
     */
    @TypeConverter
    fun fromBoolean(value: Boolean?): Int {
        return if (value == true) 1 else 0
    }

    /**
     * Int 转 Boolean
     */
    @TypeConverter
    fun toBoolean(value: Int?): Boolean {
        return value == 1
    }

    /**
     * List<String> 转 JSON String
     */
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        if (value.isNullOrEmpty()) return null
        return com.google.gson.Gson().toJson(value)
    }

    /**
     * JSON String 转 List<String>
     */
    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            com.google.gson.Gson().fromJson(value, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
