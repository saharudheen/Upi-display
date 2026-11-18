package com.saha.upidisplay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.TextView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Ui {
    val BG = Color.parseColor("#0F1419")
    val CARD = Color.parseColor("#1C232B")
    val CARD_DIM = Color.parseColor("#151A20")
    val TEXT = Color.parseColor("#E5E7EB")
    val DIM = Color.parseColor("#6B7280")
    val GREEN = Color.parseColor("#34D399")
    val CYAN = Color.parseColor("#22D3EE")
    val RED = Color.parseColor("#B91C1C")
    val RED_TEXT = Color.parseColor("#F87171")

    private val money = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }
    private val clock = SimpleDateFormat("hh:mm a", Locale.ENGLISH)

    fun money(a: Double): String = "₹" + money.format(a)
    fun time(t: Long): String = clock.format(Date(t))

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun text(c: Context, s: String, size: Float, color: Int, bold: Boolean = false) = TextView(c).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    fun button(c: Context, s: String, onClick: () -> Unit) = Button(c).apply {
        text = s
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    fun card(c: Context, color: Int = CARD) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(c, 12).toFloat()
    }
}
