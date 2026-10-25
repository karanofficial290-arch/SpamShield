package com.spamshield

/** Decide karta hai ki call block karni hai ya nahi. null = call aane do. */
object Rules {

    private const val REPEAT_WINDOW_MS = 5 * 60 * 1000L

    /** "+91 98765-43210", "098765 43210", "919876543210" -> "9876543210" */
    fun national(raw: String): String {
        var d = raw.filter { it.isDigit() }
        if (d.length == 12 && d.startsWith("91")) d = d.substring(2)
        else if (d.length == 11 && d.startsWith("0")) d = d.substring(1)
        return d
    }

    /** Pattern: pura number, ya "98765*" jaisa prefix (star = aage kuch bhi). */
    fun matches(pattern: String, raw: String): Boolean {
        val num = national(raw)
        if (num.isEmpty()) return false
        val p = pattern.trim()
        return if (p.endsWith("*")) {
            val body = p.dropLast(1).trim()
            val digits = body.filter { it.isDigit() }
            if (digits.isEmpty()) return false
            if (body.startsWith("+") && !body.startsWith("+91")) {
                // Foreign country code pattern, e.g. "+92*"
                isInternational(raw) && raw.filter { it.isDigit() }.removePrefix("00").startsWith(digits)
            } else {
                val prefix = if (body.startsWith("+91")) digits.removePrefix("91") else digits
                prefix.isNotEmpty() && num.startsWith(prefix)
            }
        } else {
            val pn = national(p)
            pn.isNotEmpty() && (num == pn || (pn.length >= 10 && num.endsWith(pn.takeLast(10))))
        }
    }

    fun isInternational(raw: String): Boolean {
        val r = raw.trim()
        return (r.startsWith("+") && !r.startsWith("+91")) || (r.startsWith("00") && !r.startsWith("0091"))
    }

    fun decide(store: Store, raw: String?, inContacts: Boolean): String? {
        val number = raw?.trim().orEmpty()

        if (number.isNotEmpty() && store.allowList().any { matches(it, number) }) return null
        if (inContacts) return null

        if (number.isEmpty() || national(number).isEmpty()) {
            return if (store.blockHidden) "Hidden / private number" else null
        }
        if (store.blockList().any { matches(it, number) }) return "Aapki blocklist"
        if (store.block140 && national(number).startsWith("140")) return "Telemarketing (140 series)"
        if (store.blockInternational && isInternational(number)) return "International number"

        if (store.blockUnknown) {
            if (store.allowRepeatCaller) {
                val now = System.currentTimeMillis()
                val calledJustNow = store.log().any {
                    now - it.time < REPEAT_WINDOW_MS && national(it.number) == national(number)
                }
                // Same anjaan number 5 min me dobara call kare = shayad emergency, aane do
                if (calledJustNow) return null
            }
            return "Unknown number (contacts me nahi)"
        }
        return null
    }
}
