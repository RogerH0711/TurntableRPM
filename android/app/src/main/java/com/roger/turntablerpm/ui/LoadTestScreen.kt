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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.R
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
    val added = parseDecimal(addedText) ?: 0.0

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
        Text(stringResource(R.string.load_title), style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.load_how_to_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.load_how_to_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.load_balance_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = Orange,
                )
            }
        }

        if (records.size < 2) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.load_need_two_records),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.load_pick_two),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    RecordPicker(stringResource(R.string.load_phone_only), records, base) {
                        baseId = it.epochMillis
                    }
                    RecordPicker(stringResource(R.string.load_with_mass), records, loaded) {
                        loadedId = it.epochMillis
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.load_weights),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    OutlinedTextField(
                        value = if (phoneMassGrams > 0) formatDecimal(phoneMassGrams) else "",
                        onValueChange = { onPhoneMassChange(parseDecimal(it) ?: 0.0) },
                        label = { Text(stringResource(R.string.load_phone_mass)) },
                        suffix = { Text("g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = addedText,
                        onValueChange = { addedText = filterDecimalInput(it) },
                        label = { Text(stringResource(R.string.load_added_mass)) },
                        suffix = { Text("g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.load_scale_note),
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
                        Text(
                            stringResource(R.string.load_result),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        StatRow(
                            stringResource(R.string.load_slope),
                            "%.5f RPM/g".format(result.slopeRPMPerGram),
                        )
                        StatRow(
                            stringResource(R.string.load_phone_effect),
                            "%+.4f RPM".format(result.phoneEffectRPM),
                        )
                        StatRow(
                            stringResource(R.string.load_zero_load),
                            "%.4f RPM".format(result.zeroLoadRPM),
                        )
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
                                Text(
                                    stringResource(
                                        R.string.load_save_to_profile,
                                        profile.displayName.ifBlank {
                                            stringResource(R.string.profile_untitled)
                                        },
                                    ),
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.load_no_profile),
                                style = MaterialTheme.typography.bodySmall,
                                color = Orange,
                            )
                        }
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.load_pick_two_hint),
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (profile?.hasLoadTest == true) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.load_saved_result),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    StatRow(
                        stringResource(R.string.load_slope),
                        "%.5f RPM/g".format(profile.loadSlopeRPMPerGram ?: 0.0),
                    )
                    StatRow(
                        stringResource(R.string.load_phone_effect),
                        "%+.4f RPM".format(profile.loadPhoneEffectRPM ?: 0.0),
                    )
                    profile.loadMeasuredAtMillis?.let {
                        StatRow(
                            stringResource(R.string.load_measured_at),
                            SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(it)),
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        if (profile.loadIsSignificant) stringResource(R.string.load_significant)
                        else stringResource(R.string.load_not_significant),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (profile.loadIsSignificant) Orange else Color.Unspecified,
                    )
                }
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.load_back_to_profile))
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
                    selected?.let { "%.4f RPM".format(it.meanRPM) }
                        ?: stringResource(R.string.load_not_selected),
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

/**
 * 斜率有沒有超過量測雜訊，決定要不要當一回事。
 *
 * 「拖慢／加快」是動詞，日文的語序跟中文相反 —— 所以整句用位置參數，
 * 由翻譯決定動詞擺在哪裡（CLAUDE.md 坑 29c）。
 */
@Composable
private fun verdict(r: LoadCompensationResult): String {
    if (!r.isSignificant) return stringResource(R.string.load_verdict_insignificant)
    val pct = abs(r.phoneEffectRPM / max(r.zeroLoadRPM, 0.001)) * 100
    val dir = stringResource(if (r.phoneEffectRPM < 0) R.string.load_slows else R.string.load_speeds_up)
    return stringResource(
        R.string.load_verdict_significant, dir, abs(r.phoneEffectRPM), pct, r.zeroLoadRPM,
    )
}
