/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-10-26 00:07:30 -03:00
 * ARCHIVO: core/document-engine/src/test/kotlin/com/fenix/studio/core/document/OpenXmlStreamerAndQueueTest.kt
 * ================================================================================
 */

package com.fenix.studio.core.document

import com.fenix.studio.core.database.entity.ProcessingCheckpointEntity
import com.fenix.studio.core.database.entity.ProtocolBlockEntity
import com.fenix.studio.core.document.converter.DocxPdfBidirectionalConverter
import com.fenix.studio.core.document.openxml.Apa7ParagraphStyle
import com.fenix.studio.core.document.openxml.DocxParagraphNode
import com.fenix.studio.core.document.openxml.OpenXmlStreamer
import com.fenix.studio.core.document.pptx.FenixSlideModel
import com.fenix.studio.core.document.pptx.NativeHorizontalProjectorDeck
import com.fenix.studio.core.document.pptx.PptxSlideDeckGenerator
import com.fenix.studio.core.document.queue.AtomicCheckpointSink
import com.fenix.studio.core.document.queue.FenixProcessingQueueManager
import com.fenix.studio.core.document.queue.PointExecutionStage
import com.fenix.studio.core.document.queue.PointQueueStatus
import com.fenix.studio.core.document.video.LocalMp4VideoCapsuleSynthesizer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class OpenXmlStreamerAndQueueTest {

    @Test
    fun `OpenXmlStreamer writes valid APA 7th docx package with 1440 twip margins 480 double spacing and round-trips streaming read`() {
        val streamer = OpenXmlStreamer()
        val inputNodes = listOf(
            DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_HEADING_1,
                text = "Unidad 1: Conceptos Base",
                isBold = true,
            ),
            DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_BODY_PARAGRAPH,
                text = "El desarrollo expositivo cumple estrictamente la sangría de primera línea y el interlineado doble.",
            ),
            DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_BLOCK_QUOTE_40_WORDS,
                text = "Cita textual extensa de más de cuarenta palabras con sangría izquierda de media pulgada.",
            ),
            DocxParagraphNode(
                style = Apa7ParagraphStyle.APA_HANGING_REFERENCE,
                text = "AutorAlfa, A. (2024). Tratado de Arquitectura Modular. Editorial Técnica.",
            ),
        )

        val baos = ByteArrayOutputStream()
        streamer.streamWriteDocx(inputNodes, baos)
        val docxBytes = baos.toByteArray()

        // 1. Inspeccionar entradas ZIP y verificar reglas APA 7.ª Edición
        val entryMap = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryMap[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertTrue(entryMap.containsKey("[Content_Types].xml"))
        assertTrue(entryMap.containsKey("word/document.xml"))
        assertTrue(entryMap.containsKey("word/styles.xml"))

        val documentXml = entryMap.getValue("word/document.xml")
        assertTrue(documentXml.contains("""w:top="1440""""))
        assertTrue(documentXml.contains("""w:right="1440""""))
        assertTrue(documentXml.contains("""w:bottom="1440""""))
        assertTrue(documentXml.contains("""w:left="1440""""))
        assertTrue(documentXml.contains("""w:line="480""""))
        assertTrue(documentXml.contains("""w:hanging="720""""))

        // 2. Verificar lectura bidireccional en streaming (readDocxParagraphsStreaming) y conversor PDF
        val readBackNodes = streamer.readDocxParagraphsStreaming(ByteArrayInputStream(docxBytes))
        assertEquals(4, readBackNodes.size)
        assertEquals("Unidad 1: Conceptos Base", readBackNodes[0].text)
        assertEquals(Apa7ParagraphStyle.APA_HANGING_REFERENCE, readBackNodes[3].style)

        val converter = DocxPdfBidirectionalConverter(streamer)
        val paginatedPages = converter.convertDocxNodesToPaginatedPdfModel("CONCEPTOS BASE", readBackNodes)
        assertEquals(1, paginatedPages.size)
        assertEquals(1440, paginatedPages.first().marginTwipsAllSides)
    }

    @Test
    fun `PptxSlideDeckGenerator and LocalMp4VideoCapsuleSynthesizer produce valid OpenXML pptx and 720p 15fps plans`() {
        val pptxGenerator = PptxSlideDeckGenerator()
        val deck = NativeHorizontalProjectorDeck(
            deckTitle = "Defensa Oral Unidad 1",
            slides = listOf(
                FenixSlideModel(
                    slideNumber = 1,
                    title = "1.1 Estructura Invariante",
                    keyBulletPoints = listOf("Desacoplamiento modular", "Mapeo 1 a 1"),
                    speakerNotes = "Explicar cómo el invariante conserva la trazabilidad.",
                ),
                FenixSlideModel(
                    slideNumber = 2,
                    title = "1.2 Síntesis Comparativa",
                    keyBulletPoints = listOf("Contraste experimental", "Validación empírica"),
                    speakerNotes = "Destacar los resultados de la tabla comparativa.",
                ),
            ),
        )

        val pptxOut = ByteArrayOutputStream()
        pptxGenerator.generatePptxStream(deck, pptxOut)

        val pptxEntries = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(pptxOut.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                pptxEntries += entry.name
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        assertTrue(pptxEntries.contains("[Content_Types].xml"))
        assertTrue(pptxEntries.contains("ppt/presentation.xml"))
        assertTrue(pptxEntries.contains("ppt/slides/slide1.xml"))
        assertTrue(pptxEntries.contains("ppt/slides/slide2.xml"))

        val videoSynthesizer = LocalMp4VideoCapsuleSynthesizer()
        val videoPlan = videoSynthesizer.buildSynthesisPlan(
            capsuleTitle = "Repaso Rápido Unidad 1",
            auditedPropositions = listOf(
                "Invariante 1" to "La conservación de flujo se verifica en todo el dominio analizado.",
                "Invariante 2" to "La convergencia asintótica garantiza estabilidad frente a perturbaciones.",
            ),
        )
        assertEquals(1280, videoPlan.widthPx)
        assertEquals(720, videoPlan.heightPx)
        assertEquals(15, videoPlan.framesPerSecond)
        assertEquals(2, videoPlan.scenes.size)
        assertTrue(videoPlan.totalFrames > 0)
    }

    @Test
    fun `FenixProcessingQueueManager persists atomic checkpoints enables early reading RN-06 and resumes without repeating completed stages`() = runTest {
        val committedBlocks = mutableListOf<ProtocolBlockEntity>()
        val completedStagesByPoint = mutableMapOf<String, MutableSet<PointExecutionStage>>()

        val fakeSink = object : AtomicCheckpointSink {
            override suspend fun commitBlockAndCheckpoint(
                block: ProtocolBlockEntity,
                checkpoint: ProcessingCheckpointEntity,
            ) {
                committedBlocks += block
                val stage = PointExecutionStage.entries.first { it.correspondingSection == block.sectionType }
                completedStagesByPoint.getOrPut(block.pointCode) { mutableSetOf() }.add(stage)
            }

            override suspend fun getCompletedStagesForPoint(
                unitId: String,
                pointCode: String,
            ): Set<PointExecutionStage> = completedStagesByPoint[pointCode]?.toSet() ?: emptySet()

            override suspend fun getBlocksForCompletedPoint(
                unitId: String,
                pointCode: String,
            ): List<ProtocolBlockEntity> = committedBlocks.filter { it.pointCode == pointCode }
        }

        // Simulamos que el punto "1.1" ya estaba 100% completado y que el punto "1.2" quedó interrumpido a mitad de camino (2 etapas listas)
        completedStagesByPoint["1.1"] = PointExecutionStage.entries.toMutableSet()
        completedStagesByPoint["1.2"] = mutableSetOf(
            PointExecutionStage.PARTE_0_MAPA,
            PointExecutionStage.PARTE_A_DESARROLLO,
        )

        val queueManager = FenixProcessingQueueManager(fakeSink)
        val executedSyntheses = mutableListOf<Pair<String, PointExecutionStage>>()

        queueManager.processOrResumePointsSequentially(
            catedraId = "cat-01",
            unitId = "unit-01",
            pointCodesInOrder = listOf("1.1", "1.2", "1.3"),
            nowEpochMsProvider = { 50_000L },
        ) { ptCode, stage ->
            executedSyntheses += ptCode to stage
            "Texto sintetizado para $ptCode - ${stage.name}"
        }

        // "1.1" jamás debe volver a ejecutarse (0 etapas); "1.2" solo ejecuta las 4 etapas faltantes; "1.3" ejecuta las 6 etapas = 10 llamadas totales
        assertEquals(10, executedSyntheses.size)
        assertTrue(executedSyntheses.none { it.first == "1.1" })
        assertTrue(executedSyntheses.none { it.first == "1.2" && it.second == PointExecutionStage.PARTE_A_DESARROLLO })

        val finalProgress = queueManager.progressState.value
        assertEquals(setOf("1.1", "1.2", "1.3"), finalProgress.earlyReadablePointCodes)
        assertEquals(PointQueueStatus.COMPLETED, finalProgress.statusByPointCode["1.2"])
        assertEquals(PointQueueStatus.COMPLETED, finalProgress.statusByPointCode["1.3"])
    }
}
