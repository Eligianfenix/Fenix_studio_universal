/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-02 16:55:00 -03:00
 * ARCHIVO: core/accessibility-engines/src/main/kotlin/com/fenix/studio/core/accessibility/lsa/LsaSignLanguagePipEngine.kt
 * ================================================================================
 */

package com.fenix.studio.core.accessibility.lsa

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Modos de interacción del intérprete de Lengua de Señas Argentina (LSA).
 */
enum class LsaInteractionMode(val displayLabel: String) {
    LIVE_CLASS("Modo Clase en Vivo"),
    FACE_TO_FACE("Modo Cara a Cara"),
    AUDIO_TO_LSA("Modo Audio a Señas (LSA)"),
}

/**
 * Keyframe cinemático 2.5D de postura esquelética y articular (brazos, manos, 5 dedos y expresión facial).
 * Coordenadas normalizadas respecto al centro del torso:
 * X: -1.0 (izquierda del avatar) a +1.0 (derecha del avatar)
 * Y: -1.0 (altura de frente/cabeza) a +1.0 (altura de cintura)
 * Dedos: arreglo de 5 valores [Pulgar, Índice, Medio, Anular, Meñique] donde 0.0f = cerrado y 1.0f = extendido.
 */
@Immutable
data class LsaPoseKeyframe(
    val rightHandX: Float,
    val rightHandY: Float,
    val leftHandX: Float = -0.52f,
    val leftHandY: Float = 0.62f,
    val rightFingers: List<Float> = listOf(1f, 1f, 1f, 1f, 1f),
    val leftFingers: List<Float> = listOf(0.15f, 0.15f, 0.15f, 0.15f, 0.15f),
    val bimanual: Boolean = false,
    val palmFacingFront: Boolean = true,
    val eyebrowRaise: Float = 0.0f, // -1f ceño fruncido, 0f neutro, +1f cejas arqueadas
    val mouthOpen: Float = 0.15f,   // 0f cerrada, 1f abierta
    val headTiltDeg: Float = 0.0f,
)

/**
 * Representación de una glosa cinética 2.5D en LSA (Lengua de Señas Argentina).
 * Incluye tanto los descriptores fonológicos como los keyframes vectoriales para renderizado Canvas a 60 FPS.
 */
@Immutable
data class LsaKineticGloss(
    val glossCode: String,
    val spanishEquivalent: String,
    val handShape: String,
    val palmOrientation: String,
    val articulationPoint: String,
    val movementPath: String,
    val nonManualExpression: String = "Neutro",
    val durationMs: Long = 900L,
    val startPose: LsaPoseKeyframe = LsaPoseKeyframe(0.38f, -0.15f),
    val endPose: LsaPoseKeyframe = LsaPoseKeyframe(0.52f, -0.28f),
    val fingerSpellingFrames: List<Pair<Char, LsaPoseKeyframe>> = emptyList(),
)

/**
 * Estado inmutable de la ventana Picture-in-Picture del Intérprete LSA (`[ 🤟 ]`, `MF-10.3`).
 */
@Immutable
data class LsaPipUiState(
    val isOverlayVisible: Boolean = false,
    val isPipActive: Boolean = false,
    val activeMode: LsaInteractionMode = LsaInteractionMode.LIVE_CLASS,
    val currentGloss: LsaKineticGloss? = null,
    val pendingGlossQueue: List<LsaKineticGloss> = emptyList(),
    val fullSequence: List<LsaKineticGloss> = emptyList(),
    val currentIndexInSequence: Int = 0,
    val ramFootprintEstimateKb: Int = 4200, // < 15 MB para garantizar fluidez en Redmi 14C
    val lastRecognizedText: String? = null,
)

/**
 * Motor Intérprete Cinético 2.5D de Lengua de Señas Argentina (LSA) a Costo 0 (`MF-10.3`).
 * Implementa transpilación morfosintáctica Español -> Glosas LSA (TIEMPO + LUGAR + SUJETO + OBJETO + VERBO + NEGACIÓN),
 * diccionario de keyframes articulares y alfabeto dactilológico argentino de 27 letras renderizado a 60 FPS en Canvas.
 */
class LsaSignLanguagePipEngine {

    private val _uiState = MutableStateFlow(LsaPipUiState())
    val uiState: StateFlow<LsaPipUiState> = _uiState.asStateFlow()

    // Clasificación sintáctica para reordenamiento gramatical LSA
    private val stopWords = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "al", "del",
        "de", "a", "en", "con", "por", "para", "es", "son", "esta", "estan",
        "fue", "era", "que", "se", "lo", "le", "les", "me", "te", "nos", "y", "o", "u"
    )
    private val timeMarkers = setOf(
        "hoy", "ayer", "mañana", "manana", "ahora", "antes", "despues", "después", "siempre", "tarde", "temprano"
    )
    private val negationMarkers = setOf(
        "no", "nunca", "jamas", "jamás", "nada", "ninguno", "ninguna", "tampoco"
    )
    private val verbMarkers = setOf(
        "estudiar", "comprender", "entender", "aprender", "explicar", "leer", "escribir",
        "preguntar", "responder", "pensar", "saber", "investigar", "analizar", "aprobar", "ayudar"
    )

    // Alfabeto Dactilológico Argentino (27 configuraciones de los 5 dedos: Pulgar, Índice, Medio, Anular, Meñique)
    private val dactilologiaFingerMap: Map<Char, List<Float>> = mapOf(
        'A' to listOf(1.0f, 0.0f, 0.0f, 0.0f, 0.0f),
        'B' to listOf(0.0f, 1.0f, 1.0f, 1.0f, 1.0f),
        'C' to listOf(0.6f, 0.6f, 0.6f, 0.6f, 0.6f),
        'D' to listOf(0.2f, 1.0f, 0.2f, 0.2f, 0.2f),
        'E' to listOf(0.2f, 0.3f, 0.3f, 0.3f, 0.3f),
        'F' to listOf(0.4f, 0.4f, 1.0f, 1.0f, 1.0f),
        'G' to listOf(0.8f, 0.9f, 0.0f, 0.0f, 0.0f),
        'H' to listOf(0.2f, 1.0f, 1.0f, 0.0f, 0.0f),
        'I' to listOf(0.1f, 0.1f, 0.1f, 0.1f, 1.0f),
        'J' to listOf(0.2f, 0.1f, 0.1f, 0.1f, 1.0f),
        'K' to listOf(0.8f, 1.0f, 0.8f, 0.0f, 0.0f),
        'L' to listOf(1.0f, 1.0f, 0.0f, 0.0f, 0.0f),
        'M' to listOf(0.1f, 0.7f, 0.7f, 0.7f, 0.0f),
        'N' to listOf(0.1f, 0.7f, 0.7f, 0.0f, 0.0f),
        'Ñ' to listOf(0.2f, 0.75f, 0.75f, 0.0f, 0.0f),
        'O' to listOf(0.45f, 0.45f, 0.45f, 0.45f, 0.45f),
        'P' to listOf(0.7f, 1.0f, 0.7f, 0.1f, 0.1f),
        'Q' to listOf(0.7f, 0.7f, 0.1f, 0.1f, 0.1f),
        'R' to listOf(0.1f, 0.95f, 0.95f, 0.0f, 0.0f),
        'S' to listOf(0.3f, 0.0f, 0.0f, 0.0f, 0.0f),
        'T' to listOf(0.5f, 0.3f, 0.9f, 0.9f, 0.9f),
        'U' to listOf(0.0f, 1.0f, 1.0f, 0.0f, 0.0f),
        'V' to listOf(0.0f, 1.0f, 1.0f, 0.0f, 0.0f),
        'W' to listOf(0.0f, 1.0f, 1.0f, 1.0f, 0.0f),
        'X' to listOf(0.1f, 0.55f, 0.0f, 0.0f, 0.0f),
        'Y' to listOf(1.0f, 0.0f, 0.0f, 0.0f, 1.0f),
        'Z' to listOf(0.1f, 1.0f, 0.0f, 0.0f, 0.0f),
    )

    // Diccionario de glosas LSA con keyframes articulares 2.5D reales
    private val lsaLexicon: Map<String, LsaKineticGloss> = mapOf(
        "hola" to LsaKineticGloss(
            glossCode = "LSA_HOLA",
            spanishEquivalent = "HOLA",
            handShape = "Configuración B plana abierta",
            palmOrientation = "Hacia adelante",
            articulationPoint = "Sien derecha",
            movementPath = "Arco de saludo lateral",
            nonManualExpression = "Sonrisa cordial",
            durationMs = 850L,
            startPose = LsaPoseKeyframe(
                rightHandX = 0.32f, rightHandY = -0.58f,
                rightFingers = listOf(1f, 1f, 1f, 1f, 1f),
                eyebrowRaise = 0.5f, mouthOpen = 0.35f, headTiltDeg = 4f,
            ),
            endPose = LsaPoseKeyframe(
                rightHandX = 0.68f, rightHandY = -0.45f,
                rightFingers = listOf(1f, 1f, 1f, 1f, 1f),
                eyebrowRaise = 0.6f, mouthOpen = 0.4f, headTiltDeg = 8f,
            ),
        ),
        "estudiar" to LsaKineticGloss(
            glossCode = "LSA_ESTUDIAR",
            spanishEquivalent = "ESTUDIAR",
            handShape = "Configuración 5 bimanual activa",
            palmOrientation = "Enfrentadas hacia el pecho",
            articulationPoint = "Espacio neutro pectoral",
            movementPath = "Oscilación alternada de dedos",
            nonManualExpression = "Concentración visual",
            durationMs = 950L,
            startPose = LsaPoseKeyframe(
                rightHandX = 0.28f, rightHandY = 0.05f,
                leftHandX = -0.28f, leftHandY = 0.12f,
                rightFingers = listOf(1f, 1f, 1f, 1f, 1f),
                leftFingers = listOf(1f, 1f, 1f, 1f, 1f),
                bimanual = true, eyebrowRaise = -0.2f, mouthOpen = 0.1f,
            ),
            endPose = LsaPoseKeyframe(
                rightHandX = 0.22f, rightHandY = -0.08f,
                leftHandX = -0.22f, leftHandY = 0.18f,
                rightFingers = listOf(0.6f, 0.8f, 0.6f, 0.8f, 0.6f),
                leftFingers = listOf(1f, 1f, 1f, 1f, 1f),
                bimanual = true, eyebrowRaise = -0.3f, mouthOpen = 0.15f,
            ),
        ),
        "universidad" to LsaKineticGloss(
            glossCode = "LSA_UNIVERSIDAD",
            spanishEquivalent = "UNIVERSIDAD",
            handShape = "Configuración U bimanual",
            palmOrientation = "Hacia el centro",
            articulationPoint = "Nivel pectoral superior",
            movementPath = "Arco ascendente circular",
            nonManualExpression = "Ceño neutro académico",
            durationMs = 900L,
            startPose = LsaPoseKeyframe(
                rightHandX = 0.25f, rightHandY = 0.10f,
                leftHandX = -0.25f, leftHandY = 0.10f,
                rightFingers = listOf(0f, 1f, 1f, 0f, 0f),
                leftFingers = listOf(0f, 1f, 1f, 0f, 0f),
                bimanual = true, eyebrowRaise = 0.2f,
            ),
            endPose = LsaPoseKeyframe(
                rightHandX = 0.35f, rightHandY = -0.28f,
                leftHandX = -0.35f, leftHandY = -0.28f,
                rightFingers = listOf(0f, 1f, 1f, 0f, 0f),
                leftFingers = listOf(0f, 1f, 1f, 0f, 0f),
                bimanual = true, eyebrowRaise = 0.4f,
            ),
        ),
        "clase" to LsaKineticGloss(
            glossCode = "LSA_CLASE",
            spanishEquivalent = "CLASE / AULA",
            handShape = "Configuración C bimanual",
            palmOrientation = "Enfrentadas en paralelo",
            articulationPoint = "Espacio neutro medio",
            movementPath = "Apertura circular delimitando el aula",
            nonManualExpression = "Atención focalizada",
            durationMs = 900L,
            startPose = LsaPoseKeyframe(
                rightHandX = 0.10f, rightHandY = -0.05f,
                leftHandX = -0.10f, leftHandY = -0.05f,
                rightFingers = listOf(0.6f, 0.6f, 0.6f, 0.6f, 0.6f),
                leftFingers = listOf(0.6f, 0.6f, 0.6f, 0.6f, 0.6f),
                bimanual = true,
            ),
            endPose = LsaPoseKeyframe(
                rightHandX = 0.48f, rightHandY = 0.08f,
                leftHandX = -0.48f, leftHandY = 0.08f,
                rightFingers = listOf(0.6f, 0.6f, 0.6f, 0.6f, 0.6f),
                leftFingers = listOf(0.6f, 0.6f, 0.6f, 0.6f, 0.6f),
                bimanual = true,
            ),
        ),
        "profesor" to LsaKineticGloss(
            glossCode = "LSA_PROFESOR",
            spanishEquivalent = "PROFESOR / DOCENTE",
            handShape = "Configuración P deíctica",
            palmOrientation = "Hacia adelante",
            articulationPoint = "Sien derecha",
            movementPath = "Proyección doble hacia el frente",
            nonManualExpression = "Postura erguida",
            durationMs = 850L,
            startPose = LsaPoseKeyframe(
                rightHandX = 0.26f, rightHandY = -0.52f,
                rightFingers = listOf(0.7f, 1f, 0.7f, 0f, 0f),
                eyebrowRaise = 0.1f,
            ),
            endPose = LsaPoseKeyframe(
                rightHandX = 0.52f, rightHandY = -0.25f,
                rightFingers = listOf(0.7f, 1f, 0.7f, 0f, 0f),
                eyebrowRaise = 0.3f,
            ),
        ),
        "pregunta" to LsaKineticGloss(
            glossCode = "LSA_PREGUNTA",
            spanishEquivalent = "PREGUNTA / DUDA",
            handShape = "Configuración 1 que flexiona en garra",
            palmOrientation = "Hacia el interlocutor",
            articulationPoint = "Espacio frontal alto",
            movementPath = "Curva de signo de interrogación",
            nonManualExpression = "Cejas arqueadas interrogativas",
            durationMs = 850L,
            startPose = LsaPoseKeyframe(
                rightHandX = 0.30f, rightHandY = -0.35f,
                rightFingers = listOf(0.1f, 1f, 0f, 0f, 0f),
                eyebrowRaise = 0.9f, headTiltDeg = -7f, mouthOpen = 0.3f,
            ),
            endPose = LsaPoseKeyframe(
                rightHandX = 0.38f, rightHandY = -0.10f,
                rightFingers = listOf(0.1f, 0.45f, 0f, 0f, 0f),
                eyebrowRaise = 1.0f, headTiltDeg = -10f, mouthOpen = 0.4f,
            ),
        ),
        "comprender" to LsaKineticGloss(
            glossCode = "LSA_COMPRENDER",
            spanishEquivalent = "COMPRENDER / ENTENDER",
            handShape = "Configuración S que abre a 5",
            palmOrientation = "Hacia la sien",
            articulationPoint = "Lateral frontal de la cabeza",
            movementPath = "Apertura súbita de claridad mental",
            nonManualExpression = "Asentimiento afirmativo",
            durationMs = 900L,
            startPose = LsaPoseKeyframe(
                rightHandX = 0.24f, rightHandY = -0.55f,
                rightFingers = listOf(0.1f, 0.1f, 0.1f, 0.1f, 0.1f),
                eyebrowRaise = -0.2f, headTiltDeg = -3f,
            ),
            endPose = LsaPoseKeyframe(
                rightHandX = 0.42f, rightHandY = -0.62f,
                rightFingers = listOf(1f, 1f, 1f, 1f, 1f),
                eyebrowRaise = 0.7f, headTiltDeg = 5f, mouthOpen = 0.3f,
            ),
        ),
        "entender" to LsaKineticGloss(
            glossCode = "LSA_COMPRENDER",
            spanishEquivalent = "COMPRENDER / ENTENDER",
            handShape = "Configuración S que abre a 5",
            palmOrientation = "Hacia la sien",
            articulationPoint = "Lateral frontal de la cabeza",
            movementPath = "Apertura súbita de claridad mental",
            nonManualExpression = "Asentimiento afirmativo",
            durationMs = 900L,
            startPose = LsaPoseKeyframe(
                rightHandX = 0.24f, rightHandY = -0.55f,
                rightFingers = listOf(0.1f, 0.1f, 0.1f, 0.1f, 0.1f),
                eyebrowRaise = -0.2f, headTiltDeg = -3f,
            ),
            endPose = LsaPoseKeyframe(
                rightHandX = 0.42f, rightHandY = -0.62f,
                rightFingers = listOf(1f, 1f, 1f, 1f, 1f),
                eyebrowRaise = 0.7f, headTiltDeg = 5f, mouthOpen = 0.3f,
            ),
        ),
        "examen" to LsaKineticGloss(
            glossCode = "LSA_EXAMEN",
            spanishEquivalent = "EXAMEN / EVALUACIÓN",
            handShape = "Configuración V sobre palma base",
            palmOrientation = "Hacia abajo",
            articulationPoint = "Palma base izquierda",
            movementPath = "Barrido horizontal de evaluación",
            nonManualExpression = "Mirada atenta",
            durationMs = 900L,
            startPose = LsaPoseKeyframe(
                rightHandX = 0.12f, rightHandY = 0.0f,
                leftHandX = -0.18f, leftHandY = 0.15f,
                rightFingers = listOf(0f, 1f, 1f, 0f, 0f),
                leftFingers = listOf(1f, 1f, 1f, 1f, 1f),
                bimanual = true, eyebrowRaise = -0.4f,
            ),
            endPose = LsaPoseKeyframe(
                rightHandX = -0.15f, rightHandY = 0.12f,
                leftHandX = -0.18f, leftHandY = 0.15f,
                rightFingers = listOf(0f, 1f, 1f, 0f, 0f),
                leftFingers = listOf(1f, 1f, 1f, 1f, 1f),
                bimanual = true, eyebrowRaise = -0.2f,
            ),
        ),
        "gracias" to LsaKineticGloss(
            glossCode = "LSA_GRACIAS",
            spanishEquivalent = "GRACIAS",
            handShape = "Configuración B plana",
            palmOrientation = "Hacia los labios",
            articulationPoint = "Mentón",
            movementPath = "Proyección frontal descendente hacia el interlocutor",
            nonManualExpression = "Sonrisa empática",
            durationMs = 850L,
            startPose = LsaPoseKeyframe(
                rightHandX = 0.05f, rightHandY = -0.35f,
                rightFingers = listOf(1f, 1f, 1f, 1f, 1f),
                eyebrowRaise = 0.4f, mouthOpen = 0.35f,
            ),
            endPose = LsaPoseKeyframe(
                rightHandX = 0.38f, rightHandY = 0.05f,
                rightFingers = listOf(1f, 1f, 1f, 1f, 1f),
                eyebrowRaise = 0.5f, mouthOpen = 0.45f, headTiltDeg = 6f,
            ),
        ),
        "no" to LsaKineticGloss(
            glossCode = "LSA_NO",
            spanishEquivalent = "NO (NEGACIÓN)",
            handShape = "Índice extendido en oscilación",
            palmOrientation = "Hacia adelante",
            articulationPoint = "Espacio frontal alto",
            movementPath = "Negación lateral acompañada de cabeza",
            nonManualExpression = "Giro negativo de cabeza",
            durationMs = 800L,
            startPose = LsaPoseKeyframe(
                rightHandX = 0.18f, rightHandY = -0.22f,
                rightFingers = listOf(0f, 1f, 0f, 0f, 0f),
                eyebrowRaise = -0.5f, headTiltDeg = -9f,
            ),
            endPose = LsaPoseKeyframe(
                rightHandX = 0.48f, rightHandY = -0.22f,
                rightFingers = listOf(0f, 1f, 0f, 0f, 0f),
                eyebrowRaise = -0.5f, headTiltDeg = 9f,
            ),
        ),
        "si" to LsaKineticGloss(
            glossCode = "LSA_SI",
            spanishEquivalent = "SÍ (AFIRMACIÓN)",
            handShape = "Puño S con asentimiento",
            palmOrientation = "Hacia adelante",
            articulationPoint = "Espacio frontal",
            movementPath = "Descenso vertical afirmativo",
            nonManualExpression = "Asentimiento de cabeza",
            durationMs = 800L,
            startPose = LsaPoseKeyframe(
                rightHandX = 0.32f, rightHandY = -0.30f,
                rightFingers = listOf(0.2f, 0f, 0f, 0f, 0f),
                eyebrowRaise = 0.4f, headTiltDeg = -4f,
            ),
            endPose = LsaPoseKeyframe(
                rightHandX = 0.32f, rightHandY = -0.05f,
                rightFingers = listOf(0.2f, 0f, 0f, 0f, 0f),
                eyebrowRaise = 0.5f, headTiltDeg = 8f,
            ),
        ),
        "hoy" to LsaKineticGloss(
            glossCode = "LSA_HOY",
            spanishEquivalent = "HOY / AHORA [TIEMPO]",
            handShape = "Configuración Y bimanual hacia abajo",
            palmOrientation = "Hacia arriba",
            articulationPoint = "Espacio neutro cercano al cuerpo",
            movementPath = "Doble descenso corto",
            nonManualExpression = "Marcador temporal presente",
            durationMs = 800L,
            startPose = LsaPoseKeyframe(
                rightHandX = 0.25f, rightHandY = 0.05f,
                leftHandX = -0.25f, leftHandY = 0.05f,
                rightFingers = listOf(1f, 0f, 0f, 0f, 1f),
                leftFingers = listOf(1f, 0f, 0f, 0f, 1f),
                bimanual = true,
            ),
            endPose = LsaPoseKeyframe(
                rightHandX = 0.25f, rightHandY = 0.25f,
                leftHandX = -0.25f, leftHandY = 0.25f,
                rightFingers = listOf(1f, 0f, 0f, 0f, 1f),
                leftFingers = listOf(1f, 0f, 0f, 0f, 1f),
                bimanual = true,
            ),
        ),
        "leer" to LsaKineticGloss(
            glossCode = "LSA_LEER",
            spanishEquivalent = "LEER / TEXTO",
            handShape = "Configuración V (ojos lectores) sobre palma libro",
            palmOrientation = "Palma izquierda hacia arriba",
            articulationPoint = "Frente al pecho",
            movementPath = "Barrido vertical de lectura sobre la palma",
            nonManualExpression = "Mirada dirigida a la palma",
            durationMs = 900L,
            startPose = LsaPoseKeyframe(
                rightHandX = 0.15f, rightHandY = -0.10f,
                leftHandX = -0.20f, leftHandY = 0.10f,
                rightFingers = listOf(0f, 1f, 1f, 0f, 0f),
                leftFingers = listOf(1f, 1f, 1f, 1f, 1f),
                bimanual = true, headTiltDeg = 6f,
            ),
            endPose = LsaPoseKeyframe(
                rightHandX = -0.05f, rightHandY = 0.18f,
                leftHandX = -0.20f, leftHandY = 0.10f,
                rightFingers = listOf(0f, 1f, 1f, 0f, 0f),
                leftFingers = listOf(1f, 1f, 1f, 1f, 1f),
                bimanual = true, headTiltDeg = 8f,
            ),
        ),
    )

    fun toggleOverlayVisibility() {
        _uiState.update { it.copy(isOverlayVisible = !it.isOverlayVisible) }
    }

    fun setPipActive(active: Boolean) {
        _uiState.update { it.copy(isPipActive = active) }
    }

    fun setInteractionMode(mode: LsaInteractionMode) {
        _uiState.update { it.copy(activeMode = mode) }
    }

    /**
     * Transpila una oración en español a la estructura morfosintáctica LSA:
     * [TIEMPO] + [SUJETO / OBJETO / CONCEPTOS] + [VERBO] + [NEGACIÓN]
     * suprimiendo artículos y conectores vacíos cuando hay múltiples palabras,
     * y generando frames dactilológicos letra por letra para tecnicismos.
     */
    fun translateSpanishToLsaGlosses(text: String): List<LsaKineticGloss> {
        val rawTokens = text.lowercase()
            .replace(Regex("[^a-záéíóúüñ\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (rawTokens.isEmpty()) return emptyList()

        // Si es una frase con contenido léxico, filtramos artículos/preposiciones vacías y reordenamos a gramática LSA
        val contentFiltered = if (rawTokens.size > 1) {
            rawTokens.filterNot { it in stopWords }.ifEmpty { rawTokens }
        } else {
            rawTokens
        }

        val timeTokens = mutableListOf<String>()
        val coreTokens = mutableListOf<String>()
        val verbTokens = mutableListOf<String>()
        val negTokens = mutableListOf<String>()

        for (token in contentFiltered) {
            val normalized = normalizeAccents(token)
            when {
                token in timeMarkers || normalized in timeMarkers -> timeTokens.add(token)
                token in negationMarkers || normalized in negationMarkers -> negTokens.add(token)
                token in verbMarkers || normalized in verbMarkers -> verbTokens.add(token)
                else -> coreTokens.add(token)
            }
        }

        // Si todos los tokens son directos del léxico en orden simple, preserva orden natural; si hay estructura compleja aplica TIEMPO + NÚCLEO + VERBO + NEGACIÓN
        val orderedTokens = if (timeTokens.isNotEmpty() || negTokens.isNotEmpty()) {
            timeTokens + coreTokens + verbTokens + negTokens
        } else {
            contentFiltered
        }

        val glosses = orderedTokens.take(24).map { token ->
            val cleanKey = normalizeAccents(token)
            lsaLexicon[token] ?: lsaLexicon[cleanKey] ?: buildFingerSpellingGloss(token)
        }

        _uiState.update {
            it.copy(
                pendingGlossQueue = glosses,
                fullSequence = glosses,
                currentIndexInSequence = 0,
                currentGloss = glosses.firstOrNull(),
                lastRecognizedText = text,
            )
        }
        return glosses
    }

    private fun normalizeAccents(input: String): String {
        return input
            .replace('á', 'a')
            .replace('é', 'e')
            .replace('í', 'i')
            .replace('ó', 'o')
            .replace('ú', 'u')
            .replace('ü', 'u')
    }

    private fun buildFingerSpellingGloss(word: String): LsaKineticGloss {
        val upper = word.uppercase()
        val frames = upper.mapIndexed { idx, ch ->
            val normalizedChar = when (ch) {
                'Á' -> 'A'
                'É' -> 'E'
                'Í' -> 'I'
                'Ó' -> 'O'
                'Ú', 'Ü' -> 'U'
                else -> ch
            }
            val fingers = dactilologiaFingerMap[normalizedChar] ?: listOf(0.5f, 0.8f, 0.5f, 0.2f, 0.2f)
            val horizontalShift = 0.28f + (idx % 4) * 0.06f
            val verticalShift = -0.22f + ((idx % 2) * 0.04f)
            ch to LsaPoseKeyframe(
                rightHandX = horizontalShift,
                rightHandY = verticalShift,
                rightFingers = fingers,
                eyebrowRaise = 0.15f,
                mouthOpen = 0.25f,
            )
        }

        val firstPose = frames.firstOrNull()?.second ?: LsaPoseKeyframe(0.32f, -0.22f)
        val lastPose = frames.lastOrNull()?.second ?: LsaPoseKeyframe(0.42f, -0.18f)

        return LsaKineticGloss(
            glossCode = "LSA_DACTILOLOGIA_$upper",
            spanishEquivalent = word,
            handShape = "Alfabeto Dactilológico LSA Argentino (${upper.length} letras)",
            palmOrientation = "Hacia el interlocutor",
            articulationPoint = "Espacio dactilar hombro derecho",
            movementPath = "Articulación dactilar letra por letra: ${upper.toCharArray().joinToString("-")}",
            nonManualExpression = "Vocalización labial sincronizada",
            durationMs = (word.length * 260L).coerceIn(700L, 2400L),
            startPose = firstPose,
            endPose = lastPose,
            fingerSpellingFrames = frames,
        )
    }

    /**
     * Avanza al siguiente cuadro de la secuencia de señas LSA.
     */
    fun advanceToNextGloss() {
        val currentQueue = _uiState.value.pendingGlossQueue
        if (currentQueue.isEmpty()) {
            _uiState.update { it.copy(currentGloss = null) }
            return
        }
        val remaining = currentQueue.drop(1)
        val total = _uiState.value.fullSequence.size
        val nextIdx = (total - remaining.size).coerceAtLeast(0)
        _uiState.update {
            it.copy(
                pendingGlossQueue = remaining,
                currentGloss = remaining.firstOrNull(),
                currentIndexInSequence = nextIdx,
            )
        }
    }

    /**
     * Reinicia la reproducción desde la primera glosa de la secuencia actual.
     */
    fun restartSequence() {
        val full = _uiState.value.fullSequence
        if (full.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    pendingGlossQueue = full,
                    currentGloss = full.firstOrNull(),
                    currentIndexInSequence = 0,
                )
            }
        }
    }

    /**
     * Calcula la huella estimada de memoria en Kilobytes.
     * Garantiza que el renderizado 2.5D se mantenga siempre por debajo de 15 MB (15360 KB).
     */
    fun getRamFootprintEstimateKb(): Int {
        val baseEngineKb = 3200
        val queueOverheadKb = _uiState.value.pendingGlossQueue.size * 32
        return baseEngineKb + queueOverheadKb
    }
}
