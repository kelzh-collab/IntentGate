package com.intentgate.app.ui
import com.intentgate.app.R
import com.intentgate.app.loc
import com.intentgate.app.UiStrings



import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.intentgate.app.data.*
import java.time.LocalDate

private val TrendColors = listOf(0xFFAFD6BF,0xFFA1CDB4,0xFFBEDFCB,0xFF92C3A8,0xFFC5E2D1,0xFF9ECDB5).map { Color(it) }
private val BreakdownColors = listOf(Color(0xFF83DFB5), Color(0xFF67B594), Color(0xFFA4CBB4), Color(0xFFCCDCC8))

@Composable
internal fun StatsContent(today: DailyUsage, history: List<DatedUsage>, date: LocalDate,
    s: Float, breakdown: StatsAppBreakdown, period: StatsPeriod, selectedDate: LocalDate?,
    selectedUsage: DailyUsage?, sessions: List<IntentSession>, onPeriod: (StatsPeriod) -> Unit,
    onDate: (LocalDate) -> Unit, onViewAll: () -> Unit, onIntents: (LocalDate,LocalDate) -> Unit) {
    val context=LocalContext.current
    val periodName=period.name + selectedDate?.toString().orEmpty()
    var selectedKey by rememberSaveable(periodName) { mutableStateOf<String?>(null) }
    val snapshot = remember(period,today,history,date,selectedDate,selectedUsage,sessions,com.intentgate.app.AppLanguage.currentTag) {
        buildStatsSelection(period,today,history,date,selectedDate,selectedUsage,sessions)
    }
    val displayUsage=(if(selectedDate!=null) selectedUsage ?: today.copy(access=UsageAccess.Loading) else today).copy(intentStatsAvailable=true)
    val selected = snapshot.trends.firstOrNull { it.key == selectedKey }
        ?: if (period == StatsPeriod.Today) snapshot.trends.lastOrNull { (it.seconds ?: 0) > 0 } ?: snapshot.trends.last()
        else snapshot.trends.last()

    Row(Modifier.fillMaxWidth().height((40*s).dp), verticalAlignment = Alignment.CenterVertically) {
        GateText(loc(R.string.ig_stats), 32*s, Modifier.weight(1f).testTag("stats_title"), weight = FontWeight.SemiBold)
        run {
            Box(Modifier.width((84*s).dp).height((27*s).dp).clip(RoundedCornerShape((14*s).dp))
                .background(Color.White.copy(alpha = .65f)), contentAlignment = Alignment.Center) {
                GateText(displayUsage.sourceLabel, 10.5f*s, color = GateColors.Secondary, weight = FontWeight.Medium)
            }
        }
    }
    StatsSpace(20f,s)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).height((48*s).dp).selectableGroup()
        .clip(RoundedCornerShape((18*s).dp)).background(Color.White.copy(alpha = .8f)).padding((4*s).dp)) {
        StatsPeriod.entries.forEach { choice ->
            val active = selectedDate==null && choice == period
            Box(Modifier.weight(1f).fillMaxHeight().testTag("stats_range_${choice.name.lowercase()}")
                .semantics { this.selected = active }.clip(RoundedCornerShape((14*s).dp))
                .background(if (active) GateColors.Mint else Color.Transparent)
                .clickable(role = Role.Tab) { onPeriod(choice); selectedKey = null }, contentAlignment = Alignment.Center) {
                GateText(choice.title, 12*s, color = if (active) GateColors.Ink else GateColors.Secondary,
                    weight = if (active) FontWeight.Medium else FontWeight.Normal)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape((14*s).dp))
            .background(if(selectedDate!=null) GateColors.Mint else Color.Transparent)
            .clickable(role=Role.Tab) { showStatsDatePicker(context,selectedDate,onDate) },
            contentAlignment=Alignment.Center) {
            GateText(loc(R.string.ig_stats_select_date),12*s,textAlign=TextAlign.Center)
        }
    }
    if(selectedDate!=null) {
        StatsSpace(10f,s)
        GateText(formatStatsDateRange(selectedDate,selectedDate),14*s,color=GateColors.Secondary)
    }
    StatsSpace(20f,s)
    StatsOverview(snapshot,s,displayUsage)
    StatsSpace(20f,s)
    StatsTrends(snapshot,selected,s) { selectedKey = it.key }
    StatsSpace(20f,s)
    StatsIntentInsights(snapshot.sessions,s) { onIntents(snapshot.start,snapshot.end) }
    StatsSpace(20f,s)
    StatsBreakdown(breakdown,period,selectedDate,s,onViewAll)
}

@Composable
private fun StatsOverview(stats: UsageStatsSnapshot, s: Float, today: DailyUsage) {
    StatsCard(118f,s) {
        Column(Modifier.fillMaxWidth().padding(horizontal = (16*s).dp).padding(top = (16*s).dp,bottom = (10*s).dp)) {
            Row(Modifier.fillMaxWidth().height((18*s).dp), verticalAlignment = Alignment.CenterVertically) {
                GateText(loc(R.string.ig_usage_overview),13.5f*s,Modifier.weight(1f),GateColors.Secondary,FontWeight.Medium)
                GateText(stats.dateLabel,11.5f*s,color = GateColors.Secondary)
            }
            StatsSpace(7f,s)
            GateText(if(stats.availableDays>0) stats.durationLabel else "—",38*s,Modifier.testTag("stats_total"),weight = FontWeight.SemiBold)
            StatsSpace(6f,s)
            GateText((if(today.intentStatsAvailable) loc(R.string.ig_sessions, stats.sessions) else loc(R.string.ig_intent_sessions_not_tracked)) + if (stats.isPartial) loc(R.string.ig_days_available, stats.availableDays, stats.period.days) else "",
                13*s,Modifier.testTag("stats_sessions"),GateColors.Secondary)
        }
    }
}

@Composable
private fun StatsTrends(stats: UsageStatsSnapshot, selected: UsageTrendPoint, s: Float,
                        onSelect: (UsageTrendPoint) -> Unit) {
    val maximum = stats.trends.mapNotNull { it.seconds }.maxOrNull() ?: 0
    val limit = listOf(1800,3600,7200,10800,21600,43200,86400).firstOrNull { it >= maximum }
        ?: ((maximum + 21599) / 21600) * 21600
    StatsCard(188f,s) {
        Column(Modifier.fillMaxWidth().padding(horizontal = (16*s).dp).padding(top = (16*s).dp,bottom = (8*s).dp)) {
            Row(Modifier.fillMaxWidth().height((22*s).dp),verticalAlignment = Alignment.CenterVertically) {
                GateText(loc(R.string.ig_usage_trends),18*s,Modifier.weight(1f),weight = FontWeight.SemiBold)
                GateText(stats.trendLabel,12*s,color = GateColors.Secondary)
            }
            StatsSpace(8f,s)
            Box(Modifier.fillMaxWidth().height((134*s).dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val u = s.dp.toPx()
                    for (value in listOf(limit,limit/2)) {
                        val y = 110*u-value.toFloat()/limit*95*u
                        drawLine(GateColors.Track,Offset(34*u,y),Offset(size.width,y),.8f*u,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2*u,4*u)))
                    }
                }
                for (value in listOf(limit,limit/2,0)) {
                    val y = 110*s-value.toFloat()/limit*95*s-6*s
                    val label = if (value == 0) "0" else if (limit <= 7200) loc(R.string.ig_m,value/60) else loc(R.string.ig_hour_axis,value/3600)
                    GateText(label,10.5f*s,Modifier.offset(x = (-6*s).dp,y = y.dp).width((28*s).dp),
                        GateColors.Secondary,textAlign = TextAlign.End)
                }
                Row(Modifier.fillMaxSize().padding(start = (34*s).dp)) {
                    stats.trends.forEachIndexed { index, point ->
                        val active = point.key == selected.key
                        val height = (point.seconds ?: 0).toFloat()/limit*95*s
                        Box(Modifier.weight(1f).fillMaxHeight().testTag("stats_bar_$index")
                            .semantics {
                                this.selected = active
                                contentDescription = "${point.description}: ${point.seconds?.let(::formatStatsDuration) ?: loc(R.string.ig_unavailable)}"
                            }.clickable(role = Role.Button) { onSelect(point) }) {
                            Canvas(Modifier.fillMaxSize()) {
                                val u = s.dp.toPx()
                                if ((point.seconds ?: 0) > 0) {
                                    val barHeight = point.seconds!!.toFloat()/limit*95*u
                                    drawRoundRect(if (active) GateColors.Ring else TrendColors[index%TrendColors.size],
                                        Offset((size.width-22*u)/2,110*u-barHeight),Size(22*u,barHeight),CornerRadius(6*u))
                                } else {
                                    drawLine(GateColors.Track,Offset(size.width/2-6*u,108*u),Offset(size.width/2+6*u,108*u),2*u,StrokeCap.Round)
                                }
                            }
                            if (active) {
                                Box(Modifier.align(Alignment.TopCenter).offset(y = (110*s-height-20*s).coerceAtLeast(0f).dp)
                                    .wrapContentWidth(unbounded = true).widthIn(min = (38*s).dp).height((17*s).dp)
                                    .clip(RoundedCornerShape((7*s).dp)).background(GateColors.Track.copy(alpha = .65f))
                                    .padding(horizontal = (4*s).dp),contentAlignment = Alignment.Center) {
                                    GateText(point.seconds?.let { if (it < 7200) loc(R.string.ig_m, it/60) else formatStatsDuration(it) } ?: "—",
                                        10.5f*s,Modifier.testTag("stats_selected_trend"),weight = FontWeight.Medium)
                                }
                            }
                            GateText(point.label,10.5f*s,Modifier.align(Alignment.TopCenter).offset(y = (120*s).dp),
                                if (active) GateColors.Ink else GateColors.Secondary,if (active) FontWeight.Medium else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsIntentInsights(count: Int, s: Float, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(role=Role.Button,onClick=onClick)) {
        StatsCard(116f,s) {
            Column(Modifier.fillMaxWidth().padding((16*s).dp)) {
                GateText(loc(R.string.ig_intent_insights),18*s,weight=FontWeight.SemiBold)
                StatsSpace(12f,s)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    GateText(loc(R.string.ig_intent_session_count,count),30*s,Modifier.weight(1f)
                        .testTag("stats_intent_count"),weight=FontWeight.SemiBold)
                    GateText(loc(R.string.ig_stats_view_records) + " \u203a",13*s,color=GateColors.Secondary)
                }
            }
        }
    }
}

@Composable
private fun StatsBreakdown(data: StatsAppBreakdown, period: StatsPeriod, selectedDate: LocalDate?, s: Float, onViewAll: () -> Unit) {
    val items = rankedStatsApps(data,period,selectedDate).take(4)
    val catalog = data.catalog
    val today = data.usage.today
    val shownSeconds = items.sumOf { it.value }
    StatsCard(166f,s) {
        Column(Modifier.fillMaxWidth().padding(horizontal = (16*s).dp).padding(top = (16*s).dp)) {
            GateText(loc(R.string.ig_app_usage_breakdown),18*s,Modifier.height((22*s).dp),weight = FontWeight.SemiBold)
            StatsSpace(16f,s)
            Canvas(Modifier.fillMaxWidth().height((8*s).dp).semantics {
                contentDescription = loc(R.string.ig_app_usage_distribution)
                stateDescription = items.joinToString { entry -> "${catalog.firstOrNull { it.id == entry.key }?.name ?: entry.key}: ${formatStatsDuration(entry.value)}" }
            }) {
                if (items.isEmpty()) drawRoundRect(GateColors.Track,size = size,cornerRadius = CornerRadius(4*s.dp.toPx()))
                else {
                    val gap = 2*s.dp.toPx()
                    val available = (size.width-gap*(items.size-1)).coerceAtLeast(0f)
                    var left = 0f
                    items.forEachIndexed { index, item ->
                        val width = available*item.value/shownSeconds
                        drawRoundRect(BreakdownColors[index%BreakdownColors.size],Offset(left,0f),Size(width,size.height),CornerRadius(4*s.dp.toPx()))
                        left += width+gap
                    }
                }
            }
            StatsSpace(16f,s)
            if (items.isEmpty()) GateText(if(!today.available) today.sourceLabel else loc(R.string.ig_no_usage_recorded),14*s,color = GateColors.Secondary)
            items.chunked(2).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { columnIndex, item ->
                        val index = rowIndex*2+columnIndex
                        val app = catalog.firstOrNull { it.id == item.key }
                            ?: AppDefinition(item.key,item.key,item.key,R.mipmap.ic_launcher)
                        Row(Modifier.weight(1f).heightIn(min = (44*s).dp).padding(top = (2*s).dp)) {
                            Image(appPainter(app),app.name,Modifier.padding(top = (2*s).dp).size((26*s).dp)
                                .clip(RoundedCornerShape((6*s).dp)),contentScale = ContentScale.Fit)
                            Spacer(Modifier.width((8*s).dp))
                            Column(Modifier.weight(1f)) {
                                GateText(app.name,13.5f*s,weight = FontWeight.Medium)
                                StatsSpace(3f,s)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Canvas(Modifier.size((4*s).dp)) { drawCircle(BreakdownColors[index%BreakdownColors.size]) }
                                    Spacer(Modifier.width((6*s).dp))
                                    GateText(formatStatsDuration(item.value),12*s,Modifier.testTag("stats_app_${item.key}"),GateColors.Secondary)
                                }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            StatsSpace(12f,s)
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("stats_view_more")
                .clickable(onClick = onViewAll),contentAlignment = Alignment.Center) {
                GateText(loc(R.string.ig_stats_view_more) + " \u203a",14*s,color = GateColors.Ring,weight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun StatsCard(minHeight: Float, s: Float, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape((18*s).dp)
    Box(Modifier.fillMaxWidth().heightIn(min = (minHeight*s).dp)
        .shadow(2.dp,shape,ambientColor = GateColors.Ink.copy(alpha = .025f),spotColor = GateColors.Ink.copy(alpha = .025f))
        .clip(shape).background(GateColors.Surface)) { content() }
}

@Composable
private fun StatsSpace(value: Float, s: Float) { Spacer(Modifier.height((value*s).dp)) }
