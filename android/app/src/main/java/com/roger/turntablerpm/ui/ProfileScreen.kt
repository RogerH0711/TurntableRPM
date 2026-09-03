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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.R
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
        Text(stringResource(R.string.prof_title), style = MaterialTheme.typography.headlineSmall)

        if (profiles.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.prof_empty_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.prof_empty_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.prof_add))
                    }
                }
            }
        } else {
            for (p in profiles) {
                ProfileCard(p, onUpdate, onOpenLoadTest) { onDelete(p.id) }
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.prof_add_another))
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back_to_measure))
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
                    profile.displayName.ifBlank { stringResource(R.string.profile_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (profile.isActive) {
                    Text(
                        stringResource(R.string.prof_active),
                        style = MaterialTheme.typography.bodySmall, color = Green,
                    )
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
                label = { Text(stringResource(R.string.prof_model)) },
                placeholder = { Text(stringResource(R.string.prof_model_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = profile.maker,
                onValueChange = { onUpdate(profile.copy(maker = it)) },
                label = { Text(stringResource(R.string.prof_maker)) },
                placeholder = { Text(stringResource(R.string.prof_maker_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            Text(stringResource(R.string.prof_section_spec), style = MaterialTheme.typography.titleSmall)
            NumberField(
                label = stringResource(R.string.prof_spec_wow),
                unit = "%",
                value = profile.specWowFlutterPercent,
                onChange = { onUpdate(profile.copy(specWowFlutterPercent = it)) },
            )
            Text(
                stringResource(R.string.prof_spec_note),
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()
            Text(
                stringResource(R.string.prof_section_drive),
                style = MaterialTheme.typography.titleSmall,
            )
            NumberField(
                label = stringResource(R.string.prof_pulley),
                unit = "mm",
                value = profile.pulleyDiameterMM,
                onChange = { onUpdate(profile.copy(pulleyDiameterMM = it)) },
            )
            NumberField(
                label = stringResource(R.string.prof_platter),
                unit = "mm",
                value = profile.platterDiameterMM,
                onChange = { onUpdate(profile.copy(platterDiameterMM = it)) },
            )
            NumberField(
                label = stringResource(R.string.prof_belt),
                unit = "mm",
                value = profile.beltThicknessMM,
                onChange = { onUpdate(profile.copy(beltThicknessMM = it)) },
            )
            profile.expectedDriveRatio?.let {
                StatRow(stringResource(R.string.prof_expected_ratio), "%.2f ×".format(it))
            }
            Text(
                stringResource(R.string.prof_drive_note),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.prof_belt_sensitivity),
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()
            Text(stringResource(R.string.prof_section_load), style = MaterialTheme.typography.titleSmall)
            if (profile.hasLoadTest) {
                StatRow(
                    stringResource(R.string.load_slope),
                    "%.5f RPM/g".format(profile.loadSlopeRPMPerGram ?: 0.0),
                )
                StatRow(
                    stringResource(R.string.load_phone_effect),
                    "%+.4f RPM".format(profile.loadPhoneEffectRPM ?: 0.0),
                )
                Text(
                    if (profile.loadIsSignificant) stringResource(R.string.load_significant)
                    else stringResource(R.string.load_not_significant),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                stringResource(R.string.prof_load_note),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onOpenLoadTest, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (profile.hasLoadTest) stringResource(R.string.prof_redo_load_test)
                    else stringResource(R.string.prof_do_load_test),
                )
            }

            HorizontalDivider()
            OutlinedTextField(
                value = profile.note,
                onValueChange = { onUpdate(profile.copy(note = it)) },
                label = { Text(stringResource(R.string.prof_section_note)) },
                placeholder = { Text(stringResource(R.string.prof_note_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            if (confirmDelete) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.prof_confirm_delete)) }
                    TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.prof_cancel)) }
                }
            } else {
                TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.prof_delete)) }
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
                Text(stringResource(R.string.prof_clear_field), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 8.5 顯示成 "8.5"、8.0 顯示成 "8"，不要一律補到小數三位。 */
private fun trimNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
