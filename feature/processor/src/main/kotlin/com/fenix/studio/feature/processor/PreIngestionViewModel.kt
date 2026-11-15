/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-15 03:20:00 -03:00
 * ARCHIVO: feature/processor/src/main/kotlin/com/fenix/studio/feature/processor/PreIngestionViewModel.kt
 * ================================================================================
 */

package com.fenix.studio.feature.processor

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenix.studio.core.document.guardian.DocumentIngestionVerdict
import com.fenix.studio.core.document.ingestion.AcademicDocumentFormat
import com.fenix.studio.core.document.ingestion.DocumentExtractionEngine
import com.fenix.studio.core.document.ingestion.DocumentSource
import com.fenix.studio.core.document.ingestion.MlKitLocalOcrEngine
import com.fenix.studio.core.document.ingestion.PreIngestionEngine
import com.fenix.studio.core.document.ingestion.PreIngestionOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Abre archivos elegidos con el selector del sistema.
 *
 * Es una interfaz para que el `ViewModel` se pueda probar en JVM con archivos sintéticos:
 * la implementación real usa `ContentResolver`, que no existe fuera de Android.
 */
interface DocumentOpener {

    /** Completa nombre, tamaño y tipo a partir del `uri`. `null` si la uri no es válida. */
    fun describe(uri: String): PickedDocument?

    /** Devuelve la fuente en streaming, o `null` si el archivo no se puede abrir. */
    fun open(picked: PickedDocument): DocumentSource?
}

/**
 * Implementación de producción: las uris del SAF (`OpenMultipleDocuments`) se resuelven
 * contra el `ContentResolver`. El archivo nunca se copia entero: se abre dos veces en
 * streaming (hash y extracción).
 */
class ContentResolverDocumentOpener(
    private val contentResolver: ContentResolver,
) : DocumentOpener {

    override fun describe(uri: String): PickedDocument? = runCatching {
        val parsed = Uri.parse(uri)
        var displayName: String? = null
        var sizeBytes: Long? = null

        contentResolver.query(
            parsed,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex).takeIf { it >= 0 }
                }
            }
        }

        PickedDocument(
            uri = uri,
            fileName = displayName?.takeIf { it.isNotBlank() }
                ?: parsed.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: "documento",
            sizeBytes = sizeBytes,
            mimeType = contentResolver.getType(parsed),
        )
    }.getOrNull()

    override fun open(picked: PickedDocument): DocumentSource? = runCatching {
        val parsed = Uri.parse(picked.uri)
        DocumentSource(
            fileName = picked.fileName,
            mimeType = picked.mimeType ?: contentResolver.getType(parsed),
            sizeBytes = picked.sizeBytes,
            streamProvider = {
                contentResolver.openInputStream(parsed)
                    ?: throw IOException("El sistema no concedió acceso a '${picked.fileName}'.")
            },
        )
    }.getOrNull()
}

/**
 * ViewModel de `P-05a` (Carga de Material y Pre-ingesta).
 *
 * Reglas que respeta:
 * - Un archivo que ya está en el lote se marca `DUPLICATE_SHA256` por hash, no por nombre.
 * - Un archivo que falla no aborta el lote: se muestra con su veredicto y se sigue.
 * - No escribe nada en Room: la propuesta de mapeo llega recién en `F5b`.
 * - `scan` es `suspend` y recibe documentos ya descritos, para poder probarla en JVM.
 */
class PreIngestionViewModel(
    private val engine: PreIngestionEngine,
    private val opener: DocumentOpener,
    private val ocrEngine: MlKitLocalOcrEngine? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreIngestionUiState())
    val uiState: StateFlow<PreIngestionUiState> = _uiState.asStateFlow()

    /** Resultados completos (con el texto sanitizado) para la pantalla siguiente. */
    private val outcomesByKey = LinkedHashMap<String, PreIngestionOutcome>()

    /** Hashes que ya existían antes de esta sesión (por ejemplo, de una cátedra guardada). */
    private val knownHashes = LinkedHashSet<String>()

    /**
     * Entrada desde el selector del sistema. Describe las uris y lanza el escaneo.
     */
    fun onDocumentsPicked(uris: List<String>, alreadyIngestedHashes: Set<String> = emptySet()) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val picked = uris.mapNotNull { uri -> runCatching { opener.describe(uri) }.getOrNull() }
            if (picked.isEmpty()) {
                _uiState.update {
                    it.copy(globalError = "No se pudo abrir ninguno de los archivos elegidos.")
                }
                return@launch
            }
            scan(picked, alreadyIngestedHashes)
        }
    }

    /**
     * Procesa los documentos en orden. Es `suspend` y pública para que los tests la
     * invoquen directamente dentro de `runTest`.
     */
    suspend fun scan(
        pickedDocuments: List<PickedDocument>,
        alreadyIngestedHashes: Set<String> = emptySet(),
    ) {
        val batch = pickedDocuments.distinctBy { it.uri }
        if (batch.isEmpty()) return

        knownHashes.addAll(alreadyIngestedHashes)
        _uiState.update {
            it.copy(
                isScanning = true,
                totalToScan = batch.size,
                scannedCount = 0,
                currentFileName = null,
                globalError = null,
            )
        }

        batch.forEachIndexed { index, picked ->
            _uiState.update { it.copy(currentFileName = picked.fileName, scannedCount = index) }

            val outcome = process(picked)

            if (outcome.isReadyForIngestion) {
                outcomesByKey[picked.uri] = outcome
            } else {
                outcomesByKey.remove(picked.uri)
            }

            _uiState.update { state ->
                state.copy(
                    items = state.items.filterNot { it.key == picked.uri } + outcome.toUi(picked),
                    scannedCount = index + 1,
                )
            }
        }

        _uiState.update { it.copy(isScanning = false, currentFileName = null) }
    }

    /** Quita un archivo del lote (no borra nada del almacenamiento). */
    fun onRemove(key: String) {
        outcomesByKey.remove(key)
        _uiState.update { state -> state.copy(items = state.items.filterNot { it.key == key }) }
    }

    /** Vacía el lote para empezar de nuevo. */
    fun onClearAll() {
        outcomesByKey.clear()
        _uiState.update {
            PreIngestionUiState(
                isScanning = false,
                scannedCount = 0,
                totalToScan = 0,
                currentFileName = null,
                globalError = null,
            )
        }
    }

    /** Documentos listos para la etapa siguiente, en el orden en que se cargaron. */
    fun readyDocuments(): List<PreIngestionOutcome> =
        _uiState.value.items.mapNotNull { item -> outcomesByKey[item.key] }

    private suspend fun process(picked: PickedDocument): PreIngestionOutcome {
        val source = runCatching { opener.open(picked) }.getOrNull()
            ?: return failedOutcome(
                picked = picked,
                message = "No se pudo abrir '${picked.fileName}'. Revisá que el archivo siga disponible.",
            )

        return runCatching { engine.prepare(source, knownHashes + outcomesByKey.values.map { it.sha256Hex }) }
            .getOrElse { error ->
                failedOutcome(
                    picked = picked,
                    message = "No se pudo procesar '${picked.fileName}': " +
                        (error.message ?: "error inesperado") + ".",
                )
            }
    }

    private fun failedOutcome(picked: PickedDocument, message: String): PreIngestionOutcome =
        PreIngestionOutcome(
            fileName = picked.fileName,
            format = AcademicDocumentFormat.fromExtension(picked.fileName),
            sha256Hex = "",
            verdict = DocumentIngestionVerdict.CORRUPT_OR_EMPTY,
            warningMessage = message,
            totalPages = 0,
            characterDensityPerPage = 0.0,
            tocEntries = emptyList(),
            sanitizationFindings = emptyList(),
            document = null,
            passivePreamble = "",
        )

    override fun onCleared() {
        ocrEngine?.close()
        super.onCleared()
    }

    companion object {
        /** Instancia de producción: OCR local empaquetado y acceso real al SAF. */
        fun persistent(context: Context): PreIngestionViewModel {
            val ocr = MlKitLocalOcrEngine()
            return PreIngestionViewModel(
                engine = PreIngestionEngine(
                    extractionEngine = DocumentExtractionEngine.withLocalOcr(ocr),
                ),
                opener = ContentResolverDocumentOpener(context.applicationContext.contentResolver),
                ocrEngine = ocr,
            )
        }
    }
}
