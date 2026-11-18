package com.saha.upidisplay

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    companion object {
        /** True while this screen is in front, so payments don't try to open it again. */
        @Volatile var visible = false
    }

    private lateinit var statusText: TextView
    private lateinit var latestCard: LinearLayout
    private lateinit var latestAmount: TextView
    private lateinit var latestName: TextView
    private lateinit var latestInfo: TextView
    private lateinit var todayText: TextView
    private lateinit var listBox: LinearLayout

    private var receiver: DisplayLink.Receiver? = null
    private var lastTopId: String? = null
    private var blink: ObjectAnimator? = null
    private val onChange: () -> Unit = { refresh() }
    private val ticker = object : Runnable {
        override fun run() {
            refreshStatus()
            statusText.postDelayed(this, 3000)
        }
    }

    private val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
    private val match = LinearLayout.LayoutParams.MATCH_PARENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showOverLockScreen()
        setContentView(buildUi())
        if (Build.VERSION.SDK_INT >= 33 && Prefs.bringToFront(this) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (intent?.getBooleanExtra(PaymentAlert.EXTRA_NEW, false) == true) latestCard.post { startBlink() }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra(PaymentAlert.EXTRA_NEW, false) == true) latestCard.post { startBlink() }
    }

    override fun onResume() {
        super.onResume()
        visible = true
        PaymentAlert.clear(this)
        PaymentStore.addListener(onChange)
        if (Prefs.displayMode(this)) {
            receiver = DisplayLink.Receiver(this).also { it.start() }
        } else {
            PaymentListenerService.scanNow() // pick up anything already in the notification shade
        }
        refresh()
        statusText.postDelayed(ticker, 3000)
    }

    override fun onPause() {
        super.onPause()
        visible = false
        statusText.removeCallbacks(ticker)
        PaymentStore.removeListener(onChange)
        receiver?.stop()
        receiver = null
    }

    private fun showOverLockScreen() {
        if (!Prefs.bringToFront(this)) return
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun lp(w: Int, h: Int, weight: Float = 0f, top: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { topMargin = dp(top) }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(14), dp(10), dp(14), dp(8))
        }

        // Header: title + settings, on one line
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(Ui.text(this, "UPI Payments", 20f, Ui.TEXT, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, lp(0, wrap, 1f))
        top.addView(Ui.text(this, "⚙", 26f, Ui.TEXT).apply {
            setPadding(dp(14), dp(2), dp(4), dp(2))
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
        root.addView(top)

        // One short status line; tap for details
        statusText = Ui.text(this, "", 13f, Ui.GREEN).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener { statusTapped() }
        }
        root.addView(statusText, lp(match, wrap, top = 2))

        latestCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Ui.card(this@MainActivity)
            setPadding(dp(12), dp(10), dp(12), dp(12))
            setOnClickListener { PaymentStore.all(this@MainActivity).firstOrNull()?.let { toggle(it.id) } }
        }
        latestCard.addView(Ui.text(this, "LATEST", 12f, Ui.CYAN, bold = true))
        latestAmount = Ui.text(this, "—", 46f, Ui.GREEN, bold = true).apply { maxLines = 1 }
        latestCard.addView(latestAmount)
        latestName = Ui.text(this, "Waiting for payments…", 18f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        latestCard.addView(latestName)
        latestInfo = Ui.text(this, "", 14f, Ui.DIM).apply { gravity = Gravity.CENTER }
        latestCard.addView(latestInfo)
        root.addView(latestCard, lp(match, wrap, top = 8))

        val totals = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        todayText = Ui.text(this, "", 15f, Ui.TEXT, bold = true).apply { maxLines = 1 }
        totals.addView(todayText, lp(0, wrap, 1f))
        totals.addView(Ui.text(this, "tap = checked", 12f, Ui.DIM))
        root.addView(totals, lp(match, wrap, top = 8))

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(listBox) }, lp(match, 0, 1f, top = 4))
        return root
    }

    private fun refresh() {
        refreshStatus()
        val list = PaymentStore.all(this)
        val first = list.firstOrNull()
        if (first == null) {
            latestAmount.text = "—"
            latestName.text = "Waiting for payments…"
            latestInfo.text = ""
        } else {
            latestAmount.text = Ui.money(first.amount)
            latestAmount.setTextColor(if (first.verified) Ui.DIM else Ui.GREEN)
            latestName.text = first.payer.ifBlank { "UPI payment" }
            latestInfo.text = "${first.app} · ${Ui.time(first.time)}" + if (first.verified) "  ✓ checked" else ""
            if (lastTopId != null && first.id != lastTopId) startBlink()
            lastTopId = first.id
        }

        val (total, count) = PaymentStore.todayTotal(list)
        todayText.text = "Today ${Ui.money(total)} · $count"

        listBox.removeAllViews()
        list.take(10).forEach { listBox.addView(row(it), lp(match, wrap, top = 6)) }
    }

    // ---- status line

    private enum class State { DISPLAY, NO_ACCESS, STOPPED, OK }

    private fun state(): State = when {
        Prefs.displayMode(this) -> State.DISPLAY
        !isListenerEnabled() -> State.NO_ACCESS
        PaymentListenerService.instance == null -> State.STOPPED
        else -> State.OK
    }

    private fun refreshStatus() {
        when (state()) {
            State.DISPLAY -> setStatus("● Display mode · from shop phone", Ui.GREEN)
            State.NO_ACCESS -> setStatus("● Notification access is OFF — tap to fix", Ui.RED_TEXT)
            State.STOPPED -> setStatus("● Listener stopped by phone — tap for help", Ui.RED_TEXT)
            State.OK -> setStatus("● Listening · tap for details", Ui.GREEN)
        }
    }

    private fun setStatus(s: String, color: Int) {
        statusText.text = s
        statusText.setTextColor(color)
    }

    private fun statusTapped() {
        if (state() == State.NO_ACCESS) {
            openAccessSettings()
            return
        }
        val any = PaymentListenerService.anyCount
        val details = buildString {
            append("Listening to: ")
            append(
                Prefs.watched(this@MainActivity).filter { isInstalled(it) }
                    .map { Prefs.tagFor(this@MainActivity, it) }.distinct()
                    .ifEmpty { listOf("no payment app found") }.joinToString(", "),
            )
            append("\n\nListener: ")
            append(
                if (PaymentListenerService.instance != null) "running (" + Prefs.status(this@MainActivity, "conn") + ")"
                else "NOT running",
            )
            append("\n\nNotifications seen (any app): ")
            append(if (any == 0) "none since start" else "$any, last at " + Ui.time(PaymentListenerService.lastAnyTime))
            append("\n\nLast payment-app notification: ")
            append(Prefs.status(this@MainActivity, "seen").ifBlank { "nothing yet" })
            if (state() == State.STOPPED) {
                append("\n\nTo fix: turn on Autostart for UPI Display, set Battery to \"No restrictions\", then turn notification access off and on.")
            }
        }
        AlertDialog.Builder(this)
            .setTitle(if (Prefs.displayMode(this)) "Display mode" else "Listener status")
            .setMessage(if (Prefs.displayMode(this)) "This phone shows the shop phone's payments over Wi-Fi. It updates every 5 seconds." else details)
            .setPositiveButton("OK", null)
            .apply { if (state() == State.STOPPED) setNeutralButton("Notification access") { _, _ -> openAccessSettings() } }
            .show()
    }

    // ---- list rows: amount + name on the left, time + app on the right

    private fun row(p: Payment): View {
        val checked = p.verified
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.card(this@MainActivity, if (checked) Ui.CARD_DIM else Ui.CARD)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { toggle(p.id) }
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(Ui.text(this, Ui.money(p.amount), 22f, if (checked) Ui.DIM else Ui.GREEN, bold = true).apply { maxLines = 1 })
        left.addView(Ui.text(this, p.payer.ifBlank { "—" }, 14f, if (checked) Ui.DIM else Ui.TEXT).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        r.addView(left, lp(0, wrap, 1f))

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        right.addView(Ui.text(this, Ui.time(p.time), 13f, if (checked) Ui.DIM else Ui.TEXT).apply { maxLines = 1 })
        right.addView(Ui.text(this, if (checked) "✓ ${p.app}" else p.app, 12f, if (checked) Ui.DIM else Ui.CYAN).apply { maxLines = 1 })
        r.addView(right, lp(wrap, wrap).apply { leftMargin = dp(8) })
        return r
    }

    private fun toggle(id: String) = PaymentStore.toggleVerified(this, id)

    private fun startBlink() {
        blink?.cancel()
        blink = ObjectAnimator.ofFloat(latestCard, "alpha", 1f, 0.25f).apply {
            duration = 450
            repeatCount = 21 // about 10 seconds
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        latestCard.postDelayed({ latestCard.alpha = 1f }, 10_500)
    }

    private fun isInstalled(pkg: String) = try {
        packageManager.getApplicationInfo(pkg, 0); true
    } catch (_: Exception) {
        false
    }

    private fun isListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(packageName)
    }

    private fun openAccessSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this, PaymentListenerService::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }
}
