package com.roger.turntablerpm.core

/** 標稱轉速。換算關係：RPM × 6 = °/s。 */
data class TurntableSpeed(val rpm: Double, val label: String) {

    val degreesPerSecond: Double get() = rpm * 6.0
    val secondsPerRevolution: Double get() = 60.0 / rpm

    companion object {
        val RPM16 = TurntableSpeed(50.0 / 3.0, "16⅔")
        val RPM33 = TurntableSpeed(100.0 / 3.0, "33⅓")
        val RPM45 = TurntableSpeed(45.0, "45")
        val RPM78 = TurntableSpeed(78.0, "78")
        val STANDARD = listOf(RPM16, RPM33, RPM45, RPM78)
    }
}
