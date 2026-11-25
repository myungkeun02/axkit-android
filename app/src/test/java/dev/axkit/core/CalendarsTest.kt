package dev.axkit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §36: many calendars on the schedule screen. */
class CalendarsTest {
    private val raw = """
        {"month":"2026-11","days":[{"date":"2026-11-25","events":[
          {"id":"a","title":"직원 회의","start":"2026-11-25T01:00:00Z","calendar":"모아카페","account":"owner@acme.example"},
          {"id":"b","title":"치과","start":"2026-11-25T09:00:00Z"}
        ]}],
        "calendars":[
          {"id":"hol","name":"대한민국의 휴일","primary":false,"writable":false},
          {"id":"cafe","name":"모아카페","primary":false,"writable":true,"description":"카페 근무","account":"owner@acme.example"},
          {"id":"me","name":"내 캘린더","primary":true,"writable":true}
        ],
        "calendarsLimited":true}
    """.trimIndent()
    private val view = ProtocolJson.decodeFromString(CalendarView.serializer(), raw)
    private val cals = view.calendars

    @Test fun decodesNewFields() {
        assertEquals(3, cals!!.size)
        assertEquals(true, view.calendarsLimited)
        val e = view.days.single().events[0]
        assertEquals("모아카페", e.calendar)
        assertEquals("owner@acme.example", e.account)
    }

    @Test fun olderServerSendsNothing() {
        val old = ProtocolJson.decodeFromString(CalendarView.serializer(), """{"month":"2026-11","days":[{"date":"2026-11-25","events":[{"id":"a","title":"x","start":"2026-11-25T01:00:00Z"}]}]}""")
        assertNull(old.calendars)
        assertNull(old.calendarsLimited)
        assertTrue(Calendars.legend(old.calendars).isEmpty())
        assertFalse(Calendars.showPicker(old.calendars))
        assertNull(Calendars.defaultId(old.calendars))
        assertEquals(0, Calendars.slotOf(old.days[0].events[0], old.calendars))
        assertNull(Calendars.label(old.days[0].events[0]))
    }

    @Test fun pickerHasWritableOnlyMainFirst() {
        assertEquals(listOf("me", "cafe"), Calendars.writable(cals).map { it.id })
        assertEquals("me", Calendars.defaultId(cals))
        assertTrue(Calendars.showPicker(cals))
        // Only one writable calendar: no picker.
        assertFalse(Calendars.showPicker(cals!!.filter { it.id != "cafe" }))
    }

    @Test fun colorsAreStablePerId() {
        assertEquals(0, Calendars.colorSlot("me", primary = true))
        val slot = Calendars.slotIn(cals!![1], cals)
        assertEquals(slot, Calendars.slotIn(cals[1], cals.reversed()))
        assertTrue(slot in 1 until Calendars.PALETTE)
        // The event on 모아카페 takes that calendar's color; one without a name takes the main one.
        assertEquals(slot, Calendars.slotOf(view.days[0].events[0], cals))
        assertEquals(0, Calendars.slotOf(view.days[0].events[1], cals))
    }

    @Test fun noTwoCalendarsShareAColorWhileColorsLeft() {
        val many = (1..7).map { CalendarView.Calendar(id = "cal-$it", name = "c$it", writable = true) } +
            CalendarView.Calendar(id = "main", name = "main", primary = true, writable = true)
        val slots = Calendars.slots(many)
        assertEquals(8, slots.values.toSet().size)
        assertEquals(0, slots["main"])
        // Same list in another order → same colors.
        assertEquals(slots, Calendars.slots(many.reversed()))
    }

    @Test fun legendAndLabels() {
        assertEquals(listOf("me", "hol", "cafe"), Calendars.legend(cals).map { it.id })
        assertTrue(Calendars.legend(cals!!.take(1)).isEmpty())
        assertEquals("모아카페 · owner@acme.example", Calendars.label(view.days[0].events[0]))
        assertEquals("카페 근무", Calendars.pickerDetail(cals[1], cals))
    }

    @Test fun todayEventCarriesCalendar() {
        val t = ProtocolJson.decodeFromString(TodayView.serializer(), """{"date":"2026-11-25","events":[{"title":"회의","start":"2026-11-25T01:00:00Z","calendar":"모아카페"}]}""")
        assertEquals("모아카페", t.events!!.single().calendar)
    }
}
