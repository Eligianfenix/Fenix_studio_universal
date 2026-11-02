/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:37:50 -03:00
 * ARCHIVO: app/src/main/kotlin/com/fenix/studio/navigation/FenixRootNavHost.kt
 * ================================================================================
 */

package com.fenix.studio.navigation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.fenix.studio.core.accessibility.voice.VoiceToTextEngine
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fenix.studio.core.accessibility.lsa.LsaInteractionMode
import com.fenix.studio.core.accessibility.lsa.LsaSignLanguagePipEngine
import com.fenix.studio.core.accessibility.tts.HybridTtsEngine
import com.fenix.studio.core.designsystem.modifiers.fenixMinTouchTarget
import com.fenix.studio.core.designsystem.theme.FenixTheme
import com.fenix.studio.crash.SanitizedCrashDialog
import com.fenix.studio.crash.SanitizedCrashReporter
import com.fenix.studio.feature.auth.OnboardingAuthScreen
import com.fenix.studio.feature.auth.OnboardingAuthViewModel
import com.fenix.studio.feature.classrooms.ClassroomDetailScreen
import com.fenix.studio.feature.classrooms.ClassroomListScreen
import com.fenix.studio.feature.classrooms.ClassroomsViewModel
import com.fenix.studio.feature.community.CommunityScreen
import com.fenix.studio.feature.community.CommunityViewModel
import com.fenix.studio.feature.fenixdocs.FenixDocsScreen
import com.fenix.studio.feature.fenixdocs.FenixDocsViewModel
import com.fenix.studio.feature.homechat.FenixHubDestination
import com.fenix.studio.feature.homechat.HomeChatMessageItem
import com.fenix.studio.feature.homechat.HomeChatScreen
import com.fenix.studio.feature.homechat.HomeChatViewModel
import com.fenix.studio.feature.processor.CurricularMatrixScreen
import com.fenix.studio.feature.processor.ProcessingMonitorScreen
import com.fenix.studio.feature.processor.ProcessorConfigScreen
import com.fenix.studio.feature.processor.ProcessorViewModel
import com.fenix.studio.feature.settings.ByokVaultScreen
import com.fenix.studio.feature.settings.SettingsAndByokVaultViewModel
import com.fenix.studio.feature.settings.SettingsScreen

/**
 * Rutas canónicas para las 10 pantallas del Roadmap Maestro de Fénix Studio (`P-01` a `P-10`).
 */
object FenixDestinations {
    const val P01_ONBOARDING = "p01_onboarding"
    const val P02_HOME_CHAT = "p02_home_chat"
    const val P03_SETTINGS = "p03_settings"
    const val P04_BYOK_VAULT = "p04_byok_vault"
    const val P05_PROCESSOR_CONFIG = "p05_processor_config"
    const val P05B_CURRICULAR_MATRIX = "p05b_curricular_matrix"
    const val P06_PROCESSING_MONITOR = "p06_processing_monitor"
    const val P07_CLASSROOMS = "p07_classrooms"
    const val P08_CLASSROOM_DETAIL = "p08_classroom_detail"
    const val P09_FENIX_DOCS = "p09_fenix_docs"
    const val P10_COMMUNITY = "p10_community"
}

/**
 * Orquestador Raíz de Navegación de Fénix Studio (`MF-8.5`).
 * Conecta las 10 pantallas canónicas (`P-01` a `P-10`), el puente LSA 2.5D flotante (`[ 🤟 ]`)
 * y el Guardián de Fallos Sanitizado.
 */
@Composable
fun FenixRootNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = FenixDestinations.P01_ONBOARDING,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = FenixTheme.colors
    val typography = MaterialTheme.typography

    // ViewModels de funcionalidades
    val authViewModel: OnboardingAuthViewModel = viewModel()
    val homeChatViewModel: HomeChatViewModel = viewModel()
    val settingsViewModel: SettingsAndByokVaultViewModel = viewModel()
    val processorViewModel: ProcessorViewModel = viewModel()
    val classroomsViewModel: ClassroomsViewModel = viewModel()
    val docsViewModel: FenixDocsViewModel = viewModel()
    val communityViewModel: CommunityViewModel = viewModel()

    // Motor LSA 2.5D
    val lsaEngine = remember { LsaSignLanguagePipEngine() }
    val lsaState by lsaEngine.uiState.collectAsState()

    // Motor TTS Híbrido (Edge-TTS / Android Nativo MF-D.6)
    val ttsEngine = remember { HybridTtsEngine(context) }

    // Motor de Dictado por Voz a Texto (STT a Costo 0, MF-8.2)
    val voiceToTextEngine = remember { VoiceToTextEngine(context) }
    var promptBackupBeforeVoice by remember { mutableStateOf("") }

    // Lanzador del intent de reconocimiento de voz de Android como fallback universal
    val speechIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        homeChatViewModel.onVoiceListeningChanged(false)
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                homeChatViewModel.onVoiceTranscriptionReceived(spoken)
            }
        }
    }

    // Solicitud de permiso de micrófono
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            homeChatViewModel.onVoiceListeningChanged(true)
            voiceToTextEngine.startListening(
                onResult = { text ->
                    homeChatViewModel.onVoiceTranscriptionReceived(text)
                },
                onPartialResult = { partial ->
                    homeChatViewModel.onVoicePartialTranscription(partial)
                },
                onRmsChanged = { rms ->
                    homeChatViewModel.onAudioRmsChanged(rms)
                },
                onError = {
                    homeChatViewModel.onVoiceListeningChanged(false)
                },
            )
        } else {
            homeChatViewModel.onVoiceListeningChanged(false)
        }
    }

    // Guardián de fallos sanitizado
    val lastCrash by SanitizedCrashReporter.lastCrashFlow.collectAsState()

    // Intérprete Interactivo de Lengua de Señas Argentina (LSA)
    if (lsaState.isOverlayVisible) {
        var customLsaInput by remember { mutableStateOf("") }
        var isAutoPlayingLsa by remember { mutableStateOf(true) }

        LaunchedEffect(isAutoPlayingLsa, lsaState.currentGloss, lsaState.pendingGlossQueue.size) {
            val activeGloss = lsaState.currentGloss
            if (isAutoPlayingLsa && activeGloss != null && lsaState.pendingGlossQueue.size > 1) {
                kotlinx.coroutines.delay(activeGloss.durationMs.coerceAtLeast(900L))
                lsaEngine.advanceToNextGloss()
            } else if (lsaState.pendingGlossQueue.size <= 1) {
                isAutoPlayingLsa = false
            }
        }

        AlertDialog(
            onDismissRequest = { lsaEngine.toggleOverlayVisibility() },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "🤟 Intérprete LSA Argentino",
                        style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary,
                    )
                    Text(
                        text = if (lsaState.pendingGlossQueue.isNotEmpty()) {
                            "Restantes: ${lsaState.pendingGlossQueue.size}"
                        } else {
                            "Listo"
                        },
                        style = typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Selector de modo LSA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LsaInteractionMode.entries.forEach { mode ->
                            val selected = lsaState.activeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) colors.primary else colors.background)
                                    .border(1.dp, if (selected) colors.primary else colors.outline, RoundedCornerShape(6.dp))
                                    .clickable { lsaEngine.setInteractionMode(mode) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = mode.displayLabel.replace("Modo ", ""),
                                    style = typography.labelSmall,
                                    color = if (selected) colors.onPrimary else colors.onSurface,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    lsaState.currentGloss?.let { gloss ->
                        val isFingerspelling = gloss.glossCode.startsWith("LSA_DACTILOLOGIA_")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.background)
                                .border(2.dp, colors.primary, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = if (isFingerspelling) "🖐️➔🔤" else "🤟🧑‍🏫",
                                style = typography.headlineLarge,
                            )
                            Text(
                                text = gloss.spanishEquivalent.uppercase(),
                                style = typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = colors.primary,
                            )
                            Text(
                                text = if (isFingerspelling) "Dactilología término a término" else "Glosa Léxica LSA",
                                style = typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text("• Mano: ${gloss.handShape}", style = typography.bodySmall, color = colors.onSurface)
                                Text("• Palma: ${gloss.palmOrientation}", style = typography.bodySmall, color = colors.onSurface)
                                Text("• Punto: ${gloss.articulationPoint}", style = typography.bodySmall, color = colors.onSurface)
                                Text("• Movimiento: ${gloss.movementPath}", style = typography.bodySmall, color = colors.onSurface)
                                Text("• Rasgo no manual: ${gloss.nonManualExpression}", style = typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        }
                    } ?: Text(
                        text = "Escribe una frase abajo o toca 'Traducir Chat Actual' para iniciar la interpretación en Lengua de Señas Argentina.",
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { isAutoPlayingLsa = !isAutoPlayingLsa },
                            modifier = Modifier.weight(1f),
                            enabled = lsaState.pendingGlossQueue.size > 1,
                        ) {
                            Text(
                                text = if (isAutoPlayingLsa) "⏸ Pausar" else "▶ Reproducir",
                                style = typography.labelSmall,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                isAutoPlayingLsa = false
                                lsaEngine.advanceToNextGloss()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = lsaState.pendingGlossQueue.isNotEmpty(),
                        ) {
                            Text("⏭ Siguiente", style = typography.labelSmall)
                        }
                    }

                    androidx.compose.material3.OutlinedTextField(
                        value = customLsaInput,
                        onValueChange = { customLsaInput = it },
                        placeholder = { Text("Escribe texto para traducir a LSA...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val textToTranslate = customLsaInput.ifBlank {
                                    homeChatViewModel.uiState.value.messages.lastOrNull()?.text
                                        ?: "Hola clase vamos a estudiar y comprender en la universidad"
                                }
                                lsaEngine.translateSpanishToLsaGlosses(textToTranslate)
                                isAutoPlayingLsa = true
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (customLsaInput.isBlank()) "Traducir Chat Actual" else "Traducir Frase",
                                style = typography.labelSmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { lsaEngine.toggleOverlayVisibility() }) {
                    Text("Cerrar")
                }
            },
        )
    }

    // Diálogo de Crash Sanitizado
    lastCrash?.let { crash ->
        SanitizedCrashDialog(
            crashData = crash,
            onSendVoluntaryReportClick = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, SanitizedCrashReporter.formatTelegramPayload(crash))
                }
                context.startActivity(Intent.createChooser(shareIntent, "Enviar reporte sanitizado"))
                SanitizedCrashReporter.dismissLastCrash()
            },
            onDismissClick = {
                SanitizedCrashReporter.dismissLastCrash()
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
        ) {
            // P-01 Onboarding & TyC
            composable(FenixDestinations.P01_ONBOARDING) {
                val state by authViewModel.uiState.collectAsState()
                OnboardingAuthScreen(
                    state = state,
                    onMarkTycScrolledToBottom = authViewModel::onTycScrolledToBottom,
                    onToggleTycAcceptance = authViewModel::onToggleTycAcceptance,
                    onToggleBetaTesterTelemetryConsent = authViewModel::onToggleBetaTesterTelemetryConsent,
                    onSelectEntryMode = authViewModel::onSelectEntryMode,
                    onProceedClick = { navController.navigate(FenixDestinations.P02_HOME_CHAT) },
                    onQuickEscapeClick = { navController.navigate(FenixDestinations.P02_HOME_CHAT) },
                )
            }

            // P-02 Home Chat
            composable(FenixDestinations.P02_HOME_CHAT) {
                val state by homeChatViewModel.uiState.collectAsState()
                HomeChatScreen(
                    state = state,
                    onPromptTextChange = homeChatViewModel::onPromptChanged,
                    onSendPromptClick = {
                        if (state.chatInputState.isVoiceListening) {
                            homeChatViewModel.onVoiceProcessingStarted()
                            voiceToTextEngine.stopListening()
                        } else {
                            homeChatViewModel.onSubmitPrompt()
                        }
                    },
                    onAttachMaterialClick = { navController.navigate(FenixDestinations.P05_PROCESSOR_CONFIG) },
                    onToggleLsaClick = {
                        if (!lsaState.isOverlayVisible) {
                            val latestText = state.chatInputState.promptText.ifBlank {
                                state.messages.lastOrNull()?.text
                                    ?: "Hola clase vamos a estudiar y comprender en la universidad"
                            }
                            lsaEngine.translateSpanishToLsaGlosses(latestText)
                        }
                        lsaEngine.toggleOverlayVisibility()
                    },
                    onAutoRetryTriggered = { homeChatViewModel.onSubmitPrompt() },
                    onSwitchProviderClick = { navController.navigate(FenixDestinations.P04_BYOK_VAULT) },
                    onNavigateToDestination = { destination: FenixHubDestination ->
                        when (destination) {
                            FenixHubDestination.P02_HOME_CHAT -> {}
                            FenixHubDestination.P03_SETTINGS -> navController.navigate(FenixDestinations.P03_SETTINGS)
                            FenixHubDestination.P04_BYOK_VAULT -> navController.navigate(FenixDestinations.P04_BYOK_VAULT)
                            FenixHubDestination.P05_PROCESSOR -> navController.navigate(FenixDestinations.P05_PROCESSOR_CONFIG)
                            FenixHubDestination.P07_CLASSROOMS -> navController.navigate(FenixDestinations.P07_CLASSROOMS)
                            FenixHubDestination.P09_FENIX_DOCS -> navController.navigate(FenixDestinations.P09_FENIX_DOCS)
                            FenixHubDestination.P10_COMMUNITY -> navController.navigate(FenixDestinations.P10_COMMUNITY)
                        }
                    },
                    onCopyMessageClick = { /* noop */ },
                    onRetryRotatingKeyClick = { /* noop */ },
                    onSpeakMessageClick = { msg ->
                        if (state.activeTtsMessageId == msg.messageId) {
                            ttsEngine.stopReading()
                            homeChatViewModel.onTtsPlaybackFinished()
                        } else {
                            homeChatViewModel.onTtsPlaybackRequested(msg.messageId)
                            ttsEngine.speak(
                                text = msg.text,
                                onPlaybackStart = { homeChatViewModel.onTtsPlaybackStarted(msg.messageId) },
                                onComplete = { homeChatViewModel.onTtsPlaybackFinished() },
                            )
                        }
                    },
                    onVoiceInputClick = {
                        if (!voiceToTextEngine.hasApiKey()) {
                            homeChatViewModel.onShowMissingGroqKeyDialog(true)
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            if (state.chatInputState.isVoiceListening) {
                                // Tocar el micrófono verde finaliza la grabación y transcribe
                                homeChatViewModel.onVoiceProcessingStarted()
                                voiceToTextEngine.stopListening()
                            } else {
                                promptBackupBeforeVoice = state.chatInputState.promptText
                                homeChatViewModel.onVoiceListeningChanged(true)
                                voiceToTextEngine.startListening(
                                    onResult = { text ->
                                        if (text.isNotBlank()) {
                                            homeChatViewModel.onVoiceTranscriptionReceived(text)
                                            homeChatViewModel.onSubmitPrompt()
                                        } else {
                                            homeChatViewModel.onVoiceTranscriptionReceived("No se detectó audio.")
                                        }
                                    },
                                    onPartialResult = { partial ->
                                        homeChatViewModel.onVoicePartialTranscription(partial)
                                    },
                                    onRmsChanged = { rms ->
                                        homeChatViewModel.onAudioRmsChanged(rms)
                                    },
                                    onError = { errorMsg ->
                                        homeChatViewModel.onVoiceTranscriptionReceived("Error al transcribir: $errorMsg")
                                    },
                                )
                            }
                        } else {
                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onCancelVoiceClick = {
                        voiceToTextEngine.cancel()
                        homeChatViewModel.onCancelVoiceRecording()
                    },
                    onNewConversationClick = homeChatViewModel::onNewChatSession,
                    onDismissMissingGroqKeyDialog = { homeChatViewModel.onShowMissingGroqKeyDialog(false) },
                    onOpenGroqConfigDialog = { homeChatViewModel.onShowGroqConfigDialog(true) },
                    onDismissGroqConfigDialog = { homeChatViewModel.onShowGroqConfigDialog(false) },
                    onSaveGroqApiKey = { key ->
                        voiceToTextEngine.setApiKey(key)
                    },
                    currentGroqApiKey = voiceToTextEngine.preferences.getApiKey(),
                    onDismissMissingAzureTtsDialog = { homeChatViewModel.onShowMissingAzureTtsDialog(false) },
                    onOpenAzureTtsConfigDialog = { homeChatViewModel.onShowAzureTtsConfigDialog(true) },
                    onDismissAzureTtsConfigDialog = { homeChatViewModel.onShowAzureTtsConfigDialog(false) },
                    onSaveAzureTtsKey = { key, region ->
                        ttsEngine.preferences?.setAzureKey(key)
                        ttsEngine.preferences?.setAzureRegion(region)
                        ttsEngine.preferences?.setUseDeviceDefaultOnly(false)
                        state.pendingSpeechText?.let { ttsEngine.speak(it) }
                    },
                    onUseDeviceDefaultTtsClick = {
                        state.pendingSpeechText?.let { ttsEngine.speakWithNativeFallback(it) }
                    },
                    currentAzureApiKey = ttsEngine.preferences?.getAzureKey().orEmpty(),
                    currentAzureRegion = ttsEngine.preferences?.getAzureRegion() ?: "eastus",
                )
            }

            // P-03 Settings
            composable(FenixDestinations.P03_SETTINGS) {
                val state by settingsViewModel.uiState.collectAsState()
                val ttsState by ttsEngine.playbackState.collectAsState()
                SettingsScreen(
                    state = state,
                    onSelectThemeMode = settingsViewModel::onSelectThemeMode,
                    onToggleOpenDyslexicFont = settingsViewModel::onToggleOpenDyslexicFont,
                    onSelectTouchTargetProfile = settingsViewModel::onSelectTouchTargetProfile,
                    onToggleAnonymousTelemetryConsent = settingsViewModel::onToggleAnonymousTelemetryConsent,
                    onOpenByokVault = { navController.navigate(FenixDestinations.P04_BYOK_VAULT) },
                    currentPitch = ttsState.pitch,
                    currentRate = ttsState.speechRate,
                    selectedVoiceId = ttsState.selectedNarratorVoice.id,
                    onChangePitch = { ttsEngine.setPitch(it) },
                    onChangeRate = { ttsEngine.setSpeechRate(it) },
                    onSelectVoice = { voiceId ->
                        val voice = com.fenix.studio.core.accessibility.tts.FenixNarratorVoice.entries.firstOrNull { it.id == voiceId }
                            ?: com.fenix.studio.core.accessibility.tts.FenixNarratorVoice.MALE_MATURE_RIOPLATENSE
                        ttsEngine.configureVoiceByNarratorProfile(voice)
                    },
                    onTestVoiceSample = {
                        ttsEngine.speak("Hola. Esta es la voz configurada para la lectura de tus apuntes y audiolibros en Fénix Studio.")
                    },
                    azureApiKey = ttsEngine.preferences?.getAzureKey().orEmpty(),
                    azureRegion = ttsEngine.preferences?.getAzureRegion() ?: "eastus",
                    onSaveAzureKey = { key, region ->
                        ttsEngine.preferences?.setAzureKey(key)
                        ttsEngine.preferences?.setAzureRegion(region)
                        ttsEngine.preferences?.setUseDeviceDefaultOnly(false)
                    },
                )
            }

            // P-04 BYOK Vault
            composable(FenixDestinations.P04_BYOK_VAULT) {
                val state by settingsViewModel.uiState.collectAsState()
                ByokVaultScreen(
                    state = state,
                    onRemoveSlot = { slotIndex -> settingsViewModel.onRemoveByokKeySlot("fenix_ia", slotIndex) },
                    onDiscoverModelsClick = { /* noop */ },
                )
            }

            // P-05 Processor Wizard
            composable(FenixDestinations.P05_PROCESSOR_CONFIG) {
                val state by processorViewModel.uiState.collectAsState()
                ProcessorConfigScreen(
                    state = state,
                    onSelectModality = processorViewModel::onSelectModality,
                    onTitleChanged = processorViewModel::onSubjectOrProjectTitleChanged,
                    onProgramaYearChanged = processorViewModel::onProgramaYearChanged,
                    onConfirmConfiguration = {
                        if (processorViewModel.onConfirmConfiguration()) {
                            navController.navigate(FenixDestinations.P05B_CURRICULAR_MATRIX)
                        }
                    },
                )
            }

            // P-05b Curricular Matrix
            composable(FenixDestinations.P05B_CURRICULAR_MATRIX) {
                val state by processorViewModel.uiState.collectAsState()
                CurricularMatrixScreen(
                    state = state,
                    onIncrementPageOffset = { unitId ->
                        val currentOffset = processorViewModel.uiState.value.matrixItems.find { it.unitId == unitId }?.printedPageOffset ?: 0
                        processorViewModel.onUpdatePageOffset(unitId, currentOffset + 1)
                    },
                    onDecrementPageOffset = { unitId ->
                        val currentOffset = processorViewModel.uiState.value.matrixItems.find { it.unitId == unitId }?.printedPageOffset ?: 0
                        processorViewModel.onUpdatePageOffset(unitId, currentOffset - 1)
                    },
                    onConvertOrphanDirectClick = { item ->
                        processorViewModel.onConvertOrphanToDirectStudyUnit(
                            unitId = item.unitId,
                            orphanFileId = item.matchedFileId ?: "orphan_file",
                            orphanFileName = item.matchedFileName ?: "documento_huerfano.pdf",
                        )
                    },
                    onStartQueueProcessing = {
                        processorViewModel.onEnqueueAllUnitsForProcessing("default_classroom")
                        navController.navigate(FenixDestinations.P06_PROCESSING_MONITOR)
                    },
                )
            }

            // P-06 Processing Monitor
            composable(FenixDestinations.P06_PROCESSING_MONITOR) {
                val state by processorViewModel.uiState.collectAsState()
                ProcessingMonitorScreen(
                    state = state,
                    onOpenEarlyReading = { pointCode ->
                        navController.navigate(FenixDestinations.P07_CLASSROOMS)
                    },
                    onExportFormatClick = { format ->
                        // Exportación soberana
                    },
                )
            }

            // P-07 Mis Aulas
            composable(FenixDestinations.P07_CLASSROOMS) {
                val state by classroomsViewModel.uiState.collectAsState()
                ClassroomListScreen(
                    state = state,
                    onSelectClassroom = { catedraId ->
                        classroomsViewModel.onSelectClassroomForDetail(catedraId)
                        navController.navigate(FenixDestinations.P08_CLASSROOM_DETAIL)
                    },
                    onCreateNewClassroomClick = {
                        navController.navigate(FenixDestinations.P05_PROCESSOR_CONFIG)
                    },
                )
            }

            // P-08 Classroom Detail
            composable(FenixDestinations.P08_CLASSROOM_DETAIL) {
                val state by classroomsViewModel.uiState.collectAsState()
                ClassroomDetailScreen(
                    state = state,
                    onRevealFlashcardClick = classroomsViewModel::onRevealActiveFlashcardAnswer,
                    onRateFlashcardClick = { classroomsViewModel.onRateActiveFlashcard(it) },
                    onAddIncrementalMaterialClick = { unitId, pointCode ->
                        // Gestionado por el diálogo interno o callback
                    },
                )
            }

            // P-09 Fenix Docs
            composable(FenixDestinations.P09_FENIX_DOCS) {
                val state by docsViewModel.uiState.collectAsState()
                FenixDocsScreen(
                    state = state,
                    onApplyApa7ToolClick = docsViewModel::onApplyApa7Tool,
                    onUndoClick = { docsViewModel.onUndo() },
                    onRedoClick = { docsViewModel.onRedo() },
                    onDragAiAssistant = docsViewModel::onDragAiAssistantWindow,
                    onToggleAiAssistantExpanded = docsViewModel::onToggleAiAssistantExpanded,
                    onExportDocxClick = {
                        // Exportación OpenXML
                    },
                    onExportPdfClick = {
                        // Exportación PDF
                    },
                )
            }

            // P-10 Community
            composable(FenixDestinations.P10_COMMUNITY) {
                val state by communityViewModel.uiState.collectAsState()
                CommunityScreen(
                    state = state,
                    onTabSelected = communityViewModel::onSelectTab,
                    onSearchQueryChanged = communityViewModel::onSearchQueryChanged,
                    onSelectAreaFilter = communityViewModel::onSelectAreaFilter,
                    onOpenSiteUrl = { url ->
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(browserIntent)
                    },
                    onSharePackageClick = { pkg ->
                        val shareText = communityViewModel.onSanitizeAndExportPackage(pkg)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir paquete soberano .fenix"))
                    },
                    onSubmitNewRequest = { topic, area ->
                        communityViewModel.onSubmitSolidaryRequest(topic, area)
                    },
                    onDismissToast = communityViewModel::onDismissToastFeedback,
                )
            }
        }

        // Botón Rápido Accesible Flotante Superior [ 🤟 ] (Lengua de Señas Argentina LSA)
        // Se oculta en P-02 para evitar solapamiento visual con la TopAppBar
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination?.route
        if (currentDestination != FenixDestinations.P02_HOME_CHAT) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.primary)
                        .border(2.dp, colors.outline, CircleShape)
                        .clickable { lsaEngine.toggleOverlayVisibility() }
                        .fenixMinTouchTarget()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "🤟 LSA",
                        style = typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.onPrimary,
                    )
                }
            }
        }
    }
}
