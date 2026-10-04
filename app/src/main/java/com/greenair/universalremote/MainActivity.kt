package com.greenair.universalremote

import android.app.Activity
import android.os.Bundle
import android.hardware.ConsumerIrManager
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.os.Handler
import android.os.Looper

class MainActivity : Activity() {
    private lateinit var ir: ConsumerIrManager
    private lateinit var status: TextView
    private lateinit var autoButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private var searching = false
    private var searchIndex = 0
    private val powerCodes = buildList { for (a in 0x00..0xFF) for (c in listOf(0x45,0x46,0x47,0x44,0x40,0x43,0x07,0x15,0x09,0x16,0x19,0x0D,0x0C,0x18,0x5E,0x08,0x1C,0x5A,0x42,0x52,0x12,0x14,0x0F,0x57,0x17,0x1A,0x1B,0x1D,0x1F,0x4C,0x4D,0x54)) add(a to c) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = getSystemService(CONSUMER_IR_SERVICE) as ConsumerIrManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 26, 22, 20)
            setBackgroundColor(Color.rgb(16, 16, 16))
        }
        root.addView(label("GREENAIR UNIVERSAL REMOTE", 23f))

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val remoteTab = Button(this).apply { text = "REMOTE" }
        val codesTab = Button(this).apply { text = "REMOTE CODES" }
        tabs.addView(remoteTab, LinearLayout.LayoutParams(0, 115, 1f))
        tabs.addView(codesTab, LinearLayout.LayoutParams(0, 115, 1f))
        root.addView(tabs)

        val remoteView = buildRemoteView()
        val codesView = buildCodesView()
        val scroll = ScrollView(this)
        scroll.addView(remoteView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        remoteTab.setOnClickListener {
            stopSearch()
            scroll.removeAllViews(); scroll.addView(remoteView)
        }
        codesTab.setOnClickListener {
            stopSearch()
            scroll.removeAllViews(); scroll.addView(codesView)
        }
        setContentView(root)
    }

    private fun buildRemoteView(): View {
        val root = column()
        status = label(if (ir.hasIrEmitter()) "IR transmitter detected ✓" else "No Android IR transmitter detected", 16f)
        root.addView(status)
        root.addView(actionButton("POWER") {
            val p = getSharedPreferences("remote", MODE_PRIVATE)
            sendNec(p.getInt("address", 0x00), p.getInt("power", 0x45))
        }, LinearLayout.LayoutParams(-1, 145))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("VOL +" to 0x46, "MUTE" to 0x47, "CH +" to 0x44).forEach { (n,c) ->
            row.addView(actionButton(n) { sendNec(0x00,c) }, LinearLayout.LayoutParams(0,125,1f))
        }
        root.addView(row)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("VOL -" to 0x15, "HOME" to 0x09, "CH -" to 0x07).forEach { (n,c) ->
            row2.addView(actionButton(n) { sendNec(0x00,c) }, LinearLayout.LayoutParams(0,125,1f))
        }
        root.addView(row2)

        root.addView(label("TCL / Universal TV code search", 18f))
        autoButton = actionButton("START AUTO SEARCH") { if (searching) stopSearch() else startSearch() }
        root.addView(autoButton, LinearLayout.LayoutParams(-1,125))
        root.addView(actionButton("TV RESPONDED - STOP & SAVE") { saveCurrentCode() }, LinearLayout.LayoutParams(-1,125))
        root.addView(label("v0.5.0 • Simplified code entry", 14f))
        return root
    }

    private fun buildCodesView(): View {
        val root = column()
        root.addView(label("SIMPLE REMOTE CODES", 21f))
        root.addView(label("Enter a button code. Use Advanced only when a code needs special settings.", 15f))

        val brand = field("Brand / remote name")
        val buttonName = field("Button name")
        val code = field("Code e.g. 45, 0x45, or raw pulses")
        listOf(brand, buttonName, code).forEach { root.addView(it) }

        val advanced = column().apply { visibility = View.GONE }
        val frequency = field("Frequency Hz")
        val address = field("NEC address")
        val raw = field("Raw pulse pattern", 5)
        listOf(frequency, address, raw).forEach { advanced.addView(it) }

        val prefs = getSharedPreferences("custom_codes", MODE_PRIVATE)
        brand.setText(prefs.getString("brand", "TCL"))
        buttonName.setText(prefs.getString("button", "POWER"))
        code.setText(prefs.getString("simple_code", prefs.getString("command", "45")))
        frequency.setText(prefs.getInt("frequency", 38000).toString())
        address.setText(prefs.getString("address", "00"))
        raw.setText(prefs.getString("raw", ""))

        val advancedButton = actionButton("ADVANCED ▼") {
            advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        root.addView(advancedButton, LinearLayout.LayoutParams(-1, 110))
        root.addView(advanced)

        val result = label("Ready", 15f)
        fun sendEnteredCode() {
            val entered = code.text.toString().trim()
            require(entered.isNotEmpty()) { "Enter a code" }
            val hz = frequency.text.toString().trim().toIntOrNull() ?: 38000
            val rawText = raw.text.toString().trim()
            val looksRaw = entered.contains(",") || entered.contains(";") || entered.trim().contains(" ")
            if (rawText.isNotEmpty() || looksRaw) {
                val pulses = parseRaw(if (rawText.isNotEmpty()) rawText else entered)
                ir.transmit(hz, pulses)
                result.text = "Raw code sent"
            } else {
                val a = parseHex(address.text.toString().ifBlank { "00" })
                val c = parseHex(entered)
                sendNec(a, c, hz)
                result.text = "Code sent: 0x${c.toString(16).uppercase().padStart(2,'0')}"
            }
        }

        root.addView(actionButton("TEST CODE") {
            try { sendEnteredCode() }
            catch (e: Exception) { result.text = "Code error: ${e.message}" }
        }, LinearLayout.LayoutParams(-1, 125))

        root.addView(actionButton("SAVE CODE") {
            val hz = frequency.text.toString().trim().toIntOrNull() ?: 38000
            prefs.edit()
                .putString("brand", brand.text.toString().trim())
                .putString("button", buttonName.text.toString().trim())
                .putString("simple_code", code.text.toString().trim())
                .putInt("frequency", hz)
                .putString("address", address.text.toString().trim().ifBlank { "00" })
                .putString("raw", raw.text.toString().trim()).apply()
            result.text = "Saved: ${brand.text} • ${buttonName.text}"
            Toast.makeText(this, "Remote code saved", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, 125))

        root.addView(result)
        root.addView(label("Most codes only need the Code box. Examples: 45 or 0x45", 14f))
        return root
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private fun label(s:String,size:Float) = TextView(this).apply {
        text=s; textSize=size; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(8,12,8,12)
    }
    private fun actionButton(s:String, action:()->Unit) = Button(this).apply {
        text=s; textSize=17f; setOnClickListener { action() }
    }
    private fun field(hintText:String, lines:Int=1) = EditText(this).apply {
        hint=hintText; setHintTextColor(Color.LTGRAY); setTextColor(Color.WHITE); textSize=16f
        setPadding(14,12,14,12); minLines=lines; if(lines>1) gravity=Gravity.TOP
    }
    private fun parseHex(s:String):Int {
        val clean=s.trim().removePrefix("0x").removePrefix("0X")
        require(clean.isNotEmpty()) { "Enter address and command" }
        return clean.toInt(16).also { require(it in 0..255) { "NEC value must be 00-FF" } }
    }
    private fun parseRaw(s:String):IntArray {
        val values=s.trim().split(Regex("[,;\\s]+")).filter { it.isNotBlank() }.map { it.toInt() }
        require(values.size >= 3) { "Raw pattern needs at least 3 pulse values" }
        require(values.all { it > 0 }) { "Pulse values must be positive" }
        return values.toIntArray()
    }

    private fun startSearch() {
        if (!ir.hasIrEmitter()) return
        searching=true; searchIndex=0; autoButton.text="STOP AUTO SEARCH"; runNextCode()
    }
    private fun runNextCode() {
        if (!searching) return
        if (searchIndex >= powerCodes.size) { stopSearch(); status.text="Search complete - no NEC match"; return }
        val code=powerCodes[searchIndex]
        status.text="TCL search ${searchIndex+1}/${powerCodes.size}  NEC addr=0x${code.first.toString(16).uppercase().padStart(2,'0')} cmd=0x${code.second.toString(16).uppercase().padStart(2,'0')}"
        sendNec(code.first,code.second); searchIndex++
        handler.postDelayed({runNextCode()},900)
    }
    private fun stopSearch() {
        searching=false; handler.removeCallbacksAndMessages(null)
        if (::autoButton.isInitialized) autoButton.text="START AUTO SEARCH"
    }
    private fun saveCurrentCode() {
        if (searchIndex==0) return
        val code=powerCodes[(searchIndex-1).coerceIn(powerCodes.indices)]
        getSharedPreferences("remote",MODE_PRIVATE).edit().putInt("address",code.first).putInt("power",code.second).apply()
        stopSearch(); status.text="TV power code saved"
        Toast.makeText(this,"TV power code saved",Toast.LENGTH_SHORT).show()
    }
    private fun sendNec(address:Int, command:Int, frequency:Int = 38000) {
        if (!ir.hasIrEmitter()) { Toast.makeText(this,"IR emitter not exposed by Android",Toast.LENGTH_SHORT).show(); return }
        val bits=mutableListOf<Int>()
        fun addByte(v:Int){ for(i in 0..7) bits.add((v shr i) and 1) }
        addByte(address); addByte(address xor 0xFF); addByte(command); addByte(command xor 0xFF)
        val p=mutableListOf(9000,4500)
        bits.forEach { b -> p.add(560); p.add(if(b==1)1690 else 560) }
        p.add(560)
        try { ir.transmit(frequency,p.toIntArray()) }
        catch(e:Exception){ Toast.makeText(this,"IR error: ${e.message}",Toast.LENGTH_LONG).show() }
    }
}