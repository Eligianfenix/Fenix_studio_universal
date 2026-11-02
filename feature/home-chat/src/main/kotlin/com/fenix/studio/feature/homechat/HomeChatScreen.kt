/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 04:30:00 -03:00
 * ARCHIVO: feature/home-chat/src/main/kotlin/com/fenix/studio/feature/homechat/HomeChatScreen.kt
 * ================================================================================
 */

package com.fenix.studio.feature.homechat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenix.studio.core.designsystem.components.FenixBrandHeader
import com.fenix.studio.core.designsystem.components.UniversalAIChatInput
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.launch

/**
 * Pantalla `P-02`: Chat Inicial de Encuadre Académico, Tarjeta OTA No Invasiva, Lienzo de Bienvenida,
 * Cajón de Historial de Sesiones (`ModalNavigationDrawer`) y Botonera Universal (`MF-4.2`, `MF-D.4`).
 */
@Composable
fun HomeChatScreen(
    state: HomeChatUiState,
    onPromptTextChange: (String) -> Unit,
    onSendPromptClick: () -> Unit,
    onAttachMaterialClick: () -> Unit,
    onToggleLsaClick: () -> Unit,
    onAutoRetryTriggered: () -> Unit,
    onSwitchProviderClick: () -> Unit,
    onNavigateToDestination: (FenixHubDestination) -> Unit,
    onCopyMessageClick: (HomeChatMessageItem) -> Unit,
    onRetryRotatingKeyClick: (HomeChatMessageItem) -> Unit,
    onSpeakMessageClick: (HomeChatMessageItem) -> Unit = {},
    onVoiceInputClick: () -> Unit = {},
    onCancelVoiceClick: () -> Unit = {},
    onNewConversationClick: () -> Unit = {},
    onSelectHistoricalSession: (HistoricalSessionItem) -> Unit = {},
    onDismissMissingGroqKeyDialog: () -> Unit = {},
    onOpenGroqConfigDialog: () -> Unit = {},
    onDismissGroqConfigDialog: () -> Unit = {},
    onSaveGroqApiKey: (String) -> Unit = {},
    currentGroqApiKey: String = "",
    onDismissMissingAzureTtsDialog: () -> Unit = {},
    onOpenAzureTtsConfigDialog: () -> Unit = {},
    onDismissAzureTtsConfigDialog: () -> Unit = {},
    onSaveAzureTtsKey: (String, String) -> Unit = { _, _ -> },
    onUseDeviceDefaultTtsClick: () -> Unit = {},
    currentAzureApiKey: String = "",
    currentAzureRegion: String = "eastus",
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Desplazamiento automático al final al recibir nuevos mensajes
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.surface,
                drawerContentColor = colors.onSurface,
                modifier = Modifier.width(300.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                ) {
                    FenixBrandHeader(
                        subtitle = "Historial de Sesiones",
                        logoSize = 32.dp,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNewConversationClick()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .fenixMinTouchTarget(),
                    ) {
                        Text(
                            text = "+ Nueva Conversación",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Sesiones Anteriores",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.historicalSessions.isEmpty()) {
                        Text(
                            text = "No hay sesiones previas registradas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(state.historicalSessions, key = { it.id }) { session ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch { drawerState.close() }
                                            onSelectHistoricalSession(session)
                                        }
                                        .fenixMinTouchTarget(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = colors.surface,
                                        contentColor = colors.onSurface,
                                    ),
                                    border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = session.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                        )
                                        if (session.preview.isNotBlank()) {
                                            Text(
                                                text = session.preview,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.outline),
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Módulos de Trabajo",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DrawerNavigationItem(
                            label = "📄 Procesador Académico",
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToDestination(FenixHubDestination.P05_PROCESSOR)
                            },
                        )
                        DrawerNavigationItem(
                            label = "🏛️ Mis Aulas",
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToDestination(FenixHubDestination.P07_CLASSROOMS)
                            },
                        )
                        DrawerNavigationItem(
                            label = "🔬 Investigación Profunda",
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToDestination(FenixHubDestination.DEEP_RESEARCH)
                            },
                        )
                        DrawerNavigationItem(
                            label = "✍️ Fenix Docs (APA 7.ª)",
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToDestination(FenixHubDestination.P09_FENIX_DOCS)
                            },
                        )
                        DrawerNavigationItem(
                            label = "🌐 Comunidad y Recursos",
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToDestination(FenixHubDestination.P10_COMMUNITY)
                            },
                        )
                        DrawerNavigationItem(
                            label = "⚡ Conseguir Créditos",
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToDestination(FenixHubDestination.GET_CREDITS)
                            },
                        )
                        DrawerNavigationItem(
                            label = "🔑 Configurar API Keys",
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToDestination(FenixHubDestination.P04_BYOK_VAULT)
                            },
                        )
                        DrawerNavigationItem(
                            label = "⚙️ Ajustes y Accesibilidad",
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToDestination(FenixHubDestination.P03_SETTINGS)
                            },
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = colors.background,
            contentColor = colors.onBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.fenixMinTouchTarget(),
                            ) {
                                Text(
                                    text = "☰",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary,
                                )
                            }

                            FenixBrandHeader(
                                subtitle = null,
                                logoSize = 36.dp,
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                    color = colors.surface,
                    border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .padding(bottom = 6.dp),
                    ) {
                        UniversalAIChatInput(
                            state = state.chatInputState,
                            onPromptTextChange = onPromptTextChange,
                            onSendPromptClick = onSendPromptClick,
                            onAttachMaterialClick = onAttachMaterialClick,
                            onLsaToggleClick = onToggleLsaClick,
                            onAutoRetryTriggered = onAutoRetryTriggered,
                            onSwitchProviderClick = onSwitchProviderClick,
                            onVoiceInputClick = onVoiceInputClick,
                            onCancelVoiceClick = onCancelVoiceClick,
                        )
                    }
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            ) {
                // Notificación OTA no invasiva si está disponible
                if (!state.otaBannerNotice.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.cooldownContainer,
                            contentColor = colors.onCooldownContainer,
                        ),
                        border = BorderStroke(colors.borderStrokeWidth, colors.cooldownAccent),
                    ) {
                        Text(
                            text = state.otaBannerNotice,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                if (state.messages.isEmpty()) {
                    // Lienzo de bienvenida (Empty State) con las 4 tarjetas de módulos
                    EmptyStateWelcomeCanvas(
                        onNavigateToDestination = onNavigateToDestination,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 16.dp),
                    )
                } else {
                    // Lista conversacional
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.messages, key = { it.messageId }) { msg ->
                            val isThisMessageActive = state.activeTtsMessageId == msg.messageId
                            val isThisMessageLoading = isThisMessageActive && state.isTtsLoading
                            val isThisMessagePlaying = isThisMessageActive && !state.isTtsLoading
                            ChatMessageBubble(
                                item = msg,
                                isTtsLoading = isThisMessageLoading,
                                isSpeaking = isThisMessagePlaying,
                                onCopyClick = { onCopyMessageClick(msg) },
                                onSpeakClick = { onSpeakMessageClick(msg) },
                                onRetryRotatingKeyClick = { onRetryRotatingKeyClick(msg) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Diálogo Empático cuando no hay API Key de Groq configurada
    if (state.isMissingGroqKeyDialogOpen) {
        AlertDialog(
            onDismissRequest = onDismissMissingGroqKeyDialog,
            title = {
                Text(
                    text = "Transcripción por Voz Instantánea",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
            },
            text = {
                Text(
                    text = "¡Hola! Para usar esta función debes configurar una API Key de Groq GRATIS. Si quieres configurarla ahora presiona en el botón 'Configuración', sino presiona 'Salir'.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissMissingGroqKeyDialog()
                        onOpenGroqConfigDialog()
                    },
                    modifier = Modifier.fenixMinTouchTarget(),
                ) {
                    Text("Configuración", style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onDismissMissingGroqKeyDialog,
                    modifier = Modifier.fenixMinTouchTarget(),
                    border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                ) {
                    Text("Salir", style = MaterialTheme.typography.labelMedium)
                }
            },
        )
    }

    // Panel de Configuración de Clave Groq Cloud a Costo $0
    if (state.isGroqConfigDialogOpen) {
        var inputKey by remember(currentGroqApiKey) { mutableStateOf(currentGroqApiKey) }
        val clipboardManager = LocalClipboardManager.current
        val uriHandler = LocalUriHandler.current

        AlertDialog(
            onDismissRequest = onDismissGroqConfigDialog,
            title = {
                Text(
                    text = "Configurar Voz (Groq Cloud)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "¿Qué es y cómo funciona?",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                            )
                            Text(
                                text = "Groq Cloud procesa audio con Whisper Large v3 a velocidad de rayo. Ofrece 2.000 solicitudes diarias 100% GRATIS a Costo $0 absoluto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurface,
                            )
                            Text(
                                text = "Tu clave se almacena de forma privada y cifrada en tu dispositivo. Nadie más tiene acceso a ella.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }

                    Text(
                        text = "Pasos para obtener tu clave GRATIS:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )

                    Text(
                        text = "1. Toca el botón de abajo para ir a la consola de Groq.\n2. Inicia sesión con Google o GitHub.\n3. Presiona 'Create API Key' y cópiala.\n4. Vuelve aquí y presiona 'Pegar'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface,
                    )

                    Button(
                        onClick = { uriHandler.openUri("https://console.groq.com/keys") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .fenixMinTouchTarget(),
                    ) {
                        Text("Abrir console.groq.com/keys ➔", style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key (gsk_...)") },
                        placeholder = { Text("gsk_...") },
                        singleLine = true,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                if (clip.isNotBlank()) inputKey = clip
                            },
                            modifier = Modifier.fenixMinTouchTarget(),
                            border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                        ) {
                            Text("📋 Pegar del Portapapeles", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveGroqApiKey(inputKey.trim())
                        onDismissGroqConfigDialog()
                    },
                    modifier = Modifier.fenixMinTouchTarget(),
                    enabled = inputKey.isNotBlank(),
                ) {
                    Text("Guardar Clave", style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onDismissGroqConfigDialog,
                    modifier = Modifier.fenixMinTouchTarget(),
                    border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                ) {
                    Text("Cancelar", style = MaterialTheme.typography.labelMedium)
                }
            },
        )
    }

    // Diálogo Empático cuando no hay API Key de Azure Speech TTS configurada
    if (state.isMissingAzureTtsKeyDialogOpen) {
        AlertDialog(
            onDismissRequest = onDismissMissingAzureTtsDialog,
            title = {
                Text(
                    text = "Voz de Narrador Profesional",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
            },
            text = {
                Text(
                    text = "¡Hola! Para escuchar las respuestas con timbre humano de narrador profesional de audiolibros (alta fidelidad y entonación natural), puedes configurar tu clave oficial de Azure Speech GRATIS (500.000 caracteres/mes a Costo $0 sin tarjeta).\n\nSi deseas configurarla ahora pulsa en 'Configurar'. También puedes escuchar ahora mismo con la voz por defecto de tu móvil.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissMissingAzureTtsDialog()
                        onOpenAzureTtsConfigDialog()
                    },
                    modifier = Modifier.fenixMinTouchTarget(),
                ) {
                    Text("Configurar Clave (Gratis)", style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onDismissMissingAzureTtsDialog()
                            onUseDeviceDefaultTtsClick()
                        },
                        modifier = Modifier.fenixMinTouchTarget(),
                        border = BorderStroke(colors.borderStrokeWidth, colors.primary),
                    ) {
                        Text("Usar Voz del Móvil", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onDismissMissingAzureTtsDialog,
                        modifier = Modifier.fenixMinTouchTarget(),
                        border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                    ) {
                        Text("Salir", style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
        )
    }

    // Panel de Configuración de Clave Azure Speech TTS a Costo $0
    if (state.isAzureTtsConfigDialogOpen) {
        var inputKey by remember(currentAzureApiKey) { mutableStateOf(currentAzureApiKey) }
        var inputRegion by remember(currentAzureRegion) { mutableStateOf(currentAzureRegion) }
        val clipboardManager = LocalClipboardManager.current
        val uriHandler = LocalUriHandler.current

        AlertDialog(
            onDismissRequest = onDismissAzureTtsConfigDialog,
            title = {
                Text(
                    text = "Configurar Narrador Profesional",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "¿Qué es y cómo funciona?",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                            )
                            Text(
                                text = "Azure Speech ofrece 500.000 caracteres mensuales 100% GRATIS en su nivel F0 para disfrutar de voces neuronales con calidad de audiolibro.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurface,
                            )
                            Text(
                                text = "Tu clave se almacena exclusivamente de forma local y cifrada en tu teléfono.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }

                    Text(
                        text = "Pasos para obtener tu clave GRATIS:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )

                    Text(
                        text = "1. Toca el botón de abajo para abrir el portal de Azure.\n2. Inicia sesión o crea una cuenta gratuita.\n3. Crea un recurso Speech en nivel de tarifa 'Free F0'.\n4. En 'Claves y punto de conexión', copia la Clave 1 y la Región, y pégalas aquí.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface,
                    )

                    Button(
                        onClick = { uriHandler.openUri("https://portal.azure.com/#create/Microsoft.CognitiveServicesSpeechServices") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .fenixMinTouchTarget(),
                    ) {
                        Text("Abrir portal.azure.com ➔", style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key (32 caracteres)") },
                        placeholder = { Text("Ej: 4a2b8c9d...") },
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = inputRegion,
                        onValueChange = { inputRegion = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Región de Azure") },
                        placeholder = { Text("eastus, brazilsouth, etc.") },
                        singleLine = true,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                if (clip.isNotBlank()) inputKey = clip
                            },
                            modifier = Modifier.fenixMinTouchTarget(),
                            border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                        ) {
                            Text("📋 Pegar del Portapapeles", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveAzureTtsKey(inputKey.trim(), inputRegion.trim().ifBlank { "eastus" })
                        onDismissAzureTtsConfigDialog()
                    },
                    modifier = Modifier.fenixMinTouchTarget(),
                    enabled = inputKey.isNotBlank(),
                ) {
                    Text("Guardar y Activar", style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onDismissAzureTtsConfigDialog,
                    modifier = Modifier.fenixMinTouchTarget(),
                    border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                ) {
                    Text("Cancelar", style = MaterialTheme.typography.labelMedium)
                }
            },
        )
    }
}

/**
 * Enlace de navegación estilizado dentro del cajón lateral accesible.
 */
@Composable
private fun DrawerNavigationItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .fenixMinTouchTarget(),
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
        border = BorderStroke(colors.borderStrokeWidth, colors.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            Text(
                text = "➔",
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
            )
        }
    }
}

/**
 * Lienzo de bienvenida con saludo empático y acceso rápido con scroll a las herramientas de Fénix Studio.
 */
@Composable
private fun EmptyStateWelcomeCanvas(
    onNavigateToDestination: (FenixHubDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "¿Qué vamos a estudiar hoy?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Escribe tu consulta académica o selecciona una herramienta:",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ModuleQuickCard(
                icon = "📄",
                title = "Procesador Académico",
                description = "Sintetizar apuntes, extraer ejes y armar programas de estudio.",
                onClick = { onNavigateToDestination(FenixHubDestination.P05_PROCESSOR) },
            )

            ModuleQuickCard(
                icon = "🏛️",
                title = "Mis Aulas",
                description = "Organiza tus materias, apuntes y tarjetas de repaso para exámenes.",
                onClick = { onNavigateToDestination(FenixHubDestination.P07_CLASSROOMS) },
            )

            ModuleQuickCard(
                icon = "🔬",
                title = "Investigación Profunda",
                description = "Búsqueda académica exhaustiva y análisis profundo de fuentes verificadas.",
                onClick = { onNavigateToDestination(FenixHubDestination.DEEP_RESEARCH) },
            )

            ModuleQuickCard(
                icon = "✍️",
                title = "Fenix Docs (APA 7.ª)",
                description = "Editor académico con herramientas de citación, estilo y formato.",
                onClick = { onNavigateToDestination(FenixHubDestination.P09_FENIX_DOCS) },
            )

            ModuleQuickCard(
                icon = "🌐",
                title = "Comunidad y Recursos",
                description = "Catálogo de 70 bibliotecas y repositorios académicos abiertos sin costo.",
                onClick = { onNavigateToDestination(FenixHubDestination.P10_COMMUNITY) },
            )

            ModuleQuickCard(
                icon = "⚡",
                title = "Conseguir Créditos",
                description = "Amplía tus capacidades de procesamiento académico.",
                onClick = { onNavigateToDestination(FenixHubDestination.GET_CREDITS) },
            )

            ModuleQuickCard(
                icon = "🔑",
                title = "Configurar API Keys",
                description = "Administra tus propias llaves de inteligencia artificial en tu bóveda privada.",
                onClick = { onNavigateToDestination(FenixHubDestination.P04_BYOK_VAULT) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Tarjeta táctil accesible para acceso rápido a una herramienta (sin códigos internos P-XX).
 */
@Composable
private fun ModuleQuickCard(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .fenixMinTouchTarget(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface,
        ),
        border = BorderStroke(colors.borderStrokeWidth, colors.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = icon,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Text(
                text = "➔",
                style = MaterialTheme.typography.titleMedium,
                color = colors.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Burbuja de mensaje accesible para estudiante y Fénix IA con botonera universal.
 */
@Composable
private fun ChatMessageBubble(
    item: HomeChatMessageItem,
    isTtsLoading: Boolean = false,
    isSpeaking: Boolean = false,
    onCopyClick: () -> Unit,
    onSpeakClick: () -> Unit,
    onRetryRotatingKeyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors

    val containerColor = if (item.isUser) {
        colors.propositionPrimaryTheoryContainer
    } else if (item.isDeontologicalWarning) {
        colors.cooldownContainer
    } else {
        colors.surface
    }

    val borderColor = if (item.isUser) {
        colors.primary
    } else if (item.isDeontologicalWarning) {
        colors.cooldownAccent
    } else {
        colors.outline
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = colors.onSurface,
        ),
        border = BorderStroke(colors.borderStrokeWidth, borderColor),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (item.isUser) "Tú" else if (item.isDeontologicalWarning) "Aviso Deontológico" else "Fénix IA (Gratuito Asistido)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isUser) colors.primary else colors.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
            )

            if (!item.isUser) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedButton(
                        onClick = onCopyClick,
                        modifier = Modifier.fenixMinTouchTarget(),
                        border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                    ) {
                        Text("Copiar", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = onSpeakClick,
                        enabled = !isTtsLoading,
                        modifier = Modifier.fenixMinTouchTarget(),
                        border = BorderStroke(colors.borderStrokeWidth, if (isSpeaking) colors.primary else colors.outline),
                    ) {
                        if (isTtsLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(14.dp),
                                strokeWidth = 2.dp,
                                color = colors.primary,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generando audio...", style = MaterialTheme.typography.labelSmall)
                        } else if (isSpeaking) {
                            Text("⏹️ Detener audio", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                        } else {
                            Text("🔊 Escuchar", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (!item.isDeontologicalWarning) {
                        OutlinedButton(
                            onClick = onRetryRotatingKeyClick,
                            modifier = Modifier
                                .fenixMinTouchTarget()
                                .semantics { contentDescription = "Reintentar rotando clave de IA" },
                            border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                        ) {
                            Text(
                                text = "🔄 Reintentar",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
