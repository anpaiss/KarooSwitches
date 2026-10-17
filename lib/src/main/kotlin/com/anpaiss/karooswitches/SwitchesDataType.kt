package com.anpaiss.karooswitches

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.TypedValue
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The "Switches" data field: the host's name on top, the state of its own
 * switch below (green ON / red OFF), a tap anywhere opens [SwitchesPopup]
 * with the switches of every installed extension.
 *
 * The host declares it in extension_info.xml with typeId "switches" and adds
 * `SwitchesDataType(extension)` to its `types`.
 */
class SwitchesDataType(extension: String) : DataTypeImpl(extension, "switches") {

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val tap = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, OpenSwitchesReceiver::class.java).setAction(OpenSwitchesReceiver.ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString().uppercase()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope.launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            Switches.changes.collect {
                val views = RemoteViews(context.packageName, R.layout.ks_field)
                views.setTextViewText(R.id.ks_caption, appName)
                views.setOnClickPendingIntent(R.id.ks_root, tap)
                val own = SwitchesClient.own(context)
                when {
                    !Settings.canDrawOverlays(context) -> {
                        views.setTextViewText(R.id.ks_state, "ALLOW OVERLAY")
                        views.setTextViewTextSize(R.id.ks_state, TypedValue.COMPLEX_UNIT_SP, 16f)
                        views.setTextColor(R.id.ks_state, context.getColor(R.color.ks_warn))
                        emitter.updateView(views)
                    }
                    own == null -> {
                        views.setTextViewText(R.id.ks_state, "SWITCHES")
                        views.setTextViewTextSize(R.id.ks_state, TypedValue.COMPLEX_UNIT_SP, 22f)
                        views.setTextColor(R.id.ks_state, context.getColor(R.color.ks_text))
                        emitter.updateView(views)
                    }
                    else -> SwitchesClient.send(context, own, Switches.OP_GET) { on ->
                        // The answer comes through the main Handler, possibly after the field was removed
                        if (!scope.isActive) return@send
                        views.setTextViewText(R.id.ks_state, when (on) { true -> "ON"; false -> "OFF"; null -> "?" })
                        views.setTextColor(R.id.ks_state, context.getColor(when (on) {
                            true -> R.color.ks_on; false -> R.color.ks_off; null -> R.color.ks_muted
                        }))
                        emitter.updateView(views)
                    }
                }
            }
        }
        emitter.setCancellable { scope.cancel() }
    }
}
