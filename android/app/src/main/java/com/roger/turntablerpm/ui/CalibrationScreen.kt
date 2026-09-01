package com.roger.turntablerpm.ui

import androidx.compose.foundation.layout.Arrangement
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
import com.roger.turntablerpm.calibration.CalibrationStore
import com.roger.turntablerpm.core.StopwatchCalibration

private val Orange = Color(0xFFCC6600)

/**
 * 碼錶校準。
 *
 * **為什麼是碼錶而不是自動校準。** iOS 端把指南針自動校準走過兩條路都失敗了：
 * `attitude.yaw` 是融合結果，拿它校準陀螺儀是同義反覆；原始磁力計則被每圈一次的
 * 空間磁場失真蓋掉（失真振幅 29.9 µT 大過訊號振幅 26.0 µT）。碼錶反而乾淨：
 * 100 圈搭配 ±0.3 秒的人為誤差就是 0.17%，而且完全不經過手機的任何感測器。
 */
@Composable
fun CalibrationScreen(
    measuredRPM: Double?,
    current: StopwatchCalibration?,
    mismatched: StopwatchCalibration?,
    onSave: (StopwatchCalibration) -> Boolean,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var revolutions by remember { mutableStateOf("100") }
    var seconds by remember { mutableStateOf("") }

    val rev = revolutions.toIntOrNull()
    val secs = seconds.toDoubleOrNull()
    val candidate = if (rev != null && secs != null && measuredRPM != null) {
        StopwatchCalibration.create(rev, secs, measuredRPM, CalibrationStore.deviceModel)
    } else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("碼錶校準", style = MaterialTheme.typography.headlineSmall)

        mismatched?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("先前的校準已失效", style = MaterialTheme.typography.titleSmall, color = Orange)
                    Text(
                        "那次校準是在 ${it.deviceModel} 上做的，這台是 ${CalibrationStore.deviceModel} —— " +
                            "校準倍率綁定在特定一支陀螺儀上，不能沿用。請重新校準。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        current?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("目前生效的校準", style = MaterialTheme.typography.titleSmall)
                    StatRow("倍率 k", "%.5f".format(it.factor))
                    StatRow("陀螺儀偏差", "%+.3f %%".format((1.0 / it.factor - 1.0) * 100))
                    StatRow("依據", "${it.revolutions} 圈 / %.2f s".format(it.seconds))
                    StatRow("校準精度", "±%.3f %%".format(it.precision() * 100))
                    OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                        Text("清除校準")
                    }
                }
            }
        }

        if (measuredRPM == null) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "要先完成一次量測，才有可以拿來比對的轉速。回上一頁量一次再回來。",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("這次校準", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = revolutions,
                            onValueChange = { revolutions = it.filter(Char::isDigit) },
                            label = { Text("圈數") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = seconds,
                            onValueChange = { seconds = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("秒數") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    StatRow("App 量到的轉速", "%.4f RPM".format(measuredRPM))
                    if (candidate == null) {
                        Text("填入圈數與秒數就會算出倍率。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        StatRow("碼錶推算轉速", "%.4f RPM".format(candidate.trueRPM))
                        StatRow("倍率 k", "%.5f".format(candidate.factor))
                        StatRow("陀螺儀偏差", "%+.3f %%".format((1.0 / candidate.factor - 1.0) * 100))
                        StatRow("這次校準的精度", "±%.3f %%".format(candidate.precision() * 100))

                        if (!candidate.isPlausible) {
                            Text(
                                "k = %.3f 不合理。MEMS 陀螺儀的比例因子誤差是百分之幾的等級，".format(candidate.factor) +
                                    "不會到這種程度 —— 檢查圈數或秒數是不是打錯了。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Orange,
                            )
                        }
                        Button(
                            onClick = { onSave(candidate) },
                            enabled = candidate.isPlausible,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("儲存") }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("怎麼量", style = MaterialTheme.typography.titleSmall)
                Text(
                    "1. 盤面邊緣貼一個看得見的記號\n" +
                        "2. 記號經過某個固定參考點時按下碼錶，同時開始數\n" +
                        "3. 數滿設定的圈數，記號再次經過同一點時按停",
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider()
                Text(
                    "100 圈在 33⅓ 轉大約 3 分鐘，精度 ±0.17%；200 圈約 6 分鐘，±0.08%。\n" +
                        "圈數太少不值得做 —— 10 圈只有 1.7%，比不校準好不了多少。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "碼錶要量的是同一段轉動。中途調過速度或換過轉速檔位，這個 k 就不對了。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("回到量測")
        }
    }
}
