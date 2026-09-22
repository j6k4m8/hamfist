package dev.hamfist.wear

import android.app.NotificationManager
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.hamfist.shared.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Tests the production receiver handler with synthetic Data Layer payloads, not radio transport. */
@RunWith(AndroidJUnit4::class)
class ScreenOffDeliveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
    private fun records(): Set<String> = shell("dumpsys vibrator_manager").lineSequence()
        .filter { "dev.hamfist" in it && "COMMUNICATION_REQUEST" in it }.toSet()
    private fun hasHandoffLock(): Boolean {
        val dump=shell("dumpsys power")
        assertTrue("Power dump must expose active wake locks",dump.contains("Wake Locks:"))
        // The full dump also contains ACQ/REL history, which must not count as a current hold.
        return dump.substringAfter("Wake Locks:").substringBefore("Suspend Blockers:").contains("hamfist:morse-handoff")
    }
    private fun payload(id: String=UUID.randomUUID().toString(), age: Long=0) = JSONObject()
        .put("version",1).put("id",id).put("app","hamfist.integration.test")
        .put("sent",System.currentTimeMillis()-age).put("text","SOS").toString().toByteArray()

    @Test fun validMessageCompletesWithoutWakingScreenAndInvalidMessagesStaySilent() {
        assertTrue("Run on Wear OS",isWatch(context))
        val store=SettingsStore.get(context)
        val original=store.current
        val power=context.getSystemService(PowerManager::class.java)
        try {
            store.update { Settings(local=true,watch=false,cooldownSeconds=0,batterySaver=false,lowBatteryPercent=0,respectSilent=false) }
            assertEquals("Disable DND before this test",NotificationManager.INTERRUPTION_FILTER_ALL,context.getSystemService(NotificationManager::class.java).currentInterruptionFilter)
            shell("input keyevent KEYCODE_HOME")
            shell("input keyevent KEYCODE_SLEEP")
            Thread.sleep(500)
            assertFalse("Screen must start asleep",power.isInteractive)
            val before=records()
            val packet=payload()
            WatchInbox.receive(context,WatchLink.PATH,packet)
            Thread.sleep(3000)
            assertFalse("Morse must not wake the display",power.isInteractive)
            val added=records()-before
            assertTrue("Vibration must finish while the screen stays off: $added",added.any { "finished" in it })
            val finished=records()
            WatchInbox.receive(context,WatchLink.PATH,packet) // duplicate ID
            WatchInbox.receive(context,WatchLink.PATH,payload(age=31000))
            WatchInbox.receive(context,WatchLink.PATH,payload(age=-60000))
            WatchInbox.receive(context,"/wrong-path",payload())
            WatchInbox.receive(context,WatchLink.PATH,"{broken".toByteArray())
            WatchInbox.receive(context,WatchLink.PATH,ByteArray(4097))
            // Wait past BOTH the 500 ms handoff and the complete SOS waveform. A shorter wait
            // would let an incorrectly accepted packet hide in the pending callback until cleanup.
            Thread.sleep(3000)
            assertEquals("Invalid/stale/replayed packets must not vibrate",finished,records())
            assertFalse(power.isInteractive)
        } finally {
            Playback.stop(context)
            store.update { original }
            shell("input keyevent KEYCODE_WAKEUP")
        }
    }

    @Test fun busyUiDoesNotDelayPlaybackOrCpuHoldRelease() {
        val store=SettingsStore.get(context)
        val original=store.current
        try {
            store.update { Settings(local=true,watch=false,batterySaver=false,lowBatteryPercent=0,respectSilent=false,startDelayMs=100) }
            val before=records()
            instrumentation.runOnMainSync {
                assertEquals("Morse scheduled",Playback.play(context,"SOS"))
                Thread.sleep(2700) // Keep the UI blocked longer than the entire SOS + handoff.
            }
            assertTrue("Playback must finish despite the blocked UI",(records()-before).any { "finished" in it })
            assertFalse("CPU hold must be released",hasHandoffLock())
        } finally { Playback.stop(context); store.update { original } }
    }

    @Test fun stoppingAPendingSignalCancelsPlaybackAndReleasesCpuHold() {
        val store=SettingsStore.get(context)
        val original=store.current
        try {
            store.update { Settings(local=true,watch=false,batterySaver=false,lowBatteryPercent=0,respectSilent=false,startDelayMs=500) }
            val before=records()
            instrumentation.runOnMainSync {
                assertEquals("Morse scheduled",Playback.play(context,"SOS"))
                Playback.stop(context)
            }
            Thread.sleep(3000)
            assertEquals(before,records())
            assertFalse("Stopping must release the CPU hold",hasHandoffLock())
        } finally { Playback.stop(context); store.update { original } }
    }
}
