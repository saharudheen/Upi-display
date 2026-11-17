package com.saha.upidisplay

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs

data class Payment(
    val id: String,
    val amount: Double,
    val payer: String,
    val app: String,
    val time: Long,
    val verified: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("amt", amount).put("name", payer)
        .put("app", app).put("ts", time).put("v", verified)

    companion object {
        fun fromJson(o: JSONObject) = Payment(
            id = o.getString("id"),
            amount = o.getDouble("amt"),
            payer = o.optString("name", ""),
            app = o.optString("app", ""),
            time = o.optLong("ts", System.currentTimeMillis()),
            verified = o.optBoolean("v", false),
        )
    }
}

/** Keeps the latest payments on the phone and tells the screen when they change. */
object PaymentStore {
    private const val MAX = 50
    private const val PREFS = "payments"
    private val items = mutableListOf<Payment>()
    private var loaded = false
    private val listeners = mutableSetOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(l: () -> Unit) { synchronized(listeners) { listeners.add(l) } }
    fun removeListener(l: () -> Unit) { synchronized(listeners) { listeners.remove(l) } }

    @Synchronized
    fun all(ctx: Context): List<Payment> {
        load(ctx)
        return items.toList()
    }

    /** Returns false when the payment was already recorded. */
    @Synchronized
    fun add(ctx: Context, p: Payment): Boolean {
        load(ctx)
        if (items.any { it.id == p.id || isRepeat(it, p) }) return false
        items.add(0, p)
        items.sortByDescending { it.time }
        while (items.size > MAX) items.removeAt(items.size - 1)
        save(ctx)
        changed()
        return true
    }

    @Synchronized
    fun toggleVerified(ctx: Context, id: String) {
        load(ctx)
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        items[i] = items[i].copy(verified = !items[i].verified)
        save(ctx)
        changed()
    }

    @Synchronized
    fun clear(ctx: Context) {
        load(ctx)
        items.clear()
        save(ctx)
        changed()
    }

    fun todayTotal(list: List<Payment>): Pair<Double, Int> {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val today = list.filter { it.time >= c.timeInMillis }
        return today.sumOf { it.amount } to today.size
    }

    /** The same app re-posting the same payment within a few seconds. */
    private fun isRepeat(a: Payment, b: Payment) =
        a.app == b.app && a.amount == b.amount &&
            a.payer.equals(b.payer, ignoreCase = true) &&
            abs(a.time - b.time) < 15_000

    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) items.add(Payment.fromJson(arr.getJSONObject(i)))
        } catch (_: Exception) {
        }
    }

    private fun save(ctx: Context) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", arr.toString()).apply()
    }

    private fun changed() {
        val copy = synchronized(listeners) { listeners.toList() }
        main.post { copy.forEach { it() } }
    }
}

/** Notifications from watched apps that did not look like a payment. Kept in memory only. */
object DebugLog {
    private val items = ArrayDeque<String>()

    @Synchronized
    fun add(app: String, text: String) {
        items.addFirst("[$app] $text")
        while (items.size > 20) items.removeLast()
    }

    @Synchronized
    fun all(): List<String> = items.toList()
}
