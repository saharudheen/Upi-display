package com.saha.upidisplay

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Android shows every notification to a listener, so the first thing this does is
 * drop anything that is not from a payment app you picked in Settings.
 * Nothing is stored or sent for other apps. SMS is never read.
 */
class PaymentListenerService : NotificationListenerService() {

    companion object {
        /** Set while Android actually has the listener running. */
        @Volatile var instance: PaymentListenerService? = null
        @Volatile var anyCount = 0
        @Volatile var lastAnyTime = 0L

        /** Re-checks notifications already sitting in the notification shade. */
        fun scanNow(): Int {
            val s = instance ?: return -1
            val list = try { s.activeNotifications } catch (_: Exception) { null } ?: return 0
            list.forEach { s.handle(it) }
            return list.size
        }
    }

    override fun onListenerConnected() {
        instance = this
        anyCount = 0
        Prefs.setStatus(this, "conn", "Connected at " + Ui.time(System.currentTimeMillis()))
        scanNow()
    }

    override fun onListenerDisconnected() {
        instance = null
        Prefs.setStatus(this, "conn", "Disconnected at " + Ui.time(System.currentTimeMillis()) + " — reconnecting")
        // Ask Android to reconnect us (it sometimes drops listeners after an update).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, PaymentListenerService::class.java))
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        anyCount++
        lastAnyTime = System.currentTimeMillis()
        handle(sbn)
    }

    fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        if (!Prefs.isWatched(this, pkg)) return
        val tag = Prefs.tagFor(this, pkg)
        val now = Ui.time(System.currentTimeMillis())
        if (Prefs.displayMode(this)) {
            Prefs.setStatus(this, "seen", "$now $tag — ignored (display mode is on)")
            return // a display phone only shows, it does not listen
        }

        val ex = sbn.notification?.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val text = "$title. $body".trim()

        val parsed = try { PaymentParser.parse(text) } catch (e: Exception) { null }
        if (parsed == null) {
            if (text.length > 2) DebugLog.add(tag, text)
            Prefs.setStatus(this, "seen", "$now $tag — not a payment / not understood")
            return
        }
        Prefs.setStatus(this, "seen", "$now $tag — payment ${Ui.money(parsed.amount)}")

        val payment = Payment(
            id = "${sbn.key}|${sbn.postTime}|${parsed.amount}",
            amount = parsed.amount,
            payer = parsed.payer,
            app = tag,
            time = sbn.postTime,
        )
        if (PaymentStore.add(this, payment) && Prefs.sendToDisplay(this)) {
            DisplayLink.send(this, payment)
        }
    }
}
