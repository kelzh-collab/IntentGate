package com.intentgate.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intentgate.app.R
import com.intentgate.app.loc
import com.intentgate.app.data.*

@Composable
internal fun StatsAllAppsPage(data: StatsAppBreakdown, period: StatsPeriod, s: Float,
    onBack: () -> Unit, modifier: Modifier = Modifier, selectedDate: java.time.LocalDate? = null) {
    val rows = rankedStatsApps(data,period,selectedDate)
    Column(modifier.padding(horizontal = (20*s).dp,vertical = (16*s).dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(GateColors.Surface)
                .clickable(onClick = onBack),contentAlignment = Alignment.Center) {
                GateGlyph(GateIcon.Back,Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            GateText(loc(R.string.ig_app_usage_breakdown),22*s,weight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        GateText(selectedDate?.let { formatStatsDateRange(it,it) } ?: period.title,16*s,weight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        GateText(loc(R.string.ig_stats_all_apps_scope),12.5f*s,color = GateColors.Secondary)
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rows.isEmpty()) item {
                StatsCard(0f,s) {
                    GateText(if (data.usage.today.access != UsageAccess.Ready) data.usage.today.sourceLabel
                        else loc(R.string.ig_no_usage_recorded),14*s,Modifier.padding(20.dp),GateColors.Secondary)
                }
            }
            items(rows,key = { it.key }) { row ->
                val app = data.catalog.first { it.packageName == row.key }
                StatsCard(0f,s) {
                    Row(Modifier.fillMaxWidth().padding((16*s).dp),verticalAlignment = Alignment.CenterVertically) {
                        Image(appPainter(app),app.name,Modifier.size((40*s).dp)
                            .clip(RoundedCornerShape(10.dp)),contentScale = ContentScale.Fit)
                        Spacer(Modifier.width((12*s).dp))
                        GateText(app.name,15*s,Modifier.weight(1f),weight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        GateText(formatStatsDuration(row.value),14*s,color = GateColors.Secondary)
                    }
                }
            }
        }
    }
}
