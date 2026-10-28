package com.blindwalk.app.domain

import com.blindwalk.app.domain.history.HistoryOrganizer
import com.blindwalk.app.domain.history.SectionKind
import com.blindwalk.app.domain.history.SortKey
import com.blindwalk.app.domain.history.SortSpec
import com.blindwalk.app.domain.model.SessionRecord
import com.blindwalk.app.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryOrganizerTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDateTime.of(year, month, day, hour, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun rec(
        id: String,
        start: Long,
        distance: Double = 0.0,
        count: Int = 0,
        pinned: Boolean = false
    ) = SessionRecord(
        id = id,
        startedAt = start,
        status = SessionStatus.ENDED,
        endedAt = start + 60_000,
        activeMillisBase = 60_000,
        distanceMeters = distance,
        instructionCount = count,
        pinned = pinned
    )

    @Test
    fun `default sort is newest first`() {
        val a = rec("a", at(2026, 10, 1))
        val b = rec("b", at(2026, 10, 2))
        val sorted = HistoryOrganizer.sort(listOf(a, b), SortSpec())
        assertEquals(listOf("b", "a"), sorted.map { it.id })
    }

    @Test
    fun `sort by distance ascending`() {
        val a = rec("a", at(2026, 10, 1), distance = 500.0)
        val b = rec("b", at(2026, 10, 2), distance = 1200.0)
        val c = rec("c", at(2026, 10, 3), distance = 80.0)
        val sorted = HistoryOrganizer.sort(listOf(a, b, c), SortSpec(SortKey.DISTANCE, descending = false))
        assertEquals(listOf("c", "a", "b"), sorted.map { it.id })
    }

    @Test
    fun `sort by instructions descending`() {
        val a = rec("a", at(2026, 10, 1), count = 5)
        val b = rec("b", at(2026, 10, 2), count = 27)
        val sorted = HistoryOrganizer.sort(listOf(a, b), SortSpec(SortKey.INSTRUCTIONS, descending = true))
        assertEquals(listOf("b", "a"), sorted.map { it.id })
    }

    @Test
    fun `equal values fall back to newest first`() {
        val older = rec("older", at(2026, 10, 1), distance = 100.0)
        val newer = rec("newer", at(2026, 10, 5), distance = 100.0)
        val sorted = HistoryOrganizer.sort(listOf(older, newer), SortSpec(SortKey.DISTANCE))
        assertEquals(listOf("newer", "older"), sorted.map { it.id })
    }

    @Test
    fun `pinned section comes first and is excluded from months`() {
        val pinned = rec("pin", at(2026, 8, 1), pinned = true)
        val oct = rec("oct", at(2026, 10, 3))
        val sep = rec("sep", at(2026, 9, 3))

        val sections = HistoryOrganizer.build(listOf(pinned, oct, sep), SortSpec())

        assertEquals(3, sections.size)
        assertEquals(SectionKind.PINNED, sections[0].kind)
        assertEquals(listOf("pin"), sections[0].items.map { it.id })
        assertEquals(SectionKind.MONTH, sections[1].kind)
        assertTrue(sections[1].title.contains("10"))
        assertEquals(SectionKind.MONTH, sections[2].kind)
        assertTrue(sections[2].title.contains("9"))
    }

    @Test
    fun `months are ordered newest first`() {
        val aug = rec("aug", at(2026, 8, 5))
        val oct = rec("oct", at(2026, 10, 5))
        val sections = HistoryOrganizer.build(listOf(aug, oct), SortSpec())
        assertEquals(listOf("oct", "aug"), sections.flatMap { s -> s.items.map { it.id } })
    }

    @Test
    fun `pinned section also follows the chosen sort`() {
        val p1 = rec("p1", at(2026, 10, 1), distance = 900.0, pinned = true)
        val p2 = rec("p2", at(2026, 10, 2), distance = 100.0, pinned = true)
        val sections = HistoryOrganizer.build(listOf(p1, p2), SortSpec(SortKey.DISTANCE, descending = true))
        assertEquals(listOf("p1", "p2"), sections[0].items.map { it.id })
    }

    @Test
    fun `stats sum distance and active time`() {
        val a = rec("a", at(2026, 10, 1), distance = 1000.0)
        val b = rec("b", at(2026, 10, 2), distance = 2000.0)
        val stats = HistoryOrganizer.stats(listOf(a, b))
        assertEquals(2, stats.count)
        assertEquals(3000.0, stats.distanceMeters, 1e-6)
        assertEquals(120_000L, stats.activeMillis)
    }

    @Test
    fun `empty list yields no sections and zero stats`() {
        assertTrue(HistoryOrganizer.build(emptyList(), SortSpec()).isEmpty())
        assertEquals(0, HistoryOrganizer.stats(emptyList()).count)
    }

    @Test
    fun `allIds covers every section`() {
        val sections = HistoryOrganizer.build(
            listOf(
                rec("pin", at(2026, 8, 1), pinned = true),
                rec("oct", at(2026, 10, 3))
            ),
            SortSpec()
        )
        assertEquals(setOf("pin", "oct"), HistoryOrganizer.allIds(sections))
    }
}
