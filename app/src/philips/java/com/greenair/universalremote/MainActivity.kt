package com.greenair.universalremote

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.greenair.universalremote.PhilipsKey.*

class MainActivity : Activity() {
    private var ir: ConsumerIrManager? = null
    private var hasEmitter = false
    private var toggle = false
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = getSystemService(ConsumerIrManager::class.java)
        hasEmitter = ir?.hasIrEmitter() == true
        toggle = savedInstanceState?.getBoolean("toggle") ?: false

        val page = column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(12), dp(10), dp(12))
        }
        val remote = column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = shape(CASE, 34, CASE_EDGE)
            setPadding(dp(16), dp(16), dp(16), dp(18))
        }
        val caseWidth = minOf(360, (resources.configuration.screenWidthDp - 20).coerceAtLeast(1))
        page.addView(remote, LinearLayout.LayoutParams(dp(caseWidth), -2))
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BACKGROUND)
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(page)
        }
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                remote.layoutParams = (remote.layoutParams as LinearLayout.LayoutParams).apply {
                    width = minOf(dp(caseWidth), (resources.displayMetrics.widthPixels - bars.left - bars.right - dp(20)).coerceAtLeast(1))
                }
                insets
            }
            scroll.requestApplyInsets()
        } else {
            window.statusBarColor = BACKGROUND
            window.navigationBarColor = BACKGROUND
        }

        val name = column().apply {
            gravity = Gravity.CENTER
            addView(label("PHILIPS", 17f, TEXT).apply {
                letterSpacing = 0.13f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAccessibilityHeading = true
            })
            addView(label("22PFT5403/98", 11f, SECONDARY))
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(button("⏻", "Power", POWER.rc6, POWER_RED, 26f), LinearLayout.LayoutParams(dp(50), dp(50)))
            addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            addView(button("Source", "Source", SOURCE.rc6, size = 13f), LinearLayout.LayoutParams(dp(66), -2))
        }
        addSection(remote, header)

        status = label("", 14f, SECONDARY).apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        addSection(remote, status)
        if (!hasEmitter) showStatus("Android did not detect an infrared blaster.")

        addSection(remote, directionPad(), top = 14, width = dp(180))
        addSection(remote, row(
            button("Menu", "Menu", MENU.rc6),
            button("Mute", "Mute", MUTE.rc6),
            button("Back", "Back", BACK.rc6)
        ), top = 12)
        addSection(remote, row(
            rocker("VOL", VOLUME_UP.rc6, VOLUME_DOWN.rc6, "Volume"),
            rocker("CH", CHANNEL_UP.rc6, CHANNEL_DOWN.rc6, "Channel")
        ), top = 12)

        // Philips RC6 TV digit commands are 0x00 through 0x09.
        for (first in listOf(1, 4, 7)) {
            val digits = (first..first + 2).map { number ->
                button(number.toString(), number.toString(), number, size = 19f)
            }
            addSection(remote, row(*digits.toTypedArray()), top = if (first == 1) 12 else 4)
        }
        addSection(remote, row(View(this), button("0", "0", 0x00, size = 19f), View(this)), top = 4)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("toggle", toggle)
        super.onSaveInstanceState(outState)
    }

    private fun directionPad() = FrameLayout(this).apply {
        minimumHeight = dp(180)
        background = shape(DIAL, 90, CASE_EDGE)

        fun addDirection(text: String, description: String, key: PhilipsKey, gravity: Int) {
            val params = FrameLayout.LayoutParams(dp(54), dp(50), gravity).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            addView(button(text, description, key.rc6, Color.TRANSPARENT, 17f), params)
        }

        addDirection("▲", "Up", UP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        addDirection("◀", "Left", LEFT, Gravity.LEFT or Gravity.CENTER_VERTICAL)
        addView(button("OK", "OK", OK.rc6, CENTER_KEY, 18f),
            FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER))
        addDirection("▶", "Right", RIGHT, Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        addDirection("▼", "Down", DOWN, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
    }

    private fun rocker(title: String, up: Int, down: Int, description: String) = column().apply {
        background = shape(DIAL, 23, CASE_EDGE)
        addView(button("+", "$description up", up, Color.TRANSPARENT, 23f))
        addView(label(title, 11f, SECONDARY).apply {
            letterSpacing = 0.16f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(-1, -2))
        addView(button("−", "$description down", down, Color.TRANSPARENT, 23f))
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        for ((index, view) in views.withIndex()) {
            addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) marginStart = dp(7)
            })
        }
    }

    private fun addSection(remote: LinearLayout, view: View, top: Int = 0, width: Int = -1) {
        remote.addView(view, LinearLayout.LayoutParams(width, -2).apply { topMargin = dp(top) })
    }

    private fun label(value: String, size: Float, colour: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(colour)
        gravity = Gravity.CENTER
        includeFontPadding = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun button(
        value: String, description: String, command: Int, fill: Int = KEY, size: Float = 15f
    ) = Button(this).apply {
        text = value
        contentDescription = description
        isAllCaps = false
        textSize = size
        setTextColor(TEXT)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        minimumWidth = 0
        minimumHeight = dp(48)
        setPadding(dp(4), 0, dp(4), 0)
        stateListAnimator = null
        background = RippleDrawable(ColorStateList.valueOf(RIPPLE), shape(fill, 28), shape(Color.WHITE, 28))
        setOnClickListener { transmit(command) }
        isEnabled = hasEmitter
        alpha = if (hasEmitter) 1f else 0.6f
    }

    private fun shape(fill: Int, radius: Int, outline: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (outline != Color.TRANSPARENT) setStroke(dp(1), outline)
    }

    private fun transmit(command: Int) {
        val emitter = ir ?: return
        val nextToggle = !toggle
        try {
            emitter.transmit(PhilipsIr.CARRIER_HZ, PhilipsIr.encode(PhilipsProtocol.RC6, command, nextToggle))
            toggle = nextToggle
            status.visibility = View.GONE
        } catch (_: RuntimeException) {
            showStatus("Could not send the infrared command.")
        }
    }

    private fun showStatus(message: String) {
        status.text = message
        status.setPadding(0, dp(8), 0, 0)
        status.visibility = View.VISIBLE
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        val BACKGROUND = 0xFF111415.toInt()
        val CASE = 0xFF202426.toInt()
        val CASE_EDGE = 0xFF404749.toInt()
        val DIAL = 0xFF2E3436.toInt()
        val KEY = 0xFF383F42.toInt()
        val CENTER_KEY = 0xFF546062.toInt()
        val POWER_RED = 0xFFB82B3A.toInt()
        val TEXT = 0xFFF6F8F8.toInt()
        val SECONDARY = 0xFFBAC5C5.toInt()
        const val RIPPLE = 0x35FFFFFF
    }
}
