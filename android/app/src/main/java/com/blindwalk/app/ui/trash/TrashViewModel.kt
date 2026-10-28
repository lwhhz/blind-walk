package com.blindwalk.app.ui.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blindwalk.app.BlindWalkApp
import com.blindwalk.app.data.SessionRepository
import com.blindwalk.app.domain.model.SessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrashViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: SessionRepository =
        (app as BlindWalkApp).container.repository

    private val _items = MutableStateFlow<List<SessionRecord>>(emptyList())
    val items: StateFlow<List<SessionRecord>> = _items.asStateFlow()

    private val _sizeBytes = MutableStateFlow(0L)
    val sizeBytes: StateFlow<Long> = _sizeBytes.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _items.value = repository.listTrash()
            _sizeBytes.value = repository.trashSizeBytes()
        }
    }

    fun restore(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restore(id)
            _items.value = repository.listTrash()
            _sizeBytes.value = repository.trashSizeBytes()
            // 通知首页：还原后列表要重扫
            com.blindwalk.app.data.HistoryEvents.bump()
        }
    }

    fun purge(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.purge(id)
            com.blindwalk.app.data.HistoryEvents.bump()
            _items.value = repository.listTrash()
            _sizeBytes.value = repository.trashSizeBytes()
        }
    }

    fun purgeAll() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.purgeAll()
            com.blindwalk.app.data.HistoryEvents.bump()
            _items.value = repository.listTrash()
            _sizeBytes.value = repository.trashSizeBytes()
        }
    }
}
