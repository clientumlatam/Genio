package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    aiAssistantViewModel: AiAssistantViewModel
) {
    val currentMode by aiAssistantViewModel.currentMode.collectAsStateWithLifecycle()
    val messages by aiAssistantViewModel.messages.collectAsStateWithLifecycle()
    val isLoading by aiAssistantViewModel.isLoading.collectAsStateWithLifecycle()
    val isListeningLive by aiAssistantViewModel.isListeningLive.collectAsStateWithLifecycle()
    val liveTranscript by aiAssistantViewModel.liveTranscript.collectAsStateWithLifecycle()

    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Genio IA & Voz",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Mode Selector Tabs
                ScrollableTabRow(
                    selectedTabIndex = when (currentMode) {
                        is AiMode.Chat -> 0
                        is AiMode.LiveVoice -> 1
                        is AiMode.GroundingExplorer -> 2
                    },
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = currentMode is AiMode.Chat,
                        onClick = { aiAssistantViewModel.setMode(AiMode.Chat) },
                        icon = { Icon(Icons.Outlined.Chat, contentDescription = null) },
                        text = { Text("Chat Grounding") },
                        modifier = Modifier.testTag("tab_ai_chat")
                    )
                    Tab(
                        selected = currentMode is AiMode.LiveVoice,
                        onClick = { aiAssistantViewModel.setMode(AiMode.LiveVoice) },
                        icon = { Icon(Icons.Outlined.Mic, contentDescription = null) },
                        text = { Text("Gemini 3.8 Live (Voz)") },
                        modifier = Modifier.testTag("tab_ai_live")
                    )
                    Tab(
                        selected = currentMode is AiMode.GroundingExplorer,
                        onClick = { aiAssistantViewModel.setMode(AiMode.GroundingExplorer) },
                        icon = { Icon(Icons.Outlined.Explore, contentDescription = null) },
                        text = { Text("Maps & Search") },
                        modifier = Modifier.testTag("tab_ai_grounding")
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentMode) {
                is AiMode.Chat -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            items(messages) { msg ->
                                ChatBubble(message = msg)
                            }
                            if (isLoading) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Consultando Google Search & Maps Grounding...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Chat Input Bar
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 4.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = inputPrompt,
                                    onValueChange = { inputPrompt = it },
                                    placeholder = { Text("Pregunta sobre Vaca Muerta, hoteles, servicios...") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ai_chat_input"),
                                    shape = RoundedCornerShape(24.dp),
                                    maxLines = 3
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        if (inputPrompt.isNotBlank()) {
                                            aiAssistantViewModel.sendMessage(inputPrompt)
                                            inputPrompt = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .testTag("ai_send_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Enviar",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                is AiMode.LiveVoice -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isListeningLive) "🎙️ Gemini 3.8 Live Activado" else "💬 Asistente de Voz en Vivo",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = liveTranscript,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Big Mic Button for Live API
                        FilledTonalIconButton(
                            onClick = { aiAssistantViewModel.toggleLiveVoice() },
                            modifier = Modifier
                                .size(96.dp)
                                .testTag("live_mic_button"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isListeningLive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (isListeningLive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isListeningLive) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = "Activar Voz",
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isListeningLive) "Escuchando en tiempo real... Toca para pausar" else "Toca para hablar con Gemini 3.8 Live",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                is AiMode.GroundingExplorer -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Capacidades de Grounding con Google Search & Maps",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Guía Genio utiliza los modelos más avanzados de Gemini con herramientas de búsqueda en tiempo real y datos geoespaciales de Google Maps para brindarte información precisa sobre Vaca Muerta, Neuquén y el Alto Valle.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("📍 Google Maps Grounding", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Ubicaciones exactas de estaciones de servicio YPF, bases petroleras en Añelo, hoteles ejecutivos y rutas provinciales con estado en vivo.")
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("🔍 Google Search Grounding", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Información actualizada de cotizaciones, normativas energéticas, novedades de operadoras y comercios locales registrados en la Cuenca Neuquina.")
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
    val backgroundColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = backgroundColor,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .testTag("chat_bubble_${if (isUser) "user" else "ai"}")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
                if (!message.groundingSource.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "✓ ${message.groundingSource}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
