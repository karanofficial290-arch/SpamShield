package com.spamshield

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.provider.CallLog

/**
 * Block hui call ko phone ki call history (dial log) se delete karta hai.
 * Android normally block hui call log me nahi daalta, lekin kai phone (Samsung, Xiaomi, Vivo, Oppo...)
 * phir bhi "Blocked" entry daal dete hain. Yeh class us entry ko dhoondh ke hata deti hai.
 */
object CallLogCleaner {

    private val handler: Handler by lazy {
        Handler(HandlerThread("calllog-cleaner").apply { start() }.looper)
    }

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    /**
     * Phone call-log me entry thodi der baad likhta hai, isliye kai baar check karte hain.
     */
    fun scheduleDelete(ctx: Context, number: String?) {
        val app = ctx.applicationContext
        val since = System.currentTimeMillis() - 60_000
        listOf(1_500L, 4_000L, 10_000L, 25_000L, 60_000L).forEach { delay ->
            handler.postDelayed({ deleteMatching(app, number, since, Long.MAX_VALUE) }, delay)
        }
    }

    /** App khulne pe: jo bhi block hui calls log me reh gayi hon, sab saaf. Returns kitni hatayi. */
    fun cleanAll(ctx: Context, entries: List<Store.Entry>): Int {
        var removed = 0
        entries.forEach { e ->
            val num = if (e.number == "Hidden") null else e.number
            removed += deleteMatching(ctx, num, e.time - 2 * 60_000, e.time + 3 * 60_000)
        }
        return removed
    }

    private fun deleteMatching(ctx: Context, number: String?, from: Long, to: Long): Int {
        if (!hasPermission(ctx)) return 0
        return try {
            val target = number?.let { Rules.national(it) }.orEmpty()
            val ids = mutableListOf<Long>()
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.NUMBER_PRESENTATION, CallLog.Calls.TYPE),
                "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} <= ?",
                arrayOf(from.toString(), to.toString()),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(3)
                    if (type == CallLog.Calls.OUTGOING_TYPE) continue // apni ki hui call kabhi mat chhedo
                    val rowNum = c.getString(1).orEmpty()
                    val presentation = c.getInt(2)
                    val match = if (target.isEmpty()) {
                        presentation != CallLog.Calls.PRESENTATION_ALLOWED || Rules.national(rowNum).isEmpty()
                    } else {
                        Rules.national(rowNum) == target
                    }
                    if (match) ids.add(c.getLong(0))
                }
            }
            if (ids.isEmpty()) 0 else ctx.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} IN (${ids.joinToString(",")})",
                null
            )
        } catch (e: Exception) {
            0
        }
    }
}
