# ADR-0011 — Motor de pre-ingesta multiformato, lectura forense y mapeo difuso

**Fecha:** 2026-11-15
**Estado:** APROBADO (F4a y F4b implementados; falta la UI y la prueba con cátedras reales)
**Decisor:** Eli Gian Cortez Leonardi

## 1. Contexto

El material real que recibe un estudiante no es un PDF ordenado. En
`catedras de prueba/` hay 41 archivos: 36 PDF, 3 PPTX, 1 ODT y 1 DOCX, con nombres
arbitrarios ("WhatsApp Doc 2026-04-18.pdf", "scan_catedra_parte2.pdf") y sin relación
explícita con las unidades del programa.

El `PreIngestionGuardian` ya existía, pero solo podía mirar el hash y la densidad de
texto: **no había ningún extractor**. Sin extracción, la pre-ingesta era un veredicto
sobre un archivo que nadie había leído.

El creador pidió explícitamente que el motor sea real y multiformato, siguiendo el deep
research `Arquitectura_e_Ingesta_Fenix_v3.md` (Fase 0) y la matriz de
`MF-1.5/02_MATRIZ_SOPORTE_MULTIFORMATO_ARCHIVOS.md`. También pidió no tocar el parser
curricular hasta probar con cátedras reales.

## 2. Decisión

### 2.1 Extracción por formato, sin backend y sin costo

`AcademicDocumentFormat` + `AcademicDocumentFormatDetector` clasifican por extensión,
MIME y firma de cabecera. Extractores implementados:

| Formato | Mecanismo | Corre en JVM |
|---|---|---|
| PDF | PDFBox-Android (capa nativa) → `PdfRenderer` + OCR si no hay texto | No |
| DOCX | `word/document.xml` de ZIP | Sí |
| ODT | `content.xml` de ZIP | Sí |
| PPTX | `ppt/slides/slideN.xml`, orden numérico | Sí |
| EPUB | `container.xml` → OPF → `spine` | Sí |
| TXT / MD / código | Lector UTF-8 acotado | Sí |
| HTML / XHTML | `MarkupText` (sin parser XML estricto) | Sí |
| Imagen | ML Kit local empaquetado | No |

`BoundedZipReader` corta zip bombs: 16 MB por entrada, 48 MB total, 512 entradas.
Los PDF se copian a un temporal en disco: nunca se cargan completos en RAM.

### 2.2 Capa forense pasiva

`PassiveContentSanitizer` implementa la directiva del creador: el material que sube el
estudiante se lee como **objeto pasivo de estudio**, aunque sea código.

- Detecta inyecciones con patrones deterministas, en inglés y español, con o sin acentos.
- Neutraliza delimitadores de rol (`<|im_start|>`, `[INST]`, `<<SYS>>`).
- Elimina caracteres bidireccionales y de ancho cero, que sirven para esconder órdenes.
- No borra el contenido: marca la línea con `[OBJETO_DE_ESTUDIO_PASIVO] [FUENTE]` y
  expone `passivePreamble` para anteponer a cualquier prompt.
- **No usa un LLM para juzgar**: si el atacante escribe el texto, no puede negociar con
  el juez.

Sanitizar no bloquea la ingesta. El material sigue siendo estudiable; solo deja de ser
obedecible.

### 2.3 Pipeline de pre-ingesta

`PreIngestionEngine.prepare(source, existingHashes)`:

1. Detecta formato.
2. SHA-256 en streaming (8 KB) y corte de duplicados **antes** de extraer.
3. Extrae hasta 15 páginas.
4. Sanitiza.
5. Aplica el guardián: densidad, TOC y veredicto.

Veredictos nuevos:

- `OCR_APPLIED`: el texto se obtuvo con OCR local (antes se reportaba como nativo).
- `UNSUPPORTED_FORMAT`: la instalación no tiene extractor para ese formato.
- Un TXT o DOCX corto **no** se marca `OCR_REQUIRED`: pedir OCR a un Word no tiene
  sentido. Se informa poco texto y se deja listo para ingerir.

### 2.4 Conciliación difusa archivo ↔ cita

`BibliographicFuzzyMatcher` conserva su API anterior y suma la fórmula del deep research:

```
total = 0.45 · (0.55·autor + 0.45·Jaro-Winkler(título))
      + 0.35 · Levenshtein ponderado por token
      + 0.20 · SimHash 64 bits (tolerancia Hamming 4)
      + 0.18 · bono patronímico (solo con evidencia de título)
```

- `AcademicTextNormalizer` normaliza (minúsculas, sin acentos, sin stopwords académicas),
  tokeniza y calcula SimHash con FNV-1a sobre 3-gramas y 4-gramas.
- Levenshtein usa fila rodante: `O(min(|a|,|b|))` de memoria, sin matrices.
- El bono de autor exige además coincidencia de título (`≥ 0.35` tokens o `≥ 0.68`
  Jaro-Winkler): un apellido común no alcanza para asignar cualquier archivo.
- Umbral de asignación fuerte: `0.72`. Entre `0.60` y `0.72` queda como **sugerencia**
  para revisión humana.

`DocumentCandidateExtractor` toma las primeras 3 páginas + nombre del archivo y produce
`DocumentCandidateMetadata`: autores candidatos, título probable, tokens significativos,
SimHash y offset de página impresa (ancla conservadora: una línea que sea exactamente
`1` en una página posterior a la primera; si no hay ancla, offset 0).

### 2.5 Propuesta pre-ordenada, sin escrituras

`CatedraPreorderedMapper.propose(...)` devuelve una
`CatedraPreorderedMappingProposal` serializable:

- Archivos que coinciden con citas, agrupados por código de punto (`2.1`) o `UNIDAD_n`.
- Programa analítico detectado → `SYLLABUS_RECTOR`.
- Resto → heurística de la Resolución Forense 24: PPTX → material complementario;
  encabezados "Trabajo Práctico / Guía / Fallo / Sentencia / Noticia" o ≤ 8 páginas →
  casuística; documentos largos → doctrina primaria.
- Duplicados por SHA-256 dentro del lote: se procesan una vez y se advierte.
- Autores consolidados por lexema patronímico.

Reutiliza `MaterialRoleType` de `core:database` y `SyllabusGraph` de `domain` en vez de
crear enums paralelos. Por eso `document-engine` declara esas dependencias como `api`.

**El parser curricular no se modificó.** La propuesta se apoya en
`SyllabusCitationEntry`, un contrato neutro que hoy puede llenar el parser y mañana
cualquier importación manual.

## 3. Consecuencias

- `:core:document-engine` pasa de 0 a 43 tests JVM: formatos, extractores con ZIP
  sintético, sanitizer, pre-ingesta, normalizador, matcher y propuesta.
- El APK debug crece a ~323 MB (PDFBox + ML Kit + 27 dex). Es un build de depuración;
  release con R8 debe medirse antes de distribuir.
- La extracción PDF y el OCR **no se pueden probar en JVM**: requieren el Redmi. Hasta
  esa prueba, el camino PDF está escrito pero no validado en hardware real.
- El offset de página es conservador a propósito: sin ancla explícita queda en 0. Se
  recalibrará con los 36 PDF reales.
- No hay persistencia Room de la propuesta todavía. Es un dato serializable; se guardará
  cuando exista la pantalla de revisión (F5), para no migrar el esquema dos veces.
- El costo sigue siendo cero: extracción local, OCR local, matching determinista. Ningún
  archivo sale del dispositivo.

## 4. Alternativas descartadas

- **Apache Tika / POI completo:** decenas de MB y APIs de escritorio; sobra para leer
  texto académico.
- **Solo PDF:** el lote real tiene PPTX, ODT y DOCX; habría que pedirle al estudiante que
  convierta, que es justo lo que la app promete evitar.
- **Parser XML estricto para OOXML/ODF:** los documentos reales llegan con entidades y
  namespaces rotos; fallar donde el estudiante necesita leer es peor que tolerar.
- **Pedirle a un LLM que detecte inyecciones:** introduce costo, red y una superficie
  nueva de ataque ("ignorá el detector").
- **Auto-ingestar sin revisión:** el mapeo es una propuesta; asignar 30 archivos sin
  confirmación convierte un error de matcher en material de estudio equivocado.
- **Crear `MaterialType` nuevo:** ya existe `MaterialRoleType` con prioridades de
  estudio; duplicarlo obligaría a mapear dos enums en toda la app.
