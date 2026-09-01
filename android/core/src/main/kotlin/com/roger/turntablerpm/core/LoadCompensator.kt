package com.roger.turntablerpm.core

import kotlin.math.abs

data class LoadCompensationResult(
    val slopeRPMPerGram: Double,
    val zeroLoadRPM: Double,
    /** 手機造成的轉速變化量（負值代表拖慢）。 */
    val phoneEffectRPM: Double,
    /** 斜率是否超出量測雜訊。false 就是「你的唱盤對載重不敏感，不需要補償」。 */
    val isSignificant: Boolean,
)

/**
 * 規格 §3.8：兩點外插。
 *
 * 手機是幾克本身沒有告訴我們任何事 —— 影響量完全取決於馬達型式
 * （同步交流馬達幾乎為零，無調速直流馬達最大）。所以實測斜率再外插回零負載。
 *
 * **重要限制（iOS 端實測發現的）**：這個方法假設「只有質量在變」。
 * 若把配重疊在手機上，改變的其實是**不平衡**而不是載重，量到的斜率是錯的。
 * 兩次量測都必須維持相同的平衡狀態。
 */
object LoadCompensator {
    fun extrapolate(
        rpmWithPhone: Double,
        rpmWithAddedMass: Double,
        addedMassGrams: Double,
        phoneMassGrams: Double,
        noiseRPM: Double = 0.005,
    ): LoadCompensationResult? {
        if (addedMassGrams <= 0 || phoneMassGrams <= 0) return null
        val slope = (rpmWithAddedMass - rpmWithPhone) / addedMassGrams
        val effect = slope * phoneMassGrams
        return LoadCompensationResult(
            slopeRPMPerGram = slope,
            zeroLoadRPM = rpmWithPhone - effect,
            phoneEffectRPM = effect,
            isSignificant = abs(effect) > 2.0 * noiseRPM,
        )
    }
}
