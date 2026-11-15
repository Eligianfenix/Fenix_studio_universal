# ADR-0012 — Pantalla de carga y pre-ingesta (`P-05a`)

**Fecha:** 2026-11-15
**Estado:** APROBADO (F5a implementado; falta la prueba en el Redmi)
**Decisor:** Eli Gian Cortez Leonardi

## 1. Contexto

El motor de `F4` ya lee archivos reales y propone un mapeo, pero **no había forma de
llegar a él desde la app**: la persona solo podía elegir un archivo suelto y no veía
ningún veredicto. Sin pantalla, la pre-ingesta era una API probada por tests y no una
función del producto.

El material real son 41 archivos con nombres arbitrarios, elegidos de a varios y desde
un teléfono de gama entrada (Redmi 14C, 4 GB). El selector tiene que aceptar cualquier
formato y el escaneo no puede bloquear la interfaz ni abortar el lote por un archivo
roto.

## 2. Decisión

### 2.1 Ubicación en el flujo

`P-05` (configuración) navega a `P-05a` (carga y pre-ingesta) y `P-05a` navega a
`P-05b` (matriz curricular). La pantalla nueva se intercala sin renumerar las demás:

```
P-05 configuración → P-05a carga/pre-ingesta → P-05b matriz → P-06 monitor
```

### 2.2 Selector del sistema, sin copiar archivos

Se usa `ActivityResultContracts.OpenMultipleDocuments()` con `*/*`. El filtro por MIME
del SAF no distingue "código fuente" ni "Markdown", así que filtrar por extensión
dejaría archivos afuera; el motor detecta el formato real por extensión, MIME y firma
de cabecera.

El archivo **no se copia** al almacenamiento de la app: `DocumentSource` abre el
`ContentResolver` dos veces en streaming (una para SHA-256 y otra para extraer). Así un
PDF de 150 MB no entra completo en RAM (estrategia anti-OOM de `MF-1.5`).

### 2.3 `DocumentOpener`: UI testeable sin Android

`PreIngestionViewModel` depende de la interfaz `DocumentOpener`
(`describe(uri)` + `open(picked)`). La implementación real es
`ContentResolverDocumentOpener`; los tests usan un abridor falso con archivos
sintéticos. `scan(...)` es `suspend` y pública para invocarla desde `runTest`, sin
necesidad de `Dispatchers.Main`.

### 2.4 Reglas del lote

- Los archivos se procesan **en orden** y uno por uno; un fallo se muestra con su
  veredicto y el lote sigue.
- El duplicado se decide **por SHA-256**, dentro del lote y contra los hashes ya
  conocidos de la cátedra. Nunca por nombre.
- Un archivo que no se puede abrir se informa como `CORRUPT_OR_EMPTY` con mensaje
  humano; no se inventa un hash.
- Quitar un archivo del lote lo saca de la lista y de los documentos preparados; el
  archivo original no se toca.
- `P-05a` **no escribe en Room**. Los `PreIngestionOutcome` quedan en memoria, indexados
  por `uri`, y se entregan a `F5b` cuando exista la pantalla de revisión.

### 2.5 Presentación accesible del veredicto

Cada archivo muestra nombre, formato, veredicto en **texto y color**, páginas, densidad
de caracteres, tamaño, índice detectado desplegable y hallazgos forenses. El color
acompaña, nunca reemplaza al texto (WCAG 2.2). Los botones respetan
`fenixMinTouchTarget()`. "Continuar" permanece deshabilitado hasta que haya al menos un
documento legible, con una explicación visible en vez de un botón mudo.

El OCR local se crea en `persistent(context)` y se cierra en `onCleared()`.

## 3. Consecuencias

- **7 tests JVM nuevos** (`PreIngestionViewModelTest`): TXT legible, duplicado en el
  lote, hash ya conocido, formato no soportado que no aborta el lote, archivo vacío,
  archivo que no se puede abrir, quitar/vaciar.
- `feature:processor` suma `androidx.activity.compose` para el selector del sistema.
- El lote vive en el `ViewModel`: sobrevive a la rotación, pero **no a la muerte del
  proceso**. Es aceptable porque los archivos siguen en el dispositivo y se pueden
  volver a elegir; persistirlos exige copiarlos o permisos persistibles, que se
  evaluarán junto con la ingesta.
- El camino PDF y el OCR siguen **sin validar en hardware**: requieren el Redmi.
- El APK debug sigue en ~323 MB por PDFBox + ML Kit; no cambia por esta pantalla.

## 4. Alternativas descartadas

- **Un archivo por vez (`GetContent`):** obliga a repetir el selector 41 veces.
- **Copiar los archivos a la carpeta de la app:** duplica el almacenamiento y tarda en
  un teléfono de 4 GB; el streaming ya resuelve el OOM.
- **Filtrar por MIME en el selector:** deja afuera Markdown, código y formatos sin MIME
  declarado.
- **Guardar la propuesta en Room ahora:** migraría el esquema dos veces; la persistencia
  llega con la revisión de asignaciones en `F5b`.
- **Mostrar el veredicto solo con color:** excluye a personas con daltonismo y a
  lectores de pantalla.
