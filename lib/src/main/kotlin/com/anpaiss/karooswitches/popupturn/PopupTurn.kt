package com.anpaiss.karooswitches.popupturn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * One popup at a time across our extensions: a popup asks for the turn before it
 * shows and gives it back when it is gone; meanwhile the others wait. The decision
 * is in [PopupTurnLogic]; this class carries it over a broadcast between the apps.
 *
 * Broadcast contract (copied as is by apps that do not use this library, e.g.
 * NotifyBridge): action [ACTION], extras [EXTRA_NAME] (the popup, e.g.
 * "poiahead.poi"), [EXTRA_STATE] (CLAIM / SHOWN / GONE), [EXTRA_PRIORITY] and
 * [EXTRA_EXPIRES_AT] (SystemClock.elapsedRealtime, the same clock for every app).
 * Received by a runtime receiver, so an implicit broadcast reaches it.
 *
 * Usage: [request] instead of showing, [done] when the popup is removed (by tap,
 * timeout, hide or a failed addView). Callable from any thread; [request]'s show
 * runs on the main thread.
 */
class PopupTurn(
    context: Context,
    private val name: String,
    private val log: (String) -> Unit,
) {
    companion object {
        const val ACTION = "com.anpaiss.popupturn.STATE"
        const val EXTRA_NAME = "name"
        const val EXTRA_STATE = "state"
        const val EXTRA_PRIORITY = "priority"
        const val EXTRA_EXPIRES_AT = "expires_at"

        // Who goes first when two want the screen at the same moment
        const val PRIORITY_INREACH_ALARM = 60
        const val PRIORITY_MTA = 50
        const val PRIORITY_BATTERY = 40
        const val PRIORITY_POI = 30
        const val PRIORITY_PLAN = 20
        const val PRIORITY_MESSAGE = 10
    }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val tick: Runnable = Runnable { logic.tick() }

    private val logic: PopupTurnLogic = PopupTurnLogic(name, SystemClock::elapsedRealtime, object : PopupTurnLogic.Port {
        override fun announce(state: String, priority: Int, expiresAt: Long) {
            runCatching {
                app.sendBroadcast(Intent(ACTION)
                    .putExtra(EXTRA_NAME, name)
                    .putExtra(EXTRA_STATE, state)
                    .putExtra(EXTRA_PRIORITY, priority)
                    .putExtra(EXTRA_EXPIRES_AT, expiresAt))
            }
        }
        override fun wakeAt(time: Long) {
            main.removeCallbacks(tick)
            main.postDelayed(tick, (time - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        }
        override fun log(message: String) = this@PopupTurn.log(message)
    })

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val from = i.getStringExtra(EXTRA_NAME) ?: return
            val state = i.getStringExtra(EXTRA_STATE) ?: return
            logic.onRemote(from, state, i.getIntExtra(EXTRA_PRIORITY, 0), i.getLongExtra(EXTRA_EXPIRES_AT, 0L))
        }
    }

    init {
        // Delivered on the main thread, like every other call into logic
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, IntentFilter(ACTION), Context.RECEIVER_EXPORTED)
        else app.registerReceiver(receiver, IntentFilter(ACTION))
    }

    /** See [PopupTurnLogic.request]. */
    fun request(priority: Int, holdMs: Long, maxWaitMs: Long = PopupTurnLogic.NO_LIMIT,
                stillWanted: () -> Boolean = { true }, show: () -> Unit) {
        main.post { logic.request(priority, holdMs, maxWaitMs, stillWanted, show) }
    }

    fun done() { main.post { logic.done() } }

    /** Gives the turn back and stops listening (service destroyed). */
    fun close() {
        main.post {
            logic.done()
            main.removeCallbacks(tick)
            runCatching { app.unregisterReceiver(receiver) }
        }
    }
}
