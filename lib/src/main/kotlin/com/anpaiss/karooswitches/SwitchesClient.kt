package com.anpaiss.karooswitches

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper

data class SwitchInfo(val component: ComponentName, val label: String, val appName: String)

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
                SwitchInfo(ComponentName(ai.packageName, ai.name), label, ai.applicationInfo.loadLabel(pm).toString())
            }
            .sortedWith(compareBy({ it.appName }, { it.label }))
    }

    /** The host's own switch, if it has one. */
    fun own(context: Context): SwitchInfo? =
        discover(context).firstOrNull { it.component.packageName == context.packageName }

    /** [op] is OP_GET or OP_TOGGLE; the callback gets the state after it, null if unanswered. */
    fun send(context: Context, info: SwitchInfo, op: String, onResult: (Boolean?) -> Unit) {
        val intent = Intent(Switches.ACTION).setComponent(info.component).putExtra(Switches.EXTRA_OP, op)
        context.sendOrderedBroadcast(
            intent, Switches.PERMISSION,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val on = if (resultCode == Activity.RESULT_OK) getResultExtras(false)?.getBoolean(Switches.RESULT_ON) else null
                    onResult(on)
                }
            },
            Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED, null, null,
        )
    }
}
