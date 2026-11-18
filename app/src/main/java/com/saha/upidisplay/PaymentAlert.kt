package com.saha.upidisplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Brings the payment screen to the front when a new payment arrives. */
object PaymentAlert {
    private const val CHANNEL = "new_payment"
    private const val NOTIF_ID = 4210
    const val EXTRA_NEW = "new_payment"

    fun show(ctx: Context, p: Payment) {
        if (!Prefs.bringToFront(ctx) || MainActivity.visible) return

        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_NEW, true)

        // 1) Open the screen directly. Android allows this when "Display over other apps"
        //    is allowed (on Xiaomi also "Display pop-up windows while running in the background").
        try { ctx.startActivity(open) } catch (_: Exception) {}

        // 2) Backup: a full-screen notification. When the phone is locked or the screen is off,
        //    it turns the screen on and opens the app. When someone is using the phone it shows
        //    as a banner at the top. Silent, because the soundbox already speaks.
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "New payment", NotificationManager.IMPORTANCE_HIGH).apply {
                        setSound(null, null)
                        enableVibration(false)
                    },
                )
            }
            val pi = PendingIntent.getActivity(
                ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val who = p.payer.ifBlank { p.app }
            @Suppress("DEPRECATION")
            val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(ctx, CHANNEL)
                else Notification.Builder(ctx).setPriority(Notification.PRIORITY_HIGH)
            val n = b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("${Ui.money(p.amount)} received")
                .setContentText("$who · ${p.app}")
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setTimeoutAfter(15_000) }
                .build()
            nm.notify(NOTIF_ID, n)
        } catch (_: Exception) {
        }
    }

    fun clear(ctx: Context) {
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
        } catch (_: Exception) {}
    }
}
