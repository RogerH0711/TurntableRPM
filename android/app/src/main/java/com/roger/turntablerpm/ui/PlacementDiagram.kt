package com.roger.turntablerpm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val Amber = Color(0xFFCC6600)

/**
 * 手機該擺在轉盤哪裡的示意圖。
 *
 * **這張圖解決兩個問題。**
 *
 * 一是**方向**：反旋轉把內容鎖在「按下開始那一刻手機的物理方向」。規定一個固定
 * 擺法，那個方向就是已知的 —— 手機下緣朝著使用者，內容就會正著顯示。
 *
 * 二是**平衡**：手機偏在一邊會在軸承產生側向負載，拖慢轉速也放大每圈一次的抖動。
 * 實測轉速慢 0.3%、偏心大三成。放在唱片鎮上置中是最省事的解法。
 */
@Composable
fun PlacementDiagram(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = minOf(w, h * 0.78f) / 2f * 0.92f
        val cx = w / 2f
        val cy = h * 0.42f

        // 轉盤
        drawCircle(Color(0xFF383838), radius = r, center = Offset(cx, cy))
        drawCircle(Color(0xFF737373), radius = r, center = Offset(cx, cy),
            style = Stroke(width = 1.5f))

        // 唱片鎮：手機底下那個圓盤
        val wr = r * 0.42f
        drawCircle(Color(0xFF8C8C8C), radius = wr, center = Offset(cx, cy))
        drawCircle(Color(0xFFB3B3B3), radius = wr, center = Offset(cx, cy),
            style = Stroke(width = 1.5f))

        // 手機：橫跨轉軸，質心落在轉軸正上方
        val pw = r * 0.62f
        val ph = r * 1.28f
        drawRoundRect(
            color = Color(0xFFEAEAEA),
            topLeft = Offset(cx - pw / 2f, cy - ph / 2f),
            size = Size(pw, ph),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(pw * 0.16f),
        )
        drawRoundRect(
            color = Color(0xFF292929),
            topLeft = Offset(cx - pw / 2f + pw * 0.1f, cy - ph / 2f + ph * 0.05f),
            size = Size(pw * 0.8f, ph * 0.9f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(pw * 0.1f),
        )
        // 聽筒缺口畫在上緣，指出「上」在哪一邊
        drawRoundRect(
            color = Color(0xFF8C8C8C),
            topLeft = Offset(cx - pw * 0.16f, cy - ph / 2f + ph * 0.03f),
            size = Size(pw * 0.32f, ph * 0.018f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
        )

        // 主軸畫在最後，壓在手機之上
        drawCircle(Amber, radius = 5f, center = Offset(cx, cy))

        // 使用者的方向
        drawLine(Amber, Offset(cx, cy + r + 10f), Offset(cx, cy + r + 30f), strokeWidth = 2f)
    }
}

/** 圖 + 說明。導覽與說明頁共用。 */
@Composable
fun PlacementGuide(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PlacementDiagram(Modifier.fillMaxWidth().height(200.dp))
        // 指示線畫在圖的正下方置中，標籤也要置中，否則兩者對不上。
        Text(
            "↑ 這一側朝著你",
            style = MaterialTheme.typography.bodySmall,
            color = Amber,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            bullet("手機橫跨轉軸放在唱片鎮上，讓質心落在轉軸正上方")
            bullet("手機下緣朝著你（跟平放在桌上看一樣）")
            bullet("螢幕朝上，唱片鎮放在轉盤原本的絨布墊上")
        }

        Text(
            "手機偏在一邊會拖慢轉速並放大抖動 —— 實測轉速慢 0.3%、每圈一次的抖動大三成。" +
                "置中最省事：不必找等重的東西，手機也躺得更平。",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "沒有唱片鎮也可以：手機右側長邊的中點貼著轉軸、機身放在左半邊，" +
                "然後在對面放一個跟手機等重的東西（小馬克杯加水就很好用），距離轉軸差不多遠。",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "照這個擺法，量測畫面的文字也會正對著你。",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("・", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
