package dev.hamfist.core

import org.junit.Assert.*
import org.junit.Test

class MorseTest {
    @Test fun sosHasExactPulseAndGapSequence() {
        assertArrayEquals(longArrayOf(0,60,60,60,60,60,180,180,60,180,60,180,180,60,60,60,60,60), Morse.encode("SOS").timings)
    }
    @Test fun wordGapReplacesLetterGap() {
        assertArrayEquals(longArrayOf(0,60,420,60), Morse.encode("E E").timings)
    }
    @Test fun parisCalibrationIncludesTrailingWordGap() {
        val timing = Timing(cpm = 100)
        assertEquals(3000L, Morse.encode("PARIS", timing).durationMs + 7 * timing.spacing)
        assertEquals(100, timing.effectiveCpm)
    }
    @Test fun customPulsesUseFarnsworthSpacingAndCannotExceedPhysicalSpeed() {
        val slow = Timing(30, false, 60, 180)
        assertEquals(60L, slow.dit); assertEquals(180L, slow.dash); assertTrue(slow.spacing > 60)
        val fast = Timing(300, false, 100, 300)
        assertEquals(60, fast.effectiveCpm)
    }
    @Test fun sanitizesUnicodeWithoutJoiningWords() {
        assertEquals("CAFE HELLO 42", Morse.normalize("café 🐈 hello\n42"))
        assertTrue(Morse.encode("🐈").timings.isEmpty())
    }
    @Test fun capsAtWholeCharactersAndNeverEndsInSilence() {
        val result = Morse.encode("TTTTTTTTTT", Timing(), maxDurationMs = 1000)
        assertEquals("TTT", result.text); assertEquals(900L, result.durationMs)
        assertEquals(0, result.timings.size % 2)
        assertEquals("ABC", Morse.encode("abcdef", maxChars = 3).text)
    }
    @Test fun quietHoursSupportMidnightAndExactBoundaries() {
        val q = QuietHours(true)
        assertTrue(q.contains(22*60)); assertTrue(q.contains(0)); assertFalse(q.contains(7*60))
        assertFalse(q.contains(12*60)); assertTrue(QuietHours(true, 0, 0).contains(500))
        assertFalse(QuietHours(false).contains(0))
    }
    @Test fun duplicatesAndBurstUpdatesDoNotBuzzAgain() {
        val gate = DeliveryGate()
        assertTrue(gate.accept("a", "app", 0, 5000))
        assertFalse(gate.accept("a", "app", 6000, 5000))
        assertFalse(gate.accept("b", "app", 1000, 5000))
        assertTrue(gate.accept("c", "other", 1001, 5000))
        assertTrue(gate.accept("d", "app", 6000, 5000))
        assertTrue(gate.accept("a", "app", 120000, 5000))
    }
}
