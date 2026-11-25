package dev.axkit.core

import kotlinx.serialization.Serializable

/** §36 `POST views/calendar/add` answer: the new event and the calendar it went into. */
@Serializable data class CalendarAdded(val id: String? = null, val calendar: String? = null)

/** §36: many calendars — a stable color per calendar, the add picker, and the "only the main calendar" line. */
object Calendars {
    const val LIMITED = "기본 캘린더만 보여요 — 비서 맥에서 ax-kit connect google을 한 번 더 해 주세요"

    /** Number of colors the app has for calendars (see CalendarColors in the UI). */
    const val PALETTE = 8

    /**
     * A palette slot that stays the same for one calendar id across launches and devices.
     * The main calendar always gets slot 0 (the accent blue), so a one-calendar owner sees the usual color.
     */
    fun colorSlot(id: String, primary: Boolean = false): Int {
        if (primary) return 0
        var h = 0x811c9dc5.toInt()
        for (c in id) { h = h xor c.code; h *= 0x01000193 }
        return 1 + Math.floorMod(h, PALETTE - 1)
    }

    /**
     * Slots for a whole list: each calendar keeps its own [colorSlot] unless another (earlier by id) already took it,
     * then it moves to the next free one. Same list in → same colors out, and no two calendars share a color while
     * there are colors left.
     */
    fun slots(calendars: List<CalendarView.Calendar>?): Map<String, Int> {
        val list = calendars.orEmpty()
        val out = LinkedHashMap<String, Int>()
        list.filter { it.primary }.forEach { out[it.id] = 0 }
        val taken = out.values.toMutableSet()
        for (c in list.filter { !it.primary }.sortedBy { it.id }) {
            if (c.id in out) continue
            var slot = colorSlot(c.id)
            if (taken.size < PALETTE) {
                var tries = 0
                while (slot in taken && tries < PALETTE) { slot = 1 + slot % (PALETTE - 1); tries++ }
            }
            out[c.id] = slot
            taken += slot
        }
        return out
    }

    /** One calendar's slot inside its list. */
    fun slotIn(cal: CalendarView.Calendar, calendars: List<CalendarView.Calendar>?): Int =
        slots(calendars)[cal.id] ?: colorSlot(cal.id, cal.primary)

    /** The calendar an event belongs to: the server names it, so match the name (then the id). */
    fun of(event: CalendarView.Event, calendars: List<CalendarView.Calendar>?): CalendarView.Calendar? {
        val name = event.calendar ?: return calendars?.firstOrNull { it.primary }
        val same = calendars?.filter { it.name == name }.orEmpty()
        return same.firstOrNull { event.account == null || it.account == null || it.account == event.account }
            ?: calendars?.firstOrNull { it.id == name }
    }

    /** Color slot for an event: its calendar's, or a slot from the name when the list is missing. */
    fun slotOf(event: CalendarView.Event, calendars: List<CalendarView.Calendar>?): Int {
        val cal = of(event, calendars)
        return when {
            cal != null -> slotIn(cal, calendars)
            event.calendar != null -> colorSlot(event.calendar)
            else -> 0
        }
    }

    /** The legend: only when there are two or more calendars. */
    fun legend(calendars: List<CalendarView.Calendar>?): List<CalendarView.Calendar> =
        calendars.orEmpty().let { list -> if (list.size >= 2) list.sortedByDescending { it.primary } else emptyList() }

    /** Calendars the owner can add to, the main one first. */
    fun writable(calendars: List<CalendarView.Calendar>?): List<CalendarView.Calendar> =
        calendars.orEmpty().filter { it.writable }.sortedByDescending { it.primary }

    /** The picker shows only when there is a real choice. */
    fun showPicker(calendars: List<CalendarView.Calendar>?): Boolean = writable(calendars).size >= 2

    /** Default choice: the main writable calendar (the first after sorting). */
    fun defaultId(calendars: List<CalendarView.Calendar>?): String? = writable(calendars).firstOrNull()?.id

    /** "업무 · work@x.com" under the event title; account only when it is sent. */
    fun label(event: CalendarView.Event): String? =
        listOfNotNull(event.calendar?.takeIf { it.isNotBlank() }, event.account?.takeIf { it.isNotBlank() })
            .joinToString(" · ").ifEmpty { null }

    /** A picker row's second line: the account when two calendars share a name, else the description. */
    fun pickerDetail(cal: CalendarView.Calendar, all: List<CalendarView.Calendar>): String? =
        (if (all.count { it.name == cal.name } > 1) cal.account else null) ?: cal.description?.takeIf { it.isNotBlank() } ?: cal.account
}
