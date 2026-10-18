package com.anpaiss.karooswitches

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Protocol shared by all the extensions. Fixed with the first release: only
 * additions from here on, never a change, because different versions of the
 * extensions will live on the same device for ever.
 *
 * A switch is an exported [SwitchReceiver] in the host's manifest:
 *
 * ```xml
 * <receiver android:name=".WaterAlertsSwitch"
 *     android:exported="true"
 *     android:permission="com.anpaiss.karooswitches.CONTROL">
 *     <intent-filter><action android:name="com.anpaiss.karooswitches.SWITCH" /></intent-filter>
 *     <meta-data android:name="com.anpaiss.karooswitches.label" android:value="Water alerts" />
 * </receiver>
 * ```
 *
 * The popup sends an ordered broadcast with [EXTRA_OP] = [OP_GET] or
 * [OP_TOGGLE]; the receiver answers with [RESULT_ON] in the result extras.
 *
 * 0.2.0 adds the kind "time" (meta-data [META_KIND]): a [TimeReceiver] = an
 * ON/OFF switch plus, while ON, an adjustable time. Ops [OP_GET], [OP_TOGGLE]
 * (the switch) and [OP_ADJUST] with [EXTRA_DELTA_MIN] (the time); the answer
 * carries [RESULT_ON] (the switch) and [RESULT_TEXT] (the time, "" = not set).
 */
object Switches {
    const val ACTION     = "com.anpaiss.karooswitches.SWITCH"
    const val PERMISSION = "com.anpaiss.karooswitches.CONTROL"
    const val META_LABEL = "com.anpaiss.karooswitches.label"
    const val EXTRA_OP   = "op"
    const val OP_GET     = "get"
    const val OP_TOGGLE  = "toggle"
    const val RESULT_ON  = "on"
    const val META_KIND  = "com.anpaiss.karooswitches.kind"
    const val KIND_TIME  = "time"
    const val OP_ADJUST  = "adjust"
    const val EXTRA_DELTA_MIN = "delta"
    const val RESULT_TEXT = "text"

    /** Bumped whenever a switch of this process changes: the field redraws. */
    internal val changes = MutableStateFlow(0L)

    /** Hosts call this when their switch changed outside the receiver (own app UI). */
    fun notifyChanged() {
        changes.value = System.nanoTime()
    }
}
