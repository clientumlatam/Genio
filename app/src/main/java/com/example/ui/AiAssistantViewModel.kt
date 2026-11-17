package com.example.ui

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PublishedBusinessRepository
import com.example.data.places.PlacesRepository
import com.example.data.remote.FirestoreDirectoryRepository
import com.example.domain.model.BusinessCategory
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.LocalityRegion
import com.example.domain.model.PatagonianLocalities
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
  val id: String = java.util.UUID.randomUUID().toString(),
  val text: String,
  val isUser: Boolean,
  val timestamp: Long = System.currentTimeMillis(),
  val groundingSource: String? = null,
)

sealed interface AiMode {
  val title: String
  val route: String

  data object Chat : AiMode {
    override val title = "Buscador Inteligente"
    override val route = "chat"
  }

  data object VoiceAssistant : AiMode {
    override val title = "Voz Libre (Android)"
    override val route = "voice"
  }

  data object FreeSources : AiMode {
    override val title = "APIs Gratuitas"
    override val route = "sources"
  }
}

class AiAssistantViewModel(
  application: Application,
  private val placesRepository: PlacesRepository,
  private val publishedRepository: PublishedBusinessRepository,
  private val firestoreRepository: FirestoreDirectoryRepository? = null,
) : AndroidViewModel(application) {

  private val _currentMode = MutableStateFlow<AiMode>(AiMode.Chat)
  val currentMode: StateFlow<AiMode> = _currentMode.asStateFlow()

  private val _messages =
    MutableStateFlow(
      listOf(
        ChatMessage(
          text =
            "¡Hola! Soy el asistente de Guía Genio (100% con APIs gratuitas y sin API key). Consultame por rubros o localidades como \"farmacias en Neuquén\", \"hoteles en Añelo\" o \"ferreterías en Cipolletti\" y busco en tiempo real en OpenStreetMap y el directorio local.",
          isUser = false,
          groundingSource = "OpenStreetMap (Overpass + Photon) + Directorio Local",
        )
      )
    )
  val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  private val _voiceTranscript =
    MutableStateFlow(
      "Tocá el micrófono para dictar una búsqueda por voz usando el reconocedor nativo gratuito de Android y escuchar los resultados."
    )
  val voiceTranscript: StateFlow<String> = _voiceTranscript.asStateFlow()

  private var tts: TextToSpeech? = null
  private var isTtsInitialized = false

  init {
    tts =
      TextToSpeech(application) { status ->
        if (status == TextToSpeech.SUCCESS) {
          tts?.language = Locale.forLanguageTag("es-AR")
          isTtsInitialized = true
        }
      }
  }

  fun setMode(mode: AiMode) {
    _currentMode.value = mode
  }

  fun sendMessage(prompt: String, speakResult: Boolean = false) {
    val cleanPrompt = prompt.trim()
    if (cleanPrompt.isBlank()) return

    val userMsg = ChatMessage(text = cleanPrompt, isUser = true)
    _messages.update { it + userMsg }
    _isLoading.value = true

    viewModelScope.launch {
      try {
        val locality = detectLocality(cleanPrompt)
        val category = detectCategory(cleanPrompt)
        val searchKeyword = extractSearchKeyword(cleanPrompt, locality, category)

        val localPublished =
          publishedRepository.publishedBusinesses.first().map { it.toDirectoryBusiness() }
        val cloudPublished =
          try {
            if (firestoreRepository?.isConfigured == true) {
              firestoreRepository.observeActiveBusinesses().first()
            } else {
              emptyList()
            }
          } catch (_: Exception) {
            emptyList()
          }

        val directoryMatches =
          (cloudPublished + localPublished)
            .distinctBy { it.id }
            .filter { biz ->
              val locMatch =
                biz.localityName.equals(locality.name, ignoreCase = true) ||
                  cleanPrompt.lowercase().contains("todo")
              val catMatch =
                category.id == DirectoryCategories.AllCategory.id || biz.categoryId == category.id
              val textMatch =
                searchKeyword.isBlank() ||
                  biz.name.contains(searchKeyword, ignoreCase = true) ||
                  biz.categoryName.contains(searchKeyword, ignoreCase = true) ||
                  (biz.description?.contains(searchKeyword, ignoreCase = true) == true)
              locMatch && (catMatch || textMatch)
            }

        val osmResult =
          placesRepository.searchBusinesses(
            query = searchKeyword,
            locality = locality,
            category = category,
            openNowOnly = cleanPrompt.lowercase().contains("abierto"),
            deliveryHint = cleanPrompt.lowercase().contains("delivery"),
          )

        val osmPlaces = osmResult.getOrNull().orEmpty()
        val combined = (directoryMatches + osmPlaces).distinctBy { it.id }.take(5)

        val responseText =
          if (combined.isEmpty()) {
            val photonFallback =
              placesRepository.autocompletePlaces(cleanPrompt, locality).getOrNull().orEmpty()
            if (photonFallback.isNotEmpty()) {
              buildString {
                appendLine("Lugares encontrados en ${locality.name} vía Photon (OSM):")
                photonFallback.take(5).forEachIndexed { index, item ->
                  appendLine("${index + 1}. ${item.primaryText} — ${item.secondaryText}")
                }
              }.trim()
            } else {
              "No encontré resultados exactos para \"$cleanPrompt\" en ${locality.name}. Probá mencionar un rubro (farmacia, gastronomía, ferretería, hotel, Vaca Muerta) o cambiar de localidad."
            }
          } else {
            buildString {
              appendLine("Encontré ${combined.size} opciones en ${locality.name} (${category.shortName}):")
              appendLine()
              combined.forEachIndexed { idx, biz ->
                append("${idx + 1}. ${biz.name} (${biz.categoryName})")
                if (biz.address.isNotBlank()) append(" • ${biz.address}")
                if (!biz.phoneNumber.isNullOrBlank()) append(" • Tel: ${biz.phoneNumber}")
                if (!biz.whatsappNumber.isNullOrBlank()) append(" • WA: ${biz.whatsappNumber}")
                appendLine()
              }
            }.trim()
          }

        if (speakResult) {
          _voiceTranscript.value = responseText
          speakOut(responseText)
        }

        val aiMsg =
          ChatMessage(
            text = responseText,
            isUser = false,
            groundingSource = "OpenStreetMap (Overpass/Photon) + Directorio Guía Genio",
          )
        _messages.update { it + aiMsg }
      } catch (e: Exception) {
        Log.e(TAG, "Búsqueda en fuentes gratuitas falló", e)
        _messages.update {
          it +
            ChatMessage(
              text = "No pudimos completar la consulta: ${e.localizedMessage ?: "error de red"}.",
              isUser = false,
            )
        }
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun onVoiceRecognized(spokenText: String) {
    val clean = spokenText.trim()
    if (clean.isEmpty()) return
    _voiceTranscript.value = "Consulta detectada: \"$clean\"\nBuscando en OpenStreetMap..."
    sendMessage(clean, speakResult = true)
  }

  fun stopSpeaking() {
    tts?.stop()
    _isSpeaking.value = false
  }

  private fun speakOut(text: String) {
    if (isTtsInitialized) {
      _isSpeaking.value = true
      tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guia_genio_tts")
    }
  }

  private fun detectLocality(prompt: String): LocalityRegion {
    val lower = prompt.lowercase()
    return PatagonianLocalities.all.firstOrNull { loc ->
      lower.contains(loc.name.lowercase()) ||
        (loc.id == "anelo" && (lower.contains("anelo") || lower.contains("vaca muerta"))) ||
        (loc.id == "neuquen_capital" && lower.contains("neuquen")) ||
        (loc.id == "san_patricio_chanar" && lower.contains("chañar"))
    } ?: PatagonianLocalities.NeuquenCapital
  }

  private fun detectCategory(prompt: String): BusinessCategory {
    val lower = prompt.lowercase()
    return when {
      lower.contains("farmac") || lower.contains("salud") || lower.contains("clinic") ->
        DirectoryCategories.findById("salud_farmacias")
      lower.contains("comer") || lower.contains("restaur") || lower.contains("parrill") || lower.contains("cafe") || lower.contains("gastronom") ->
        DirectoryCategories.findById("gastronomia")
      lower.contains("ferret") || lower.contains("construc") || lower.contains("corralon") ->
        DirectoryCategories.findById("ferreterias_construccion")
      lower.contains("hotel") || lower.contains("hosped") || lower.contains("cabañ") || lower.contains("aloj") ->
        DirectoryCategories.findById("alojamiento_turismo")
      lower.contains("petrol") || lower.contains("vaca muerta") || lower.contains("industr") || lower.contains("logist") ->
        DirectoryCategories.findById("vaca_muerta_industria")
      lower.contains("auto") || lower.contains("taller") || lower.contains("gomer") || lower.contains("combustible") || lower.contains("ypf") ->
        DirectoryCategories.findById("automotor_talleres")
      lower.contains("super") || lower.contains("almacen") || lower.contains("mercado") || lower.contains("despensa") ->
        DirectoryCategories.findById("supermercados_almacenes")
      else ->
        DirectoryCategories.items.firstOrNull {
          lower.contains(it.shortName.lowercase()) || lower.contains(it.name.lowercase())
        } ?: DirectoryCategories.AllCategory
    }
  }

  private fun extractSearchKeyword(
    prompt: String,
    locality: LocalityRegion,
    category: BusinessCategory,
  ): String {
    if (category.id != DirectoryCategories.AllCategory.id) return ""
    return prompt
      .replace(locality.name, "", ignoreCase = true)
      .replace("neuquen", "", ignoreCase = true)
      .replace("en ", "", ignoreCase = true)
      .trim()
  }

  override fun onCleared() {
    super.onCleared()
    tts?.stop()
    tts?.shutdown()
  }

  companion object {
    private const val TAG = "AiAssistantViewModel"
  }

  class Factory(
    private val application: Application,
    private val placesRepository: PlacesRepository,
    private val publishedRepository: PublishedBusinessRepository,
    private val firestoreRepository: FirestoreDirectoryRepository? = null,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return AiAssistantViewModel(
        application = application,
        placesRepository = placesRepository,
        publishedRepository = publishedRepository,
        firestoreRepository = firestoreRepository,
      ) as T
    }
  }
}
