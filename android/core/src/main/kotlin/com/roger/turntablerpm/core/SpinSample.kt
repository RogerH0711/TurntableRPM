package com.roger.turntablerpm.core

/**
 * 一筆已處理過的樣本。
 *
 * @param t     感測器自己的時間戳，秒。**務必用真實時間戳，不要假設等間隔** ——
 *              Android 的取樣率設定只是建議值，實際間隔由廠商實作決定。
 * @param omega 已投影到自轉軸的角速度，°/s。
 * @param yaw   磁北參考的偏航角，弧度；裝置不支援時為 null。
 */
data class SpinSample(val t: Double, val omega: Double, val yaw: Double? = null)
