package com.saha.upidisplay

import java.text.Normalizer

/** Reads the amount and payer out of a payment notification's text. */
object PaymentParser {
    data class Parsed(val amount: Double, val payer: String)

    private val AMOUNT = Regex("""(?:₹|\brs\.?|\binr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    // Words that mean money came IN.
    private val CREDIT = Regex("""\b(received|credited|paid you|sent you|has paid|collected)\b""", RegexOption.IGNORE_CASE)

    // Words that mean money went OUT, or that it is not a real payment.
    private val NOT_CREDIT = Regex(
        """\b(debited|paid to|sent to|transferred to|you paid|you sent|request|requested|requesting|reminder|cashback|reward|offer|failed|declined|pending|otp|refund|bill due|due on)\b""",
        RegexOption.IGNORE_CASE,
    )

    // "... from Ravi Kumar via UPI" / "from ravi@okaxis"
    private val FROM_NAME = Regex(
        """\b(?:from|by)\s+([\p{L}\p{N}][\p{L}\p{N} .@_'&-]{0,40}?)(?=\s+(?:via|on|to|in|using|at|for|through|and)\b|[.,!|:;]|\s*$)""",
        RegexOption.IGNORE_CASE,
    )

    // "Ravi Kumar paid you ₹100"
    private val PAID_YOU = Regex("""(\p{Lu}[\p{L}.]*(?:\s+\p{Lu}[\p{L}.]*){0,3})\s+(?:has\s+)?(?:paid|sent)\s+you""")

    fun parse(text: String): Parsed? {
        // PhonePe writes amounts in styled digits like 𝟭𝟬; NFKC turns them into plain 10.
        val t = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(Regex("\\p{Cf}"), "") // invisible characters that can sit between digits
            .replace('\n', ' ').trim()
        if (t.isEmpty()) return null
        if (!CREDIT.containsMatchIn(t)) return null
        if (NOT_CREDIT.containsMatchIn(t)) return null
        val m = AMOUNT.find(t) ?: return null
        val amount = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null
        val payer = (FROM_NAME.find(t)?.groupValues?.get(1)
            ?: PAID_YOU.find(t)?.groupValues?.get(1)
            ?: "").trim().trimEnd('.', '-')
        return Parsed(amount, cleanName(payer))
    }

    private fun cleanName(n: String): String {
        if (n.isBlank()) return ""
        // Drop words that are part of the sentence, not the name.
        val bad = setOf("your", "account", "a/c", "upi", "bank")
        val words = n.split(Regex("\\s+")).filter { it.lowercase() !in bad }
        return words.joinToString(" ").take(30)
    }
}
