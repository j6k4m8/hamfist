package dev.hamfist.shared

import android.content.Context
import android.os.SystemClock
import com.google.android.gms.wearable.*
import dev.hamfist.core.DeliveryGate
import dev.hamfist.core.DeferredDelivery
import org.json.JSONObject
import java.util.UUID

object WatchLink {
    const val PATH = "/hamfist/v1/morse"
    const val CAPABILITY = "hamfist_morse_v1"
    fun send(context: Context, text: String, app: String, notification: Boolean = false, callback: (String) -> Unit = {}) {
        val appContext = context.applicationContext
        val request = DeferredDelivery(System.currentTimeMillis(), SystemClock.elapsedRealtime(), 30000) {
            if (!notification) true else {
                val current = SettingsStore.get(appContext).current
                current.enabled && current.watch && app in current.apps &&
                    AppCatalog.hasAccess(appContext) && Playback.suppression(appContext, current) == null
            }
        }
        if (!request.canDeliver(SystemClock.elapsedRealtime())) { callback("Watch delivery cancelled"); return }
        // Query only on demand; MessageClient never queues old notification text for reconnection.
        Wearable.getCapabilityClient(appContext).getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { capability ->
                // Pause, app deselection, access revocation and quiet hours can change during lookup.
                if (!request.canDeliver(SystemClock.elapsedRealtime())) { callback("Watch delivery cancelled or expired"); return@addOnSuccessListener }
                val node = capability.nodes.sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.id }).firstOrNull()
                if (node == null) { callback("No reachable watch with Hamfist installed"); return@addOnSuccessListener }
                val bytes = JSONObject().put("version",1).put("id",UUID.randomUUID().toString())
                    .put("sent",request.sentAtMillis).put("app",app.take(200)).put("text",text.take(120)).toString().toByteArray(Charsets.UTF_8)
                Wearable.getMessageClient(appContext).sendMessage(node.id, PATH, bytes)
                    .addOnSuccessListener { callback("Sent to ${node.displayName}") }
                    .addOnFailureListener { callback("Watch delivery unavailable") }
            }.addOnFailureListener { callback("Watch connection unavailable") }
    }
    fun status(context: Context, callback: (String) -> Unit) {
        Wearable.getCapabilityClient(context).getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { callback(it.nodes.firstOrNull()?.displayName ?: "No watch connected") }
            .addOnFailureListener { callback("Watch connection unavailable") }
    }
}

class WatchReceiver : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        WatchInbox.receive(this, event.path, event.data)
    }
}

object WatchInbox {
    private val gate = DeliveryGate()
    fun receive(context: Context, path: String, data: ByteArray) {
        if (!isWatch(context) || path != WatchLink.PATH || data.size > 4096) return
        val settings = SettingsStore.get(context).current
        if (!settings.local || Playback.suppression(context,settings) != null) return
        val packet = runCatching { JSONObject(String(data, Charsets.UTF_8)) }.getOrNull() ?: return
        if (packet.optInt("version") != 1) return
        val age = System.currentTimeMillis() - packet.optLong("sent",0)
        if (age !in -5000L..30000L) return
        val id = packet.optString("id"); val app = packet.optString("app")
        if (id.isBlank() || app.isBlank()) return
        if (!gate.accept(id, app, SystemClock.elapsedRealtime(), settings.cooldownSeconds * 1000L)) return
        Playback.play(context, packet.optString("text").take(120))
    }
}
