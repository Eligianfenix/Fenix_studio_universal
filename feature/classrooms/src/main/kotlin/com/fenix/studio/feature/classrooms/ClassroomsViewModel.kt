/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 01:02:30 -03:00
 * ARCHIVO: feature/classrooms/src/main/kotlin/com/fenix/studio/feature/classrooms/ClassroomsViewModel.kt
 * ================================================================================
 */

package com.fenix.studio.feature.classrooms

import androidx.lifecycle.ViewModel
import com.fenix.studio.domain.fsrs.FsrsCardMemoryState
import com.fenix.studio.domain.fsrs.FsrsRating
import com.fenix.studio.domain.fsrs.FsrsV6Scheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel para `P-07` (Mis Aulas) y `P-08` (Aula en Detalle, Mazo Interactivo FSRS v6 e Ingesta Incremental `RN-22`).
 * Implementa carga perezosa (*lazy loading*) por aula seleccionada para mantener el consumo PSS < 150 MB.
 */
class ClassroomsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ClassroomsUiState())
    val uiState: StateFlow<ClassroomsUiState> = _uiState.asStateFlow()

    private val unitsByClassroomId = mutableMapOf<String, List<SyllabusUnitUiModel>>()

    fun setClassroomsCatalog(
        summaries: List<ClassroomSummaryUiModel>,
        unitsMap: Map<String, List<SyllabusUnitUiModel>> = emptyMap(),
    ) {
        unitsByClassroomId.clear()
        unitsByClassroomId.putAll(unitsMap)
        _uiState.update { it.copy(classrooms = summaries) }
    }

    /**
     * Selecciona un aula para abrir en `P-08` cargando bajo demanda únicamente sus unidades
     * y liberando las referencias previas.
     */
    fun onSelectClassroomForDetail(catedraId: String) {
        val loadedUnits = unitsByClassroomId[catedraId].orEmpty()
        _uiState.update {
            it.copy(
                selectedClassroomId = catedraId,
                selectedClassroomUnits = loadedUnits,
            )
        }
    }

    fun onStartFsrsReviewSession(cards: List<FsrsCardMemoryState>) {
        _uiState.update {
            it.copy(
                fsrsSession = FsrsReviewSessionState(
                    deckCards = cards,
                    currentCardIndex = 0,
                    isBackAnswerRevealed = false,
                    reviewedCountInSession = 0,
                ),
            )
        }
    }

    fun onRevealActiveFlashcardAnswer() {
        _uiState.update { current ->
            current.copy(
                fsrsSession = current.fsrsSession.copy(isBackAnswerRevealed = true),
            )
        }
    }

    /**
     * Califica la tarjeta activa (`AGAIN`, `HARD`, `GOOD`, `EASY`) ejecutando `FsrsV6Scheduler.scheduleNextReview`
     * en < 1 ms y a 0 costo de tokens de IA (`MF-6.2`), actualizando también el Nivel B de progreso (`RN-24`).
     */
    fun onRateActiveFlashcard(
        rating: FsrsRating,
        reviewEpochMs: Long = System.currentTimeMillis(),
    ): FsrsCardMemoryState? {
        val session = _uiState.value.fsrsSession
        val active = session.activeCard ?: return null

        val updatedCard = FsrsV6Scheduler.scheduleNextReview(
            currentCard = active,
            rating = rating,
            reviewEpochMs = reviewEpochMs,
        )

        val updatedDeck = session.deckCards.toMutableList().apply {
            set(session.currentCardIndex, updatedCard)
        }
        val nextIndex = (session.currentCardIndex + 1).coerceAtMost((updatedDeck.size - 1).coerceAtLeast(0))
        val masteredCount = updatedDeck.count { it.stabilityDays >= 3.0 }

        _uiState.update { current ->
            val updatedClassrooms = current.classrooms.map { summary ->
                if (summary.catedraId == updatedCard.catedraId) {
                    summary.copy(
                        masteredFsrsCardsCount = masteredCount,
                        totalFsrsCardsCount = updatedDeck.size.coerceAtLeast(1),
                    )
                } else {
                    summary
                }
            }
            current.copy(
                classrooms = updatedClassrooms,
                fsrsSession = session.copy(
                    deckCards = updatedDeck,
                    currentCardIndex = nextIndex,
                    isBackAnswerRevealed = false,
                    reviewedCountInSession = session.reviewedCountInSession + 1,
                ),
            )
        }
        return updatedCard
    }

    /**
     * Ejecuta la **Ingesta Incremental (`RN-22`)**: vincula un nuevo PDF/apunte únicamente al punto indicado
     * y lo marca para encolado incremental, preservando intactos los bloques de todas las demás unidades y puntos.
     */
    fun onSubmitIncrementalIngestion(draft: IncrementalIngestionDraftModel): Boolean {
        val currentUnits = unitsByClassroomId[draft.targetCatedraId] ?: _uiState.value.selectedClassroomUnits
        if (currentUnits.isEmpty()) return false

        var foundTargetPoint = false
        val updatedUnits = currentUnits.map { unit ->
            if (unit.unitId != draft.targetUnitId) {
                unit
            } else {
                unit.copy(
                    points = unit.points.map { point ->
                        if (point.pointCode == draft.targetPointCode) {
                            foundTargetPoint = true
                            point.copy(
                                linkedMaterialNames = point.linkedMaterialNames + draft.newMaterialFileName,
                                isPendingIncrementalProcessing = true,
                            )
                        } else {
                            point
                        }
                    },
                )
            }
        }

        if (!foundTargetPoint) return false

        unitsByClassroomId[draft.targetCatedraId] = updatedUnits
        _uiState.update {
            it.copy(
                selectedClassroomUnits = if (it.selectedClassroomId == draft.targetCatedraId) updatedUnits else it.selectedClassroomUnits,
                lastIncrementalIngestionPointCode = draft.targetPointCode,
            )
        }
        return true
    }
}
