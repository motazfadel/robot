package com.robote.joe.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConversationMessage(
    val sender: String,
    val text: String
)

class JoeViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository = JoeRepository(JoeDatabase.get(application).dao())
    private val brain = JoeLocalBrain(repository)

    val snapshot: StateFlow<HomeSnapshot> = repository.observeSnapshot()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSnapshot())

    private val _conversation = MutableStateFlow(
        listOf(
            ConversationMessage("جو", "أنا جاهز يا سيدي. اطلب ملخص اليوم أو سجّل دينًا أو تذكيرًا أو فاتورة أو مشتريات.")
        )
    )
    val conversation = _conversation.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
        }
    }

    fun handleUserMessage(text: String, onReplyReady: (String) -> Unit = {}) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val userText = text.trim()
            val result = brain.handle(userText, snapshot.value)
            _conversation.value = _conversation.value + ConversationMessage("علاء", userText) + ConversationMessage("جو", result.reply)
            onReplyReady(result.reply)
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return JoeViewModel(application) as T
                }
            }
        }
    }
}
