package com.roger.turntablerpm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.R

private val Amber = Color(0xFFCC6600)

/**
 * 第一次開啟時的導覽。
 *
 * **刻意寫得很短。** 導覽要做的是讓人能開始用，不是把原理講完 ——
 * 每頁塞五六條說明沒有人會看。深入的內容放在說明頁，想知道的人自己會去看。
 *
 * 唯一不能省的是擺法那一頁：那不只是說明，它讓量測畫面的文字方向
 * 從「隨機」變成「固定正對使用者」（見 SpinningDialScreen）。
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    var page by remember { mutableIntStateOf(0) }
    val last = 3

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 40.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (page) {
                0 -> {
                    Title(stringResource(R.string.onb_title_measure_speed))
                    Body(stringResource(R.string.onb_body_put_phone))
                    Muted(stringResource(R.string.onb_no_strobe_needed))
                }
                1 -> {
                    Title(stringResource(R.string.onb_title_place_phone))
                    PlacementGuide()
                }
                2 -> {
                    Title(stringResource(R.string.onb_title_how_to_measure))
                    Step(1, stringResource(R.string.onb_step_platter_stopped))
                    Step(2, stringResource(R.string.onb_step_tap_ready))
                    Step(3, stringResource(R.string.onb_step_start_platter))
                    Muted(stringResource(R.string.onb_measure_90s))
                }
                else -> {
                    Title(stringResource(R.string.onb_title_two_things))
                    Body(stringResource(R.string.onb_measures_platter), emphasis = true)
                    Muted(stringResource(R.string.onb_offcenter_hole_invisible))
                    Body(stringResource(R.string.onb_error_needs_calibration), emphasis = true)
                    Muted(stringResource(R.string.onb_error_explained))
                    Muted(stringResource(R.string.onb_details_in_about))
                }
            }
        }

        // 底部固定列。**背景要不透明** —— 上面的內容會捲到這裡來。
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = 14.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(last + 1) { i ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(
                                if (i == page) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                CircleShape,
                            ),
                    )
                }
            }
            Button(
                onClick = { if (page == last) onFinish() else page++ },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(
                    if (page == last) stringResource(R.string.onb_get_started)
                    else stringResource(R.string.onb_next),
                )
            }
        }
    }
}

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

@Composable
private fun Body(text: String, emphasis: Boolean = false) = Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    fontWeight = if (emphasis) FontWeight.Medium else FontWeight.Normal,
    color = if (emphasis) Amber else Color.Unspecified,
)

@Composable
private fun Muted(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
