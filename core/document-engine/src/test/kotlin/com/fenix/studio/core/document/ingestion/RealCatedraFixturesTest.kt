/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-15 02:35:00 -03:00
 * ARCHIVO: core/document-engine/src/test/kotlin/com/fenix/studio/core/document/ingestion/RealCatedraFixturesTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

import com.fenix.studio.core.document.guardian.DocumentIngestionVerdict
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream

/**
 * Prueba de humo contra el material real de `catedras de prueba/`.
 *
 * Solo corre cuando ese directorio existe (máquina de desarrollo). En CI o en otra
 * máquina se salta: no se inventan fixtures que digan que el motor funciona.
 *
 * Los PDF quedan afuera a propósito: PDFBox-Android y ML Kit necesitan el dispositivo.
 */
class RealCatedraFixturesTest {

    private val engine = PreIngestionEngine()

    @Test
    fun `the real docx odt and pptx files are readable end to end`() = runBlocking {
        val fixtures = locateFixturesDirectory()
        assumeTrue(fixtures != null, "No está el directorio 'catedras de prueba' en esta máquina.")
        val files = fixtures!!.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in JVM_SAFE_EXTENSIONS }
            .toList()

        assumeTrue(files.isNotEmpty(), "No hay DOCX/ODT/PPTX reales para probar.")
        assertTrue(files.size >= 5, "Se esperaban al menos 5 archivos reales, hay ${files.size}.")

        val problems = mutableListOf<String>()
        files.forEach { file ->
            val source = DocumentSource(
                fileName = file.name,
                sizeBytes = file.length(),
            ) { FileInputStream(file) }

            val result = engine.prepare(source)
            val text = result.document?.fullText.orEmpty()

            if (result.verdict == DocumentIngestionVerdict.CORRUPT_OR_EMPTY ||
                result.verdict == DocumentIngestionVerdict.UNSUPPORTED_FORMAT
            ) {
                problems += "${file.name}: ${result.verdict} → ${result.warningMessage}"
            }
            if (text.length < 200) {
                problems += "${file.name}: solo ${text.length} caracteres extraídos"
            }
        }

        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `real files produce a proposal without writing anything`() = runBlocking {
        val fixtures = locateFixturesDirectory()
        assumeTrue(fixtures != null, "No está el directorio 'catedras de prueba' en esta máquina.")
        val files = fixtures!!.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in JVM_SAFE_EXTENSIONS }
            .toList()
        assumeTrue(files.isNotEmpty(), "No hay DOCX/ODT/PPTX reales para probar.")

        val documents = files.mapNotNull { file ->
            val source = DocumentSource(file.name, sizeBytes = file.length()) { FileInputStream(file) }
            engine.prepare(source).document
        }

        val proposal = CatedraPreorderedMapper().propose(
            catedraId = "catedra-real",
            subjectTitle = "Cátedra real",
            documents = documents,
            citations = emptyList(),
        )

        assertFalse(documents.isEmpty())
        assertTrue(proposal.totalDocuments == documents.size)
        assertTrue(proposal.warnings.isEmpty(), proposal.warnings.joinToString("\n"))
    }

    private fun locateFixturesDirectory(): File? {
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(5) {
            val candidate = directory?.resolve("catedras de prueba")
            if (candidate != null && candidate.isDirectory) return candidate
            directory = directory?.parentFile
        }
        return null
    }

    private companion object {
        val JVM_SAFE_EXTENSIONS = setOf("docx", "odt", "pptx")
    }
}
