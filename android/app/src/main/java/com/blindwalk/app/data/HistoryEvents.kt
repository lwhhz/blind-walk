package com.blindwalk.app.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 历史数据版本号。
 *
 * 首页列表是缓存住的，回收站里还原/彻底删除之后回到首页不会自动重扫目录，
 * 看起来就像"没刷新"。任何改动历史数据的操作都调 [bump]，
 * 首页监听这个版本号重新载入即可。
 */
object HistoryEvents {
    val version = MutableStateFlow(0)
    fun bump() { version.value++ }
}