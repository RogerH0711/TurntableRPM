package com.roger.turntablerpm.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadCompensatorTest {

    @Test
    fun `兩點外插符合黃金值`() {
        val slope = Golden.number("load", "expected_slope")
        val rpm0 = Golden.number("load", "expected_rpm0")
        val r = LoadCompensator.extrapolate(
            rpmWithPhone = Golden.number("load", "rpm1"),
            rpmWithAddedMass = Golden.number("load", "rpm2"),
            addedMassGrams = Golden.number("load", "delta_m_g"),
            phoneMassGrams = Golden.number("load", "phone_m_g"),
        )!!
        assertTrue(abs(r.slopeRPMPerGram - slope) < 1e-9, "斜率：期望 $slope，實得 ${r.slopeRPMPerGram}")
        assertTrue(abs(r.zeroLoadRPM - rpm0) < 1e-9, "零載：期望 $rpm0，實得 ${r.zeroLoadRPM}")
        assertTrue(r.isSignificant)
    }

    /** 斜率在雜訊以內就該說「這台盤對載重不敏感」，而不是硬套一個補償。 */
    @Test
    fun `斜率在雜訊內時不顯著`() {
        val r = LoadCompensator.extrapolate(33.30, 33.3001, 100.0, 170.0)!!
        assertTrue(!r.isSignificant, "手機影響 ${r.phoneEffectRPM} RPM 應視為雜訊")
    }

    @Test
    fun `無效輸入回 null`() {
        assertNull(LoadCompensator.extrapolate(33.3, 33.24, 0.0, 170.0))
        assertNull(LoadCompensator.extrapolate(33.3, 33.24, 100.0, 0.0))
    }
}
