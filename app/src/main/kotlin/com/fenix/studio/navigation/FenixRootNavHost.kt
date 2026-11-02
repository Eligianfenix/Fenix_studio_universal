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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.navigation.compose.rememberNavController
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

    val settingsState by settingsViewModel.uiState.collectAsState()
    val accessibilityPrefs = remember(context) {
        context.getSharedPreferences("fenix_accessibility_prefs", android.content.Context.MODE_PRIVATE)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val savedModeName = accessibilityPrefs.getString("theme_mode", null)
        val savedMode = savedModeName?.let {
            runCatching { com.fenix.studio.core.designsystem.theme.FenixThemeMode.valueOf(it) }.getOrNull()
        }
        if (savedMode != null && savedMode != settingsState.accessibilityConfig.themeMode) {
            settingsViewModel.onSelectThemeMode(savedMode)
        }
        val savedDyslexic = accessibilityPrefs.getBoolean("open_dyslexic", false)
        if (savedDyslexic != settingsState.accessibilityConfig.useOpenDyslexicFont) {
            settingsViewModel.onToggleOpenDyslexicFont(savedDyslexic)
        }
        val savedProfileName = accessibilityPrefs.getString("touch_profile", null)
        val savedProfile = savedProfileName?.let {
            runCatching { com.fenix.studio.core.designsystem.theme.FenixTouchTargetProfile.valueOf(it) }.getOrNull()
        }
        if (savedProfile != null && savedProfile != settingsState.accessibilityConfig.touchTargetProfile) {
            settingsViewModel.onSelectTouchTargetProfile(savedProfile)
        }
    }

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

    FenixTheme(config = settingsState.accessibilityConfig) {
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
                    onToggleLsaClick = { /* Posicionado para fase dedicada post-Versión Estable 1 (Decisión A1) */ },
                    onAutoRetryTriggered = { homeChatViewModel.onSubmitPrompt() },
                    onSwitchProviderClick = { navController.navigate(FenixDestinations.P04_BYOK_VAULT) },
                    onNavigateToDestination = { destination: FenixHubDestination ->
                        when (destination) {
                            FenixHubDestination.P02_HOME_CHAT -> {}
                            FenixHubDestination.P03_SETTINGS -> navController.navigate(FenixDestinations.P03_SETTINGS)
                            FenixHubDestination.P04_BYOK_VAULT,
                            FenixHubDestination.GET_CREDITS -> navController.navigate(FenixDestinations.P04_BYOK_VAULT)
                            FenixHubDestination.P05_PROCESSOR -> navController.navigate(FenixDestinations.P05_PROCESSOR_CONFIG)
                            FenixHubDestination.P07_CLASSROOMS -> navController.navigate(FenixDestinations.P07_CLASSROOMS)
                            FenixHubDestination.P09_FENIX_DOCS -> navController.navigate(FenixDestinations.P09_FENIX_DOCS)
                            FenixHubDestination.P10_COMMUNITY -> navController.navigate(FenixDestinations.P10_COMMUNITY)
                            FenixHubDestination.DEEP_RESEARCH -> homeChatViewModel.onActivateDeepResearchMode()
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
                    onSelectThemeMode = { mode ->
                        settingsViewModel.onSelectThemeMode(mode)
                        accessibilityPrefs.edit().putString("theme_mode", mode.name).apply()
                    },
                    onToggleOpenDyslexicFont = { enabled ->
                        settingsViewModel.onToggleOpenDyslexicFont(enabled)
                        accessibilityPrefs.edit().putBoolean("open_dyslexic", enabled).apply()
                    },
                    onSelectTouchTargetProfile = { profile ->
                        settingsViewModel.onSelectTouchTargetProfile(profile)
                        accessibilityPrefs.edit().putString("touch_profile", profile.name).apply()
                    },
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
        }
    }
}
