package dev.hamfist.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.*
import androidx.wear.compose.material3.*
import dev.hamfist.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme=dynamicColorScheme(this) ?: ColorScheme(primary=Color(0xFFC9E99B),primaryContainer=Color(0xFF334B23),onPrimary=Color(0xFF213610))) {
                AppScaffold { WearApp() }
            }
        }
    }
}

@Composable private fun WearApp() {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val s by store.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("home") }
    var previewResult by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(page) { if (page == "apps") apps=withContext(Dispatchers.IO) {AppCatalog.list(context,s.apps)} }
    BackHandler(page != "home") { page="home" }
    key(page) {
        val state = rememberScalingLazyListState(initialCenterItemIndex=0)
        ScreenScaffold(scrollState=state) { padding ->
            ScalingLazyColumn(state=state,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(start=14.dp,end=14.dp,top=32.dp,bottom=40.dp),verticalArrangement=Arrangement.spacedBy(8.dp),autoCentering=null) {
                when(page) {
                    "home" -> {
                        item { Heading("Hamfist", "Feel the message") }
                        item { Text("· —",fontSize=36.sp,color=MaterialTheme.colorScheme.primary,fontFamily=FontFamily.Monospace) }
                        item { SwitchButton(checked=s.enabled,onCheckedChange={v -> store.update { it.copy(enabled=v) }; if(!v) Playback.stop(context)},modifier=Modifier.fillMaxWidth(),label={Text(if(s.enabled && s.local) "Listening" else "Paused")},secondaryLabel={Text("${s.timing.effectiveCpm} chars/min")}) }
                        item { Action("Feel SOS") { previewResult=Playback.play(context,"SOS",true) } }
                        item { Action("Stop vibration",tonal=true) { Playback.stop(context); previewResult="Stopped" } }
                        if(previewResult.isNotEmpty()) item { Note(previewResult) }
                        item { Action("Timing", "${s.timing.dit} / ${s.timing.dash} ms",true) { page="timing" } }
                        item { Action("Quiet & battery",tonal=true) { page="limits" } }
                        item { Action("Watch apps", "${s.apps.size} selected",true) { page="apps" } }
                        item { Note("Phone notifications arrive from the paired Hamfist app. Choose phone apps there. This watch has its own timing and limits.") }
                    }
                    "timing" -> {
                        item { Heading("Your rhythm", "Watch timing") }
                        item { Adjuster("Chars / minute",s.timing.cpm,20,300,10) {v -> store.update {it.copy(timing=it.timing.copy(cpm=v))}} }
                        item { SwitchButton(s.timing.automatic,{v -> store.update {it.copy(timing=it.timing.copy(automatic=v,ditMs=it.timing.dit.toInt(),dashMs=it.timing.dash.toInt()))}},label={Text("Standard ratio")},secondaryLabel={Text("Dit : dash = 1 : 3")},modifier=Modifier.fillMaxWidth()) }
                        if(!s.timing.automatic) {
                            item { Adjuster("Dit · ms",s.timing.ditMs,20,500,10) {v -> store.update {it.copy(timing=it.timing.copy(ditMs=v))}} }
                            item { Adjuster("Dash — ms",s.timing.dashMs,20,1500,10) {v -> store.update {it.copy(timing=it.timing.copy(dashMs=v))}} }
                        }
                        item { Note("Dit ${s.timing.dit} ms\nDash ${s.timing.dash} ms\nEffective ${s.timing.effectiveCpm} chars/min\nCustom pulses stay exact. Gaps adjust to your pace.") }
                        item { Action("Feel PARIS") { previewResult=Playback.play(context,"PARIS",true) } }
                        item { Action("Stop",tonal=true) { Playback.stop(context) } }
                        if(previewResult.isNotEmpty()) item { Note(previewResult) }
                    }
                    "limits" -> {
                        item { Heading("Keep it quiet", "Watch preferences") }
                        item { WearToggle("Receive Morse", "Phone + local apps",s.local) {v -> store.update {it.copy(local=v)}; if(!v) Playback.stop(context)} }
                        item { Adjuster("Max characters",s.maxChars,5,120,5) {v -> store.update {it.copy(maxChars=v)}} }
                        item { Adjuster("Max seconds",s.maxSeconds,5,60,5) {v -> store.update {it.copy(maxSeconds=v)}} }
                        item { Adjuster("Cooldown · sec",s.cooldownSeconds,0,120,5) {v -> store.update {it.copy(cooldownSeconds=v)}} }
                        item { Adjuster("Start delay · ms",s.startDelayMs,0,2000,100) {v -> store.update {it.copy(startDelayMs=v)}} }
                        item { Note("Gives the original buzz time to finish. The screen stays off.") }
                        item { WearToggle("Battery Saver", "Pause signals",s.batterySaver) {v -> store.update {it.copy(batterySaver=v)}} }
                        item { Adjuster("Low battery · %",s.lowBatteryPercent,0,50,5) {v -> store.update {it.copy(lowBatteryPercent=v)}} }
                        item { Note("0% disables the battery threshold. DND is always respected.") }
                        item { WearToggle("Silent mode", "Respect device setting",s.respectSilent) {v -> store.update {it.copy(respectSilent=v)}} }
                        item { WearToggle("Quiet hours", "Local watch time",s.quiet.enabled) {v -> store.update {it.copy(quiet=it.quiet.copy(enabled=v))}} }
                        if(s.quiet.enabled) {
                            item { TimeAdjuster("From",s.quiet.startMinute) {v -> store.update {it.copy(quiet=it.quiet.copy(startMinute=v))}} }
                            item { TimeAdjuster("Until",s.quiet.endMinute) {v -> store.update {it.copy(quiet=it.quiet.copy(endMinute=v))}} }
                            item { Note("Equal times pause all day.") }
                        }
                    }
                    "apps" -> {
                        item { Heading("Watch apps", "Local notifications") }
                        item { Note("Optional: choose apps installed on this watch. Leave empty when using only phone notifications to avoid duplicate signals.") }
                        item { Action("Notification access",tonal=true) { AppCatalog.openAccess(context) } }
                        item { Action("Encode: ${s.content.label}","Tap to change",true) {store.update {it.copy(content=ContentMode.entries[(it.content.ordinal+1)%ContentMode.entries.size])}} }
                        item { WearToggle("Skip ongoing", "Media & progress",s.skipOngoing) {v -> store.update {it.copy(skipOngoing=v)}} }
                        item { WearToggle("Skip silent", "Quiet channels",s.skipSilent) {v -> store.update {it.copy(skipSilent=v)}} }
                        items(apps, key={it.packageName}) { app ->
                            WearToggle(app.label,"",app.packageName in s.apps) {v -> store.update {it.copy(apps=if(v) it.apps+app.packageName else it.apps-app.packageName)}}
                        }
                        if (apps.isEmpty()) item { Note("No local apps found.") }
                    }
                }
                if(page!="home") item { Action("Back",tonal=true) {page="home"} }
            }
        }
    }
}

@Composable private fun Heading(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(title,style=MaterialTheme.typography.titleLarge,textAlign=TextAlign.Center)
        Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
    }
}

@Composable private fun Action(title: String, detail: String? = null, tonal: Boolean = false, click: () -> Unit) {
    Button(onClick=click,modifier=Modifier.fillMaxWidth(),colors=if(tonal) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors()) {
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {Text(title,textAlign=TextAlign.Center);if(detail!=null) Text(detail,style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)}
    }
}

@Composable private fun WearToggle(title: String, detail: String, checked: Boolean, change: (Boolean)->Unit) {
    SwitchButton(checked,change,modifier=Modifier.fillMaxWidth(),label={Text(title)},secondaryLabel={Text(detail)})
}

@Composable private fun Adjuster(label: String, value: Int, min: Int, max: Int, step: Int, change: (Int)->Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(label,style=MaterialTheme.typography.labelMedium,textAlign=TextAlign.Center)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly,modifier=Modifier.fillMaxWidth()) {
            TextButton(onClick={change((value-step).coerceAtLeast(min))},enabled=value>min) { Text("−",fontSize=24.sp) }
            Text("$value",style=MaterialTheme.typography.titleLarge)
            TextButton(onClick={change((value+step).coerceAtMost(max))},enabled=value<max) { Text("+",fontSize=24.sp) }
        }
    }
}

@Composable private fun TimeAdjuster(label: String, minute: Int, change: (Int)->Unit) {
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(label,style=MaterialTheme.typography.labelMedium)
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={change((minute+1440-30)%1440)}) {Text("−")}
            Text("%02d:%02d".format(minute/60,minute%60))
            TextButton(onClick={change((minute+30)%1440)}) {Text("+")}
        }
    }
}

@Composable private fun Note(text: String) { Text(text,modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant) }
