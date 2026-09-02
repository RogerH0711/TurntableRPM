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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
                    Title("量唱盤的轉速")
                    Body("把手機放在轉動的唱盤上，就能量出轉速、偏差和抖晃率。")
                    Muted("不需要頻閃盤或其他硬體。")
                }
                1 -> {
                    Title("手機這樣擺")
                    PlacementGuide()
                }
                2 -> {
                    Title("怎麼量")
                    Step(1, "轉盤停著，把手機照上一頁的方式放好")
                    Step(2, "按下「準備好，開始偵測」")
                    Step(3, "啟動轉盤 —— 轉速穩了會自動開始，停下時自動結束")
                    Muted("量 90 秒以上頻譜才夠清楚；想要準確的振幅就量 3 分鐘。")
                }
                else -> {
                    Title("兩件要先知道的事")
                    Body("它量的是盤，不是唱片。", emphasis = true)
                    Muted("唱片中心孔偏心造成的抖動，這個方法看不到。")
                    Body("校準之前，偏差 % 不能拿來調唱盤。", emphasis = true)
                    Muted(
                        "那是唱盤誤差和陀螺儀誤差相乘的結果。校準用碼錶做，一支手機做一次就好。" +
                            "抖晃率與其他百分比不受影響，那些不必校準就能用。",
                    )
                    Muted("詳細說明在主畫面的「說明」裡。")
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
                Text(if (page == last) "開始使用" else "下一步")
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
