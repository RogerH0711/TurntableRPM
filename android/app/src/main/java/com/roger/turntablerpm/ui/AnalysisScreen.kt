package com.roger.turntablerpm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.R
import com.roger.turntablerpm.core.MeasurementAnalysis
import com.roger.turntablerpm.profile.TurntableProfile

/**
 * 分析結果。獨立一頁，不接在主畫面後面。
 *
 * **這一頁是這個 app 跟「只顯示一個 RPM 數字」的工具之間的差別**，但它同時也是
 * 最長的一頁（三張圖加上譜峰判讀）。接在主畫面下面的話，每次打開 app 都要捲過
 * 一整頁分析才看得到校準與設定 —— 而分析是「量完才想看」的東西，
 * 校準與設定是「量之前要碰」的東西。iOS 端一開始就是分開的。
 */
@Composable
fun AnalysisScreen(
    analysis: MeasurementAnalysis,
    profile: TurntableProfile?,
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
        Text(
            stringResource(R.string.meas_analysis_result),
            style = MaterialTheme.typography.headlineSmall,
        )
        AnalysisCard(analysis, profile)
        AnalysisCharts(analysis)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back_to_measure))
        }
    }
}
