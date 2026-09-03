package com.roger.turntablerpm.history

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * 量測歷史。
 *
 * **分析一完成就自動存檔，不做手動按鈕。** iOS 端的筆記寫得很清楚：
 * 使用者不會記得按，而歷史的價值就在「調整前後能比較」，漏存一次就斷了。
 * 這條在移植 Android 版時漏掉了，直到使用者做「手機轉 180°」的實驗、
 * 連做兩次才發現第一次的結果已經沒了（見 CLAUDE.md 坑 40）。
 */
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    private val _records = MutableStateFlow<List<MeasurementRecord>>(emptyList())

    /** 由新到舊。 */
    val records: StateFlow<List<MeasurementRecord>> = _records.asStateFlow()

    init { load() }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        _records.value = try {
            val arr = JSONArray(raw)
            (0 until arr.length())
                .map { MeasurementRecord.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.epochMillis }
        } catch (_: Exception) {
            emptyList()      // 格式壞掉就當作沒有，不要讓 app 開不起來
        }
    }

    fun add(record: MeasurementRecord) {
        // 只留最近 MAX 筆。摘要很小，但沒有上限的成長遲早會變成問題。
        _records.value = (listOf(record) + _records.value).take(MAX)
        persist()
    }

    /** 只改備註。其他欄位是量到的，不該事後編輯。 */
    fun setNote(epochMillis: Long, note: String) {
        _records.value = _records.value.map {
            if (it.epochMillis == epochMillis) it.copy(note = note) else it
        }
        persist()
    }

    fun delete(epochMillis: Long) {
        _records.value = _records.value.filterNot { it.epochMillis == epochMillis }
        persist()
    }

    fun clear() {
        _records.value = emptyList()
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        _records.value.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "records"
        private const val MAX = 200
    }
}
