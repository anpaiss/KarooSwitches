package com.anpaiss.karooswitches.popupturn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupTurnLogicTest {

    /** Several apps on one fake clock; announcements are delivered at once to everyone else. */
    private class Bus {
        var now = 1_000_000L
        val apps = mutableListOf<App>()
        val logs = mutableListOf<String>()
        fun app(name: String) = App(name, this).also { apps += it }
        /** Moves the clock in 50 ms steps, firing the wake-ups that come due. */
        fun advance(ms: Long) {
            val end = now + ms
            while (now < end) {
                now = minOf(end, now + 50)
                apps.forEach { a -> if (a.wakeAt in 0..now) { a.wakeAt = -1; a.logic.tick() } }
            }
        }
    }

    private class App(val name: String, val bus: Bus) {
        var wakeAt = -1L
        var shownAt = -1L
        val announced = mutableListOf<String>()
        val logic: PopupTurnLogic = PopupTurnLogic(name, { bus.now }, object : PopupTurnLogic.Port {
            override fun announce(state: String, priority: Int, expiresAt: Long) {
                announced += state
                bus.apps.filter { it !== this@App }.forEach { it.logic.onRemote(name, state, priority, expiresAt) }
            }
            override fun wakeAt(time: Long) { wakeAt = time }
            override fun log(message: String) { bus.logs += message }
        })
        fun request(priority: Int, holdMs: Long = 45_000L, maxWaitMs: Long = PopupTurnLogic.NO_LIMIT,
                    stillWanted: () -> Boolean = { true }) =
            logic.request(priority, holdMs, maxWaitMs, stillWanted) { shownAt = bus.now }
        val shown get() = shownAt >= 0
    }

    @Test fun alone_showsAfterTheClaimWindowOnly() {
        val bus = Bus()
        val a = bus.app("a")
        val t0 = bus.now
        a.request(10)
        assertFalse(a.shown)
        bus.advance(PopupTurnLogic.CLAIM_WINDOW_MS)
        assertEquals(t0 + PopupTurnLogic.CLAIM_WINDOW_MS, a.shownAt)
        assertEquals(listOf("CLAIM", "SHOWN"), a.announced)
        assertTrue(bus.logs.isEmpty())   // nothing waited: no extra line
    }

    @Test fun waitsWhileAnotherIsOnScreen_thenShowsOnGone() {
        val bus = Bus()
        val a = bus.app("a"); val b = bus.app("b")
        a.request(10); bus.advance(400)
        assertTrue(a.shown)
        b.request(50); bus.advance(10_000)
        assertFalse(b.shown)
        a.logic.done()
        bus.advance(400)
        assertTrue(b.shown)
        assertTrue(bus.logs.any { it.startsWith("Popup b waits: a on screen") })
        assertTrue(bus.logs.any { it.startsWith("Popup b gets its turn after") })
    }

    @Test fun aCrashedHolderBlocksOnlyUntilItsExpiry() {
        val bus = Bus()
        val a = bus.app("a"); val b = bus.app("b")
        a.request(10, holdMs = 10_000L); bus.advance(400)
        b.request(10)
        bus.advance(10_000L + PopupTurnLogic.MARGIN_MS - 500)   // a never says GONE
        assertFalse(b.shown)
        bus.advance(1_000)
        assertTrue(b.shown)
    }

    @Test fun simultaneousRequests_higherPriorityFirst() {
        val bus = Bus()
        val plan = bus.app("bettereta.plan"); val mta = bus.app("bettereta.mta"); val msg = bus.app("notifybridge.message")
        plan.request(PopupTurn.PRIORITY_PLAN)
        msg.request(PopupTurn.PRIORITY_MESSAGE)
        mta.request(PopupTurn.PRIORITY_MTA)
        bus.advance(400)
        assertTrue(mta.shown); assertFalse(plan.shown); assertFalse(msg.shown)
        mta.logic.done(); bus.advance(400)
        assertTrue(plan.shown); assertFalse(msg.shown)
        plan.logic.done(); bus.advance(400)
        assertTrue(msg.shown)
    }

    @Test fun samePriority_tieBrokenByName() {
        val bus = Bus()
        val z = bus.app("z"); val a = bus.app("a")
        z.request(30); a.request(30)
        bus.advance(400)
        assertTrue(a.shown); assertFalse(z.shown)
    }

    @Test fun lowerPriorityClaimDuringOurWindowDoesNotStopUs() {
        val bus = Bus()
        val hi = bus.app("hi"); val lo = bus.app("lo")
        hi.request(50); bus.advance(100)
        lo.request(10); bus.advance(400)
        assertTrue(hi.shown); assertFalse(lo.shown)
    }

    @Test fun waitingRequestDroppedWhenNoLongerWanted() {
        val bus = Bus()
        val a = bus.app("a"); val plan = bus.app("plan")
        a.request(50); bus.advance(400)
        var rideStarted = false
        plan.request(20, stillWanted = { !rideStarted }); bus.advance(5_000)
        rideStarted = true
        bus.advance(1_100)        // looked at again within a second
        a.logic.done(); bus.advance(1_000)
        assertFalse(plan.shown)
        assertTrue(bus.logs.any { it.startsWith("Popup plan dropped") && it.endsWith("no longer needed") })
    }

    @Test fun waitingRequestDroppedAfterMaxWait() {
        val bus = Bus()
        val alarm = bus.app("alarm"); val msg = bus.app("msg")
        alarm.request(60, holdMs = 600_000L); bus.advance(400)
        msg.request(10, holdMs = 10_000L, maxWaitMs = 30_000L)
        bus.advance(29_000); assertTrue(bus.logs.none { it.contains("dropped") })
        bus.advance(1_100)
        assertTrue(bus.logs.any { it.startsWith("Popup msg dropped after 30.0 s") })
        alarm.logic.done(); bus.advance(1_000)
        assertFalse(msg.shown)
    }

    @Test fun requestWhileHoldingShowsAtOnce() {
        val bus = Bus()
        val a = bus.app("a")
        a.request(10); bus.advance(400)
        val first = a.shownAt
        bus.advance(2_000)
        a.request(10)
        assertTrue(a.shownAt > first)
        assertEquals(bus.now, a.shownAt)
    }

    @Test fun cancelledWhileWaiting_neverShows() {
        val bus = Bus()
        val a = bus.app("a"); val b = bus.app("b")
        a.request(10); bus.advance(400)
        b.request(10); bus.advance(1_000)
        b.logic.done()
        a.logic.done(); bus.advance(2_000)
        assertFalse(b.shown)
    }

    @Test fun newcomerLearnsAboutAPopupAlreadyOnScreen() {
        val bus = Bus()
        val a = bus.app("a")
        a.request(10); bus.advance(400)
        val late = bus.app("late")       // started after a's SHOWN went out
        late.request(60); bus.advance(1_000)
        assertFalse(late.shown)          // a answered the claim with SHOWN again
        a.logic.done(); bus.advance(400)
        assertTrue(late.shown)
    }

    @Test fun ownBroadcastsAreIgnored() {
        val bus = Bus()
        val a = bus.app("a")
        a.logic.onRemote("a", PopupTurnLogic.SHOWN, 99, bus.now + 60_000)
        a.request(10); bus.advance(400)
        assertTrue(a.shown)
    }
}
