package com.anpaiss.karooswitches.popupturn

/**
 * One popup at a time across our extensions, the decision part: pure Kotlin, no
 * Android, the clock injected. [PopupTurn] wires it to the broadcasts.
 *
 * Each popup (a [self] name, e.g. "bettereta.mta") tells the others three things:
 * CLAIM (it wants the screen, for a short window), SHOWN (it is on screen, until
 * an expiry) and GONE. Every entry carries an expiry, so an app that crashed with
 * its popup up never blocks the others for longer than that.
 *
 * A request shows at once when nothing else is on screen and no claim of a higher
 * priority arrives during the claim window; otherwise it waits for the GONE (or the
 * expiry) and claims again. While waiting it is looked at again every second, so a
 * request whose reason is gone ([request]'s stillWanted) or that waited too long
 * is dropped instead of appearing late.
 *
 * All calls on one thread (the main one in [PopupTurn]).
 */
class PopupTurnLogic(
    private val self: String,
    private val clock: () -> Long,
    private val port: Port,
) {
    interface Port {
        /** Tell the others: [state] CLAIM, SHOWN or GONE, valid until [expiresAt] (clock time). */
        fun announce(state: String, priority: Int, expiresAt: Long)
        /** Call [tick] at [time] (clock time); a later call replaces an earlier one. */
        fun wakeAt(time: Long)
        fun log(message: String)
    }

    companion object {
        const val CLAIM = "CLAIM"
        const val SHOWN = "SHOWN"
        const val GONE = "GONE"
        const val CLAIM_WINDOW_MS = 300L
        /** Added to a popup's own timeout in its SHOWN expiry. */
        const val MARGIN_MS = 5_000L
        /** A waiting request looks again at least this often (stillWanted, max wait). */
        const val RECHECK_MS = 1_000L
        const val NO_LIMIT = Long.MAX_VALUE
    }

    private class Entry(val state: String, val priority: Int, val expiresAt: Long)
    private val others = HashMap<String, Entry>()

    private class Request(
        val priority: Int, val holdMs: Long, val requestedAt: Long, val deadline: Long,
        val stillWanted: () -> Boolean, val show: () -> Unit,
    )
    private var pending: Request? = null
    private var claimEndsAt = 0L          // 0 = not claiming
    private var waitingFor: String? = null   // last blocker logged, one line per blocker
    private var holdPriority = 0
    private var holdExpiresAt = 0L

    /** True while our popup has the screen (between the turn and [done]). */
    var holding = false
        private set

    /**
     * Our popup wants the screen for [holdMs] (its own auto-dismiss); [show] runs when
     * it gets the turn. A request while we already hold the screen shows at once (the
     * popup replaces its content, as before); one while waiting replaces the waiting one.
     */
    fun request(priority: Int, holdMs: Long, maxWaitMs: Long = NO_LIMIT,
                stillWanted: () -> Boolean = { true }, show: () -> Unit) {
        val now = clock()
        if (holding) {
            holdPriority = priority
            holdExpiresAt = now + holdMs + MARGIN_MS
            port.announce(SHOWN, priority, holdExpiresAt)
            show()
            return
        }
        val deadline = if (maxWaitMs == NO_LIMIT) NO_LIMIT else now + maxWaitMs
        pending = Request(priority, holdMs, pending?.requestedAt ?: now, deadline, stillWanted, show)
        evaluate()
    }

    /** Our popup is gone (closed, timed out, failed to show), or the request is no longer wanted. */
    fun done() {
        if (holding) {
            holding = false
            port.announce(GONE, holdPriority, clock())
        } else if (pending != null) {
            port.log("Popup $self: request cancelled while waiting")
            if (claimEndsAt != 0L) port.announce(GONE, pending!!.priority, clock())
            pending = null
            claimEndsAt = 0L
            waitingFor = null
        }
    }

    /** A CLAIM / SHOWN / GONE of another popup (ours are ignored). */
    fun onRemote(name: String, state: String, priority: Int, expiresAt: Long) {
        if (name == self) return
        if (state == GONE) others.remove(name) else others[name] = Entry(state, priority, expiresAt)
        // A newcomer claiming (or an app just restarted) learns that we are on screen
        if (state == CLAIM && holding) port.announce(SHOWN, holdPriority, holdExpiresAt)
        if (pending != null) evaluate()
    }

    fun tick() {
        if (pending != null) evaluate()
    }

    private fun evaluate() {
        val r = pending ?: return
        val now = clock()
        if (!r.stillWanted()) return drop(r, now, "no longer needed")
        if (now >= r.deadline) return drop(r, now, "waited longer than ${(r.deadline - r.requestedAt) / 1000} s")
        others.entries.removeAll { it.value.expiresAt <= now }

        val shown = others.entries.filter { it.value.state == SHOWN }.minByOrNull { it.value.expiresAt }
        if (shown != null) {
            claimEndsAt = 0L
            return wait(r, now, "${shown.key} on screen", shown.value.expiresAt)
        }
        if (claimEndsAt == 0L) {
            val end = now + CLAIM_WINDOW_MS
            claimEndsAt = end
            port.announce(CLAIM, r.priority, end + CLAIM_WINDOW_MS)
            port.wakeAt(end)
            return
        }
        if (now < claimEndsAt) { port.wakeAt(claimEndsAt); return }

        val outranking = others.entries
            .filter { it.value.state == CLAIM && outranks(it.key, it.value.priority, r.priority) }
            .minByOrNull { it.value.expiresAt }
        if (outranking != null) {
            claimEndsAt = 0L
            return wait(r, now, "${outranking.key} claimed the turn", outranking.value.expiresAt)
        }

        pending = null
        claimEndsAt = 0L
        holding = true
        holdPriority = r.priority
        holdExpiresAt = now + r.holdMs + MARGIN_MS
        port.announce(SHOWN, r.priority, holdExpiresAt)
        if (waitingFor != null) port.log("Popup $self gets its turn after %.1f s".format(java.util.Locale.ROOT, (now - r.requestedAt) / 1000.0))
        waitingFor = null
        r.show()
    }

    /** Higher priority first; same priority: the smaller name, so both sides agree. */
    private fun outranks(name: String, priority: Int, ours: Int): Boolean =
        priority > ours || (priority == ours && name < self)

    private fun wait(r: Request, now: Long, why: String, until: Long) {
        val blocker = why.substringBefore(' ')
        if (waitingFor != blocker) {
            waitingFor = blocker
            port.log("Popup $self waits: $why")
        }
        port.wakeAt(minOf(until, r.deadline, now + RECHECK_MS))
    }

    private fun drop(r: Request, now: Long, why: String) {
        port.log("Popup $self dropped after %.1f s: $why".format(java.util.Locale.ROOT, (now - r.requestedAt) / 1000.0))
        if (claimEndsAt != 0L) port.announce(GONE, r.priority, now)
        pending = null
        claimEndsAt = 0L
        waitingFor = null
    }
}
