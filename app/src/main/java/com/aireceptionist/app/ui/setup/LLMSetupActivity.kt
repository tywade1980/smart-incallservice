package com.aireceptionist.app.ui.setup

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aireceptionist.app.databinding.ActivityLlmSetupBinding
import com.aireceptionist.app.ai.llm.ModelDownloader
import com.aireceptionist.app.ai.llm.OnDeviceLLM
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity for setting up the on-device LLM
 * 
 * This activity handles:
 * - Checking if LLM model is available
 * - Downloading Phi-3.5-mini model if needed
 * - Initializing the LLM for first use
 * - Providing user feedback during setup
 */
@AndroidEntryPoint
class LLMSetupActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLlmSetupBinding
    private val viewModel: LLMSetupViewModel by viewModels()
    
    @Inject
    lateinit var modelDownloader: ModelDownloader
    
    @Inject
    lateinit var onDeviceLLM: OnDeviceLLM
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLlmSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        observeViewModel()
        checkModelStatus()
    }
    
    private fun setupUI() {
        binding.apply {
            // Setup button click listeners
            btnDownloadModel.setOnClickListener {
                downloadModel()
            }
            
            btnSkipSetup.setOnClickListener {
                // Skip LLM setup - app will use fallback responses
                setResult(RESULT_OK)
                finish()
            }
            
            btnTestLLM.setOnClickListener {
                testLLM()
            }
            
            btnContinue.setOnClickListener {
                setResult(RESULT_OK)
                finish()
            }
        }
    }
    
    private fun observeViewModel() {
        viewModel.setupState.observe(this) { state ->
            updateUI(state)
        }
        
        viewModel.downloadProgress.observe(this) { progress ->
            updateDownloadProgress(progress)
        }
    }
    
    private fun checkModelStatus() {
        lifecycleScope.launch {
            val isAvailable = modelDownloader.isModelAvailable()
            val modelInfo = modelDownloader.getModelInfo()
            
            if (isAvailable) {
                // Model already downloaded, try to initialize
                binding.apply {
                    tvStatus.text = "Model found. Initializing AI..."
                    progressBar.visibility = View.VISIBLE
                }
                
                val initialized = onDeviceLLM.initialize()
                if (initialized) {
                    viewModel.setSetupState(LLMSetupState.READY)
                } else {
                    viewModel.setSetupState(LLMSetupState.ERROR("Failed to initialize LLM"))
                }
            } else {
                viewModel.setSetupState(LLMSetupState.NEEDS_DOWNLOAD)
            }
        }
    }
    
    private fun downloadModel() {
        binding.apply {
            btnDownloadModel.isEnabled = false
            progressBar.visibility = View.VISIBLE
            tvStatus.text = "Downloading AI model..."
        }
        
        lifecycleScope.launch {
            modelDownloader.downloadModel().collect { progress ->
                viewModel.setDownloadProgress(progress)
                
                if (progress.isComplete) {
                    // Download complete, initialize LLM
                    binding.tvStatus.text = "Download complete. Initializing..."
                    
                    val initialized = onDeviceLLM.initialize()
                    if (initialized) {
                        viewModel.setSetupState(LLMSetupState.READY)
                    } else {
                        viewModel.setSetupState(LLMSetupState.ERROR("Failed to initialize after download"))
                    }
                } else if (progress.error != null) {
                    viewModel.setSetupState(LLMSetupState.ERROR(progress.error))
                }
            }
        }
    }
    
    private fun testLLM() {
        binding.apply {
            btnTestLLM.isEnabled = false
            tvTestResult.visibility = View.VISIBLE
            tvTestResult.text = "Testing AI response..."
        }
        
        lifecycleScope.launch {
            try {
                val testResponse = onDeviceLLM.generateResponse(
                    prompt = "Hello, I'd like to schedule an appointment",
                    maxTokens = 100
                )
                
                binding.apply {
                    tvTestResult.text = "Test successful!\nAI Response: $testResponse"
                    btnTestLLM.isEnabled = true
                }
                
            } catch (e: Exception) {
                binding.apply {
                    tvTestResult.text = "Test failed: ${e.message}"
                    btnTestLLM.isEnabled = true
                }
            }
        }
    }
    
    private fun updateUI(state: LLMSetupState) {
        binding.apply {
            when (state) {
                is LLMSetupState.NEEDS_DOWNLOAD -> {
                    tvTitle.text = "AI Brain Setup Required"
                    tvStatus.text = "Download the AI model to enable intelligent responses"
                    btnDownloadModel.visibility = View.VISIBLE
                    btnSkipSetup.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    btnTestLLM.visibility = View.GONE
                    btnContinue.visibility = View.GONE
                }
                
                is LLMSetupState.DOWNLOADING -> {
                    tvTitle.text = "Downloading AI Brain"
                    tvStatus.text = "Downloading intelligent AI model..."
                    btnDownloadModel.visibility = View.GONE
                    btnSkipSetup.visibility = View.GONE
                    progressBar.visibility = View.VISIBLE
                    btnTestLLM.visibility = View.GONE
                    btnContinue.visibility = View.GONE
                }
                
                is LLMSetupState.READY -> {
                    tvTitle.text = "AI Brain Ready!"
                    tvStatus.text = "Your AI Receptionist is now powered by on-device intelligence"
                    btnDownloadModel.visibility = View.GONE
                    btnSkipSetup.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    btnTestLLM.visibility = View.VISIBLE
                    btnContinue.visibility = View.VISIBLE
                    
                    // Show model info
                    val modelInfo = onDeviceLLM.getModelInfo()
                    tvModelInfo.visibility = View.VISIBLE
                    tvModelInfo.text = buildString {
                        append("Model: ${modelInfo["model"]}\n")
                        append("Provider: ${modelInfo["provider"]}\n")
                        append("Status: ${modelInfo["status"]}")
                    }
                }
                
                is LLMSetupState.ERROR -> {
                    tvTitle.text = "Setup Error"
                    tvStatus.text = "Error: ${state.message}"
                    btnDownloadModel.visibility = View.VISIBLE
                    btnSkipSetup.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    btnTestLLM.visibility = View.GONE
                    btnContinue.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun updateDownloadProgress(progress: ModelDownloader.DownloadProgress) {
        binding.apply {
            progressBar.progress = progress.percentage
            tvProgress.visibility = View.VISIBLE
            tvProgress.text = "${progress.fileName}: ${progress.percentage}%"
            
            if (progress.bytesDownloaded > 0 && progress.totalBytes > 0) {
                val mbDownloaded = progress.bytesDownloaded / (1024 * 1024)
                val mbTotal = progress.totalBytes / (1024 * 1024)
                tvStatus.text = "Downloaded ${mbDownloaded}MB / ${mbTotal}MB"
            }
        }
    }
}

/**
 * States for LLM setup process
 */
sealed class LLMSetupState {
    object NEEDS_DOWNLOAD : LLMSetupState()
    object DOWNLOADING : LLMSetupState()
    object READY : LLMSetupState()
    data class ERROR(val message: String) : LLMSetupState()
}