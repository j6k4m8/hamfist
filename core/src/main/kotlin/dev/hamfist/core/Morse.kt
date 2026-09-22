package dev.hamfist.core

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

data class Timing(val cpm: Int = 100, val automatic: Boolean = true, val ditMs: Int = 60, val dashMs: Int = 180) {
    val dit: Long get() = if (automatic) (6000.0 / cpm.coerceIn(20, 300)).roundToLong() else ditMs.coerceIn(20, 500).toLong()
    val dash: Long get() = if (automatic) dit * 3 else dashMs.coerceIn(20, 1500).toLong()
    // PARIS: 19 dit units (including intra-symbol spaces), 4 dashes, 19 spacing units.
    // Custom pulses retain their duration; Farnsworth spacing slows the overall cadence.
    val spacing: Long get() = max(dit, ((300000.0 / cpm.coerceIn(20, 300) - 19 * dit - 4 * dash) / 19).roundToLong())
    val effectiveCpm: Int get() = (300000.0 / (19 * dit + 4 * dash + 19 * spacing)).toInt()
}

data class Pattern(val text: String, val timings: LongArray, val morse: String) {
    val durationMs: Long get() = timings.sum()
    val activeMs: Long get() = timings.filterIndexed { i, _ -> i % 2 == 1 }.sum()
}

object Morse {
    val alphabet = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.",
        'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
        'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.",
        'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
        'Y' to "-.--", 'Z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---",
        '3' to "...--", '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...",
        '8' to "---..", '9' to "----.", '.' to ".-.-.-", ',' to "--..--", '?' to "..--..",
        '!' to "-.-.--", '/' to "-..-.", '@' to ".--.-.", ':' to "---...", '-' to "-....-",
        '(' to "-.--.", ')' to "-.--.-", '=' to "-...-", '+' to ".-.-.", '\'' to ".----."
    )

    fun normalize(input: String): String = Normalizer.normalize(input.take(4096), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").uppercase(Locale.ROOT)
        .map { if (it in alphabet) it else ' ' }.joinToString("").trim().replace(Regex(" +"), " ")

    fun encode(input: String, timing: Timing = Timing(), maxChars: Int = 40, maxDurationMs: Long = 20000): Pattern {
        val source = normalize(input).take(maxChars.coerceIn(1, 120)).trim()
        val wave = mutableListOf(0L)
        val text = StringBuilder()
        val codes = mutableListOf<String>()
        var total = 0L
        var wordBreak = false
        for (char in source) {
            if (char == ' ') { wordBreak = true; continue }
            val code = alphabet.getValue(char)
            val gap = if (text.isEmpty()) 0L else timing.spacing * if (wordBreak) 7 else 3
            val pulses = code.map { if (it == '.') timing.dit else timing.dash }
            val length = gap + pulses.sum() + (pulses.size - 1) * timing.dit
            // Truncate only at a character boundary; a chopped dash would change the message.
            if (total + length > maxDurationMs.coerceIn(1000, 60000)) break
            if (text.isNotEmpty()) { wave.add(gap); if (wordBreak) { text.append(' '); codes.add("/") } }
            pulses.forEachIndexed { i, pulse -> if (i > 0) wave.add(timing.dit); wave.add(pulse) }
            text.append(char); codes.add(code); total += length; wordBreak = false
        }
        return Pattern(text.toString(), if (text.isEmpty()) longArrayOf() else wave.toLongArray(), codes.joinToString(" "))
    }
}

data class QuietHours(val enabled: Boolean = false, val startMinute: Int = 22 * 60, val endMinute: Int = 7 * 60) {
    fun contains(minute: Int): Boolean {
        if (!enabled) return false
        if (startMinute == endMinute) return true
        return if (startMinute < endMinute) minute in startMinute until endMinute
        else minute >= startMinute || minute < endMinute
    }
}

/** Bounded RAM only. Monotonic time, no notification text or history persisted. */
class DeliveryGate {
    private val seen = LinkedHashMap<String, Long>()
    private val lastApp = LinkedHashMap<String, Long>()
    @Synchronized fun accept(id: String, app: String, now: Long, cooldownMs: Long, dedupeMs: Long = 120000): Boolean {
        seen.entries.removeAll { now - it.value >= dedupeMs }
        if (seen.containsKey(id)) return false
        seen[id] = now
        while (seen.size > 256) seen.remove(seen.keys.first())
        val previous = lastApp[app]
        if (previous != null && now - previous < cooldownMs) return false
        lastApp[app] = now
        while (lastApp.size > 128) lastApp.remove(lastApp.keys.first())
        return true
    }
}
