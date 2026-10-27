package com.spamshield

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private val roleReq = 1
    private val contactsReq = 2

    private val green = Color.parseColor("#1E8E3E")
    private val red = Color.parseColor("#D93025")
    private val accent = Color.parseColor("#1A73E8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(32))
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        render()
        // Agar koi blocked call history me reh gayi ho to app khulte hi saaf kar do
        if (store.cleanCallLog && CallLogCleaner.hasPermission(this)) {
            Thread { CallLogCleaner.cleanAll(applicationContext, store.log()) }.start()
        }
    }

    private val callLogReq = 3

    private fun requestCallLog() {
        requestPermissions(
            arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG), callLogReq
        )
    }

    private fun isShieldOn(): Boolean =
        getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

    private fun hasContacts(): Boolean =
        checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun render() {
        root.removeAllViews()

        title("🛡️ Spam Shield", 26f)

        // ---------- Status ----------
        val on = isShieldOn()
        card {
            label(if (on) "✅ Shield ON hai" else "❌ Shield OFF hai", 20f, bold = true, color = if (on) green else red)
            label(
                if (on) "Spam calls ring hone se pehle hi kat jayengi. Total blocked: ${store.totalBlocked()}"
                else "Neeche button dabao aur 'Spam Shield' ko caller ID & spam app choose karo."
            )
            if (!on) button("Shield ON karo") { requestRole() }
            if (!hasContacts()) {
                label("⚠️ Contacts permission do, taaki apne logon ki call kabhi block na ho.", color = red)
                button("Contacts permission do") {
                    requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), contactsReq)
                }
            }
        }

        // ---------- Rules ----------
        section("Kya block karna hai")
        card {
            toggle("140 wale telemarketing number", "Loan / credit card / insurance promotion calls (140xxxxxxx)",
                store.block140) { store.block140 = it }
            toggle("Hidden / private number", "Jo number chhupa ke call karte hain", store.blockHidden) { store.blockHidden = it }
            toggle("International number", "+91 ke alawa sab bahar ke number", store.blockInternational) {
                store.blockInternational = it
            }
            toggle("🔥 SAARE anjaan number block", "Sirf contacts wale hi call kar payenge. Loan app wale roz naye number se call karte hain to yeh ON karo.",
                store.blockUnknown) { store.blockUnknown = it; render() }
            toggle("🧹 Call history se bhi delete karo", "Block hui call phone ke dial log / recent calls me bhi nahi dikhegi",
                store.cleanCallLog) {
                store.cleanCallLog = it
                if (it && !CallLogCleaner.hasPermission(this)) requestCallLog()
                render()
            }
            if (store.cleanCallLog && !CallLogCleaner.hasPermission(this)) {
                label("⚠️ Call history se delete karne ke liye 'Call logs' permission chahiye.", color = red)
                button("Call log permission do") { requestCallLog() }
            }
            if (store.cleanCallLog && CallLogCleaner.hasPermission(this) && store.log().isNotEmpty()) {
                button("Purani block hui calls bhi history se hatao") {
                    Thread {
                        val n = CallLogCleaner.cleanAll(applicationContext, store.log())
                        runOnUiThread { Toast.makeText(this, "$n call history se hatayi", Toast.LENGTH_SHORT).show() }
                    }.start()
                }
            }
            if (store.blockUnknown) {
                toggle("Emergency: dobara call aane do", "Koi anjaan number 5 minute me 2 baar call kare to doosri call aa jayegi",
                    store.allowRepeatCaller) { store.allowRepeatCaller = it }
                label("Tip: Delivery / bank / doctor ka number contacts me save kar lo ya neeche 'Hamesha allow' me daal do.", 13f)
            }
        }

        // ---------- Blocklist ----------
        section("Meri blocklist")
        card {
            label("Pura number likho, ya prefix ke baad * lagao (jaise 98765* = 98765 se shuru hone wale sab).", 13f)
            inputRow("Number ya 98765*", "Block") { store.addBlock(it); render() }
            val list = store.blockList()
            if (list.isEmpty()) label("Abhi koi number nahi", 13f)
            list.forEach { n -> itemRow(n, "Hatao") { store.removeBlock(n); render() } }
        }

        // ---------- Allowlist ----------
        section("Hamesha allow (kabhi block nahi)")
        card {
            inputRow("Number ya prefix*", "Allow") { store.addAllow(it); render() }
            val list = store.allowList()
            if (list.isEmpty()) label("Abhi koi number nahi", 13f)
            list.forEach { n -> itemRow(n, "Hatao") { store.removeAllow(n); render() } }
        }

        // ---------- Log ----------
        section("Block hui calls")
        card {
            val log = store.log()
            if (log.isEmpty()) label("Abhi tak koi call block nahi hui", 13f)
            val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            log.take(100).forEach { e ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(6))
                }
                row.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply { text = e.number; textSize = 16f; setTypeface(null, Typeface.BOLD) })
                    addView(TextView(context).apply { text = "${fmt.format(Date(e.time))} • ${e.reason}"; textSize = 12f })
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                row.addView(smallButton("⋮") { logMenu(e.number) })
                currentCard.addView(row)
            }
            if (log.isNotEmpty()) button("Log saaf karo") { store.clearLog(); render() }
        }
    }

    private fun logMenu(number: String) {
        AlertDialog.Builder(this)
            .setTitle(number)
            .setItems(arrayOf("Hamesha allow karo (galti se block hua)", "Pakka block list me daalo")) { _, which ->
                if (which == 0) store.addAllow(number) else store.addBlock(number)
                Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show()
                render()
            }
            .show()
    }

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "Is phone me call screening support nahi hai", Toast.LENGTH_LONG).show()
            return
        }
        @Suppress("DEPRECATION")
        startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), roleReq)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == roleReq && isShieldOn()) {
            val need = mutableListOf<String>()
            if (!hasContacts()) need += Manifest.permission.READ_CONTACTS
            if (store.cleanCallLog && !CallLogCleaner.hasPermission(this)) {
                need += Manifest.permission.READ_CALL_LOG
                need += Manifest.permission.WRITE_CALL_LOG
            }
            if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), contactsReq)
        }
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    // ================= UI helpers =================

    private lateinit var currentCard: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun title(t: String, size: Float) {
        root.addView(TextView(this).apply {
            text = t; textSize = size; setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        })
    }

    private fun section(t: String) {
        root.addView(TextView(this).apply {
            text = t; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(accent)
            setPadding(dp(4), dp(18), 0, dp(6))
        })
    }

    private fun card(build: () -> Unit) {
        currentCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.argb(22, 128, 128, 128))
            }
        }
        build()
        root.addView(currentCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun label(t: String, size: Float = 15f, bold: Boolean = false, color: Int? = null) {
        currentCard.addView(TextView(this).apply {
            text = t; textSize = size
            if (bold) setTypeface(null, Typeface.BOLD)
            color?.let { setTextColor(it) }
            setPadding(0, dp(4), 0, dp(4))
        })
    }

    private fun button(t: String, onClick: () -> Unit) {
        currentCard.addView(Button(this).apply {
            text = t; isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(accent) }
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })
    }

    private fun smallButton(t: String, onClick: () -> Unit): Button = Button(this).apply {
        text = t; isAllCaps = false; minWidth = dp(48); minimumWidth = dp(48)
        setOnClickListener { onClick() }
    }

    private fun toggle(t: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = t; textSize = 16f; setTypeface(null, Typeface.BOLD) })
            addView(TextView(context).apply { text = sub; textSize = 12f })
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(Switch(this).apply {
            isChecked = value
            setOnCheckedChangeListener { _, c -> onChange(c) }
        })
        currentCard.addView(row)
    }

    private fun inputRow(hint: String, action: String, onAdd: (String) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = EditText(this).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_PHONE
        }
        row.addView(input, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = action; isAllCaps = false
            setOnClickListener {
                val v = input.text.toString().trim()
                if (v.any { it.isDigit() }) onAdd(v)
                else Toast.makeText(context, "Sahi number likho", Toast.LENGTH_SHORT).show()
            }
        })
        currentCard.addView(row)
    }

    private fun itemRow(t: String, action: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply { text = t; textSize = 16f }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(Button(this).apply { text = action; isAllCaps = false; setOnClickListener { onClick() } })
        currentCard.addView(row)
        currentCard.addView(View(this).apply { setBackgroundColor(Color.argb(30, 128, 128, 128)) },
            LinearLayout.LayoutParams(MATCH_PARENT, 1))
    }
}
