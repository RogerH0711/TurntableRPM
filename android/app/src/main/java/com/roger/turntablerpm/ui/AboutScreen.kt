package com.roger.turntablerpm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val Amber = Color(0xFFCC6600)

/**
 * 說明頁。
 *
 * **這一頁的重點是誠實交代限制，不是介紹功能。** 這個 app 的可信度建立在
 * 「它看不到什麼」講得夠清楚 —— 量的是盤不是唱片、未校準的偏差不能拿來調唱盤、
 * 報出來的偏心有一大半是手機造成的。這些不寫出來，數字再漂亮也沒有意義。
 */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("說明", style = MaterialTheme.typography.headlineSmall)

        Section("這個方法看不到什麼") {
            Para(
                "手機是跟著盤面一起轉的，量到的是「盤」的轉速。唱片中心孔沒對準所造成的" +
                    "音高起伏，這個方法完全偵測不到 —— 而那在實務上經常是你聽到的抖動裡" +
                    "最大的一項。",
            )
            Para(
                "換句話說：這裡讀到很漂亮的數字，不保證放起來就不抖。" +
                    "它能告訴你的是「唱盤本身好不好」，不是「這張唱片放起來好不好」。",
                emphasis = true,
            )
        }

        Section("關於校準") {
            Para(
                "陀螺儀可能有固定比例的讀數誤差，它是乘性的、量再久也平均不掉，" +
                    "只能靠外部參考校掉。這個 app 用碼錶：盤面貼個記號，數 100 圈計時，" +
                    "算出真實轉速再跟 app 的讀數比。",
            )
            Para(
                "沒有校準之前，「偏差 %」不能拿來調唱盤 —— 你看到的偏差是「唱盤誤差」" +
                    "和「陀螺儀誤差」相乘的結果，分不開。",
                emphasis = true,
            )
            Para(
                "但抖晃率與所有百分比都不受影響：那些是比值，陀螺儀的比例因子誤差" +
                    "會完全抵消。所以診斷功能不必校準就能用，校準只影響「我的盤轉速對不對」。",
            )
            Para("校準結果綁定在這一台裝置上（不同手機的陀螺儀不一樣），換手機要重做。")
        }

        Section("報出來的偏心有一大半是手機造成的") {
            Para(
                "手機放在盤上就是一個偏心質量，它自己會產生每圈一次的擾動。" +
                    "實測這台開發用的唱盤：報出來的 1× 有一半以上來自手機而不是唱盤。",
            )
            Para(
                "想知道唱盤自己有多少：量一次、把手機原地轉 180°、再量一次。" +
                    "手機的貢獻會反相、唱盤的不會，兩次的差就是手機的份。",
                emphasis = true,
            )
            Para(
                "同理，如果「每圈一次」很大，下面那根「每圈兩次」多半只是它的諧波，" +
                    "不是盤面橢圓 —— 先把偏心降下來再看。",
            )
        }

        Section("怎麼量得準") {
            Para(
                "・手機置中（見擺法圖）—— 這是影響最大的一項\n" +
                    "・唱盤放水平，手機也放水平\n" +
                    "・至少量 90 秒；頻譜的解析度是 1 ÷ 量測時長\n" +
                    "・想要準確的譜峰振幅就量 3 分鐘 —— 1 分鐘的量測會低估約 8%，\n" +
                    "　因為解析度不夠細，峰值落在兩個頻率格之間\n" +
                    "・校準時碼錶跟 app 要同步 —— 同一段轉動才算得準\n" +
                    "・量測中不要碰唱盤或桌子",
            )
        }

        Section("手機怎麼擺") { PlacementGuide() }

        Section("安全") {
            Para("拿掉磁吸配件與含磁鐵的手機殼。磁鐵靠近 MC 唱頭可能造成永久損傷。", emphasis = true)
            Para("把唱臂鎖在臂座上，不要讓唱頭懸在盤面上方。手機在盤上時碰到唱針，壞的是唱針。")
            Para(
                "絨布墊、不織布墊、橡膠墊都可以，不需要另外放一張唱片。重點是不要讓手機" +
                    "直接壓在裸露的盤面上 —— 兩邊都會刮。放之前確認手機背面和墊子上沒有沙粒。",
            )
            Para("78 轉時放靠近中心：偏心擺放的離心力在 78 轉時是 33 轉的 5.5 倍。")
        }

        Section("怎麼量的") {
            Para(
                "手機放在轉動的盤面上，用陀螺儀量自轉角速度。三軸角速度會投影到重力方向，" +
                    "所以手機擺得歪一點也不影響讀數 —— 傾斜 5° 若只讀單軸就會低估 0.38%，" +
                    "已經超過目標精度。",
            )
            Para(
                "Android 的取樣率設定只是建議值，實際頻率由廠商實作決定。" +
                    "所以時間戳一律用感測器自己的時鐘，不假設每筆間隔相等，" +
                    "分析前也會重採樣到等間隔。實際拿到多少可以在「取樣特性診斷」看到。",
            )
        }

        Section("隱私") {
            Para("量測資料只留在這台裝置上，不會上傳到任何地方。")
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("回到量測")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Para(text: String, emphasis: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (emphasis) FontWeight.Medium else FontWeight.Normal,
        color = if (emphasis) Amber else Color.Unspecified,
    )
}
