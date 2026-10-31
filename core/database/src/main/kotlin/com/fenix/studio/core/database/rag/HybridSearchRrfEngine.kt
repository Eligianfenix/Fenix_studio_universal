/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * ARCHIVO: core/database/src/main/kotlin/com/fenix/studio/core/database/rag/HybridSearchRrfEngine.kt
 * ================================================================================
 */

package com.fenix.studio.core.database.rag

import com.fenix.studio.core.database.converter.FenixRoomConverters
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Resultado clasificado del Motor de Búsqueda Híbrida Local (BM25 + Similitud Coseno + RRF).
 */
data class RankedChunkResult(
    val chunk: ChunkEntity,
    val lexicalRank: Int?,
    val vectorRank: Int?,
    val lexicalBm25Score: Double,
    val cosineSimilarity: Double,
    val rawRrfScore: Double,
    val weightedFinalScore: Double,
)

/**
 * Motor determinista de Búsqueda Híbrida Local (`MF-1.2`).
 * Implementa:
 * 1. Filtrado estricto pre-búsqueda por anclaje curricular (`catedraId`, `unitNumber`, `pointCode`).
 * 2. Ranking léxico BM25 ($k_1 = 1.2, b = 0.75$) sobre texto y encabezados.
 * 3. Similitud coseno vectorial sobre `embeddingBlob` (IEEE-754 float32 Little-Endian).
 * 4. Fusión de ranking Reciprocal Rank Fusion (RRF, constante $k = 60$) ponderada por el rol jerárquico
 *    del material (`1.0x` Doctrina Troncal vs `0.75x` Material Complementario).
 */
object HybridSearchRrfEngine {

    const val DEFAULT_RRF_K: Int = 60
    private const val BM25_K1: Double = 1.2
    private const val BM25_B: Double = 0.75

    /**
     * Ejecuta la búsqueda híbrida fusionada por RRF sobre el lote de fragmentos candidatos.
     */
    fun rankCandidates(
        candidates: List<ChunkEntity>,
        queryText: String,
        queryEmbedding: FloatArray,
        catedraId: String,
        unitNumber: Int? = null,
        pointCode: String? = null,
        rrfK: Int = DEFAULT_RRF_K,
        topK: Int = 15,
    ): List<RankedChunkResult> {
        require(rrfK > 0) { "La constante RRF k debe ser positiva." }
        if (candidates.isEmpty() || topK <= 0) return emptyList()

        // 1. Filtrado estricto por anclaje curricular (Cero contaminación cruzada entre unidades o puntos)
        val anchoredChunks = candidates.filter { chunk ->
            chunk.catedraId == catedraId &&
                (unitNumber == null || chunk.unitNumber == unitNumber) &&
                (pointCode == null || chunk.pointCode == pointCode)
        }
        if (anchoredChunks.isEmpty()) return emptyList()

        // 2. Puntaje Léxico BM25
        val bm25Scores = computeBm25Scores(anchoredChunks, queryText)
        val lexicalOrdered = anchoredChunks
            .mapIndexed { idx, chunk -> chunk.chunkId to bm25Scores[idx] }
            .filter { it.second > 0.0 }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })

        val lexicalRankByChunkId = lexicalOrdered
            .mapIndexed { index, pair -> pair.first to (index + 1) }
            .toMap()
        val bm25ByChunkId = anchoredChunks
            .mapIndexed { idx, chunk -> chunk.chunkId to bm25Scores[idx] }
            .toMap()

        // 3. Puntaje Vectorial por Similitud Coseno
        val cosineByChunkId = anchoredChunks.associate { chunk ->
            val docVector = FenixRoomConverters.blobToFloatArray(chunk.embeddingBlob)
            chunk.chunkId to computeCosineSimilarity(queryEmbedding, docVector)
        }

        val vectorOrdered = anchoredChunks
            .map { chunk -> chunk.chunkId to (cosineByChunkId[chunk.chunkId] ?: 0.0) }
            .filter { it.second > 0.0 }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })

        val vectorRankByChunkId = vectorOrdered
            .mapIndexed { index, pair -> pair.first to (index + 1) }
            .toMap()

        // 4. Fusión Reciprocal Rank Fusion (RRF) + Ponderación Doctrina Troncal (1.0x) vs Complementario (0.75x)
        return anchoredChunks
            .map { chunk ->
                val lexRank = lexicalRankByChunkId[chunk.chunkId]
                val vecRank = vectorRankByChunkId[chunk.chunkId]

                val lexTerm = if (lexRank != null) 1.0 / (rrfK + lexRank).toDouble() else 0.0
                val vecTerm = if (vecRank != null) 1.0 / (rrfK + vecRank).toDouble() else 0.0
                val rawRrf = lexTerm + vecTerm

                val roleMultiplier = chunk.priorityWeight.coerceIn(0.1f, 2.0f).toDouble()
                val weightedScore = rawRrf * roleMultiplier

                RankedChunkResult(
                    chunk = chunk,
                    lexicalRank = lexRank,
                    vectorRank = vecRank,
                    lexicalBm25Score = bm25ByChunkId[chunk.chunkId] ?: 0.0,
                    cosineSimilarity = cosineByChunkId[chunk.chunkId] ?: 0.0,
                    rawRrfScore = rawRrf,
                    weightedFinalScore = weightedScore,
                )
            }
            .filter { it.rawRrfScore > 0.0 }
            .sortedWith(
                compareByDescending<RankedChunkResult> { it.weightedFinalScore }
                    .thenByDescending { it.cosineSimilarity }
                    .thenByDescending { it.lexicalBm25Score }
                    .thenBy { it.chunk.chunkId },
            )
            .take(topK)
    }

    /**
     * Calcula la similitud del coseno en [-1.0, 1.0] entre dos vectores float32.
     */
    fun computeCosineSimilarity(a: FloatArray, b: FloatArray): Double {
        val len = min(a.size, b.size)
        if (len == 0) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in 0 until len) {
            val va = a[i].toDouble()
            val vb = b[i].toDouble()
            dot += va * vb
            normA += va * va
            normB += vb * vb
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom <= 1e-12) 0.0 else (dot / denom).coerceIn(-1.0, 1.0)
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 }

    private fun computeBm25Scores(
        docs: List<ChunkEntity>,
        queryText: String,
    ): DoubleArray {
        val queryTokens = tokenize(queryText).distinct()
        if (queryTokens.isEmpty() || docs.isEmpty()) return DoubleArray(docs.size)

        val tokenizedDocs = docs.map { doc ->
            tokenize("${doc.sectionHeading} ${doc.chunkText}")
        }
        val avgDocLen = tokenizedDocs.sumOf { it.size }.toDouble().coerceAtLeast(1.0) / docs.size.toDouble()
        val nDocs = docs.size.toDouble()

        val docFreq = queryTokens.associateWith { term ->
            tokenizedDocs.count { tokens -> tokens.contains(term) }
        }

        return DoubleArray(docs.size) { idx ->
            val tokens = tokenizedDocs[idx]
            if (tokens.isEmpty()) {
                0.0
            } else {
                val docLen = tokens.size.toDouble()
                var score = 0.0
                for (term in queryTokens) {
                    val tf = tokens.count { it == term }.toDouble()
                    if (tf > 0.0) {
                        val df = (docFreq[term] ?: 0).toDouble()
                        val idf = ln(1.0 + (nDocs - df + 0.5) / (df + 0.5))
                        val numerator = tf * (BM25_K1 + 1.0)
                        val denominator = tf + BM25_K1 * (1.0 - BM25_B + BM25_B * (docLen / avgDocLen))
                        score += idf * (numerator / denominator)
                    }
                }
                score
            }
        }
    }
}
