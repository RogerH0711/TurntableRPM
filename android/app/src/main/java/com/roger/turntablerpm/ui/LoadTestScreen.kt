package com.roger.turntablerpm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.core.LoadCompensationResult
import com.roger.turntablerpm.core.LoadCompensator
import com.roger.turntablerpm.history.MeasurementRecord
import com.roger.turntablerpm.profile.TurntableProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val Orange = Color(0xFFCC6600)

/**
 * 載重測試：手機自己的重量會不會把唱盤拖慢。
 *
 * **不做成特殊的量測模式。** 使用者只要正常量兩次（一次只放手機、一次加配重），
 * 然後在這裡挑那兩筆記錄 —— 少一套流程要學，也不必擔心中途操作錯誤。
 *
 * 為什麼要實測而不是查表：手機是幾克本身不說明任何事，影響量完全取決於馬達型式。
 * 同步交流馬達幾乎為零，無調速直流馬達最大。
 *
 * **這個方法有一個容易違反的前提：兩次量測的「平衡狀態」必須一樣，只有質量在變。**
 * 開發時踩過 —— 把配重疊在偏心擺放的手機上，改變的其實是不平衡而不是載重，
 * 量到的斜率是錯的（CLAUDE.md 坑 28）。手機置中擺在唱片鎮上時直接疊上去才成立。
 */
@Composable
fun LoadTestScreen(
    profile: TurntableProfile?,
    records: List<MeasurementRecord>,
    phoneMassGrams: Double,
    onPhoneMassChange: (Double) -> Unit,
    onSave: (LoadCompensationResult) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var baseId by remember { mutableStateOf<Long?>(null) }
    var loadedId by remember { mutableStateOf<Long?>(null) }
    var addedText by remember { mutableStateOf("100") }

    val base = records.firstOrNull { it.epochMillis == baseId }
    val loaded = records.firstOrNull { it.epochMillis == loadedId }
    val added = addedText.toDoubleOrNull() ?: 0.0

    val result = if (base != null && loaded != null && base !== loaded && added > 0) {
        LoadCompensator.extrapolate(
            rpmWithPhone = base.meanRPM,
            rpmWithAddedMass = loaded.meanRPM,
            addedMassGrams = added,
            phoneMassGrams = phoneMassGrams,
        )
    } else null

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("載重測試", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("怎麼做", style = MaterialTheme.typography.titleSmall)
                Text(
                    "正常量兩次：一次只放手機，一次在手機上再疊一個已知重量的東西。" +
                        "然後在下面挑出那兩筆。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "兩次的平衡狀態必須一樣，只有質量在變 —— 所以手機要置中放在唱片鎮上，" +
                        "配重疊在手機正上方。手機偏在一邊時加配重，改變的是不平衡而不是載重，" +
                        "量到的斜率會是錯的。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Orange,
                )
            }
        }

        if (records.size < 2) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "至少要有兩筆量測記錄才能做這個測試。先照上面的方法量兩次。",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("挑出兩次量測", style = MaterialTheme.typography.titleSmall)
                    RecordPicker("只放手機", records, base) { baseId = it.epochMillis }
                    RecordPicker("加了配重", records, loaded) { loadedId = it.epochMillis }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("重量", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = if (phoneMassGrams > 0) trimGrams(phoneMassGrams) else "",
                        onValueChange = { onPhoneMassChange(it.toDoubleOrNull() ?: 0.0) },
                        label = { Text("手機重量") },
                        suffix = { Text("g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = addedText,
                        onValueChange = { addedText = it },
                        label = { Text("加上去的配重") },
                        suffix = { Text("g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "配重用食譜秤量一下就好，不必很精確 —— 誤差只會等比例反映在斜率上。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (result != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("結果", style = MaterialTheme.typography.titleMedium)
                        StatRow("載重斜率", "%.5f RPM/g".format(result.slopeRPMPerGram))
                        StatRow("手機造成的變化", "%+.4f RPM".format(result.phoneEffectRPM))
                        StatRow("外插回零負載", "%.4f RPM".format(result.zeroLoadRPM))
                        Text(
                            verdict(result),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.isSignificant) Orange else Color.Unspecified,
                        )
                        if (profile != null) {
                            Button(
                                onClick = { onSave(result) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("存進「${profile.displayName}」")
                            }
                        } else {
                            Text(
                                "還沒有唱盤設定檔，結果沒地方存。先去「唱盤設定檔」新增一台。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Orange,
                            )
                        }
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "挑出兩筆不同的量測，並填入配重的重量。",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (profile?.hasLoadTest == true) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("已存的結果", style = MaterialTheme.typography.titleSmall)
                    StatRow("斜率", "%.5f RPM/g".format(profile.loadSlopeRPMPerGram ?: 0.0))
                    StatRow("手機造成的變化", "%+.4f RPM".format(profile.loadPhoneEffectRPM ?: 0.0))
                    profile.loadMeasuredAtMillis?.let {
                        StatRow(
                            "測於",
                            SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(it)),
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        if (profile.loadIsSignificant) "手機的重量確實會影響讀數。"
                        else "這台盤對載重不敏感，手機的重量不影響讀數。",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (profile.loadIsSignificant) Orange else Color.Unspecified,
                    )
                }
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("回到唱盤設定檔")
        }
    }
}

@Composable
private fun RecordPicker(
    label: String,
    records: List<MeasurementRecord>,
    selected: MeasurementRecord?,
    onSelect: (MeasurementRecord) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("M/d HH:mm", Locale.getDefault()) }

    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label)
                Text(
                    selected?.let { "%.4f RPM".format(it.meanRPM) } ?: "尚未選擇",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (r in records) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "%.4f RPM · %s".format(r.meanRPM, format.format(Date(r.epochMillis))),
                        )
                    },
                    onClick = { onSelect(r); open = false },
                )
            }
        }
    }
}

/** 斜率有沒有超過量測雜訊，決定要不要當一回事。 */
private fun verdict(r: LoadCompensationResult): String {
    if (!r.isSignificant) {
        return "斜率在量測雜訊以內 —— 這台唱盤對載重不敏感，手機的重量不影響讀數。"
    }
    val pct = abs(r.phoneEffectRPM / max(r.zeroLoadRPM, 0.001)) * 100
    val dir = if (r.phoneEffectRPM < 0) "拖慢" else "加快"
    return ("手機的重量把轉速%s了 %.4f RPM（%.3f%%）。這是量測方法本身造成的偏差，" +
        "不是唱盤的問題 —— 真實的無載轉速是 %.4f RPM。")
        .format(dir, abs(r.phoneEffectRPM), pct, r.zeroLoadRPM)
}

private fun trimGrams(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
