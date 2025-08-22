package com.aireceptionist.app.ai.agents

import com.aireceptionist.app.ai.agents.impl.*
import com.aireceptionist.app.data.models.CallContext
import com.aireceptionist.app.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central coordinator for all AI agents
 * Manages agent lifecycle, routing, and orchestration
 */
@Singleton
class AgentManager @Inject constructor() {
    
    private val agents = ConcurrentHashMap<String, Agent>()
    private val agentResponses = MutableSharedFlow<AgentResponse>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var isInitialized = false
    
    /**
     * Initialize all agents
     */
    suspend fun initialize() {
        if (isInitialized) return
        
        Logger.i(TAG, "Initializing AI Agent Manager")
        
        try {
            // Create and initialize all agents
            val agentList = listOf(
                SpeechRecognitionAgent(),
                NaturalLanguageAgent(),
                CallRoutingAgent(),
                CustomerServiceAgent(),
                VoiceSynthesisAgent(),
                EmotionDetectionAgent(),
                IntegrationAgent(),
                AppointmentAgent()
            )
            
            // Initialize agents concurrently
            agentList.map { agent ->
                scope.async {
                    try {
                        if (agent.initialize()) {
                            agents[agent.agentId] = agent
                            Logger.i(TAG, "Agent ${agent.agentName} initialized successfully")
                        } else {
                            Logger.e(TAG, "Failed to initialize agent ${agent.agentName}")
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error initializing agent ${agent.agentName}", e)
                    }
                }
            }.awaitAll()
            
            isInitialized = true
            Logger.i(TAG, "AgentManager initialized with ${agents.size} agents")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize AgentManager", e)
            throw e
        }
    }
    
    /**
     * Process input through the appropriate agent chain
     */
    suspend fun processInput(
        input: AgentInput,
        callContext: CallContext
    ): Flow<AgentResponse> = flow {
        
        Logger.d(TAG, "Processing input: ${input.type} - ${input.content}")
        
        try {
            // Determine the best agent for this input
            val primaryAgent = selectPrimaryAgent(input, callContext)
            
            if (primaryAgent != null) {
                Logger.d(TAG, "Selected primary agent: ${primaryAgent.agentName}")
                
                // Process with primary agent
                val response = primaryAgent.processInput(input.copy(context = callContext))
                emit(response)
                
                // Check if we need to chain to another agent
                response.nextSuggestedAgent?.let { nextAgentId ->
                    val nextAgent = agents[nextAgentId]
                    if (nextAgent != null) {
                        Logger.d(TAG, "Chaining to agent: ${nextAgent.agentName}")
                        
                        val chainedInput = AgentInput(
                            type = InputType.SYSTEM_EVENT,
                            content = response.content,
                            context = callContext,
                            metadata = response.metadata
                        )
                        
                        val chainedResponse = nextAgent.processInput(chainedInput)
                        emit(chainedResponse)
                    }
                }
                
            } else {
                Logger.w(TAG, "No suitable agent found for input type: ${input.type}")
                emit(createErrorResponse("No suitable agent available"))
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "Error processing input", e)
            emit(createErrorResponse("Processing error: ${e.message}"))
        }
    }
    
    /**
     * Select the most appropriate agent for the given input
     */
    private fun selectPrimaryAgent(input: AgentInput, context: CallContext): Agent? {
        return when (input.type) {
            InputType.AUDIO_SPEECH -> {
                // Start with speech recognition, then chain to NLU
                agents.values.find { 
                    it.capabilities.contains(AgentCapability.SPEECH_RECOGNITION) 
                }
            }
            InputType.TEXT_MESSAGE -> {
                // Direct to natural language understanding
                agents.values.find { 
                    it.capabilities.contains(AgentCapability.NATURAL_LANGUAGE_UNDERSTANDING) 
                }
            }
            InputType.CALL_EVENT -> {
                // Route to call routing agent
                agents.values.find { 
                    it.capabilities.contains(AgentCapability.CALL_ROUTING) 
                }
            }
            InputType.SYSTEM_EVENT -> {
                // Context-based selection
                selectContextualAgent(context)
            }
            InputType.USER_COMMAND -> {
                // Route to customer service agent
                agents.values.find { 
                    it.capabilities.contains(AgentCapability.CUSTOMER_SERVICE) 
                }
            }
        }
    }
    
    /**
     * Select agent based on call context
     */
    private fun selectContextualAgent(context: CallContext): Agent? {
        return when {
            context.intent?.contains("appointment") == true -> {
                agents.values.find { 
                    it.capabilities.contains(AgentCapability.APPOINTMENT_SCHEDULING) 
                }
            }
            context.intent?.contains("information") == true -> {
                agents.values.find { 
                    it.capabilities.contains(AgentCapability.INFORMATION_RETRIEVAL) 
                }
            }
            else -> {
                agents.values.find { 
                    it.capabilities.contains(AgentCapability.CUSTOMER_SERVICE) 
                }
            }
        }
    }
    
    /**
     * Get agent by ID
     */
    fun getAgent(agentId: String): Agent? = agents[agentId]
    
    /**
     * Get all active agents
     */
    fun getAllAgents(): List<Agent> = agents.values.toList()
    
    /**
     * Check system health
     */
    fun getSystemHealth(): Map<String, Boolean> {
        return agents.mapValues { it.value.isHealthy() }
    }
    
    /**
     * Shutdown all agents
     */
    suspend fun shutdown() {
        Logger.i(TAG, "Shutting down AgentManager")
        
        agents.values.map { agent ->
            scope.async {
                try {
                    agent.shutdown()
                    Logger.d(TAG, "Agent ${agent.agentName} shut down successfully")
                } catch (e: Exception) {
                    Logger.e(TAG, "Error shutting down agent ${agent.agentName}", e)
                }
            }
        }.awaitAll()
        
        agents.clear()
        scope.cancel()
        isInitialized = false
        
        Logger.i(TAG, "AgentManager shutdown complete")
    }
    
    private fun createErrorResponse(message: String): AgentResponse {
        return AgentResponse(
            agentId = "system",
            responseType = ResponseType.TEXT_RESPONSE,
            content = message,
            confidence = 0.0f
        )
    }
    
    companion object {
        private const val TAG = "AgentManager"
    }
}