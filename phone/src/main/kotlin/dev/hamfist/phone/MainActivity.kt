package dev.hamfist.phone

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hamfist.core.Morse
import dev.hamfist.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HamfistTheme { HamfistApp() } }
    }
}

@Composable private fun HamfistTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (android.os.Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = Color(0xFFC9E99B), secondary = Color(0xFFBBCAB0), surface = Color(0xFF11140E))
    else lightColorScheme(primary = Color(0xFF42652C), secondary = Color(0xFF56624B), surface = Color(0xFFF8FAF0))
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable private fun HamfistApp() {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val settings by store.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = listOf("Signal", "Apps", "Timing", "Settings")
    val icons = listOf(Icons.Outlined.GraphicEq, Icons.Outlined.Apps, Icons.Outlined.Tune, Icons.Outlined.Settings)
    Scaffold(bottomBar = {
        NavigationBar { pages.forEachIndexed { i, title -> NavigationBarItem(selected = page == i, onClick = { page = i }, icon = { Icon(icons[i],null) }, label = { Text(title) }) } }
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (page) {
                0 -> SignalScreen(settings,store) { page = it }
                1 -> AppsScreen(settings,store)
                2 -> TimingScreen(settings,store)
                else -> SettingsScreen(settings,store)
            }
        }
    }
}

@Composable private fun PageTitle(kicker: String, title: String, subtitle: String) {
    Column(Modifier.padding(top=20.dp,bottom=12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(kicker.uppercase(), style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.primary, letterSpacing=2.sp)
        Text(title,style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.SemiBold)
        Text(subtitle,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun Panel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            content()
        }
    }
}

@Composable private fun SignalScreen(s: Settings, store: SettingsStore, navigate: (Int) -> Unit) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(AppCatalog.hasAccess(context)) }
    var watch by remember { mutableStateOf("Checking watch…") }
    var preview by rememberSaveable { mutableStateOf("HELLO") }
    var result by remember { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            access = AppCatalog.hasAccess(context); WatchLink.status(context) { watch = it }
        } }
        lifecycle.lifecycle.addObserver(observer)
        access = AppCatalog.hasAccess(context); WatchLink.status(context) { watch = it }
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val pattern = remember(preview,s) { Playback.pattern(preview,s) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { PageTitle("A little less screen time", "Hamfist", "Feel the message. Stay in the moment.") }
        item {
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),shape=RoundedCornerShape(32.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Outlined.GraphicEq,null,Modifier.size(40.dp))
                        Spacer(Modifier.weight(1f))
                        Switch(s.enabled, { store.update { it.copy(enabled=it.enabled.not()) }; if (s.enabled) Playback.stop(context) })
                    }
                    Text(if (!s.enabled) "Taking a breather" else if (!access || s.apps.isEmpty()) "Make it your signal" else "Listening for your apps", style=MaterialTheme.typography.headlineMedium)
                    Text(if (!access) "First, allow notification access below." else if (s.apps.isEmpty()) "Choose the apps you want to feel." else "${s.apps.size} selected apps · ${s.timing.effectiveCpm} chars/min",style=MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick={ navigate(1) },label={ Text("${s.apps.size} apps") },leadingIcon={ Icon(Icons.Outlined.Apps,null,Modifier.size(18.dp)) })
                        AssistChip(onClick={ navigate(2) },label={ Text("${s.timing.dit} / ${s.timing.dash} ms") })
                    }
                }
            }
        }
        if (!access) item {
            Panel("1. Let Hamfist listen") {
                Text("Android will ask you to allow notification access. Hamfist processes selected apps locally and keeps no notification history.")
                FilledTonalButton(onClick={ AppCatalog.openAccess(context) },modifier=Modifier.fillMaxWidth()) { Text("Allow notification access") }
            }
        }
        if (s.apps.isEmpty()) item {
            Panel("${if (access) "1" else "2"}. Pick your apps") {
                Text("Start with one or two. Short signals are easier to recognize and gentler on your battery.")
                FilledTonalButton(onClick={ navigate(1) },modifier=Modifier.fillMaxWidth()) { Text("Choose apps") }
            }
        }
        item {
            Panel("Where you feel it") {
                Toggle("Pixel Watch / Wear OS",watch,s.watch) { value -> store.update { it.copy(watch=value) } }
                Toggle("This phone","Use Hamfist without a watch, or on both devices.",s.local) { value -> store.update { it.copy(local=value) }; if (!value) Playback.stop(context) }
                if (!s.local && !s.watch) Text("Choose at least one destination to receive signals.",color=MaterialTheme.colorScheme.error)
                Text("Install Hamfist on your paired watch. Each device has its own timing and quiet-hour controls.",style=MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Panel("Try a little Morse") {
                OutlinedTextField(preview,{ preview=it.take(120) },label={ Text("Preview text") },singleLine=true,modifier=Modifier.fillMaxWidth())
                Text(pattern.morse.ifBlank { "· · ·" },fontFamily=FontFamily.Monospace,fontSize=22.sp,color=MaterialTheme.colorScheme.primary)
                Text("${pattern.text.length} characters · %.1f s · preview follows your length limits".format(pattern.durationMs/1000.0),style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={ result=Playback.play(context,preview,true) },enabled=pattern.text.isNotEmpty()) { Text("Feel on phone") }
                    OutlinedButton(onClick={ WatchLink.send(context,pattern.text,"preview") { result=it } },enabled=pattern.text.isNotEmpty()) { Text("On watch") }
                }
                TextButton(onClick={ Playback.stop(context); result="Stopped on phone" }) { Icon(Icons.Outlined.Stop,null); Spacer(Modifier.width(8.dp)); Text("Stop phone vibration") }
                Text("Phone preview works while paused. Watch preview follows the watch’s pause, quiet hours and battery settings. System DND still applies.",style=MaterialTheme.typography.bodySmall)
                if (result.isNotEmpty()) Text(result,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
            }
        }
        item { Text("PRIVATE BY DESIGN  ·  NO ACCOUNT  ·  NO POLLING",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=12.dp)) }
    }
}

@Composable private fun AppsScreen(s: Settings, store: SettingsStore) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppEntry?>(null) }
    LaunchedEffect(s.apps) { apps=withContext(Dispatchers.IO) { AppCatalog.list(context,s.apps) }; loading=false }
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Your attention, your rules", "Selected apps", "Only the apps you choose can send a Morse signal.") }
        item { OutlinedTextField(query,{query=it},leadingIcon={Icon(Icons.Outlined.Search,null)},label={Text("Search apps")},singleLine=true,modifier=Modifier.fillMaxWidth()) }
        item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("${s.apps.size} selected",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge); TextButton(onClick={manual=true}) { Text("Add package") } } }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        val filtered = apps.filter { it.label.contains(query,true) || it.packageName.contains(query,true) }.sortedByDescending { it.packageName in s.apps }
        if (!loading && filtered.isEmpty()) item { Text("No matching apps. You can add a package name for an app without a launcher icon.") }
        items(filtered,key={it.packageName}) { app ->
            Card(shape=RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(horizontal=16.dp,vertical=8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(14.dp)) { Text(app.label.take(1).uppercase(),Modifier.padding(14.dp),style=MaterialTheme.typography.titleMedium) }
                        Column(Modifier.weight(1f).padding(horizontal=12.dp)) { Text(app.label,fontWeight=FontWeight.Medium); Text(app.packageName,style=MaterialTheme.typography.bodySmall,maxLines=2) }
                        Switch(app.packageName in s.apps,{ checked -> store.update { it.copy(apps=if (checked) it.apps+app.packageName else it.apps-app.packageName) } })
                    }
                    if (app.packageName in s.apps) TextButton(onClick={editing=app}) { Text("App-name signal: ${s.aliases[app.packageName]?.ifBlank { app.label } ?: app.label}") }
                }
            }
        }
    }
    if (manual) {
        var pkg by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest={manual=false},title={Text("Add an app by package")},text={ OutlinedTextField(pkg,{pkg=it.trim()},label={Text("com.example.app")},supportingText={Text("Useful for apps without a launcher icon.")},singleLine=true) },confirmButton={TextButton(onClick={store.update { it.copy(apps=it.apps+pkg) };manual=false},enabled=pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) { Text("Add app") }},dismissButton={TextButton(onClick={manual=false}){Text("Cancel")}})
    }
    editing?.let { app ->
        var alias by remember(app.packageName) { mutableStateOf(s.aliases[app.packageName].orEmpty()) }
        AlertDialog(onDismissRequest={editing=null},title={Text("${app.label} signal")},text={Column {Text("A short code saves battery in App name mode. For example, MSG or MAIL. Leave blank to use the app name.");OutlinedTextField(alias,{alias=it.take(20)},label={Text("Short code")},singleLine=true)}},confirmButton={TextButton(onClick={store.update { it.copy(aliases=it.aliases+(app.packageName to Morse.normalize(alias))) };editing=null}) { Text("Save") }},dismissButton={TextButton(onClick={editing=null}){Text("Cancel")}})
    }
}

@Composable private fun TimingScreen(s: Settings, store: SettingsStore) {
    val context = LocalContext.current
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { PageTitle("Find your rhythm", "Timing", "Tune this phone’s pace. Watch timing lives on the watch.") }
        item { Panel("Characters per minute") {
            Text("${s.timing.cpm}",style=MaterialTheme.typography.displayLarge,color=MaterialTheme.colorScheme.primary)
            ValueSlider("Pace",s.timing.cpm,20,300,"chars/min",10) { value -> store.update { it.copy(timing=it.timing.copy(cpm=value)) } }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Learn" to 40,"Easy" to 100,"Quick" to 200).forEach { (label, speed) -> FilterChip(s.timing.cpm==speed,onClick={store.update { it.copy(timing=it.timing.copy(cpm=speed)) }},label={Text(label)}) } }
            Text("PARIS standard: 5 characters = 1 word. At 100 chars/min, a dit is 60 ms.",style=MaterialTheme.typography.bodySmall)
        } }
        item { Panel("Dots & dashes") {
            Toggle("Standard Morse ratio","Dit : dash = 1 : 3. Turn off for custom pulse lengths.",s.timing.automatic) { value -> store.update { it.copy(timing=it.timing.copy(automatic=value,ditMs=it.timing.dit.toInt(),dashMs=it.timing.dash.toInt())) } }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) { PulseValue("·", "${s.timing.dit} ms", "Dit"); PulseValue("—", "${s.timing.dash} ms", "Dash") }
            if (!s.timing.automatic) {
                ValueSlider("Dit duration",s.timing.ditMs,20,500,"ms",10) { value -> store.update { it.copy(timing=it.timing.copy(ditMs=value)) } }
                ValueSlider("Dash duration",s.timing.dashMs,20,1500,"ms",10) { value -> store.update { it.copy(timing=it.timing.copy(dashMs=value)) } }
                Text("Custom pulses stay fixed. Letter and word spacing expands for a slower pace. These pulse lengths allow up to ${s.timing.effectiveCpm} chars/min at this setting.",style=MaterialTheme.typography.bodySmall)
            }
            Text("Between pulses ${s.timing.dit} ms · letters ${s.timing.spacing*3} ms · words ${s.timing.spacing*7} ms",style=MaterialTheme.typography.bodySmall)
            Button(onClick={Playback.play(context,"PARIS",true)},modifier=Modifier.fillMaxWidth()) { Text("Feel PARIS") }
            TextButton(onClick={Playback.stop(context)},modifier=Modifier.fillMaxWidth()) { Text("Stop") }
        } }
        item { Panel("Morse pocket guide") { Text(Morse.alphabet.entries.filter { it.key.isLetter() }.chunked(3).joinToString("\n") { row -> row.joinToString("    ") { "${it.key} ${it.value.padEnd(4)}" } },fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodyLarge) } }
    }
}

@Composable private fun PulseValue(mark: String, duration: String, label: String) {
    Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(mark,fontSize=44.sp,color=MaterialTheme.colorScheme.primary);Text(duration,style=MaterialTheme.typography.titleLarge);Text(label,style=MaterialTheme.typography.labelMedium) }
}

@Composable private fun SettingsScreen(s: Settings, store: SettingsStore) {
    val context = LocalContext.current
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { PageTitle("Small signals. Sensible limits.", "Settings", "Keep notifications useful, private, and easy on your battery.") }
        item { Panel("What to encode") {
            ContentMode.entries.forEach { mode -> Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(s.content==mode,{store.update{it.copy(content=mode)}});TextButton(onClick={store.update{it.copy(content=mode)}}) { Text(mode.label) } } }
            Text("App name uses your short app codes. Message modes can include private notification text. Android may redact sensitive notifications.",style=MaterialTheme.typography.bodySmall)
        } }
        item { Panel("Keep it brief") {
            ValueSlider("Maximum characters",s.maxChars,5,120,"chars",5) { v -> store.update{it.copy(maxChars=v)} }
            ValueSlider("Maximum vibration",s.maxSeconds,5,60,"seconds",5) { v -> store.update{it.copy(maxSeconds=v)} }
            ValueSlider("Per-app cooldown",s.cooldownSeconds,0,120,"seconds",5) { v -> store.update{it.copy(cooldownSeconds=v)} }
            ValueSlider("Delay before Morse",s.startDelayMs,0,2000,"ms",100) { v -> store.update{it.copy(startDelayMs=v)} }
            Text("A short delay lets the original notification buzz finish before Morse begins. The display stays off.",style=MaterialTheme.typography.bodySmall)
            Text("Whole characters only. Duplicate updates and group summaries are ignored. New signals are skipped while the motor is busy; nothing queues up.",style=MaterialTheme.typography.bodySmall)
            Toggle("Skip ongoing notifications","Ignore playback controls, downloads and foreground services.",s.skipOngoing) { v -> store.update{it.copy(skipOngoing=v)} }
            Toggle("Skip silent channels","Only alert for default or high-importance channels.",s.skipSilent) { v -> store.update{it.copy(skipSilent=v)} }
        } }
        item { Panel("Quiet & battery aware") {
            Toggle("Respect silent mode","Do Not Disturb is always respected.",s.respectSilent) { v -> store.update{it.copy(respectSilent=v)} }
            Toggle("Pause in Battery Saver","No vibration or watch relay while this device is saving power.",s.batterySaver) { v -> store.update{it.copy(batterySaver=v)} }
            ValueSlider("Pause below battery level",s.lowBatteryPercent,0,50,"% (0 = off)",5) { v -> store.update{it.copy(lowBatteryPercent=v)} }
            Toggle("Quiet hours","Uses this device’s local time. Equal times mean all day.",s.quiet.enabled) { v -> store.update{it.copy(quiet=it.quiet.copy(enabled=v))} }
            if (s.quiet.enabled) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick={TimePickerDialog(context,{_,h,m -> store.update{it.copy(quiet=it.quiet.copy(startMinute=h*60+m))}},s.quiet.startMinute/60,s.quiet.startMinute%60,true).show()}) {Text("From ${clock(s.quiet.startMinute)}")}
                OutlinedButton(onClick={TimePickerDialog(context,{_,h,m -> store.update{it.copy(quiet=it.quiet.copy(endMinute=h*60+m))}},s.quiet.endMinute/60,s.quiet.endMinute%60,true).show()}) {Text("Until ${clock(s.quiet.endMinute)}")}
            }
        } }
        item { Panel("Made to stay out of the way") {
            Text("Hamfist reacts to incoming notifications. No polling, screen wake, foreground service, analytics, or stored message history. A short CPU-only hold covers the handoff to the vibration motor. Watch messages expire after 30 seconds and are never replayed on reconnection.")
            Text("Your apps’ normal alerts remain active. Disable their regular watch vibration in the Pixel Watch companion app if you only want Morse. Hamfist cannot mute another app’s alerts.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={AppCatalog.openAccess(context)}) { Text("Manage notification access") }
            Text("Hamfist 0.1.0",style=MaterialTheme.typography.labelSmall)
        } }
    }
}

@Composable private fun Toggle(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=12.dp)) { Text(title,style=MaterialTheme.typography.titleSmall); Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked,change)
    }
}

@Composable private fun ValueSlider(label: String, value: Int, min: Int, max: Int, unit: String, step: Int, change: (Int) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth()) { Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge); Text("${draft.roundToInt()} $unit",style=MaterialTheme.typography.labelMedium) }
        Slider(value=draft,onValueChange={draft=it},onValueChangeFinished={change(draft.roundToInt())},valueRange=min.toFloat()..max.toFloat(),steps=((max-min)/step-1).coerceAtLeast(0))
    }
}

private fun clock(minute: Int) = "%02d:%02d".format(minute/60,minute%60)
