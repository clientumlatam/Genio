package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AiAssistantViewModel
import com.example.ui.AiMode
import com.example.ui.ChatMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
  aiAssistantViewModel: AiAssistantViewModel,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  val currentMode by aiAssistantViewModel.currentMode.collectAsStateWithLifecycle()
  val messages by aiAssistantViewModel.messages.collectAsStateWithLifecycle()
  val isLoading by aiAssistantViewModel.isLoading.collectAsStateWithLifecycle()
  val isSpeaking by aiAssistantViewModel.isSpeaking.collectAsStateWithLifecycle()
  val voiceTranscript by aiAssistantViewModel.voiceTranscript.collectAsStateWithLifecycle()

  var inputPrompt by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  val speechLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == Activity.RESULT_OK) {
        val matches =
          result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val spoken = matches?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
          aiAssistantViewModel.onVoiceRecognized(spoken)
        }
      }
    }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
      ) {
        TopAppBar(
          title = {
            Text(
              text = "Asistente Libre & Voz",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
            )
          },
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surface
            ),
        )

        val selectedIndex =
          when (currentMode) {
            is AiMode.Chat -> 0
            is AiMode.VoiceAssistant -> 1
            is AiMode.FreeSources -> 2
          }

        PrimaryScrollableTabRow(
          selectedTabIndex = selectedIndex,
          edgePadding = 16.dp,
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.primary,
        ) {
          Tab(
            selected = currentMode is AiMode.Chat,
            onClick = { aiAssistantViewModel.setMode(AiMode.Chat) },
            icon = { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null) },
            text = { Text("Buscador Chat") },
            modifier = Modifier.testTag("tab_ai_chat"),
          )
          Tab(
            selected = currentMode is AiMode.VoiceAssistant,
            onClick = { aiAssistantViewModel.setMode(AiMode.VoiceAssistant) },
            icon = { Icon(Icons.Outlined.Mic, contentDescription = null) },
            text = { Text("Voz Android") },
            modifier = Modifier.testTag("tab_ai_live"),
          )
          Tab(
            selected = currentMode is AiMode.FreeSources,
            onClick = { aiAssistantViewModel.setMode(AiMode.FreeSources) },
            icon = { Icon(Icons.Outlined.Explore, contentDescription = null) },
            text = { Text("APIs Gratuitas") },
            modifier = Modifier.testTag("tab_ai_grounding"),
          )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      }
    },
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .background(MaterialTheme.colorScheme.background)
    ) {
      when (currentMode) {
        is AiMode.Chat -> {
          Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
              state = listState,
              modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              contentPadding = PaddingValues(vertical = 16.dp),
            ) {
              items(messages, key = { it.id }) { msg ->
                ChatBubble(message = msg)
              }
              if (isLoading) {
                item {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "Buscando en OpenStreetMap y directorio local...",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
              }
            }

            Surface(
              modifier = Modifier.fillMaxWidth(),
              tonalElevation = 4.dp,
              color = MaterialTheme.colorScheme.surface,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                OutlinedTextField(
                  value = inputPrompt,
                  onValueChange = { inputPrompt = it },
                  placeholder = { Text("Ej: farmacias en Neuquén, hoteles en Añelo...") },
                  modifier = Modifier.weight(1f).testTag("ai_chat_input"),
                  shape = RoundedCornerShape(24.dp),
                  maxLines = 3,
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                  onClick = {
                    if (inputPrompt.isNotBlank()) {
                      aiAssistantViewModel.sendMessage(inputPrompt)
                      inputPrompt = ""
                    }
                  },
                  modifier =
                    Modifier.size(48.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primary)
                      .testTag("ai_send_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = MaterialTheme.colorScheme.onPrimary,
                  )
                }
              }
            }
          }
        }

        is AiMode.VoiceAssistant -> {
          Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Card(
              modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
              shape = RoundedCornerShape(24.dp),
              colors =
                CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
              Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(
                  text = "Asistente de Voz Gratuito (Android)",
                  style =
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                  text = voiceTranscript,
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Spacer(modifier = Modifier.height(24.dp))

            FilledTonalIconButton(
              onClick = {
                val intent =
                  Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                      RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                      RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
                    putExtra(
                      RecognizerIntent.EXTRA_PROMPT,
                      "Decí qué buscás en Neuquén o el Alto Valle",
                    )
                  }
                if (intent.resolveActivity(context.packageManager) != null) {
                  speechLauncher.launch(intent)
                } else {
                  scope.launch {
                    snackbarHostState.showSnackbar(
                      "El servicio de dictado por voz no está disponible en este dispositivo."
                    )
                  }
                }
              },
              modifier = Modifier.size(96.dp).testTag("live_mic_button"),
              colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.primaryContainer,
                  contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
              Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Dictar búsqueda por voz",
                modifier = Modifier.size(48.dp),
              )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = "Tocá el micrófono para dictar tu búsqueda sin API key",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onBackground,
            )

            if (isSpeaking) {
              Spacer(modifier = Modifier.height(12.dp))
              OutlinedButton(
                onClick = { aiAssistantViewModel.stopSpeaking() },
                modifier = Modifier.height(48.dp),
              ) {
                Icon(Icons.Rounded.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Detener lectura")
              }
            }
          }
        }

        is AiMode.FreeSources -> {
          Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Text(
              text = "100% APIs Gratuitas y Sin API Key",
              style =
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text =
                "Guía Genio funciona sin claves pagas ni facturación externa, combinando datos abiertos de OpenStreetMap con tu directorio local.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onBackground,
            )

            Card(
              modifier = Modifier.fillMaxWidth(),
              colors =
                CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Text(
                  "OpenStreetMap (osmdroid + Overpass + Photon + Nominatim)",
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  "Mapas interactivos, autocompletado, geocodificación y búsqueda de comercios reales en Neuquén, Añelo y el Alto Valle sin API key."
                )
              }
            }

            Card(
              modifier = Modifier.fillMaxWidth(),
              colors =
                CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Text(
                  "Firebase Spark (Gratis) + Android Location & Voz",
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  "Inicio de sesión con Google, Firestore, GPS con FusedLocationProviderClient y voz nativa de Android sin costo."
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ChatBubble(message: ChatMessage) {
  val isUser = message.isUser
  val backgroundColor =
    if (isUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
  val textColor =
    if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
  val alignment = if (isUser) Alignment.End else Alignment.Start

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = alignment,
  ) {
    Surface(
      shape =
        RoundedCornerShape(
          topStart = 16.dp,
          topEnd = 16.dp,
          bottomStart = if (isUser) 16.dp else 4.dp,
          bottomEnd = if (isUser) 4.dp else 16.dp,
        ),
      color = backgroundColor,
      modifier =
        Modifier.widthIn(max = 320.dp)
          .testTag("chat_bubble_${if (isUser) "user" else "ai"}"),
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        Text(
          text = message.text,
          style = MaterialTheme.typography.bodyMedium,
          color = textColor,
        )
        if (!message.groundingSource.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "✓ ${message.groundingSource}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}
