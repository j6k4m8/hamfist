package dev.hamfist.shared

import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.hamfist.core.DeliveryGate
import dev.hamfist.core.Morse
import java.security.MessageDigest

class NotificationRelay : NotificationListenerService() {
    private val gate = DeliveryGate()
    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val s = SettingsStore.get(this).current
        if (!s.enabled || sbn.packageName == packageName || sbn.packageName !in s.apps) return
        if (!s.local && (!s.watch || isWatch(this))) return
        if (Playback.suppression(this,s) != null) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (s.skipOngoing && (sbn.isOngoing || n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)) return
        val ranking = Ranking()
        if (rankingMap.getRanking(sbn.key, ranking)) {
            if (ranking.isSuspended) return
            if (s.skipSilent && ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) return
        }
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val messages = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val latest = runCatching { Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages).lastOrNull()?.text?.toString() }.getOrNull()
        val body = latest ?: n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val label = s.aliases[sbn.packageName]?.takeIf { it.isNotBlank() } ?: runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName,0)).toString()
        }.getOrDefault(sbn.packageName.substringAfterLast('.'))
        val raw = when (s.content) {
            ContentMode.APP -> label
            ContentMode.TITLE -> title.ifBlank { label }
            ContentMode.MESSAGE -> body.ifBlank { title.ifBlank { label } }
            ContentMode.TITLE_MESSAGE -> listOf(title,body).filter { it.isNotBlank() }.distinct().joinToString(" ").ifBlank { label }
        }
        val text = Morse.normalize(raw).take(s.maxChars)
        if (text.isBlank()) return
        // Hash content for RAM dedupe. Include actual title/body even in app-name mode so distinct
        // messages sharing an Android notification key can still alert after the cooldown.
        val digest = MessageDigest.getInstance("SHA-256").digest("${sbn.key}|$title|$body".toByteArray()).joinToString("") { "%02x".format(it) }
        if (!gate.accept(digest, sbn.packageName, SystemClock.elapsedRealtime(), s.cooldownSeconds * 1000L)) return
        if (s.local) Playback.play(this,text,sourceApp=sbn.packageName)
        if (s.watch && !isWatch(this)) WatchLink.send(this,text,sbn.packageName,notification=true)
    }
}
