package com.roger.turntablerpm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.profile.TurntableProfile

private val Green = Color(0xFF2E7D32)

/**
 * 唱盤設定檔。
 *
 * **這個畫面存在的理由是把「有一根 18.85 Hz 的峰」變成「那是你的馬達」。**
 * 光有頻率與倍數，使用者只知道「有東西在 35.3 倍的地方」；填了傳動鏈尺寸之後，
 * app 才講得出那根峰對應哪個實體零件。原廠規格同理 —— 0.09% 是好是壞，
 * 要看手冊寫幾。
 *
 * 清單與編輯做在同一頁：一般人只有一台唱盤，為了那一台再切一層導覽不划算。
 */
@Composable
fun ProfileScreen(
    profiles: List<TurntableProfile>,
    onAdd: () -> Unit,
    onUpdate: (TurntableProfile) -> Unit,
    onDelete: (Long) -> Unit,
    onOpenLoadTest: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("唱盤", style = MaterialTheme.typography.headlineSmall)

        if (profiles.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("還沒有唱盤設定檔", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "記下原廠規格與傳動鏈尺寸之後，量測結果就能自動跟規格比對，" +
                            "頻譜上的非諧波峰也能對上實體零件。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                        Text("新增唱盤")
                    }
                }
            }
        } else {
            for (p in profiles) {
                ProfileCard(p, onUpdate, onOpenLoadTest) { onDelete(p.id) }
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Text("再新增一台")
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("回到量測")
        }
    }
}

@Composable
private fun ProfileCard(
    profile: TurntableProfile,
    onUpdate: (TurntableProfile) -> Unit,
    onOpenLoadTest: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember(profile.id) { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (profile.isActive) {
                    Text("使用中", style = MaterialTheme.typography.bodySmall, color = Green)
                }
                Switch(
                    checked = profile.isActive,
                    // 取消勾選不做事：至少要有一台使用中，否則規格比對就消失了，
                    // 而使用者不會知道為什麼。要換就去勾另一台。
                    onCheckedChange = { on -> if (on) onUpdate(profile.copy(isActive = true)) },
                )
            }

            OutlinedTextField(
                value = profile.name,
                onValueChange = { onUpdate(profile.copy(name = it)) },
                label = { Text("型號") },
                placeholder = { Text("例如 TD 235 EV") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = profile.maker,
                onValueChange = { onUpdate(profile.copy(maker = it)) },
                label = { Text("廠牌") },
                placeholder = { Text("例如 Thorens") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            Text("原廠規格", style = MaterialTheme.typography.titleSmall)
            NumberField(
                label = "抖晃率規格",
                unit = "%",
                value = profile.specWowFlutterPercent,
                onChange = { onUpdate(profile.copy(specWowFlutterPercent = it)) },
            )
            Text(
                "填了之後，分析頁會直接標出你的盤超規格多少。手冊上通常寫成 WRMS 或 DIN。",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()
            Text("傳動鏈尺寸（選填）", style = MaterialTheme.typography.titleSmall)
            NumberField(
                label = "馬達皮帶輪直徑",
                unit = "mm",
                value = profile.pulleyDiameterMM,
                onChange = { onUpdate(profile.copy(pulleyDiameterMM = it)) },
            )
            NumberField(
                label = "皮帶接觸的盤面直徑",
                unit = "mm",
                value = profile.platterDiameterMM,
                onChange = { onUpdate(profile.copy(platterDiameterMM = it)) },
            )
            NumberField(
                label = "皮帶厚度",
                unit = "mm",
                value = profile.beltThicknessMM,
                onChange = { onUpdate(profile.copy(beltThicknessMM = it)) },
            )
            profile.expectedDriveRatio?.let {
                StatRow("預期傳動比", "%.2f ×".format(it))
            }
            Text(
                "量了這幾個，頻譜上「非諧波的某某倍」就能對上實體零件 —— " +
                    "剛好等於傳動比的那根峰就是馬達。皮帶跑在外盤緣就量外盤，" +
                    "跑在內盤就量內盤。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "皮帶厚度對比值影響很大：皮帶輪 8.5 mm 配厚度 0.5 mm，" +
                    "光是厚度就讓比值差 5.6%。量不準沒關係 —— app 會把預期與量到的" +
                    "兩個數字並排講出來，不做「符合／不符合」的判定。",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()
            Text("載重", style = MaterialTheme.typography.titleSmall)
            if (profile.hasLoadTest) {
                StatRow("載重斜率", "%.5f RPM/g".format(profile.loadSlopeRPMPerGram ?: 0.0))
                StatRow("手機造成的變化", "%+.4f RPM".format(profile.loadPhoneEffectRPM ?: 0.0))
                Text(
                    if (profile.loadIsSignificant) "手機的重量確實會影響讀數。"
                    else "這台盤對載重不敏感，手機的重量不影響讀數。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "手機的重量會不會把唱盤拖慢，完全取決於馬達型式，查表沒有用，要實測。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onOpenLoadTest, modifier = Modifier.fillMaxWidth()) {
                Text(if (profile.hasLoadTest) "重做載重測試" else "做載重測試")
            }

            HorizontalDivider()
            OutlinedTextField(
                value = profile.note,
                onValueChange = { onUpdate(profile.copy(note = it)) },
                label = { Text("備註") },
                placeholder = { Text("例如：2026 換過皮帶、速度微調在底板右側") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            if (confirmDelete) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDelete) { Text("確定刪除") }
                    TextButton(onClick = { confirmDelete = false }) { Text("取消") }
                }
            } else {
                TextButton(onClick = { confirmDelete = true }) { Text("刪除這台唱盤") }
            }
        }
    }
}

/**
 * 可以留白的數值欄位。**留白代表「還沒量」，跟「量到 0」是不同的意思** ——
 * 所以空字串轉成 null，不轉成 0。
 *
 * 輸入中的文字獨立存著，不是每打一個字就轉成 Double 再轉回字串：
 * 那樣「0.」會在打字途中被吃掉，小數點根本輸入不進去。
 */
@Composable
private fun NumberField(
    label: String,
    unit: String,
    value: Double?,
    onChange: (Double?) -> Unit,
) {
    var text by remember(value == null) { mutableStateOf(value?.let { trimNumber(it) } ?: "") }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(if (it.isBlank()) null else it.toDoubleOrNull())
            },
            label = { Text(label) },
            suffix = { Text(unit) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        if (text.isNotEmpty()) {
            TextButton(onClick = { text = ""; onChange(null) }, modifier = Modifier.width(64.dp)) {
                Text("清除", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 8.5 顯示成 "8.5"、8.0 顯示成 "8"，不要一律補到小數三位。 */
private fun trimNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
