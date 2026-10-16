package com.anpaiss.karooswitches

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Overlay with one ON/OFF button per switch of every installed extension.
 * Same window recipe as the PoiAhead popup (top band, touches outside pass
 * through to the ride app). Closes on CLOSE or after [AUTO_CLOSE_MS] without taps.
 */
object SwitchesPopup {
    private const val TAG = "KarooSwitches"
    private const val AUTO_CLOSE_MS = 20_000L
    private const val COLOR_ON  = "#2E7D32"   // white on it = 6.5:1
    private const val COLOR_OFF = "#C62828"   // white on it = 5.6:1
    private const val COLOR_UNKNOWN = "#616161"

    private val handler = Handler(Looper.getMainLooper())
    private var current: View? = null
    private var wm: WindowManager? = null
    private val autoClose = Runnable { removeCurrent() }

    fun show(context: Context) {
        handler.post { showOnMain(context) }
    }

    fun hide() {
        handler.post { removeCurrent() }
    }

    private fun showOnMain(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "No overlay permission: popup not shown")
            return
        }
        removeCurrent()
        val switches = SwitchesClient.discover(context)
        val density = context.resources.displayMetrics.density
        fun dp(v: Float) = v * density

        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        switches.chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { info ->
                val cell = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                // Single line + ellipsis: with maxLines alone a long name wraps and the
                // second line is silently hidden ("Auto Loop Karoo" showed as "Auto Loop").
                val caption = TextView(context).apply {
                    text = info.appName
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                }
                val sub = TextView(context).apply {
                    text = info.label
                    setTextColor(Color.parseColor("#BDBDBD"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                }
                val button = Button(context).apply {
                    text = "..."
                    setTextColor(Color.WHITE)
                    textSize = 22f
                    setTypeface(typeface, Typeface.BOLD)
                    background = GradientDrawable().apply { cornerRadius = dp(8f); setColor(Color.parseColor(COLOR_UNKNOWN)) }
                    setOnClickListener {
                        rearm()
                        SwitchesClient.send(context, info, Switches.OP_TOGGLE) { on -> paint(this, on) }
                    }
                }
                SwitchesClient.send(context, info, Switches.OP_GET) { on -> paint(button, on) }
                cell.addView(caption)
                cell.addView(sub)
                cell.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60f).toInt()).apply {
                    setMargins(dp(4f).toInt(), dp(4f).toInt(), dp(4f).toInt(), dp(8f).toInt())
                })
                row.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            if (pair.size == 1) row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            grid.addView(row)
        }
        if (switches.isEmpty()) {
            grid.addView(TextView(context).apply {
                text = "No switches found"
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
            })
        }

        val close = Button(context).apply {
            text = "CLOSE"
            setTextColor(Color.WHITE)
            textSize = 16f
            background = GradientDrawable().apply { cornerRadius = dp(6f); setColor(Color.parseColor("#424242")) }
            setOnClickListener { removeCurrent() }
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(12f).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(10f)
                setColor(Color.parseColor("#EE1A1A1A"))
                setStroke(dp(2f).toInt(), Color.parseColor("#2196F3"))
            }
            addView(grid)
            addView(close, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48f).toInt()).apply {
                setMargins(dp(4f).toInt(), dp(4f).toInt(), dp(4f).toInt(), 0)
            })
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(40f).toInt()
            horizontalMargin = 0.04f
        }

        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        runCatching { manager.addView(root, params) }
            .onSuccess {
                wm = manager
                current = root
                rearm()
                Log.i(TAG, "Popup shown with ${switches.size} switches")
            }
            .onFailure { e -> Log.e(TAG, "addView failed", e) }
    }

    private fun paint(button: Button, on: Boolean?) {
        button.text = when (on) { true -> "ON"; false -> "OFF"; null -> "?" }
        (button.background as? GradientDrawable)?.setColor(
            Color.parseColor(when (on) { true -> COLOR_ON; false -> COLOR_OFF; null -> COLOR_UNKNOWN })
        )
    }

    private fun rearm() {
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, AUTO_CLOSE_MS)
    }

    private fun removeCurrent() {
        handler.removeCallbacks(autoClose)
        val v = current ?: return
        runCatching { wm?.removeView(v) }
        current = null
    }
}
