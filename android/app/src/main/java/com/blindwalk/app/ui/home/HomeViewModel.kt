package com.blindwalk.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blindwalk.app.BlindWalkApp
import com.blindwalk.app.data.SessionRepository
import com.blindwalk.app.domain.history.HistoryOrganizer
import com.blindwalk.app.domain.history.HistorySection
import com.blindwalk.app.domain.history.HistoryStats
import com.blindwalk.app.domain.history.SortSpec
import com.blindwalk.app.domain.model.SessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: SessionRepository =
        (app as BlindWalkApp).container.repository

    private val _records = MutableStateFlow<List<SessionRecord>>(emptyList())
    private val _sections = MutableStateFlow<List<HistorySection>>(emptyList())
    val sections: StateFlow<List<HistorySection>> = _sections.asStateFlow()

    private val _stats = MutableStateFlow(HistoryStats.EMPTY)
    val stats: StateFlow<HistoryStats> = _stats.asStateFlow()

    private val _sort = MutableStateFlow(SortSpec())
    val sort: StateFlow<SortSpec> = _sort.asStateFlow()

    private val _trashCount = MutableStateFlow(0)
    val trashCount: StateFlow<Int> = _trashCount.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private var refreshJob: Job? = null

    fun refresh() {
        // 回前台、旋转、从总结页返回都可能连着触发，重入一次就多扫一遍目录。
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val all = repository.list()
            _records.value = all
            _sections.value = HistoryOrganizer.build(all, _sort.value)
            _stats.value = HistoryOrganizer.stats(all)
            _trashCount.value = repository.listTrash().size
        }
    }

    /** 前台服务写记录是异步的，隔一小会儿再扫一遍才能读到新状态。 */
    fun refreshSoon(delayMillis: Long = 300) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(delayMillis)
            refresh()
        }
    }

    fun setSort(spec: SortSpec) {
        _sort.value = spec
        _sections.value = HistoryOrganizer.build(_records.value, spec)
    }

    // ------------------------------------------------------------ 多选模式

    fun enterSelection(id: String) {
        _selectionMode.value = true
        _selection.value = setOf(id)
    }

    fun toggleSelection(id: String) {
        val current = _selection.value
        _selection.value = if (id in current) current - id else current + id
        if (_selection.value.isEmpty()) _selectionMode.value = false
    }

    fun selectAll() {
        _selection.value = HistoryOrganizer.allIds(_sections.value)
    }

    fun clearSelection() {
        _selectionMode.value = false
        _selection.value = emptySet()
    }

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = repository.load(id) ?: return@launch
            repository.save(record.copy(pinned = pinned))
            val all = repository.list()
            _records.value = all
            _sections.value = HistoryOrganizer.build(all, _sort.value)
            _stats.value = HistoryOrganizer.stats(all)
        }
    }

    /** 单条删除：滑动后点「删除」即生效，不做二次确认，靠 Snackbar 提供撤销。 */
    fun deleteToTrash(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.load(id)?.let { repository.moveToTrash(it) }
            val all = repository.list()
            _records.value = all
            _sections.value = HistoryOrganizer.build(all, _sort.value)
            _stats.value = HistoryOrganizer.stats(all)
            _trashCount.value = repository.listTrash().size
        }
    }

    /** Snackbar 的「撤销」：直接从回收站捞回来。 */
    fun undoDelete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restore(id)
            val all = repository.list()
            _records.value = all
            _sections.value = HistoryOrganizer.build(all, _sort.value)
            _stats.value = HistoryOrganizer.stats(all)
            _trashCount.value = repository.listTrash().size
        }
    }

    /** Snackbar 的「撤销」：直接把行程从暂存区搬回来，然后由调用方重新启动服务接着走。 */
    fun undoDiscard(id: String, onRestored: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val restored = repository.restoreDiscarded(id)
            if (restored) {
                val all = repository.list()
                _records.value = all
                _sections.value = HistoryOrganizer.build(all, _sort.value)
                _stats.value = HistoryOrganizer.stats(all)
            }
            launch(Dispatchers.Main) { onRestored() }
        }
    }

    /** 撤销窗口过去，真删。 */
    fun purgeDiscard(id: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.purgeDiscarded(id) }
    }

    /** 批量删除：多选模式下仍然弹确认。 */
    fun moveSelectionToTrash(onDone: () -> Unit = {}) {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.list()
                .filter { it.id in ids }
                .forEach { repository.moveToTrash(it) }
            val all = repository.list()
            _records.value = all
            _sections.value = HistoryOrganizer.build(all, _sort.value)
            _stats.value = HistoryOrganizer.stats(all)
            _trashCount.value = repository.listTrash().size
            _selection.value = emptySet()
            _selectionMode.value = false
            launch(Dispatchers.Main) { onDone() }
        }
    }
}
