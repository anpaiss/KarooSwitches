package com.anpaiss.karooswitches

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * An adjustable time of the host behind its own ON/OFF switch (meta-data
 * kind = "time", e.g. BetterETA's mandatory arrival time). The popup shows
 * the label with an ON/OFF button; while ON it adds the time as HH:mm and
 * the −15/−5/+5/+15 buttons, while OFF the row is just the label and the button.
 * Subclass, implement the four accessors, declare in the manifest.
 *
 * Answers: [Switches.RESULT_ON] = the switch, [Switches.RESULT_TEXT] = the time
 * ("" = not set). Ops: get, toggle (the switch), adjust (the time, by
 * [Switches.EXTRA_DELTA_MIN] minutes). A popup of an older library version
 * sees it as a plain switch.
 */
abstract class TimeReceiver : BroadcastReceiver() {

    /** The switch: false = the feature is off (no time, nothing asked). */
    abstract fun isOn(context: Context): Boolean
    abstract fun setOn(context: Context, on: Boolean)

    /** The time to show, null when not set. */
    abstract fun text(context: Context): String?

    /** Shifts the time by [deltaMin] minutes; when not set, starts from the host's best default. */
    abstract fun adjust(context: Context, deltaMin: Int)

    final override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Switches.ACTION) return
        when (intent.getStringExtra(Switches.EXTRA_OP)) {
            Switches.OP_TOGGLE -> { setOn(context, !isOn(context)); Switches.notifyChanged() }
            Switches.OP_ADJUST -> { adjust(context, intent.getIntExtra(Switches.EXTRA_DELTA_MIN, 0)); Switches.notifyChanged() }
        }
        if (isOrderedBroadcast) {
            resultCode = Activity.RESULT_OK
            setResultExtras(Bundle().apply {
                putBoolean(Switches.RESULT_ON, isOn(context))
                putString(Switches.RESULT_TEXT, text(context) ?: "")
            })
        }
    }
}
