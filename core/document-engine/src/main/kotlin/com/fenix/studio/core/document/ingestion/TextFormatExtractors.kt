/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 22:38:00 -03:00
 * ARCHIVO: core/document-engine/src/main/kotlin/com/fenix/studio/core/document/ingestion/TextFormatExtractors.kt
 * ================================================================================
 */

package com.fenix.studio.core.document.ingestion

/** Texto plano, Markdown, código fuente y HTML: lectura directa con buffer acotado. */
class PlainTextDocumentExtractor(
    override val formats: Set<AcademicDocumentFormat> = setOf(
        AcademicDocumentFormat.PLAIN_TEXT,
        AcademicDocumentFormat.MARKDOWN,
        AcademicDocumentFormat.SOURCE_CODE,
        AcademicDocumentFormat.HTML,
    ),
    private val maxCharacters: Int = 2_000_000,
) : AcademicDocumentExtractor {

    override suspend fun extract(source: DocumentSource, maxPages: Int): ExtractedContent {
        val format = AcademicDocumentFormatDetector.detect(source.fileName, source.mimeType)
        val raw = source.openStream().bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(8 * 1024)
            val builder = StringBuilder(minOf(maxCharacters, 64 * 1024))
            var total = 0
            while (total < maxCharacters) {
                val read = reader.read(buffer)
                if (read <= 0) break
                builder.append(buffer, 0, read)
                total += read
            }
            builder.toString()
        }

        val text = if (format == AcademicDocumentFormat.HTML) {
            MarkupText.toPlainText(raw, HTML_BLOCK_TAGS)
        } else {
            raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        }

        val pages = MarkupText.chunkIntoPages(text)
            .take(if (maxPages <= 0) Int.MAX_VALUE else maxPages)
            .mapIndexed { index, pageText -> RawDocumentPage(index + 1, pageText) }

        val warnings = buildList {
            if (raw.length >= maxCharacters) {
                add("El archivo supera ${maxCharacters / 1000} mil caracteres: se leyó solo el inicio.")
            }
        }

        return ExtractedContent(
            pages = pages,
            totalPages = pages.size.coerceAtLeast(1),
            mode = if (text.isBlank()) ExtractionMode.UNSUPPORTED else ExtractionMode.NATIVE_TEXT,
            warnings = warnings,
        )
    }

    private companion object {
        val HTML_BLOCK_TAGS = setOf(
            "p", "div", "br", "li", "tr", "section", "article", "header", "footer",
            "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "table",
        )
    }
}

/** DOCX: se lee `word/document.xml` y se convierte OOXML a texto. */
class DocxDocumentExtractor : AcademicDocumentExtractor {

    override val formats: Set<AcademicDocumentFormat> = setOf(AcademicDocumentFormat.DOCX)

    override suspend fun extract(source: DocumentSource, maxPages: Int): ExtractedContent {
        val entries = BoundedZipReader.readSelectedEntries(source) { name ->
            name == "word/document.xml"
        }
        val xml = entries["word/document.xml"]
            ?: return ExtractedContent(
                pages = emptyList(),
                totalPages = 0,
                mode = ExtractionMode.UNSUPPORTED,
                warnings = listOf("El DOCX no contiene word/document.xml; puede estar dañado."),
            )

        val normalized = xml
            .replace("<w:tab/>", "\t")
            .replace("<w:tab />", "\t")
            .replace("<w:br/>", "\n")
            .replace("<w:br />", "\n")
            .replace("<w:cr/>", "\n")

        val text = MarkupText.toPlainText(normalized, DOCX_BLOCK_TAGS)
        val pages = MarkupText.chunkIntoPages(text)
            .take(if (maxPages <= 0) Int.MAX_VALUE else maxPages)
            .mapIndexed { index, pageText -> RawDocumentPage(index + 1, pageText) }

        return ExtractedContent(
            pages = pages,
            totalPages = pages.size.coerceAtLeast(1),
            mode = if (text.isBlank()) ExtractionMode.UNSUPPORTED else ExtractionMode.NATIVE_TEXT,
            warnings = emptyList(),
        )
    }

    private companion object {
        val DOCX_BLOCK_TAGS = setOf("p", "tr", "tc", "br", "sdt")
    }
}

/** ODT: el contenido vive en `content.xml`. */
class OdtDocumentExtractor : AcademicDocumentExtractor {

    override val formats: Set<AcademicDocumentFormat> = setOf(AcademicDocumentFormat.ODT)

    override suspend fun extract(source: DocumentSource, maxPages: Int): ExtractedContent {
        val entries = BoundedZipReader.readSelectedEntries(source) { name ->
            name == "content.xml"
        }
        val xml = entries["content.xml"]
            ?: return ExtractedContent(
                pages = emptyList(),
                totalPages = 0,
                mode = ExtractionMode.UNSUPPORTED,
                warnings = listOf("El ODT no contiene content.xml; puede estar dañado."),
            )

        val normalized = xml
            .replace("<text:tab/>", "\t")
            .replace("<text:tab />", "\t")
            .replace("<text:line-break/>", "\n")
            .replace("<text:line-break />", "\n")
            .replace("<text:s/>", " ")

        val text = MarkupText.toPlainText(normalized, ODT_BLOCK_TAGS)
        val pages = MarkupText.chunkIntoPages(text)
            .take(if (maxPages <= 0) Int.MAX_VALUE else maxPages)
            .mapIndexed { index, pageText -> RawDocumentPage(index + 1, pageText) }

        return ExtractedContent(
            pages = pages,
            totalPages = pages.size.coerceAtLeast(1),
            mode = if (text.isBlank()) ExtractionMode.UNSUPPORTED else ExtractionMode.NATIVE_TEXT,
            warnings = emptyList(),
        )
    }

    private companion object {
        val ODT_BLOCK_TAGS = setOf("p", "h", "table-row", "table-cell")
    }
}

/** PPTX: una página por diapositiva, leyendo `ppt/slides/slideN.xml`. */
class PptxDocumentExtractor : AcademicDocumentExtractor {

    override val formats: Set<AcademicDocumentFormat> = setOf(AcademicDocumentFormat.PPTX)

    override suspend fun extract(source: DocumentSource, maxPages: Int): ExtractedContent {
        val slideEntryRegex = Regex("""ppt/slides/slide(\d+)\.xml""")
        val entries = BoundedZipReader.readSelectedEntries(source) { name ->
            slideEntryRegex.matches(name)
        }
        if (entries.isEmpty()) {
            return ExtractedContent(
                pages = emptyList(),
                totalPages = 0,
                mode = ExtractionMode.UNSUPPORTED,
                warnings = listOf("La presentación no contiene diapositivas legibles."),
            )
        }

        val ordered = entries.entries.sortedBy { entry ->
            slideEntryRegex.find(entry.key)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
        }

        val pages = ordered
            .take(if (maxPages <= 0) Int.MAX_VALUE else maxPages)
            .mapIndexed { index, (_, xml) ->
                RawDocumentPage(
                    pageNumber = index + 1,
                    text = MarkupText.toPlainText(xml, PPTX_BLOCK_TAGS),
                )
            }

        return ExtractedContent(
            pages = pages,
            totalPages = ordered.size,
            mode = if (pages.all { it.text.isBlank() }) ExtractionMode.UNSUPPORTED else ExtractionMode.NATIVE_TEXT,
            warnings = emptyList(),
        )
    }

    private companion object {
        val PPTX_BLOCK_TAGS = setOf("p", "br")
    }
}

/** EPUB: se respeta el orden del `spine` del OPF; cada capítulo es una página lógica. */
class EpubDocumentExtractor : AcademicDocumentExtractor {

    override val formats: Set<AcademicDocumentFormat> = setOf(AcademicDocumentFormat.EPUB)

    override suspend fun extract(source: DocumentSource, maxPages: Int): ExtractedContent {
        val archive = BoundedZipReader.readSelectedEntries(source) { name ->
            name == "META-INF/container.xml" ||
                name.endsWith(".opf") ||
                name.endsWith(".xhtml", ignoreCase = true) ||
                name.endsWith(".html", ignoreCase = true) ||
                name.endsWith(".htm", ignoreCase = true)
        }
        if (archive.isEmpty()) {
            return ExtractedContent(
                pages = emptyList(),
                totalPages = 0,
                mode = ExtractionMode.UNSUPPORTED,
                warnings = listOf("El EPUB no contiene capítulos legibles."),
            )
        }

        val orderedChapters = resolveSpine(archive)
            .ifEmpty {
                archive.entries
                    .filter { !it.key.startsWith("META-INF/") && !it.key.endsWith(".opf") }
                    .sortedBy { it.key }
                    .map { it.key }
            }

        val limit = if (maxPages <= 0) Int.MAX_VALUE else maxPages
        val pages = orderedChapters
            .asSequence()
            .mapNotNull { path -> archive[path] }
            .map { xml -> MarkupText.toPlainText(xml, EPUB_BLOCK_TAGS) }
            .filter { it.isNotBlank() }
            .take(limit)
            .mapIndexed { index, chapterText -> RawDocumentPage(index + 1, chapterText) }
            .toList()

        return ExtractedContent(
            pages = pages,
            totalPages = orderedChapters.size,
            mode = if (pages.isEmpty()) ExtractionMode.UNSUPPORTED else ExtractionMode.NATIVE_TEXT,
            warnings = emptyList(),
        )
    }

    private fun resolveSpine(archive: Map<String, String>): List<String> {
        val container = archive["META-INF/container.xml"] ?: return emptyList()
        val opfPath = Regex("""full-path\s*=\s*"([^"]+)"""")
            .find(container)?.groupValues?.get(1) ?: return emptyList()
        val opf = archive[opfPath] ?: return emptyList()

        val manifest = Regex("""<item\b[^>]*>""").findAll(opf).mapNotNull { match ->
            val tag = match.value
            val id = Regex("""id\s*=\s*"([^"]+)"""").find(tag)?.groupValues?.get(1) ?: return@mapNotNull null
            val href = Regex("""href\s*=\s*"([^"]+)"""").find(tag)?.groupValues?.get(1) ?: return@mapNotNull null
            id to href
        }.toMap()

        val baseDirectory = opfPath.substringBeforeLast('/', "")
        return Regex("""<itemref\b[^>]*idref\s*=\s*"([^"]+)"""")
            .findAll(opf)
            .mapNotNull { match -> manifest[match.groupValues[1]] }
            .map { href ->
                val cleanHref = href.substringBefore('#')
                if (baseDirectory.isEmpty()) cleanHref else "$baseDirectory/$cleanHref"
            }
            .toList()
    }

    private companion object {
        val EPUB_BLOCK_TAGS = setOf(
            "p", "div", "br", "li", "tr", "section", "article",
            "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre",
        )
    }
}
