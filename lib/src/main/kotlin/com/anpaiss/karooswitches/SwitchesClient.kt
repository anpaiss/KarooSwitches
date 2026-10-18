package com.anpaiss.karooswitches

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper

data class SwitchInfo(val component: ComponentName, val label: String, val appName: String, val kind: String = "") {
    val isTime: Boolean get() = kind == Switches.KIND_TIME
}

/** Answer of a kind "time" receiver: its switch and its time ("" = not set). */
data class TimeState(val on: Boolean, val text: String)

/** Finds the switches of every installed extension and talks to them. */
object SwitchesClient {

    fun discover(context: Context): List<SwitchInfo> {
        val pm = context.packageManager
        return pm.queryBroadcastReceivers(Intent(Switches.ACTION), PackageManager.GET_META_DATA)
            .mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                // android:value="@string/..." lands in the bundle as a resource id, not a string
                val label = when (val v = ai.metaData?.get(Switches.META_LABEL)) {
                    is String -> v
                    is Int -> runCatching { pm.getText(ai.packageName, v, ai.applicationInfo)?.toString() }.getOrNull()
                    else -> null
                } ?: ai.loadLabel(pm).toString()
                SwitchInfo(ComponentName(ai.packageName, ai.name), label, ai.applicationInfo.loadLabel(pm).toString(),
                    ai.metaData?.getString(Switches.META_KIND) ?: "")
            }
            .sortedWith(compareBy({ it.appName }, { it.label }))
    }

    /** The host's own switch, if it has one. */
    fun own(context: Context): SwitchInfo? =
        discover(context).firstOrNull { it.component.packageName == context.packageName }

    /** [op] is OP_GET or OP_TOGGLE; the callback gets the state after it, null if unanswered. */
    fun send(context: Context, info: SwitchInfo, op: String, onResult: (Boolean?) -> Unit) {
        sendRaw(context, Intent(Switches.ACTION).setComponent(info.component).putExtra(Switches.EXTRA_OP, op)) { extras ->
            onResult(extras?.getBoolean(Switches.RESULT_ON))
        }
    }

    /** Kind "time": [op] is OP_GET, OP_TOGGLE or OP_ADJUST (by [deltaMin]); the callback gets the state after it, null if unanswered. */
    fun sendTime(context: Context, info: SwitchInfo, op: String, deltaMin: Int = 0, onResult: (TimeState?) -> Unit) {
        val intent = Intent(Switches.ACTION).setComponent(info.component).putExtra(Switches.EXTRA_OP, op)
        if (op == Switches.OP_ADJUST) intent.putExtra(Switches.EXTRA_DELTA_MIN, deltaMin)
        sendRaw(context, intent) { extras ->
            onResult(extras?.let { TimeState(it.getBoolean(Switches.RESULT_ON), it.getString(Switches.RESULT_TEXT) ?: "") })
        }
    }

    private fun sendRaw(context: Context, intent: Intent, onResult: (Bundle?) -> Unit) {
        context.sendOrderedBroadcast(
            intent, Switches.PERMISSION,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    onResult(if (resultCode == Activity.RESULT_OK) getResultExtras(false) else null)
                }
            },
            Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED, null, null,
        )
    }
}
