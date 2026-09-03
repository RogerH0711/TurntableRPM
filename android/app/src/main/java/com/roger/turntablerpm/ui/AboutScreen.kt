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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.R

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
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineSmall)

        Section(stringResource(R.string.about_sec_blind_spots)) {
            Para(
                stringResource(R.string.about_measures_platter),
            )
            Para(
                stringResource(R.string.about_pretty_numbers),
                emphasis = true,
            )
        }

        Section(stringResource(R.string.about_sec_calibration)) {
            Para(
                stringResource(R.string.about_scale_error),
            )
            Para(
                stringResource(R.string.about_error_not_usable),
                emphasis = true,
            )
            Para(
                stringResource(R.string.about_ratios_unaffected),
            )
            Para(stringResource(R.string.about_calibration_per_device))
        }

        Section(stringResource(R.string.about_sec_phone_eccentricity)) {
            Para(
                stringResource(R.string.about_phone_is_a_mass),
            )
            Para(
                stringResource(R.string.about_rotate_180),
                emphasis = true,
            )
            Para(
                stringResource(R.string.about_2x_is_harmonic),
            )
        }

        Section(stringResource(R.string.about_sec_accuracy)) {
            Para(
                stringResource(R.string.about_accuracy_tips),
            )
        }

        Section(stringResource(R.string.about_sec_placement)) { PlacementGuide() }

        Section(stringResource(R.string.about_sec_safety)) {
            Para(stringResource(R.string.about_safety_magnets), emphasis = true)
            Para(stringResource(R.string.about_safety_tonearm))
            Para(
                stringResource(R.string.about_safety_mat),
            )
            Para(stringResource(R.string.about_safety_78))
        }

        Section(stringResource(R.string.about_sec_how)) {
            Para(
                stringResource(R.string.about_how_gyro),
            )
            Para(
                stringResource(R.string.about_how_sampling),
            )
        }

        Section(stringResource(R.string.about_sec_privacy)) {
            Para(stringResource(R.string.about_privacy_body))
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back_to_measure))
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
