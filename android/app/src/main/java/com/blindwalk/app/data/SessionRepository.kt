package com.blindwalk.app.data

import com.blindwalk.app.domain.model.SessionRecord
import com.blindwalk.app.domain.model.TrackPoint
import com.blindwalk.app.domain.session.SessionReducer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * 文件式存储。每个会话一个目录：
 *   sessions/<id>/meta.json     元数据（原子写：tmp + rename）
 *   sessions/<id>/points.jsonl  轨迹点，只追加
 *   trash/<id>/                 回收站，结构与 sessions 相同，仅多一个 deletedAt 字段
 *
 * 为什么不用 Room：轨迹是高频追加、几乎不查询的时序数据，
 * JSONL 追加天然崩溃安全，也不需要 KSP 代码生成。
 *
 * 回收站放在 sessions 的**兄弟目录**，而不是 sessions 下面，
 * 这样 list() / findUnfinished() 天然不会误扫到已删除的行程。
 */
class SessionRepository(
    private val root: File,
    private val json: Json
) {

    private companion object {
        const val META = "meta.json"
        const val POINTS = "points.jsonl"
    }

    private val trashRoot: File = File(root.parentFile ?: root, "trash")

    /**
     * 元数据内存缓存。
     *
     * 首页每次回前台都会全量扫目录。几百条记录时那是几百次文件读 + JSON 解码，
     * 而绝大多数文件根本没变。这里用 (mtime, size) 做指纹，命中就跳过解码。
     */
    private data class MetaCache(val modified: Long, val length: Long, val record: SessionRecord)

    private val metaCache = ConcurrentHashMap<String, MetaCache>()

    // 数据变更信号：首页据此在回收站还原/删除后立即刷新。

    /**
     * 作废暂存区。「作废」要销毁数据，但给了 5 秒撤销窗口，
     * 所以先把目录搬到这里，超时才真删。放兄弟目录同样不会污染 list()。
     */
    private val discardRoot: File = File(root.parentFile ?: root, ".discard")

    fun sessionDir(id: String): File = File(root, id)

    fun trashDir(id: String): File = File(trashRoot, id)

    // ------------------------------------------------------------ 正常行程

    @Synchronized
    fun save(record: SessionRecord) = writeMeta(record, sessionDir(record.id))

    @Synchronized
    fun appendPoint(sessionId: String, point: TrackPoint) {
        val dir = sessionDir(sessionId).apply { mkdirs() }
        File(dir, POINTS).appendText(
            json.encodeToString(TrackPoint.serializer(), point) + "\n",
            Charsets.UTF_8
        )
    }

    fun load(id: String): SessionRecord? = readMeta(sessionDir(id))

    fun loadPoints(id: String): List<TrackPoint> = readPoints(sessionDir(id))

    fun list(): List<SessionRecord> = readAll(root)

    fun findUnfinished(): SessionRecord? =
        list().firstOrNull { it.status != com.blindwalk.app.domain.model.SessionStatus.ENDED }

    /** 进程被杀后按落盘点位重算路程，修掉元数据可能落后的距离。 */
    fun recomputeDistance(id: String): SessionRecord? {
        val record = load(id) ?: return null
        val points = loadPoints(id)
        val fixed = record.copy(distanceMeters = SessionReducer.recomputeDistance(points))
        save(fixed)
        return fixed
    }

    // -------------------------------------------------------------- 回收站

    /** 删除 = 移入回收站。不销毁任何数据。 */
    @Synchronized
    fun moveToTrash(record: SessionRecord, now: Long = System.currentTimeMillis()): Boolean {
        val source = sessionDir(record.id)
        if (!source.exists()) return false

        // 先把 deletedAt 写进 meta，再整体搬目录，保证搬完立刻可读。
        writeMeta(record.copy(deletedAt = now), source)

        val target = trashDir(record.id)
        if (target.exists()) target.deleteRecursively()
        trashRoot.mkdirs()
        return moveDir(source, target)
    }

    fun listTrash(): List<SessionRecord> =
        readAll(trashRoot).sortedByDescending { it.deletedAt ?: 0L }

    @Synchronized
    fun restore(id: String): Boolean {
        val source = trashDir(id)
        val record = readMeta(source) ?: return false
        val target = sessionDir(id)
        if (target.exists()) return false
        if (!moveDir(source, target)) return false
        writeMeta(record.copy(deletedAt = null), target)
        return true
    }

    @Synchronized
    fun purge(id: String): Boolean {
        metaCache.remove(trashDir(id).absolutePath)
        return trashDir(id).deleteRecursively()
    }

    @Synchronized
    fun purgeAll(): Boolean {
        metaCache.keys.removeIf { it.startsWith(trashRoot.absolutePath) }
        val dirs = trashRoot.listFiles() ?: return true
        var ok = true
        dirs.forEach { if (!it.deleteRecursively()) ok = false }
        return ok
    }

    /** 回收站占用字节数，用于在页面上提示"你为正躺在里面的数据花了多少空间"。 */
    fun trashSizeBytes(): Long {
        val dirs = trashRoot.listFiles() ?: return 0L
        return dirs.filter { it.isDirectory }.sumOf { dirSize(it) }
    }

    // -------------------------------------------------------------- 作废暂存

    /** 作废第一步：把行程搬到暂存区，此时还来得及撤销。 */
    @Synchronized
    fun discardToPending(id: String): Boolean {
        val source = sessionDir(id)
        if (!source.exists()) return false
        val target = File(discardRoot, id)
        if (target.exists()) target.deleteRecursively()
        discardRoot.mkdirs()
        return moveDir(source, target)
    }

    /** 撤销作废：搬回原处。行程状态没被改过，服务恢复后能接着走。 */
    @Synchronized
    fun restoreDiscarded(id: String): Boolean {
        val source = File(discardRoot, id)
        if (!source.exists()) return false
        val target = sessionDir(id)
        if (target.exists()) return false
        return moveDir(source, target)
    }

    /** 作废第二步：撤销窗口过去，真删。 */
    @Synchronized
    fun purgeDiscarded(id: String): Boolean = File(discardRoot, id).deleteRecursively()

    /** 进程被杀留下的孤儿暂存目录，启动时清掉。 */
    @Synchronized
    fun sweepDiscarded(olderThanMillis: Long, now: Long = System.currentTimeMillis()): Int {
        val dirs = discardRoot.listFiles() ?: return 0
        var removed = 0
        dirs.forEach { dir ->
            if (now - dir.lastModified() > olderThanMillis && dir.deleteRecursively()) removed++
        }
        return removed
    }

    // ---------------------------------------------------------------- 内部

    private fun writeMeta(record: SessionRecord, dir: File) {
        dir.mkdirs()
        val target = File(dir, META)
        val tmp = File(dir, "$META.tmp")
        tmp.writeText(json.encodeToString(SessionRecord.serializer(), record), Charsets.UTF_8)
        try {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        // 自己写的就直接进缓存，省掉下一次读盘解码。
        metaCache[dir.absolutePath] = MetaCache(target.lastModified(), target.length(), record)
    }

    private fun readMeta(dir: File): SessionRecord? {
        val file = File(dir, META)
        if (!file.exists()) return null

        val key = dir.absolutePath
        val modified = file.lastModified()
        val length = file.length()
        metaCache[key]?.let { cached ->
            if (cached.modified == modified && cached.length == length) return cached.record
        }

        val record = runCatching {
            json.decodeFromString(SessionRecord.serializer(), file.readText(Charsets.UTF_8))
        }.getOrNull() ?: return null

        metaCache[key] = MetaCache(modified, length, record)
        return record
    }

    private fun readPoints(dir: File): List<TrackPoint> {
        val file = File(dir, POINTS)
        if (!file.exists()) return emptyList()
        return file.readLines(Charsets.UTF_8).mapNotNull { line ->
            if (line.isBlank()) null
            else runCatching {
                json.decodeFromString(TrackPoint.serializer(), line)
            }.getOrNull()
        }
    }

    private fun readAll(dir: File): List<SessionRecord> {
        val dirs = dir.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { readMeta(it) }.sortedByDescending { it.startedAt }
    }

    private fun moveDir(source: File, target: File): Boolean {
        return try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            true
        } catch (_: Exception) {
            runCatching {
                source.copyRecursively(target, overwrite = true)
                source.deleteRecursively()
                true
            }.getOrDefault(false)
        }
    }

    private fun dirSize(dir: File): Long {
        var total = 0L
        dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }
}
