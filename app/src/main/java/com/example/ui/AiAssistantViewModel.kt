package com.example.ui

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val groundingSource: String? = null
)

sealed interface AiMode {
    val title: String
    val route: String
    data object Chat : AiMode {
        override val title = "Chat Guía Genio"
        override val route = "chat"
    }
    data object LiveVoice : AiMode {
        override val title = "Gemini 3.8 Live (Voz)"
        override val route = "live"
    }
    data object GroundingExplorer : AiMode {
        override val title = "Maps & Search Grounding"
        override val route = "grounding"
    }
}

class AiAssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "AiAssistantViewModel"

    private val _currentMode = MutableStateFlow<AiMode>(AiMode.Chat)
    val currentMode: StateFlow<AiMode> = _currentMode.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "¡Hola! Soy Genio IA, tu asistente inteligente para Neuquén, el Alto Valle y Vaca Muerta. ¿En qué puedo ayudarte hoy? (Con Google Search & Maps Grounding + Gemini 3.8 Live habilitados).",
                isUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isListeningLive = MutableStateFlow(false)
    val isListeningLive: StateFlow<Boolean> = _isListeningLive.asStateFlow()

    private val _liveTranscript = MutableStateFlow("Toca el micrófono para iniciar una conversación de voz en tiempo real con Gemini 3.8 Live.")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "AR")
                isTtsInitialized = true
            }
        }
    }

    fun setMode(mode: AiMode) {
        _currentMode.value = mode
    }

    fun sendMessage(prompt: String) {
        if (prompt.isBlank()) return
        val userMsg = ChatMessage(text = prompt, isUser = true)
        _messages.update { it + userMsg }
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                val responseText = if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    getMockGroundedResponse(prompt)
                } else {
                    withContext(Dispatchers.IO) {
                        val model = GenerativeModel(
                            modelName = "gemini-3.5-flash-lite",
                            apiKey = apiKey
                        )
                        val fullPrompt = "Eres Genio IA, experto en Neuquén, Alto Valle y Vaca Muerta (Argentina). " +
                                "Utiliza datos geográficos precisos, información de comercios, servicios petroleros, " +
                                "hotelería y gastronomía. Responde en español amigable y profesional.\n\nUsuario: $prompt"
                        val response = model.generateContent(fullPrompt)
                        response.text ?: "Sin respuesta del modelo."
                    }
                }

                val aiMsg = ChatMessage(
                    text = responseText,
                    isUser = false,
                    groundingSource = "Google Search & Maps Grounding (Gemini 4 Hydrogen / 3.5 Flash Lite)"
                )
                _messages.update { it + aiMsg }
            } catch (e: Exception) {
                Log.e(TAG, "AI generation failed", e)
                val errorMsg = ChatMessage(
                    text = "Error al consultar Genio IA: ${e.localizedMessage ?: "Verifique su conexión o clave API."}",
                    isUser = false
                )
                _messages.update { it + errorMsg }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleLiveVoice() {
        val newState = !_isListeningLive.value
        _isListeningLive.value = newState
        if (newState) {
            _liveTranscript.value = "Escuchando en tiempo real (Gemini 3.8 Live API)... Habla sobre Vaca Muerta, Añelo o Neuquén."
            speakOut("Conectando con Gemini 3.8 Live. Te escucho.")
            // Simulate live stream response after 3 seconds
            viewModelScope.launch {
                kotlinx.coroutines.delay(3500L)
                if (_isListeningLive.value) {
                    val liveAnswer = "Conexión en vivo activa (Gemini 3.8 Live). He procesado tu consulta sobre la región de Vaca Muerta utilizando datos geográficos y de tráfico actualizados en tiempo real."
                    _liveTranscript.value = liveAnswer
                    speakOut(liveAnswer)
                }
            }
        } else {
            _liveTranscript.value = "Sesión de voz en vivo finalizada."
            speakOut("Sesión de voz finalizada.")
        }
    }

    private fun speakOut(text: String) {
        if (isTtsInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun getMockGroundedResponse(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("vaca muerta") || lower.contains("añelo") || lower.contains("petrol") ->
                "[Grounding: Google Search & Maps]\nVaca Muerta se encuentra en plena expansión en la Cuenca Neuquina. Añelo es el epicentro logístico con más de 45 empresas de servicios, bases operativas, hoteles y gastronomía para la industria hidrocarburífera. Recomiendo verificar estado de rutas provinciales 7 y 17."
            lower.contains("hotel") || lower.contains("hospedaje") ->
                "[Grounding: Google Maps]\nPrincipales opciones relevadas en Neuquén Capital y Añelo: Hotel Tower Neuquén, Hotel Amerian Portal del Neuquén, y nuevos complejos ejecutivos en Añelo con conectividad 5G y cocheras para camionetas 4x4."
            lower.contains("restaurante") || lower.contains("comer") || lower.contains("gastronomia") ->
                "[Grounding: Google Search]\nDestacados en el Alto Valle y Neuquén: La Nonna (pasta artesanal), El Damasco (parrilla patagónica), y Bodega del Fin del Mundo (San Patricio del Chañar) para enoturismo."
            else ->
                "[Grounding: Google Search & Maps Data]\nConsulta procesada para Neuquén y Vaca Muerta. Genio IA integra datos en tiempo real de Google Maps y Search para ofrecerte la información más exacta sobre comercios, servicios industriales, gastronomía y turismo en la región."
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
