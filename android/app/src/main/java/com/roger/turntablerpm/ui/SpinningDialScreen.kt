package com.roger.turntablerpm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roger.turntablerpm.R
import com.roger.turntablerpm.sensor.EngineState
import com.roger.turntablerpm.sensor.Phase
import kotlin.math.abs

private val Amber = Color(0xFFFFB300)
private val Green = Color(0xFF66BB6A)

/**
 * 量測中的反旋轉盤面。規格 §6.2。
 *
 * 手機在轉盤上轉的時候，把畫面**反向旋轉同樣的角度** —— 對站著不動的人來說，
 * 內容看起來就是靜止的，不必把手機拿起來就能讀。
 *
 * **三個必須處理的約束（iOS 端都踩過）：**
 *
 * 1. **介面方向要鎖死。** 手機平躺旋轉時系統的自動轉向會胡亂觸發 ——
 *    AndroidManifest 已鎖 `portrait`。
 * 2. **內容要放得進內接圓。** 旋轉中的矩形內容會被螢幕邊緣裁掉，所以所有會轉的
 *    元件都必須落在直徑 = min(寬, 高) 的圓內。這是硬版面約束，加的資訊愈多字要愈小。
 * 3. **可觸控區另外處理。** 按鈕跟著畫面轉很難按 —— 所以**整個上半部都是停止鍵**，
 *    轉向鍵固定在下緣、不跟著內容轉。
 *
 * 角度用 `engine.displayAngleDegrees()`，它會用最新一筆的時間戳外推，
 * 補掉感測器（約 108 Hz）與畫面（60/120 Hz）之間的落後。直接讀累積值會讓畫面抖。
 */
@Composable
fun SpinningDialScreen(
    state: EngineState,
    angleProvider: () -> Double,
    rotationOffset: Double,
    onRotate: (Double) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var angle by remember { mutableFloatStateOf(0f) }
    // TimelineView(.animation) 的等價寫法：每一幀重新取一次角度。
    LaunchedEffect(state.running) {
        while (state.running) {
            withFrameMillis { angle = angleProvider().toFloat() }
        }
    }

    val frozen = !state.running

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 內接圓的直徑 —— 會轉的內容全部要塞進這個正方形裡。
        val side = minOf(maxWidth, maxHeight)

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(side)
                    // 反向旋轉：手機順時針轉，內容就逆時針轉同樣的角度。
                    // 凍結時轉回正 —— 使用者已經把手機拿起來了。
                    .rotate(if (frozen) 0f else (rotationOffset - angle).toFloat()),
                contentAlignment = Alignment.Center,
            ) {
                DialContent(state)
            }
        }

        Column(Modifier.fillMaxSize()) {
            // 上半部：點一下停止。刻意做成很大的區域 —— 手機在轉，小按鈕根本按不到。
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { if (!frozen) onStop() },
            )
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                if (frozen) {
                    FrozenControls(onDismiss)
                } else {
                    TurnControls(onRotate)
                }
            }
        }
    }
}

@Composable
private fun DialContent(state: EngineState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(horizontal = 24.dp),
    ) {
        Text(
            if (state.instantRPM > 0) "%.2f".format(state.instantRPM) else "—",
            fontSize = 76.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            maxLines = 1,
        )
        Text("RPM", color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp)

        val nominal = state.nominal
        val error = state.errorPercent
        if (nominal != null && error != null) {
            Text(
                stringResource(R.string.dial_nominal_rpm, nominal.label),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "%+.2f%%".format(error),
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = if (abs(error) <= 0.3) Green else Amber,
            )
        } else {
            Text(
                if (state.phase == Phase.WAITING_FOR_STABILITY) {
                    stringResource(R.string.dial_waiting_for_speed)
                } else {
                    stringResource(R.string.dial_not_steady)
                },
                color = Amber,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (state.appliedFactor == null) {
            Text(
                stringResource(R.string.dial_uncalibrated),
                color = Amber.copy(alpha = 0.8f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            stringResource(
                R.string.dial_elapsed_and_revs,
                state.elapsedSeconds.toInt() / 60, state.elapsedSeconds.toInt() % 60,
                state.revolutions,
            ),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * 轉向控制。**固定在螢幕下緣、不跟著內容轉** —— 手機在轉的時候，
 * 只有位置固定又夠大的東西按得到。
 *
 * 正常情況下不需要它：規定的擺法加上正確的角度零點，文字就會正對使用者。
 * 但 app 無從知道使用者站在轉盤的哪一側，所以留一個備援。
 */
@Composable
private fun TurnControls(onRotate: (Double) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 24.dp),
    ) {
        Text(
            stringResource(R.string.dial_tap_top_to_stop),
            color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onRotate(-15.0) }) {
                Text("⟲", color = Color.White.copy(alpha = 0.75f), fontSize = 30.sp)
            }
            Text(
                stringResource(R.string.dial_rotate),
                color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp,
            )
            TextButton(onClick = { onRotate(15.0) }) {
                Text("⟳", color = Color.White.copy(alpha = 0.75f), fontSize = 30.sp)
            }
        }
    }
}

@Composable
private fun FrozenControls(onDismiss: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 24.dp),
    ) {
        Text(
            stringResource(R.string.dial_done_pick_up_phone),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.dial_see_analysis), color = Color.White, fontSize = 20.sp)
        }
    }
}
