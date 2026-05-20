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

data class JoeUiState(
    val isBusy: Boolean = false,
    val aiStatus: String = "OpenAI جاهز",
    val lastSource: String = "startup"
)

class JoeViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository = JoeRepository(JoeDatabase.get(application).dao())
    private val assistant = JoeSmartAssistant(
        repository = repository,
        remoteBrain = JoeRemoteBrain(BuildConfig.JOE_API_BASE_URL),
        localBrain = JoeLocalBrain(repository)
    )

    val snapshot: StateFlow<HomeSnapshot> = repository.observeSnapshot()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSnapshot())

    private val _conversation = MutableStateFlow(
        listOf(
            ConversationMessage("جو", "أنا جاهز يا سيدي. أرسل أمرًا طبيعيًا وسأحاول فهمه عبر OpenAI ثم أنفذه داخل التطبيق.")
        )
    )
    val conversation = _conversation.asStateFlow()

    private val _uiState = MutableStateFlow(JoeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
        }
    }

    fun handleUserMessage(text: String, onReplyReady: (String) -> Unit = {}) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val userText = text.trim()
            _uiState.value = _uiState.value.copy(isBusy = true, aiStatus = "جارٍ التفكير...")
            _conversation.value = _conversation.value + ConversationMessage("علاء", userText)

            val result = assistant.handle(userText, snapshot.value)

            _conversation.value = _conversation.value + ConversationMessage("جو", result.reply)
            _uiState.value = JoeUiState(
                isBusy = false,
                aiStatus = result.modeLabel,
                lastSource = result.source
            )
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
