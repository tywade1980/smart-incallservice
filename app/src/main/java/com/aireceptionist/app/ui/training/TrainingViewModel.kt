package com.aireceptionist.app.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aireceptionist.app.data.dao.FAQDao
import com.aireceptionist.app.data.models.FAQEntry
import com.aireceptionist.app.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val faqDao: FAQDao
) : ViewModel() {

    private val _faqs = MutableStateFlow<List<FAQEntry>>(emptyList())
    val faqs: StateFlow<List<FAQEntry>> = _faqs.asStateFlow()

    private val _trainingStatus = MutableStateFlow("Ready to train")
    val trainingStatus: StateFlow<String> = _trainingStatus.asStateFlow()

    private val _isTraining = MutableStateFlow(false)
    val isTraining: StateFlow<Boolean> = _isTraining.asStateFlow()

    init {
        loadFAQs()
    }

    private fun loadFAQs() {
        viewModelScope.launch {
            try {
                _faqs.value = faqDao.getActiveFAQEntries()
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load FAQs", e)
            }
        }
    }

    fun addFAQ(question: String, answer: String, category: String) {
        viewModelScope.launch {
            val entry = FAQEntry(
                id        = UUID.randomUUID().toString(),
                question  = question,
                answer    = answer,
                category  = category,
                keywords  = ""
            )
            faqDao.insertFAQEntry(entry)
            loadFAQs()
        }
    }

    fun deleteFAQ(entry: FAQEntry) {
        viewModelScope.launch {
            faqDao.deleteFAQEntry(entry.id)
            loadFAQs()
        }
    }

    fun toggleFAQ(entry: FAQEntry) {
        viewModelScope.launch {
            faqDao.updateFAQEntry(entry.copy(isActive = !entry.isActive))
            loadFAQs()
        }
    }

    /**
     * Simulates an AI training pass that indexes all active FAQs.
     * In production this would push entries to the on-device vector store.
     */
    fun runTrainingPass() {
        viewModelScope.launch {
            _isTraining.value   = true
            _trainingStatus.value = "Loading FAQs…"
            delay(300)

            val activeFaqs = faqDao.getActiveFAQEntries()
            _trainingStatus.value = "Processing ${activeFaqs.size} FAQs…"

            activeFaqs.forEachIndexed { i, _ ->
                _trainingStatus.value = "Indexing FAQ ${i + 1} of ${activeFaqs.size}…"
                delay(80)
            }

            _trainingStatus.value = "✓ Training complete – ${activeFaqs.size} FAQs indexed"
            _isTraining.value = false
            Logger.i(TAG, "Training pass complete for ${activeFaqs.size} FAQs")
        }
    }

    companion object {
        private const val TAG = "TrainingViewModel"
    }
}
