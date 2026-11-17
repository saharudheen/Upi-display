package com.saha.upidisplay

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch

class SettingsActivity : Activity() {
    private val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
    private val match = LinearLayout.LayoutParams.MATCH_PARENT

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(top) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(16), dp(16), dp(16), dp(40))
        }
        root.addView(Ui.text(this, "Settings", 22f, Ui.TEXT, bold = true))

        // ---- This phone
        section(root, "This phone")
        root.addView(switch("Send payments to the display over Wi-Fi", Prefs.sendToDisplay(this)) {
            Prefs.setSendToDisplay(this, it)
        }, lp(4))
        root.addView(switch(
            "Display mode — this phone only SHOWS payments sent from the shop phone on the same Wi-Fi",
            Prefs.displayMode(this),
        ) { Prefs.setDisplayMode(this, it) }, lp(8))

        root.addView(Ui.text(this, "Display IP (optional). Leave empty to send to every device on the Wi-Fi.", 13f, Ui.DIM), lp(12))
        root.addView(EditText(this).apply {
            setText(Prefs.displayIp(this@SettingsActivity))
            hint = "e.g. 192.168.1.50"
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.DIM)
            inputType = InputType.TYPE_CLASS_TEXT
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { Prefs.setDisplayIp(this@SettingsActivity, s?.toString() ?: "") }
            })
        }, lp(2))

        // ---- Apps
        section(root, "Payment apps to watch")
        root.addView(Ui.text(
            this,
            "Only notifications from ticked apps are read. Everything else, including SMS and bank apps, is ignored.",
            13f, Ui.DIM,
        ), lp(2))
        val watched = Prefs.watched(this).toMutableSet()
        val pm = packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        ).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != packageName }
            .sortedWith(compareBy({ it.first !in watched }, { it.second.lowercase() }))
        apps.forEach { (pkg, label) ->
            root.addView(CheckBox(this).apply {
                text = label
                setTextColor(Ui.TEXT)
                isChecked = pkg in watched
                setOnCheckedChangeListener { _, on ->
                    if (on) watched.add(pkg) else watched.remove(pkg)
                    Prefs.setWatched(this@SettingsActivity, watched)
                }
            }, lp(0))
        }

        // ---- Unrecognised
        section(root, "Notifications not understood")
        val log = DebugLog.all()
        root.addView(Ui.text(
            this,
            if (log.isEmpty()) "None yet. If a real payment does not appear, it will show here."
            else log.joinToString("\n\n"),
            13f, if (log.isEmpty()) Ui.DIM else Ui.TEXT,
        ), lp(4))
        if (log.isNotEmpty()) {
            root.addView(Ui.button(this, "Share these so the reader can be fixed") {
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, log.joinToString("\n\n")),
                    "Share",
                ))
            }, lp(6))
        }

        // ---- Data
        section(root, "Data")
        root.addView(Ui.button(this, "Clear all payments on this phone") {
            AlertDialog.Builder(this)
                .setMessage("Remove all payments from this phone's list?")
                .setPositiveButton("Clear") { _, _ -> PaymentStore.clear(this) }
                .setNegativeButton("Cancel", null)
                .show()
        }, lp(4))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            addView(root)
        })
    }

    private fun section(root: LinearLayout, title: String) {
        root.addView(Ui.text(this, title.uppercase(), 13f, Ui.CYAN, bold = true), lp(22))
    }

    private fun switch(label: String, on: Boolean, change: (Boolean) -> Unit): View = Switch(this).apply {
        text = label
        setTextColor(Ui.TEXT)
        textSize = 15f
        isChecked = on
        setOnCheckedChangeListener { _, v -> change(v) }
    }
}
