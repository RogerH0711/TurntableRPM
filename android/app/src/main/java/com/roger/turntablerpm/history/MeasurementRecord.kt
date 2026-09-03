package com.roger.turntablerpm.history

import com.roger.turntablerpm.core.MeasurementAnalysis
import com.roger.turntablerpm.core.SpectralPeak
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一次量測的存檔。
 *
 * **只存分析後的結論，不存逐樣本原始資料。** 一次 3 分鐘的量測是兩萬筆樣本、
 * 約 2 MB，存幾十次就爆了。歷史記錄要回答的是「上一次調整之後有沒有變好」，
 * 以及「換一個擺法之後 1× 有沒有變」—— 摘要就夠。
 *
 * **譜峰一定要存。** 沒有它就做不了「手機轉 180° 再量一次」這種需要比對
 * 個別成分的實驗，而那正是這個 app 最有價值的用法。
 */
data class MeasurementRecord(
    val epochMillis: Long,
    val note: String,
    val meanRPM: Double,
    val rawMeanRPM: Double,
    val calibrationFactor: Double?,
    val nominalLabel: String?,
    val errorPercent: Double?,
    val durationSeconds: Double,
    /** 這段量測轉了幾圈。iOS 端有存，移植時漏掉了 —— 詳情頁要顯示。 */
    val revolutions: Int,
    val trimmedSeconds: Double,
    val sampleRate: Double,
    val rotationHz: Double,
    val wrmsPercent: Double,
    val peak2SigmaPercent: Double,
    val onePerRevPercent: Double,
    val dominantPeakShare: Double,
    val peakAngleDegrees: Double?,
    val peaks: List<StoredPeak>,
) {
    val isCalibrated: Boolean get() = calibrationFactor != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("t", epochMillis); put("note", note)
        put("mean", meanRPM); put("raw", rawMeanRPM)
        calibrationFactor?.let { put("k", it) }
        nominalLabel?.let { put("nom", it) }
        errorPercent?.let { put("err", it) }
        put("dur", durationSeconds); put("rev", revolutions)
        put("trim", trimmedSeconds); put("fs", sampleRate)
        put("rot", rotationHz); put("wrms", wrmsPercent); put("p2s", peak2SigmaPercent)
        put("one", onePerRevPercent); put("share", dominantPeakShare)
        peakAngleDegrees?.let { put("angle", it) }
        put("peaks", JSONArray().apply { peaks.forEach { put(it.toJson()) } })
    }

    companion object {
        fun from(
            analysis: MeasurementAnalysis,
            rawMeanRPM: Double,
            revolutions: Int,
            calibrationFactor: Double?,
            nominalLabel: String?,
            errorPercent: Double?,
            note: String = "",
            epochMillis: Long = System.currentTimeMillis(),
        ) = MeasurementRecord(
            epochMillis = epochMillis,
            note = note,
            meanRPM = analysis.meanRPM,
            rawMeanRPM = rawMeanRPM,
            calibrationFactor = calibrationFactor,
            nominalLabel = nominalLabel,
            errorPercent = errorPercent,
            durationSeconds = analysis.durationSeconds,
            revolutions = revolutions,
            trimmedSeconds = analysis.trimmedStartSeconds + analysis.trimmedEndSeconds,
            sampleRate = analysis.sampleRate,
            rotationHz = analysis.rotationHz,
            wrmsPercent = analysis.wowFlutter.wrmsPercent,
            peak2SigmaPercent = analysis.wowFlutter.peak2SigmaPercent,
            onePerRevPercent = analysis.onePerRevolutionPercent,
            dominantPeakShare = analysis.dominantPeakShare,
            peakAngleDegrees = analysis.peakAngleDegrees,
            peaks = analysis.peaks.take(8).map { StoredPeak.from(it) },
        )

        fun fromJson(o: JSONObject): MeasurementRecord {
            val arr = o.optJSONArray("peaks") ?: JSONArray()
            return MeasurementRecord(
                epochMillis = o.getLong("t"),
                note = o.optString("note", ""),
                meanRPM = o.getDouble("mean"),
                rawMeanRPM = o.optDouble("raw", o.getDouble("mean")),
                calibrationFactor = if (o.has("k")) o.getDouble("k") else null,
                nominalLabel = if (o.has("nom")) o.getString("nom") else null,
                errorPercent = if (o.has("err")) o.getDouble("err") else null,
                durationSeconds = o.getDouble("dur"),
                revolutions = o.optInt("rev", 0),
                trimmedSeconds = o.optDouble("trim", 0.0),
                sampleRate = o.optDouble("fs", 100.0),
                rotationHz = o.optDouble("rot", 0.0),
                wrmsPercent = o.getDouble("wrms"),
                peak2SigmaPercent = o.optDouble("p2s", 0.0),
                onePerRevPercent = o.optDouble("one", 0.0),
                dominantPeakShare = o.optDouble("share", 0.0),
                peakAngleDegrees = if (o.has("angle")) o.getDouble("angle") else null,
                peaks = (0 until arr.length()).map { StoredPeak.fromJson(arr.getJSONObject(it)) },
            )
        }
    }
}

/**
 * 存檔用的譜峰。**刻意跟核心的 `SpectralPeak` 分開** —— 核心的型別可能會演進，
 * 存檔格式不該跟著動，否則舊記錄會讀不出來。
 */
data class StoredPeak(
    val frequencyHz: Double,
    val amplitudePercent: Double,
    val orderOfRotation: Double,
    val isHarmonic: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("hz", frequencyHz); put("amp", amplitudePercent)
        put("ord", orderOfRotation); put("harm", isHarmonic)
    }

    companion object {
        fun from(p: SpectralPeak) =
            StoredPeak(p.frequencyHz, p.amplitudePercent, p.orderOfRotation, p.isRotationHarmonic)

        fun fromJson(o: JSONObject) = StoredPeak(
            o.getDouble("hz"), o.getDouble("amp"), o.getDouble("ord"), o.getBoolean("harm"),
        )
    }
}
