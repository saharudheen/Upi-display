package com.saha.upidisplay

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
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
import kotlin.random.Random

class MainActivity : Activity() {
    private lateinit var banner: TextView
    private lateinit var modeText: TextView
    private lateinit var latestCard: LinearLayout
    private lateinit var latestAmount: TextView
    private lateinit var latestInfo: TextView
    private lateinit var todayText: TextView
    private lateinit var listBox: LinearLayout

    private var receiver: DisplayLink.Receiver? = null
    private var lastTopId: String? = null
    private var blink: ObjectAnimator? = null
    private val onChange: () -> Unit = { refresh() }
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            modeText.postDelayed(this, 2000)
        }
    }

    private val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
    private val match = LinearLayout.LayoutParams.MATCH_PARENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        PaymentStore.addListener(onChange)
        if (Prefs.displayMode(this)) {
            receiver = DisplayLink.Receiver(this).also { it.start() }
        }
        refresh()
        modeText.postDelayed(ticker, 2000)
    }

    override fun onPause() {
        super.onPause()
        modeText.removeCallbacks(ticker)
        PaymentStore.removeListener(onChange)
        receiver?.stop()
        receiver = null
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun lp(w: Int, h: Int, weight: Float = 0f, top: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { topMargin = dp(top) }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(14), dp(14), dp(14), dp(10))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(Ui.text(this, "UPI Payments", 22f, Ui.TEXT, bold = true), lp(0, wrap, 1f))
        top.addView(Ui.button(this, "Scan") { scan() })
        top.addView(Ui.button(this, "Test") { testPayment() })
        top.addView(Ui.button(this, "Settings") {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        root.addView(top)

        modeText = Ui.text(this, "", 13f, Ui.DIM)
        root.addView(modeText, lp(match, wrap, top = 2))

        banner = Ui.text(this, "Notification access is OFF. Tap here to allow it.", 15f, Color.WHITE, bold = true).apply {
            background = Ui.card(this@MainActivity, Ui.RED)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { openAccessSettings() }
        }
        root.addView(banner, lp(match, wrap, top = 8))

        latestCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Ui.card(this@MainActivity)
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setOnClickListener { PaymentStore.all(this@MainActivity).firstOrNull()?.let { toggle(it.id) } }
        }
        latestCard.addView(Ui.text(this, "LATEST", 12f, Ui.CYAN, bold = true))
        latestAmount = Ui.text(this, "—", 46f, Ui.GREEN, bold = true)
        latestCard.addView(latestAmount)
        latestInfo = Ui.text(this, "Waiting for payments…", 16f, Ui.TEXT).apply { gravity = Gravity.CENTER }
        latestCard.addView(latestInfo)
        root.addView(latestCard, lp(match, wrap, top = 10))

        todayText = Ui.text(this, "", 15f, Ui.TEXT, bold = true)
        root.addView(todayText, lp(match, wrap, top = 10))
        root.addView(Ui.text(this, "Last 10 · tap a payment to mark it checked", 13f, Ui.DIM), lp(match, wrap, top = 2))

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(listBox) }
        root.addView(scroll, lp(match, 0, 1f, top = 6))
        return root
    }

    private fun refresh() {
        val listening = isListenerEnabled()
        val display = Prefs.displayMode(this)
        banner.visibility = if (!display && !listening) View.VISIBLE else View.GONE
        modeText.text = when {
            display -> "Display mode: showing payments from the shop phone (same Wi-Fi)"
            listening -> "Listening to: " + Prefs.watched(this)
                .filter { isInstalled(it) }
                .map { Prefs.tagFor(this, it) }.distinct()
                .ifEmpty { listOf("no payment app found — pick one in Settings") }
                .joinToString(", ")
            else -> ""
        } + if (!display) {
            val live = PaymentListenerService.instance != null
            val conn = if (live) "RUNNING (" + Prefs.status(this, "conn") + ")"
                else "NOT RUNNING — phone stopped it (check Autostart / battery)"
            val any = PaymentListenerService.anyCount
            val anyLine = if (any == 0) "none since start" else "$any, last at " + Ui.time(PaymentListenerService.lastAnyTime)
            val seen = Prefs.status(this, "seen").ifBlank { "nothing yet" }
            "\nListener: $conn\nNotifications seen (any app): $anyLine\nLast payment-app notification: $seen"
        } else ""

        val list = PaymentStore.all(this)
        val first = list.firstOrNull()
        if (first == null) {
            latestAmount.text = "—"
            latestInfo.text = "Waiting for payments…"
        } else {
            latestAmount.text = Ui.money(first.amount)
            latestAmount.setTextColor(if (first.verified) Ui.DIM else Ui.GREEN)
            val who = first.payer.ifBlank { "UPI payment" }
            latestInfo.text = "$who\n${first.app} · ${Ui.time(first.time)}" + if (first.verified) "  ✓ checked" else ""
            if (lastTopId != null && first.id != lastTopId) startBlink()
            lastTopId = first.id
        }

        val (total, count) = PaymentStore.todayTotal(list)
        todayText.text = "Today: ${Ui.money(total)}  ($count payments)"

        listBox.removeAllViews()
        list.take(10).forEach { listBox.addView(row(it), lp(match, wrap, top = 6)) }
    }

    private fun row(p: Payment): View {
        val color = if (p.verified) Ui.DIM else Ui.TEXT
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.card(this@MainActivity, if (p.verified) Ui.CARD_DIM else Ui.CARD)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { toggle(p.id) }
        }
        r.addView(Ui.text(this, Ui.time(p.time), 14f, color), lp(dp(76), wrap))
        r.addView(
            Ui.text(this, Ui.money(p.amount), 20f, if (p.verified) Ui.DIM else Ui.GREEN, bold = true),
            lp(dp(112), wrap),
        )
        r.addView(Ui.text(this, p.payer.ifBlank { "—" }, 15f, color).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, lp(0, wrap, 1f))
        r.addView(Ui.text(this, if (p.verified) "✓ ${p.app}" else p.app, 12f, if (p.verified) Ui.DIM else Ui.CYAN))
        return r
    }

    private fun scan() {
        val n = PaymentListenerService.scanNow()
        val msg = if (n < 0) "Listener is not running, so nothing can be read. Check Autostart and notification access."
            else "Checked $n notifications in the notification shade."
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        refresh()
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

    private fun testPayment() {
        val names = listOf("Test Customer", "Ravi Kumar", "Priya", "Murugan S")
        val p = Payment(
            id = "test-" + System.currentTimeMillis(),
            amount = (Random.nextInt(1, 100) * 10).toDouble(),
            payer = names.random(),
            app = "TEST",
            time = System.currentTimeMillis(),
        )
        PaymentStore.add(this, p)
        if (Prefs.sendToDisplay(this) && !Prefs.displayMode(this)) DisplayLink.send(this, p)
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
