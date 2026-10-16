package com.anpaiss.karooswitches

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Target of the tap on the Switches field. */
class OpenSwitchesReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.anpaiss.karooswitches.OPEN"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        SwitchesPopup.show(context.applicationContext)
    }
}
