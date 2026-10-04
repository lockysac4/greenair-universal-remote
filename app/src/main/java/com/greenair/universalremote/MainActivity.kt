package com.greenair.universalremote

import android.app.Activity
import android.os.Bundle
import android.hardware.ConsumerIrManager
import android.graphics.Color
import android.view.Gravity
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
    private val powerCodes = buildList { for (a in 0x00..0xFF) for (c in listOf(0x45,0x46,0x47,0x44,0x40,0x43,0x07,0x15,0x09,0x16,0x19,0x0D,0x0C,0x18,0x5E,0x08,0x1C,0x5A,0x42,0x52,0x12,0x14,0x18,0x0F,0x57,0x17,0x1A,0x1B,0x1D,0x1F,0x4C,0x4D,0x54)) add(a to c) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = getSystemService(CONSUMER_IR_SERVICE) as ConsumerIrManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 32, 28, 24)
            setBackgroundColor(Color.rgb(16, 16, 16))
        }
        fun text(s: String, size: Float) = TextView(this).apply {
            text = s; textSize = size; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(8, 12, 8, 12)
        }
        fun button(s: String, action: () -> Unit) = Button(this).apply {
            text = s; textSize = 18f; setOnClickListener { action() }
        }

        root.addView(text("GREENAIR UNIVERSAL REMOTE", 24f))
        status = text(if (ir.hasIrEmitter()) "IR transmitter detected ✓" else "No Android IR transmitter detected", 16f)
        root.addView(status)
        root.addView(button("POWER") { sendNec(0x00, 0x45) }, LinearLayout.LayoutParams(-1, 150))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("VOL +" to 0x46, "MUTE" to 0x47, "CH +" to 0x44).forEach { (n, c) ->
            row.addView(button(n) { sendNec(0x00, c) }, LinearLayout.LayoutParams(0, 130, 1f))
        }
        root.addView(row)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("VOL -" to 0x15, "HOME" to 0x09, "CH -" to 0x07).forEach { (n, c) ->
            row2.addView(button(n) { sendNec(0x00, c) }, LinearLayout.LayoutParams(0, 130, 1f))
        }
        root.addView(row2)

        root.addView(text("TCL / Universal TV code search", 18f))
        autoButton = button("START AUTO SEARCH") {
            if (searching) stopSearch() else startSearch()
        }
        root.addView(autoButton, LinearLayout.LayoutParams(-1, 130))
        root.addView(button("TV RESPONDED - STOP & SAVE") { saveCurrentCode() }, LinearLayout.LayoutParams(-1, 130))
        root.addView(text("v0.3.0 • TCL expanded IR search\nDisplays address + command while searching", 14f))
        setContentView(root)
    }

    private fun startSearch() {
        if (!ir.hasIrEmitter()) return
        searching = true; searchIndex = 0; autoButton.text = "STOP AUTO SEARCH"; runNextCode()
    }
    private fun runNextCode() {
        if (!searching) return
        if (searchIndex >= powerCodes.size) { stopSearch(); status.text = "Search complete - no NEC match"; return }
        val code = powerCodes[searchIndex]
        status.text = "TCL search " + (searchIndex + 1) + "/" + powerCodes.size + "  NEC addr=0x" + code.first.toString(16).uppercase().padStart(2, '0') + " cmd=0x" + code.second.toString(16).uppercase().padStart(2, '0')
        sendNec(code.first, code.second)
        searchIndex++
        handler.postDelayed({ runNextCode() }, 900)
    }
    private fun stopSearch() {
        searching = false; handler.removeCallbacksAndMessages(null); autoButton.text = "START AUTO SEARCH"
    }
    private fun saveCurrentCode() {
        if (searchIndex == 0) return
        val code = powerCodes[(searchIndex - 1).coerceIn(powerCodes.indices)]
        getSharedPreferences("remote", MODE_PRIVATE).edit().putInt("address", code.first).putInt("power", code.second).apply()
        stopSearch()
        status.text = "TV power code saved"
        Toast.makeText(this, "TV power code saved", Toast.LENGTH_SHORT).show()
    }

    private fun sendNec(address: Int, command: Int) {
        if (!ir.hasIrEmitter()) {
            Toast.makeText(this, "IR emitter not exposed by Android", Toast.LENGTH_SHORT).show()
            return
        }
        val bits = mutableListOf<Int>()
        fun addByte(v: Int) { for (i in 0..7) bits.add((v shr i) and 1) }
        addByte(address); addByte(address xor 0xFF); addByte(command); addByte(command xor 0xFF)
        val p = mutableListOf(9000, 4500)
        bits.forEach { b -> p.add(560); p.add(if (b == 1) 1690 else 560) }
        p.add(560)
        try {
            ir.transmit(38000, p.toIntArray())
            Toast.makeText(this, "IR sent", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "IR error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
