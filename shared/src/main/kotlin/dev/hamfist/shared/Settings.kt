package dev.hamfist.shared

import android.content.Context
import android.content.pm.PackageManager
import android.content.SharedPreferences
import dev.hamfist.core.QuietHours
import dev.hamfist.core.Timing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class ContentMode(val label: String) { APP("App name"), TITLE("Title / sender"), MESSAGE("Message"), TITLE_MESSAGE("Title + message") }

data class Settings(
    val enabled: Boolean = true,
    val apps: Set<String> = emptySet(),
    val aliases: Map<String, String> = emptyMap(),
    val timing: Timing = Timing(),
    val content: ContentMode = ContentMode.TITLE,
    val local: Boolean = false,
    val watch: Boolean = true,
    val maxChars: Int = 40,
    val maxSeconds: Int = 20,
    val cooldownSeconds: Int = 10,
    val startDelayMs: Int = 500,
    val skipOngoing: Boolean = true,
    val skipSilent: Boolean = true,
    val respectSilent: Boolean = true,
    val batterySaver: Boolean = true,
    val lowBatteryPercent: Int = 15,
    val quiet: QuietHours = QuietHours()
)

class SettingsStore private constructor(context: Context) {
    private val watchDevice = isWatch(context)
    private val preferences = context.getSharedPreferences("hamfist", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    val current: Settings get() = mutable.value
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mutable.value = read() }
    init { preferences.registerOnSharedPreferenceChangeListener(listener) }

    @Synchronized fun update(change: (Settings) -> Settings) {
        val next = change(current)
        val json = JSONObject().apply {
            put("enabled", next.enabled); put("cpm", next.timing.cpm); put("auto", next.timing.automatic)
            put("dit", next.timing.ditMs); put("dash", next.timing.dashMs); put("content", next.content.name)
            put("local", next.local); put("watch", next.watch); put("chars", next.maxChars); put("seconds", next.maxSeconds)
            put("cooldown", next.cooldownSeconds); put("ongoing", next.skipOngoing); put("silent", next.skipSilent)
            put("delay",next.startDelayMs)
            put("respectSilent", next.respectSilent); put("saver", next.batterySaver); put("battery", next.lowBatteryPercent)
            put("quiet", next.quiet.enabled); put("start", next.quiet.startMinute); put("end", next.quiet.endMinute)
            put("aliases", JSONObject(next.aliases))
        }
        mutable.value = next
        preferences.edit().putString("settings", json.toString()).putStringSet("apps", next.apps).apply()
    }

    private fun read(): Settings {
        val j = runCatching { JSONObject(preferences.getString("settings", "{}")!!) }.getOrDefault(JSONObject())
        val aliases = j.optJSONObject("aliases")
        return Settings(
            enabled = j.optBoolean("enabled", true), apps = preferences.getStringSet("apps", emptySet())!!.toSet(),
            aliases = aliases?.keys()?.asSequence()?.associateWith { aliases.optString(it) }.orEmpty(),
            timing = Timing(j.optInt("cpm",100).coerceIn(20,300), j.optBoolean("auto",true), j.optInt("dit",60).coerceIn(20,500), j.optInt("dash",180).coerceIn(20,1500)),
            content = runCatching { ContentMode.valueOf(j.optString("content", "TITLE")) }.getOrDefault(ContentMode.TITLE),
            local = j.optBoolean("local", watchDevice), watch = j.optBoolean("watch", !watchDevice),
            maxChars = j.optInt("chars",40).coerceIn(1,120), maxSeconds = j.optInt("seconds",20).coerceIn(5,60),
            cooldownSeconds = j.optInt("cooldown",10).coerceIn(0,120), skipOngoing = j.optBoolean("ongoing",true),
            startDelayMs = j.optInt("delay",500).coerceIn(0,2000),
            skipSilent = j.optBoolean("silent",true), respectSilent = j.optBoolean("respectSilent",true),
            batterySaver = j.optBoolean("saver",true), lowBatteryPercent = j.optInt("battery",15).coerceIn(0,50),
            quiet = QuietHours(j.optBoolean("quiet",false), j.optInt("start",1320).coerceIn(0,1439), j.optInt("end",420).coerceIn(0,1439))
        )
    }

    companion object {
        @Volatile private var instance: SettingsStore? = null
        fun get(context: Context): SettingsStore = instance ?: synchronized(this) {
            instance ?: SettingsStore(context.applicationContext).also { instance = it }
        }
    }
}

fun isWatch(context: Context): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
