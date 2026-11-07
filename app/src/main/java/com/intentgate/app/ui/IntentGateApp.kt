package com.intentgate.app.ui
import com.intentgate.app.R
import com.intentgate.app.loc
import com.intentgate.app.UiStrings
import com.intentgate.app.AppLanguage
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle



import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.intentgate.app.data.IntentionRecord
import com.intentgate.app.data.AppRepository
import com.intentgate.app.data.DailyUsage
import com.intentgate.app.data.LocalAppRepository
import com.intentgate.app.data.MockUsageDataSource
import com.intentgate.app.data.PreferencesAppSettingsStorage
import com.intentgate.app.data.UsageDataSource
import com.intentgate.app.session.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.intentgate.app.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private enum class GateTab(val icon: GateIcon) {
    Home(GateIcon.Home), Apps(GateIcon.Apps), Stats(GateIcon.Stats), Settings(GateIcon.Settings)
}

@Composable
fun IntentGateApp(previewFrozen: Boolean = false, appRepository: AppRepository? = null, usageDataSource: UsageDataSource? = null, intentSessions: IntentSessionRepository? = null, dailyLimits:DailyLimitRepository?=null,
                  onEnableNotifications:()->Unit={},onLaunchApp:(String)->Unit={},requestedLimitAppId:String?=null,onLimitNavigationConsumed:()->Unit={}) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context, appRepository) { appRepository ?: createAndroidAppRepository(context) }
    val usageProvider = remember(usageDataSource, repository, previewFrozen) { usageDataSource ?: if(previewFrozen) MockUsageDataSource() else AndroidUsageDataSource(context,repository) }
    val managedApps by repository.apps.collectAsState()
    val catalog = repository.catalogState?.collectAsState()?.value ?: repository.catalog
    val fallbackToday by usageProvider.today.collectAsState()
    val systemSnapshot = usageProvider.snapshot?.collectAsState()?.value
    val sessionBook = intentSessions?.state?.collectAsState()?.value
    val fallbackHistory by usageProvider.history.collectAsState()
    val projected = remember(systemSnapshot,fallbackToday,fallbackHistory,sessionBook) {
        val source=systemSnapshot ?: UsageSnapshot(fallbackToday,fallbackHistory)
        if(sessionBook!=null) projectIntentSessions(source,sessionBook) else source
    }
    val limitState=dailyLimits?.state?.collectAsState()?.value
    val dailyUsage = projected.today.copy(dailyLimits=limitState?.apps ?: emptyMap())
    var sessionAppId by rememberSaveable { mutableStateOf<String?>(null) }
    val realSession = sessionBook?.sessions?.lastOrNull {sessionAppId==null || it.appId==sessionAppId}
    var uiStarted by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    var catalogRefreshing by remember { mutableStateOf(false) }
    var catalogRefreshFailed by remember { mutableStateOf(false) }
    fun refreshAppCatalog() {
        if(catalogRefreshing) return
        catalogRefreshing=true
        refreshScope.launch {
            try { catalogRefreshFailed=!withContext(Dispatchers.IO) { repository.refreshCatalog() } }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(_:RuntimeException) { catalogRefreshFailed=true }
            finally { catalogRefreshing=false }
        }
    }
    LaunchedEffect(uiStarted, AppLanguage.currentTag) { if(uiStarted && appRepository==null) refreshAppCatalog() }
    LaunchedEffect(uiStarted, usageProvider, managedApps, catalog) {
        if(uiStarted) while(true) { usageProvider.refresh(); delay(30_000) }
    }
    var showAddApps by rememberSaveable { mutableStateOf(false) }
    var pickerResumeCount by remember { mutableIntStateOf(0) }
    var appDetailsId by rememberSaveable { mutableStateOf<String?>(null) }
    // Daily allowance and Intent sessions are independent projections of their existing repositories.
    val remainingToday = homeDailyRemaining(dailyUsage, managedApps, LocalDate.now())
    var tabName by rememberSaveable { mutableStateOf(GateTab.Home.name) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showSession by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val allSessions=sessionBook?.sessions.orEmpty()
    val todayDate=LocalDate.now()
    val records=realIntentionRecords(sessionsInDates(allSessions,todayDate,todayDate))
    val historyRecords=realIntentionRecords(allSessions)
    var historyStart by rememberSaveable { mutableStateOf(todayDate.toString()) }
    var historyEnd by rememberSaveable { mutableStateOf(todayDate.toString()) }
    val filteredHistory=realIntentionRecords(sessionsInDates(allSessions,LocalDate.parse(historyStart),LocalDate.parse(historyEnd)))
    LaunchedEffect(requestedLimitAppId) {
        if(requestedLimitAppId!=null && managedApps.any { it.appId==requestedLimitAppId }) {
            tabName=GateTab.Apps.name;appDetailsId=requestedLimitAppId;showHistory=false;showAddApps=false;onLimitNavigationConsumed()
        }
    }
    val currentTab = GateTab.valueOf(tabName)
    val statsStateHolder = rememberSaveableStateHolder()
    var statsDetailsPeriod by rememberSaveable { mutableStateOf<String?>(null) }
    var statsPeriodName by rememberSaveable { mutableStateOf(StatsPeriod.Week.name) }
    var statsSelectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var statsBreakdown by remember { mutableStateOf(StatsAppBreakdown()) }
    LaunchedEffect(uiStarted,currentTab,pickerResumeCount,statsSelectedDate) {
        if(uiStarted && currentTab==GateTab.Stats) {
            statsBreakdown=StatsAppBreakdown()
            while(true) {
                statsBreakdown=readStatsAppBreakdown(context,statsSelectedDate?.let(LocalDate::parse))
                delay(30_000)
            }
        }
    }
    val mainScroll = rememberScrollState()
    val statsScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(activity, usageProvider) {
        uiStarted = activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> uiStarted = true
                Lifecycle.Event.ON_STOP -> uiStarted = false
                Lifecycle.Event.ON_RESUME -> {
                    pickerResumeCount++
                    refreshScope.launch { usageProvider.refresh() }
                }
                else -> Unit
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    BackHandler(showAddApps || appDetailsId != null || showHistory || statsDetailsPeriod != null || currentTab != GateTab.Home) {
        when {
            showAddApps -> showAddApps = false
            appDetailsId != null -> appDetailsId = null
            statsDetailsPeriod != null -> statsDetailsPeriod = null
            showHistory -> showHistory = false
            else -> tabName = GateTab.Home.name
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(GateColors.Background))) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val widthScale = min(maxWidth.value, 480f) / 412f
            val scale = min(widthScale, maxHeight.value / 915f).coerceAtLeast(.78f)
            if (showAddApps && currentTab == GateTab.Apps) {
                AddAppPage(catalog, managedApps, onBack = { showAddApps = false },
                    onAdd = { ids -> repository.addApps(ids).also { if (it) showAddApps = false } },
                    onRefresh = ::refreshAppCatalog, refreshing = catalogRefreshing,
                    refreshFailed = catalogRefreshFailed, resumed = pickerResumeCount, uiStarted = uiStarted,
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxSize().align(Alignment.TopCenter))
            } else Column(Modifier.widthIn(max = 480.dp).fillMaxSize().align(Alignment.TopCenter)) {
                if (showHistory) {
                    HistoryPage(filteredHistory, scale, { showHistory = false }, { detailId = it.id },
                        dateLabel=formatStatsDateRange(LocalDate.parse(historyStart),LocalDate.parse(historyEnd)), modifier = Modifier.weight(1f).fillMaxWidth())
                } else if(currentTab==GateTab.Stats && statsDetailsPeriod!=null) {
                    StatsAllAppsPage(statsBreakdown,StatsPeriod.valueOf(statsDetailsPeriod!!),scale,
                        onBack={statsDetailsPeriod=null},modifier=Modifier.weight(1f).fillMaxWidth(),selectedDate=statsSelectedDate?.let(LocalDate::parse))
                } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(when (currentTab) { GateTab.Stats -> statsScroll; GateTab.Settings -> settingsScroll; else -> mainScroll })
                    .padding(horizontal = (20 * scale).dp)) {
                    Spacer(Modifier.height((38 * scale).dp))
                    if ((currentTab != GateTab.Apps && currentTab != GateTab.Stats && currentTab != GateTab.Settings) || showHistory) BrandHeader(scale, previewFrozen)
                    when {
                        currentTab == GateTab.Home -> HomeContent(remainingToday, records.take(3), dailyUsage, scale,
                            onSession = { sessionAppId=null;showSession = true }, onHistory = { historyStart=todayDate.toString();historyEnd=todayDate.toString();showHistory = true }, onRecord = { detailId = it.id })
                        currentTab == GateTab.Apps -> {
                            val settings = managedApps.firstOrNull { it.appId == appDetailsId }
                            val definition = catalog.firstOrNull { it.id == appDetailsId }
                            if (settings != null && definition != null) {
                                AppDetailsContent(definition, settings, dailyUsage, scale,
                                    onBack = { appDetailsId = null },
                                    onSave = { repository.saveSettings(it); appDetailsId = null },
                                    onOpenApp={onLaunchApp(definition.id)},
                                    onSessionControls={sessionAppId=definition.id;showSession=true},
                                    onRemove={ if(repository.removeApps(setOf(definition.id))) { appDetailsId=null;true } else false },
                                    onExtend=dailyLimits?.let { limits -> { minutes:Int,action:String -> limits.extendToday(definition.id,minutes,action) } })
                            } else {
                                AppsContent(managedApps, catalog, dailyUsage, scale,
                                    onManaged = repository::setManaged, onApp = { appDetailsId = it }, onAdd = { showAddApps = true },
                                    onLaunch=onLaunchApp,onRemove=repository::removeApps)
                            }
                        }
                        currentTab == GateTab.Stats -> statsStateHolder.SaveableStateProvider("Stats") {
                            val history by usageProvider.history.collectAsState()
                            val selectedDate=statsSelectedDate?.let(LocalDate::parse)
                            val selectedUsage=selectedDate?.let { day ->
                                statsBreakdown.usage.today.takeIf { it.date==day }?.let { usage ->
                                    val managedIds=managedApps.map { it.appId }.toSet()
                                    usage.copy(secondsByApp=usage.secondsByApp.filterKeys { it in managedIds })
                                }
                            }
                            StatsContent(dailyUsage,projected.history,dailyUsage.date,scale,statsBreakdown,
                                StatsPeriod.valueOf(statsPeriodName),selectedDate,selectedUsage,allSessions,
                                onPeriod={statsPeriodName=it.name;statsSelectedDate=null},
                                onDate={if(validStatsDate(it)) statsSelectedDate=it.toString()},
                                onViewAll={statsDetailsPeriod=if(selectedDate!=null) StatsPeriod.Today.name else statsPeriodName},
                                onIntents={start,end -> historyStart=start.toString();historyEnd=end.toString();showHistory=true})
                        }
                        currentTab == GateTab.Settings -> SettingsContent(scale, dailyUsage,onEnableNotifications)
                        else -> Unit
                    }
                    Spacer(Modifier.height((20 * scale).dp))
                }
                BottomNavigation(currentTab, scale) { tabName = it.name; showHistory = false; appDetailsId = null; statsDetailsPeriod = null }
            }
        }
    }
    if (showSession) {
        if(realSession==null) {
            AlertDialog(onDismissRequest={showSession=false},containerColor=GateColors.Background.last(),
                title={GateText(loc(R.string.ig_daily_remaining_title),22f,weight=FontWeight.SemiBold)},
                text={Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    GateText(if(remainingToday.hasLimits && remainingToday.available)
                        loc(R.string.ig_daily_budget_remaining,formatDailyRemaining(remainingToday.remainingSeconds!!)) else remainingToday.statusLabel,16f)
                    GateText(loc(R.string.ig_no_session_started),14f,color=GateColors.Secondary)
                }},confirmButton={TextButton(onClick={showSession=false}) { GateText(loc(R.string.ig_done),16f) }})
        } else {
            val countdown=realSession.countdown()
            val status=countdown.status
            AlertDialog(onDismissRequest={showSession=false},containerColor=GateColors.Background.last(),
                title={GateText(if(status==SessionStatus.Ended && countdown.remainingMillis>0) loc(R.string.ig_session_ended)
                    else if(status==SessionStatus.Ended) loc(R.string.ig_time_s_up) else loc(R.string.ig_session_controls),22f,weight=FontWeight.SemiBold)},
                text={Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    GateText("${realSession.appName} · ${UiStrings.purpose(realSession.purpose)}",16f)
                    GateText(loc(R.string.ig_remaining,formatClock(countdown.remainingSeconds)),24f,weight=FontWeight.Medium)
                    GateText(UiStrings.message(realSession.timingNote),14f,color=GateColors.Secondary)
                    if(status!=SessionStatus.Ended) {
                        TextButton(onClick={if(status==SessionStatus.Running) intentSessions?.pause(realSession.appId) else intentSessions?.resume(realSession.appId)}) {
                            GateText(if(status==SessionStatus.Running) loc(R.string.ig_pause) else loc(R.string.ig_continue),16f)
                        }
                        TextButton(onClick={intentSessions?.end(false,realSession.appId)}) { GateText(loc(R.string.ig_end_session),16f) }
                    }
                }},confirmButton={TextButton(onClick={showSession=false}) { GateText(loc(R.string.ig_done),16f) }})
        }
    }
    historyRecords.firstOrNull { it.id == detailId }?.let { record ->
        val session = sessionBook?.sessions?.firstOrNull { it.id == record.id }
        val timing = session?.let {
            val format = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                java.text.DateFormat.SHORT, UiStrings.locale())
            buildList {
                add(loc(R.string.ig_intent_started_at, format.format(java.util.Date(it.startedAt))))
                add(loc(when (it.status) {
                    SessionStatus.Running -> R.string.ig_running
                    SessionStatus.Paused -> R.string.ig_paused
                    SessionStatus.Ended -> R.string.ig_session_ended
                }))
                it.endedAt?.let { ended -> add(loc(R.string.ig_intent_ended_at, format.format(java.util.Date(ended)))) }
            }.joinToString("\n", prefix = "\n")
        }.orEmpty()
        AlertDialog(onDismissRequest = { detailId = null }, containerColor = GateColors.Background.last(),
            title = { GateText((if(record.isDemo) UiStrings.message(record.purpose) else UiStrings.purpose(record.purpose)), 22f, weight = FontWeight.SemiBold) },
            text = { GateText("${record.appName} · ${record.durationLabel}\n${if(record.isDemo) loc(R.string.ig_demo_intent_record) else loc(R.string.ig_intent_foreground_use)}$timing", 16f, color = GateColors.Secondary) },
            confirmButton = { TextButton(onClick = { detailId = null }) { GateText(loc(R.string.ig_close), 16f) } })
    }
}

@Composable
private fun BrandHeader(s: Float, frozen: Boolean) {
    Row(Modifier.fillMaxWidth().height((26*s).dp), verticalAlignment = Alignment.CenterVertically) {
        GateGlyph(GateIcon.Logo, Modifier.size((24*s).dp))
        Spacer(Modifier.width((10*s).dp))
        GateText(loc(R.string.ig_intentgate), 24*s, weight = FontWeight.Bold, letterSpacing = -.8f*s)
        Spacer(Modifier.weight(1f))
        val date = if (frozen) loc(R.string.ig_sample_date) else LocalDate.now().format(DateTimeFormatter.ofPattern(loc(R.string.ig_header_date_pattern),UiStrings.locale()))
        GateText(date, 14*s, color = GateColors.Secondary)
    }
}

@Composable
private fun HomeContent(remaining: HomeDailyRemaining, records: List<IntentionRecord>, usage: DailyUsage, s: Float,
    onSession: () -> Unit, onHistory: () -> Unit, onRecord: (IntentionRecord) -> Unit) {
    Spacer(Modifier.height((44*s).dp))
    DailyRemainingRing(remaining, s, onSession)
    Spacer(Modifier.height((16*s).dp))
    GateText(if(remaining.hasLimits && remaining.available) loc(R.string.ig_daily_managed_apps,remaining.appCount) else remaining.statusLabel, 17*s, Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Spacer(Modifier.height((6*s).dp))
    // Only Android usage is shown here; Session time is never subtracted from daily allowance.
    GateText(if(usage.isMock) loc(R.string.ig_today_demo_unavailable) else loc(R.string.ig_today_83, if(usage.available) usage.totalLabel else "—", if(usage.intentStatsAvailable) loc(R.string.ig_intent_sessions, usage.sessions) else loc(R.string.ig_intent_sessions_85)), 14*s, Modifier.fillMaxWidth(), GateColors.Muted, textAlign = TextAlign.Center)
    Spacer(Modifier.height((8*s).dp))
    GateText(if(!usage.isMock) (if(usage.available) UiStrings.message(usage.note) else usage.sourceLabel + loc(R.string.ig_settings_usage_access)) else loc(R.string.ig_demo_data_no_real_limit_status),
        12*s, Modifier.fillMaxWidth(), GateColors.Muted, textAlign = TextAlign.Center)
    Spacer(Modifier.height((20*s).dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GateText(loc(R.string.ig_today_s_intentions), 23*s, Modifier.weight(1f), weight = FontWeight.SemiBold, letterSpacing = -.9f*s)
        Row(Modifier.height((28*s).dp).clickable(onClickLabel = loc(R.string.ig_view_all_intentions), onClick = onHistory), verticalAlignment = Alignment.CenterVertically) {
            GateText(loc(R.string.ig_view_all), 14*s, color = GateColors.Secondary)
            Spacer(Modifier.width((4*s).dp))
            GateGlyph(GateIcon.Chevron, Modifier.size((18*s).dp), GateColors.Secondary)
        }
    }
    if (records.isNotEmpty()) GateText(if(records.all { it.isDemo }) loc(R.string.ig_demo_intent_records_latest_3)
        else loc(R.string.ig_latest_3_sessions), 14*s, color = GateColors.Muted)
    Spacer(Modifier.height((12*s).dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape((18*s).dp)).background(GateColors.Surface)) {
        if (records.isEmpty()) EmptyIntentions(s)
        records.forEach { IntentionRow(it, s) { onRecord(it) } }
    }
}

@Composable
private fun DailyRemainingRing(remaining: HomeDailyRemaining, s: Float, onClick: () -> Unit) {
    fun ringTime(seconds: Long): String {
        val minutes = seconds / 60 + if (seconds % 60 > 0) 1 else 0
        return (minutes / 60).toString() + ":" + (minutes % 60).toString().padStart(2, '0')
    }
    val clock = if (remaining.available && remaining.hasLimits) ringTime(remaining.remainingSeconds!!) else "—"
    val units = loc(R.string.ig_hours_minutes)
    val caption = if (remaining.available && remaining.hasLimits) {
        units + " · " + loc(R.string.ig_daily_budget_total, ringTime(remaining.limitSeconds!!))
    } else units
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size((324*s).dp).clip(RoundedCornerShape(50))
            .clickable(onClickLabel = loc(R.string.ig_daily_remaining_details), onClick = onClick)
            .semantics { contentDescription = if(remaining.available && remaining.hasLimits)
                loc(R.string.ig_daily_budget_remaining,clock) else remaining.statusLabel; stateDescription = clock }) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 11*s.dp.toPx()
                val inset = stroke/2
                val d = size.minDimension-stroke
                drawCircle(GateColors.Track, d/2, style = Stroke(stroke))
                if (remaining.progress > 0f) {
                    val sweep = 360f*remaining.progress
                    drawArc(GateColors.Ring, -90f, sweep, false, Offset(inset,inset), Size(d,d), style = Stroke(stroke, cap = StrokeCap.Round))
                    val angle = Math.toRadians((sweep-90).toDouble())
                    drawCircle(GateColors.Ring, stroke*.72f,
                        Offset(size.width/2 + cos(angle).toFloat()*d/2, size.height/2 + sin(angle).toFloat()*d/2))
                }
            }
            Column(Modifier.align(Alignment.Center).widthIn(max = (240*s).dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.heightIn(min = (28*s).dp), contentAlignment = Alignment.Center) {
                    GateText(loc(R.string.ig_daily_remaining_title), 18*s,
                        color = GateColors.Secondary, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height((8*s).dp))
                GateText(clock, 64*s*min(1f, 4f/clock.length), weight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, letterSpacing = -2*s)
                Spacer(Modifier.height((8*s).dp))
                Box(Modifier.heightIn(min = (28*s).dp), contentAlignment = Alignment.Center) {
                    GateText(caption, 13*s, color = GateColors.Secondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun IntentionRow(record: IntentionRecord, s: Float, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = (74*s).dp).clickable(onClick = onClick)
        .padding(horizontal = (16*s).dp, vertical = (12*s).dp), verticalAlignment = Alignment.CenterVertically) {
        val icon = if (record.isDemo) painterResource(record.iconRes) else appPainter(
            AppDefinition(record.packageName.orEmpty(), record.appName, record.packageName.orEmpty(),
                R.drawable.ic_app_icon_placeholder, installed = record.packageName != null))
        Image(icon, record.appName,
            Modifier.size((44*s).dp).clip(RoundedCornerShape((10*s).dp)), contentScale = ContentScale.Fit)
        Spacer(Modifier.width((20*s).dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4*s).dp)) {
            GateText((if(record.isDemo) UiStrings.message(record.purpose) else UiStrings.purpose(record.purpose)), 15.5f*s, weight = FontWeight.SemiBold, letterSpacing = -.4f*s)
            GateText("${record.appName} · ${record.durationLabel}${if(record.isDemo) loc(R.string.ig_demo) else ""}", 14*s, color = GateColors.Muted)
        }
        Spacer(Modifier.width((6*s).dp))
        GateGlyph(GateIcon.Chevron, Modifier.size((18*s).dp), GateColors.Secondary)
    }
}

@Composable
private fun BottomNavigation(selected: GateTab, s: Float, onSelect: (GateTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = (18*s).dp).padding(bottom = (17*s).dp)
        .selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
        GateTab.entries.forEach { tab ->
            val active = tab == selected
            Column(Modifier.weight(1f).heightIn(min = maxOf(48f, 60*s).dp).clickable(role = Role.Tab) { onSelect(tab) }
                .semantics { this.selected = active }, horizontalAlignment = Alignment.CenterHorizontally) {
                GateGlyph(tab.icon, Modifier.size((26*s).dp), if (active) GateColors.Ink else GateColors.Secondary, active && tab == GateTab.Home)
                Spacer(Modifier.height((5*s).dp))
                GateText(loc(when(tab) { GateTab.Home -> R.string.ig_home;GateTab.Apps -> R.string.ig_apps;GateTab.Stats -> R.string.ig_stats;GateTab.Settings -> R.string.ig_settings }), 14*s, color = if (active) GateColors.Ink else GateColors.Secondary,
                    weight = if (active) FontWeight.Medium else FontWeight.Normal)
                Spacer(Modifier.height((5*s).dp))
                Canvas(Modifier.size((8*s).dp)) { if (active) drawCircle(GateColors.Mint) }
            }
        }
    }
}

@Composable
private fun EmptyIntentions(s: Float) {
    GateText(loc(R.string.ig_no_real_intentions), 14*s, Modifier.fillMaxWidth().padding(16.dp),
        color = GateColors.Secondary)
}

@Composable
private fun HistoryPage(records: List<IntentionRecord>, s: Float, onBack: () -> Unit,
                        onRecord: (IntentionRecord) -> Unit, dateLabel: String, modifier: Modifier) {
    LazyColumn(modifier.padding(horizontal = (20*s).dp), contentPadding = PaddingValues(top = 30.dp, bottom = 20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).semantics { contentDescription = loc(R.string.ig_back_to_home) }
                    .clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    GateGlyph(GateIcon.Back, Modifier.size(22.dp))
                }
                GateText(loc(R.string.ig_intent_history),
                    24*s, weight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            GateText(dateLabel,14*s,color=GateColors.Secondary)
            Spacer(Modifier.height(20.dp))
            if (records.isEmpty()) Box(Modifier.clip(RoundedCornerShape(18.dp)).background(GateColors.Surface)) {
                EmptyIntentions(s)
            }
        }
        items(records, key = { it.id }) { record ->
            Column(Modifier.background(GateColors.Surface)) {
                record.startedAt?.let { start ->
                    GateText(loc(R.string.ig_intent_started_at,java.text.DateFormat.getTimeInstance(
                        java.text.DateFormat.SHORT,UiStrings.locale()).format(java.util.Date(start))),
                        12*s,Modifier.padding(horizontal=(16*s).dp).padding(top=(10*s).dp),GateColors.Secondary)
                }
                IntentionRow(record,s) { onRecord(record) }
            }
        }
    }
}

@Composable
private fun PlaceholderPage(title: String, s: Float, onHome: () -> Unit) {
    Spacer(Modifier.height(42.dp))
    GateText(title, 30*s, weight = FontWeight.SemiBold)
    Spacer(Modifier.height(22.dp))
    GateText(loc(R.string.ig_this_page_is_coming_next), 16*s, color = GateColors.Secondary)
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onHome) { GateText(loc(R.string.ig_back_to_home), 16*s) }
}

@Composable
internal fun GateText(text: String, size: Float, modifier: Modifier = Modifier, color: Color = GateColors.Ink,
    weight: FontWeight = FontWeight.Normal, textAlign: TextAlign? = null, letterSpacing: Float = 0f) {
    Text(text, modifier, color = color, fontFamily = GateFont, fontSize = size.sp, fontWeight = weight,
        lineHeight = (size * 1.2f).sp, letterSpacing = letterSpacing.sp, textAlign = textAlign,
        // CJK fallback fonts have extra leading; trim it inside the existing English-sized rows.
        style = if(AppLanguage.currentTag=="zh-CN" && text.any { it in '一'..'鿿' }) TextStyle(
            platformStyle=PlatformTextStyle(includeFontPadding=false),
            lineHeightStyle=LineHeightStyle(LineHeightStyle.Alignment.Center,LineHeightStyle.Trim.Both,LineHeightStyle.Mode.Tight)
        ) else TextStyle.Default)
}

private fun formatClock(seconds: Long): String = String.format(Locale.ROOT, "%02d:%02d", seconds/60, seconds%60)
