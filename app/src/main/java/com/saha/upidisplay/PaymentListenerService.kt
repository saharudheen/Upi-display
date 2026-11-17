package com.saha.upidisplay

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Android shows every notification to a listener, so the first thing this does is
 * drop anything that is not from a payment app you picked in Settings.
 * Nothing is stored or sent for other apps. SMS is never read.
 */
class PaymentListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        if (!Prefs.isWatched(this, pkg)) return
        if (Prefs.displayMode(this)) return // a display phone only shows, it does not listen

        val ex = sbn.notification?.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val text = "$title. $body".trim()

        val tag = Prefs.tagFor(this, pkg)
        val parsed = PaymentParser.parse(text)
        if (parsed == null) {
            if (text.length > 2) DebugLog.add(tag, text)
            return
        }

        val payment = Payment(
            id = "${sbn.key}|${sbn.postTime}|${parsed.amount}",
            amount = parsed.amount,
            payer = parsed.payer,
            app = tag,
            time = System.currentTimeMillis(),
        )
        if (PaymentStore.add(this, payment) && Prefs.sendToDisplay(this)) {
            DisplayLink.send(this, payment)
        }
    }
}
