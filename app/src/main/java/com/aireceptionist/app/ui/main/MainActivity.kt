package com.aireceptionist.app.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.app.role.RoleManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.aireceptionist.app.databinding.ActivityMainBinding
// import com.aireceptionist.app.ui.call.CallActivity
// import com.aireceptionist.app.ui.settings.SettingsActivity
import com.aireceptionist.app.ui.setup.LLMSetupActivity
import com.aireceptionist.app.ai.llm.OnDeviceLLM
import com.aireceptionist.app.utils.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main activity of the AI Receptionist app
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var callHistoryAdapter: CallHistoryAdapter
    
    @Inject
    lateinit var onDeviceLLM: OnDeviceLLM
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Logger.i(TAG, "All permissions granted")
            ensureDeviceRoles()
            initializeApp()
        } else {
            Logger.w(TAG, "Some permissions denied")
            showPermissionDeniedMessage()
        }
    }

    private val requestAssistantRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Set as default Assistant", Toast.LENGTH_SHORT).show()
            Logger.i(TAG, "ROLE_ASSISTANT granted")
        } else {
            Logger.w(TAG, "ROLE_ASSISTANT not granted; opening settings")
            openAssistantSettingsFallback()
        }
    }

    private val requestDialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Set as default Phone app", Toast.LENGTH_SHORT).show()
            Logger.i(TAG, "ROLE_DIALER granted")
        } else {
            Logger.w(TAG, "ROLE_DIALER not granted; opening settings")
            openDefaultAppsSettingsFallback()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        Logger.i(TAG, "MainActivity created")
        
        setupUI()
        checkPermissions()
    }
    
    private fun setupUI() {
        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "AI Receptionist"
        
        // Set up RecyclerView for call history
        callHistoryAdapter = CallHistoryAdapter { callRecord ->
            // Handle call record click
            val intent = Intent(this, LLMSetupActivity::class.java)
            startActivity(intent)
        }
        
        binding.recyclerViewCallHistory.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = callHistoryAdapter
        }
        
        // Set up click listeners
        binding.fabNewCall.setOnClickListener {
            startActivity(Intent(this, LLMSetupActivity::class.java))
        }
        
        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, LLMSetupActivity::class.java))
        }
        
        binding.buttonViewStats.setOnClickListener {
            // Show statistics
            viewModel.loadCallStats()
        }
        
        // Observe ViewModel
        observeViewModel()
    }
    
    private fun observeViewModel() {
        // Observe call history
        viewModel.callHistory.observe(this) { callRecords ->
            callHistoryAdapter.submitList(callRecords)
            
            // Update empty state
            if (callRecords.isEmpty()) {
                binding.textEmptyState.visibility = android.view.View.VISIBLE
                binding.recyclerViewCallHistory.visibility = android.view.View.GONE
            } else {
                binding.textEmptyState.visibility = android.view.View.GONE
                binding.recyclerViewCallHistory.visibility = android.view.View.VISIBLE
            }
        }
        
        // Observe call statistics
        viewModel.callStats.observe(this) { stats ->
            updateStatsUI(stats)
        }
        
        // Observe agent status
        viewModel.agentStatus.observe(this) { agentHealth ->
            updateAgentStatusUI(agentHealth)
        }
        
        // Observe error messages
        viewModel.errorMessage.observe(this) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
        
        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
    }
    
    private fun updateStatsUI(stats: com.aireceptionist.app.data.repository.CallStats) {
        binding.textTotalCalls.text = "Total Calls: ${stats.totalCalls}"
        binding.textAverageDuration.text = "Avg Duration: ${String.format("%.1f", stats.averageDuration / 60)} min"
        binding.textHumanTransfers.text = "Human Transfers: ${stats.humanTransfers}"
        binding.textSatisfactionScore.text = "Satisfaction: ${String.format("%.1f", stats.averageSatisfaction)}/5.0"
    }
    
    private fun updateAgentStatusUI(agentHealth: Map<String, Boolean>) {
        val healthyCount = agentHealth.count { it.value }
        val totalCount = agentHealth.size
        
        binding.textAgentStatus.text = "AI Agents: $healthyCount/$totalCount online"
        
        // Update status indicator
        val statusColor = if (healthyCount == totalCount) {
            ContextCompat.getColor(this, android.R.color.holo_green_light)
        } else if (healthyCount > 0) {
            ContextCompat.getColor(this, android.R.color.holo_orange_light)
        } else {
            ContextCompat.getColor(this, android.R.color.holo_red_light)
        }
        
        binding.indicatorAgentStatus.setColorFilter(statusColor)
    }
    
    private fun checkPermissions() {
        val required = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            Logger.i(TAG, "Requesting permissions: ${missing.joinToString()}")
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            Logger.i(TAG, "All permissions already granted")
            ensureDeviceRoles()
            initializeApp()
        }
    }
    
    private fun showPermissionDeniedMessage() {
        Toast.makeText(
            this,
            "Some permissions are required for the app to function properly. Please enable them in settings.",
            Toast.LENGTH_LONG
        ).show()
    }
    
    private fun initializeApp() {
        Logger.i(TAG, "Initializing app with all permissions granted")
        viewModel.initialize()
        loadData()
    }
    
    private fun loadData() {
        viewModel.loadCallHistory()
        viewModel.loadCallStats()
        viewModel.loadAgentStatus()
    }
    
    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            loadData()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "MainActivity destroyed")
    }
    
    private fun ensureDeviceRoles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null) {
                try {
                    // Request to be Default Assistant (digital assistant)
                    if (roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                        requestAssistantRoleLauncher.launch(intent)
                    }
                    // Optionally request to be Default Dialer if eligible
                    if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                        requestDialerRoleLauncher.launch(intent)
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "Role request failed, opening settings", e)
                    openDefaultAppsSettingsFallback()
                }
            }
        }
    }

    private fun openAssistantSettingsFallback() {
        // Try voice input settings first, then default apps
        try {
            startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (e: Exception) {
                Logger.e(TAG, "Unable to open assistant settings", e)
            }
        }
    }

    private fun openDefaultAppsSettingsFallback() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: Exception) {
            Logger.e(TAG, "Unable to open default apps settings", e)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}