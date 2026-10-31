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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
    onNewConversationClick: () -> Unit = {},
    onSelectHistoricalSession: (HistoricalSessionItem) -> Unit = {},
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
                            label = "✍️ Fenix Docs (APA 7.ª)",
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToDestination(FenixHubDestination.P09_FENIX_DOCS)
                            },
                        )
                        DrawerNavigationItem(
                            label = "🌐 Comunidad & Recursos",
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToDestination(FenixHubDestination.P10_COMMUNITY)
                            },
                        )
                        DrawerNavigationItem(
                            label = "⚙️ Ajustes & Accesibilidad",
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
                                subtitle = "Fénix IA (Gratuito Asistido)",
                                logoSize = 30.dp,
                            )
                        }

                        OutlinedButton(
                            onClick = onToggleLsaClick,
                            modifier = Modifier.fenixMinTouchTarget(),
                            border = BorderStroke(colors.borderStrokeWidth, colors.primary),
                        ) {
                            Text(
                                text = "🤟 LSA",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        UniversalAIChatInput(
                            state = state.chatInputState,
                            onPromptTextChange = onPromptTextChange,
                            onSendPromptClick = onSendPromptClick,
                            onAttachMaterialClick = onAttachMaterialClick,
                            onLsaToggleClick = onToggleLsaClick,
                            onAutoRetryTriggered = onAutoRetryTriggered,
                            onSwitchProviderClick = onSwitchProviderClick,
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
                            ChatMessageBubble(
                                item = msg,
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
 * Lienzo de bienvenida con saludo empático y acceso rápido a los 4 módulos soberanos de Fénix Studio.
 */
@Composable
private fun EmptyStateWelcomeCanvas(
    onNavigateToDestination: (FenixHubDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FenixTheme.colors

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "¿Qué vamos a estudiar hoy?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Escribe tu consulta académica o selecciona un módulo de trabajo:",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ModuleQuickCard(
                title = "Procesador Académico",
                description = "Sintetizar apuntes, extraer ejes y generar matrices curriculares.",
                tag = "📄 Módulo P-05",
                onClick = { onNavigateToDestination(FenixHubDestination.P05_PROCESSOR) },
            )

            ModuleQuickCard(
                title = "Mis Aulas y FSRS v6",
                description = "Planificación de cátedras y repaso espaciado adaptativo.",
                tag = "🏛️ Módulo P-07",
                onClick = { onNavigateToDestination(FenixHubDestination.P07_CLASSROOMS) },
            )

            ModuleQuickCard(
                title = "Fenix Docs (APA 7.ª)",
                description = "Editor con 20 herramientas de citación, estilo y verificación.",
                tag = "✍️ Módulo P-09",
                onClick = { onNavigateToDestination(FenixHubDestination.P09_FENIX_DOCS) },
            )

            ModuleQuickCard(
                title = "Comunidad & Recursos",
                description = "Catálogo de 70 repositorios académicos abiertos sin costo.",
                tag = "🌐 Módulo P-10",
                onClick = { onNavigateToDestination(FenixHubDestination.P10_COMMUNITY) },
            )
        }
    }
}

/**
 * Tarjeta táctil accesible para acceso rápido a un módulo.
 */
@Composable
private fun ModuleQuickCard(
    title: String,
    description: String,
    tag: String,
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
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                        fontWeight = FontWeight.SemiBold,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
                        modifier = Modifier.fenixMinTouchTarget(),
                        border = BorderStroke(colors.borderStrokeWidth, colors.primary),
                    ) {
                        Text("🔊 Escuchar", style = MaterialTheme.typography.labelSmall)
                    }

                    if (!item.isDeontologicalWarning) {
                        OutlinedButton(
                            onClick = onRetryRotatingKeyClick,
                            modifier = Modifier
                                .fenixMinTouchTarget()
                                .semantics { contentDescription = "Reintentar rotando clave de IA" },
                            border = BorderStroke(colors.borderStrokeWidth, colors.outline),
                        ) {
                            Text("🔄 Reintentar", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
