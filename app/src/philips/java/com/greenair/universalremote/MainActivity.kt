package com.greenair.universalremote

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.hardware.ConsumerIrManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.greenair.universalremote.PhilipsKey.*

class MainActivity : Activity() {
    private var ir: ConsumerIrManager? = null
    private var toggle = false
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = getSystemService(ConsumerIrManager::class.java)
        toggle = savedInstanceState?.getBoolean("toggle") ?: false

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BACKGROUND)
            isFillViewport = true
            addView(page)
        }
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            scroll.requestApplyInsets()
        } else {
            window.statusBarColor = BACKGROUND
            window.navigationBarColor = BACKGROUND
        }

        page.addView(TextView(this).apply {
            text = "Philips Remote"
            textSize = 30f
            setTextColor(TEXT)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAccessibilityHeading = true
        })
        page.addView(TextView(this).apply {
            text = "22PFT5403/98"
            textSize = 16f
            setTextColor(MUTED)
            setPadding(0, dp(3), 0, dp(18))
        })

        status = TextView(this).apply {
            textSize = 15f
            setTextColor(MUTED)
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        page.addView(status)
        val hasEmitter = ir?.hasIrEmitter() == true
        if (!hasEmitter) showStatus("Android did not detect an infrared blaster.")

        page.addView(row(hasEmitter, POWER, SOURCE))
        page.addView(row(hasEmitter, VOLUME_DOWN, MUTE, VOLUME_UP))
        page.addView(row(hasEmitter, CHANNEL_DOWN, CHANNEL_UP))
        page.addView(row(hasEmitter, MENU, UP, BACK))
        page.addView(row(hasEmitter, LEFT, OK, RIGHT))
        page.addView(row(hasEmitter, null, DOWN, null))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("toggle", toggle)
        super.onSaveInstanceState(outState)
    }

    private fun row(enabled: Boolean, vararg keys: PhilipsKey?): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((index, key) in keys.withIndex()) {
            val view = if (key == null) View(this) else remoteButton(key, enabled)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = dp(8)
                bottomMargin = dp(8)
            }
            row.addView(view, params)
        }
        return row
    }

    private fun remoteButton(key: PhilipsKey, enabled: Boolean): Button {
        val power = key == PhilipsKey.POWER
        val fill = when (key) {
            PhilipsKey.POWER -> ACCENT
            PhilipsKey.OK -> NAVIGATION
            else -> BUTTON
        }
        val foreground = if (power) BACKGROUND else TEXT
        val shape = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(14).toFloat()
        }
        return Button(this).apply {
            text = key.label
            isAllCaps = false
            textSize = when (key) {
                PhilipsKey.UP, PhilipsKey.DOWN, PhilipsKey.LEFT, PhilipsKey.RIGHT -> 24f
                else -> 17f
            }
            contentDescription = when (key) {
                PhilipsKey.UP -> "Up"
                PhilipsKey.DOWN -> "Down"
                PhilipsKey.LEFT -> "Left"
                PhilipsKey.RIGHT -> "Right"
                PhilipsKey.VOLUME_UP -> "Volume up"
                PhilipsKey.VOLUME_DOWN -> "Volume down"
                PhilipsKey.CHANNEL_UP -> "Channel up"
                PhilipsKey.CHANNEL_DOWN -> "Channel down"
                else -> key.label
            }
            setTextColor(foreground)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            minimumHeight = dp(58)
            minimumWidth = 0
            setPadding(dp(6), dp(8), dp(6), dp(8))
            stateListAnimator = null
            background = RippleDrawable(ColorStateList.valueOf(RIPPLE), shape, null)
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.55f
            setOnClickListener { transmit(key) }
        }
    }

    private fun transmit(key: PhilipsKey) {
        val emitter = ir ?: return
        val nextToggle = !toggle
        try {
            emitter.transmit(PhilipsIr.CARRIER_HZ, PhilipsIr.encode(PhilipsProtocol.RC6, key.rc6, nextToggle))
            toggle = nextToggle
            status.visibility = View.GONE
        } catch (_: RuntimeException) {
            showStatus("Could not send the infrared command.")
        }
    }

    private fun showStatus(message: String) {
        status.text = message
        status.setPadding(0, 0, 0, dp(12))
        status.visibility = View.VISIBLE
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        val BACKGROUND = 0xFF111814.toInt()
        val BUTTON = 0xFF26342C.toInt()
        val NAVIGATION = 0xFF3B5745.toInt()
        val ACCENT = 0xFF9CE3AF.toInt()
        val TEXT = 0xFFF4F8F5.toInt()
        val MUTED = 0xFFACBEB1.toInt()
        val RIPPLE = 0x33FFFFFF
    }
}
