package com.blindwalk.app.data

import com.blindwalk.app.domain.model.SessionRecord
import com.blindwalk.app.domain.model.SessionStatus
import com.blindwalk.app.domain.model.TrackPoint
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SessionRepositoryTrashTest {

    private lateinit var base: File
    private lateinit var repo: SessionRepository

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        base = Files.createTempDirectory("blindwalk-test").toFile()
        repo = SessionRepository(File(base, "sessions"), json)
    }

    @After
    fun tearDown() {
        base.deleteRecursively()
    }

    private fun record(id: String, start: Long = 1_000L) = SessionRecord(
        id = id,
        startedAt = start,
        status = SessionStatus.ENDED,
        endedAt = start + 60_000,
        activeMillisBase = 60_000,
        distanceMeters = 123.4,
        instructionCount = 7
    )

    @Test
    fun `save then list round trip`() {
        repo.save(record("a"))
        val all = repo.list()
        assertEquals(1, all.size)
        assertEquals("a", all[0].id)
        assertEquals(123.4, all[0].distanceMeters, 1e-9)
    }

    @Test
    fun `moveToTrash hides from list and shows in trash`() {
        repo.save(record("a"))
        assertTrue(repo.moveToTrash(record("a"), now = 5_000L))

        assertTrue(repo.list().isEmpty())
        val trash = repo.listTrash()
        assertEquals(1, trash.size)
        assertEquals(5_000L, trash[0].deletedAt)
        assertTrue(repo.trashSizeBytes() > 0L)
    }

    @Test
    fun `trashed session is not treated as unfinished`() {
        val running = record("r").copy(status = SessionStatus.RUNNING, endedAt = null, segmentStartedAt = 1_000L)
        repo.save(running)
        assertEquals("r", repo.findUnfinished()?.id)

        repo.moveToTrash(running)
        assertNull(repo.findUnfinished())
    }

    @Test
    fun `restore brings it back with deletedAt cleared`() {
        repo.save(record("a"))
        repo.moveToTrash(record("a"))
        assertTrue(repo.restore("a"))

        assertEquals(1, repo.list().size)
        assertNull(repo.list()[0].deletedAt)
        assertTrue(repo.listTrash().isEmpty())
    }

    @Test
    fun `restore keeps track points`() {
        repo.save(record("a"))
        repo.appendPoint("a", TrackPoint(1_000L, 31.23, 121.47, 5f, 0))
        repo.moveToTrash(record("a"))
        repo.restore("a")

        assertEquals(1, repo.loadPoints("a").size)
    }

    @Test
    fun `purge removes permanently`() {
        repo.save(record("a"))
        repo.moveToTrash(record("a"))
        assertTrue(repo.purge("a"))

        assertTrue(repo.listTrash().isEmpty())
        assertFalse(repo.trashDir("a").exists())
    }

    @Test
    fun `purgeAll empties the bin`() {
        repo.save(record("a"))
        repo.save(record("b", 2_000L))
        repo.moveToTrash(record("a"))
        repo.moveToTrash(record("b", 2_000L))

        assertTrue(repo.purgeAll())
        assertTrue(repo.listTrash().isEmpty())
        assertEquals(0L, repo.trashSizeBytes())
    }

    @Test
    fun `trash metadata survives a reload`() {
        repo.save(record("a"))
        repo.moveToTrash(record("a"), now = 9_999L)

        val reloaded = SessionRepository(File(base, "sessions"), json).listTrash()
        assertEquals(1, reloaded.size)
        assertEquals(9_999L, reloaded[0].deletedAt)
        assertNotNull(reloaded[0].endedAt)
    }

    @Test
    fun `title and pinned survive serialization`() {
        repo.save(record("a").copy(title = "周五晚上的漫无目的", pinned = true))
        val loaded = repo.load("a")
        assertNotNull(loaded)
        assertEquals("周五晚上的漫无目的", loaded!!.title)
        assertTrue(loaded.pinned)
    }
}
