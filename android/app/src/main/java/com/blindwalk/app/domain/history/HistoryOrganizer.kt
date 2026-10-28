package com.blindwalk.app.domain.history

import androidx.compose.runtime.Immutable
import com.blindwalk.app.domain.model.SessionRecord
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class SortKey(val label: String) {
    TIME("时间"),
    DISTANCE("距离"),
    INSTRUCTIONS("次数")
}

/**
 * 这些模型都标成 @Immutable：Compose 默认会把带 List 字段的类判为不稳定，
 * 每次重组都要重新比较，导致列表项无法跳过重组。它们的实例是重建而非原地修改的。
 */
@Immutable
data class SortSpec(
    val key: SortKey = SortKey.TIME,
    val descending: Boolean = true
) {
    val label: String get() = "${key.label} ${if (descending) "↓" else "↑"}"
}

enum class SectionKind { PINNED, MONTH }

@Immutable
data class HistorySection(
    val key: String,
    val title: String,
    val kind: SectionKind,
    val items: List<SessionRecord>
)

@Immutable
data class HistoryStats(
    val count: Int,
    val distanceMeters: Double,
    val activeMillis: Long
) {
    companion object {
        val EMPTY = HistoryStats(0, 0.0, 0L)
    }
}

/**
 * 首页列表的组织逻辑：置顶区 → 月份分组 → 组内排序。
 *
 * 纯函数，全部可单测。UI 只负责把结果渲染出来。
 */
object HistoryOrganizer {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /**
     * 排序。主键按 [spec]，同值时**一律**用开始时间倒序兜底——
     * 否则排序不稳定，同样的数据每次刷新顺序不同，用户会以为出了 bug。
     */
    fun sort(items: List<SessionRecord>, spec: SortSpec): List<SessionRecord> {
        val primary: Comparator<SessionRecord> = when (spec.key) {
            SortKey.TIME -> compareBy { it.startedAt }
            SortKey.DISTANCE -> compareBy { it.distanceMeters }
            SortKey.INSTRUCTIONS -> compareBy { it.instructionCount }
        }
        val ordered = if (spec.descending) primary.reversed() else primary
        return items.sortedWith(ordered.thenByDescending { it.startedAt })
    }

    /**
     * 分组。置顶项单独成区并排在最前，其余按月倒序分组，
     * 每组内部再按 [spec] 排序（置顶区也遵循同一排序）。
     */
    fun build(records: List<SessionRecord>, spec: SortSpec): List<HistorySection> {
        val (pinned, rest) = records.partition { it.pinned }
        val sections = ArrayList<HistorySection>()

        if (pinned.isNotEmpty()) {
            sections += HistorySection(
                key = "pinned",
                title = "置顶",
                kind = SectionKind.PINNED,
                items = sort(pinned, spec)
            )
        }

        rest.groupBy { YearMonth.from(Instant.ofEpochMilli(it.startedAt).atZone(zone)) }
            .toList()
            .sortedByDescending { (month, _) -> month }
            .forEach { (month, items) ->
                sections += HistorySection(
                    key = "month-${month.year}-${month.monthValue}",
                    title = "${month.year} 年 ${month.monthValue} 月",
                    kind = SectionKind.MONTH,
                    items = sort(items, spec)
                )
            }

        return sections
    }

    /** 顶部累计统计：全部（未删除）行程。 */
    fun stats(records: List<SessionRecord>, now: Long = System.currentTimeMillis()): HistoryStats {
        if (records.isEmpty()) return HistoryStats.EMPTY
        return HistoryStats(
            count = records.size,
            distanceMeters = records.sumOf { it.distanceMeters },
            activeMillis = records.sumOf { it.activeMillis(it.endedAt ?: now) }
        )
    }

    /** 多选模式下"全选"的候选集合：当前所有分区里的所有项。 */
    fun allIds(sections: List<HistorySection>): Set<String> =
        sections.flatMap { it.items }.map { it.id }.toSet()
}
