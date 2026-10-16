package com.anpaiss.karooswitches

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** One switch of the host. Subclass, implement the two accessors, declare in the manifest. */
abstract class SwitchReceiver : BroadcastReceiver() {

    abstract fun isOn(context: Context): Boolean
    abstract fun setOn(context: Context, on: Boolean)

    final override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Switches.ACTION) return
        if (intent.getStringExtra(Switches.EXTRA_OP) == Switches.OP_TOGGLE) {
            setOn(context, !isOn(context))
            Switches.notifyChanged()
        }
        if (isOrderedBroadcast) {
            resultCode = Activity.RESULT_OK
            setResultExtras(Bundle().apply { putBoolean(Switches.RESULT_ON, isOn(context)) })
        }
    }
}
