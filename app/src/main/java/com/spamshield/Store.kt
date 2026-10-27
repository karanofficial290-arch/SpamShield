package com.spamshield

import android.content.Context
import android.content.SharedPreferences

/** Saari settings, blocklist, allowlist aur blocked-call log yahan save hote hain. */
class Store(context: Context) {
    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("spamshield", Context.MODE_PRIVATE)

    // ---- Switches ----
    var block140: Boolean
        get() = p.getBoolean("block140", true)
        set(v) = p.edit().putBoolean("block140", v).apply()

    var blockHidden: Boolean
        get() = p.getBoolean("blockHidden", true)
        set(v) = p.edit().putBoolean("blockHidden", v).apply()

    var blockInternational: Boolean
        get() = p.getBoolean("blockIntl", false)
        set(v) = p.edit().putBoolean("blockIntl", v).apply()

    var blockUnknown: Boolean
        get() = p.getBoolean("blockUnknown", false)
        set(v) = p.edit().putBoolean("blockUnknown", v).apply()

    var allowRepeatCaller: Boolean
        get() = p.getBoolean("allowRepeat", true)
        set(v) = p.edit().putBoolean("allowRepeat", v).apply()

    var cleanCallLog: Boolean
        get() = p.getBoolean("cleanCallLog", true)
        set(v) = p.edit().putBoolean("cleanCallLog", v).apply()

    // ---- Lists ----
    fun blockList(): List<String> = p.getStringSet("blockList", emptySet())!!.sorted()
    fun allowList(): List<String> = p.getStringSet("allowList", emptySet())!!.sorted()

    fun addBlock(s: String) = edit("blockList") { it.add(s.trim()) }
    fun removeBlock(s: String) = edit("blockList") { it.remove(s) }
    fun addAllow(s: String) = edit("allowList") { it.add(s.trim()) }
    fun removeAllow(s: String) = edit("allowList") { it.remove(s) }

    private fun edit(key: String, f: (MutableSet<String>) -> Unit) {
        val set = HashSet(p.getStringSet(key, emptySet())!!)
        f(set)
        p.edit().putStringSet(key, set).apply()
    }

    // ---- Blocked call log ----
    data class Entry(val time: Long, val number: String, val reason: String)

    fun log(): List<Entry> =
        p.getString("log", "")!!.lines().filter { it.isNotBlank() }.mapNotNull {
            val parts = it.split("|", limit = 3)
            if (parts.size == 3) Entry(parts[0].toLongOrNull() ?: 0L, parts[1], parts[2]) else null
        }

    @Synchronized
    fun addLog(number: String, reason: String) {
        val line = "${System.currentTimeMillis()}|${number.replace("|", "")}|$reason"
        val all = (listOf(line) + p.getString("log", "")!!.lines().filter { it.isNotBlank() }).take(300)
        p.edit().putString("log", all.joinToString("\n")).putInt("total", totalBlocked() + 1).apply()
    }

    fun clearLog() = p.edit().putString("log", "").apply()
    fun totalBlocked(): Int = p.getInt("total", 0)
}
