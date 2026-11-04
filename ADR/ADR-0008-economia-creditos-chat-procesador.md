# ADR-0008 — Economía de Créditos Chat/Procesador, Freemium Diferido y Biblioteca Compartida

**ID:** ADR-0008  
**Fecha:** 2026-11-03  
**Estado:** APROBADO (Decisión Vinculante del Creador — D-002 a D-007)  
**Autoridad:** Eli Gian Cortez Leonardi (Creador)  
**Área:** Monetización, Cuotas y Biblioteca (:core:security-vault, :feature:processor, :feature:home-chat, Edge)  

---

## 1. Contexto y Conflicto

El chat P-02 y el procesador P-05/P-06 consumen APIs de IA, pero no existía una unidad de cuenta única ni una política de cobro que distinguiera chat de procesamiento documental. El modelo previo (`QuotaAndCreditEnforcer`, `FenixToolId.INITIAL_CHAT = 50/día`) era un contador único, en memoria, con `Int`, reset en UTC y sin bolsillos separados. Además, el cobro por procesamiento no tenía unidad definida: no se sabía si se cobraba por cátedra, por unidad o por punto.

Se midieron dos programas reales de la UNSJ para fijar la unidad:

| Programa | Divisiones globales | Puntos/subtemas | Puntos por división |
|---|---|---|---|
| Investigación Social I (2026) | 6 | 26 | 4, 6, 5, 4, 4, 3 |
| Economía Política (2026) | 9 | ~37 | 6, 5, 5, 3, 3, 3, 3, 5, 4 |

**Conclusión:** la división global (unidad/tema/bolilla) contiene entre 3 y 9 puntos. Es la unidad de cobro correcta; el punto/subtema es demasiado fino y la cátedra demasiado gruesa.

## 2. Opciones Consideradas

1. **Unidad de cuenta:** (a) créditos enteros con redondeo; (b) **centicréditos enteros (`Long`)**. Se adopta (b): 1 mensaje = 5 cc y 1 CP = 400 cc son exactos, sin `Float`.
2. **Precio:** (a) $500 = 1 crédito de chat (→ cátedra de 9 divisiones = $18.000); (b) **$500 = 1 crédito de procesador** (→ cátedra de 9 divisiones = $4.500). Se adopta (b) por viabilidad de mercado.
3. **Bono por publicar:** (a) comisión perpetua del 20% por venta; (b) **bono único del 20% al aprobarse la publicación**. Se adopta (b): (a) canibaliza el ingreso y premia la reventa; (b) premia una vez y no genera pasivo permanente.
4. **Publicación en biblioteca:** (a) opcional siempre; (b) **obligatoria para procesamientos 100% gratuitos, voluntaria para pagos**. Se adopta (b): el cupo gratuito se paga con contenido, no con dinero.
5. **Descuento de biblioteca:** 50%, 30%, 25% o **20%**. Se adopta 20% porque iguala el neto del primer comprador (ver §8).

## 3. Decisión

Se adoptan **nueve decisiones firmes**:

1. **Unidad de cuenta en centicréditos (cc), `Long`.** Nunca `Float`/`Double` para saldo.
2. **Precio:** ARS $500 por crédito de procesador (CP). Equivalencias en §4.
3. **Otorgamientos por perfil** con bolsillos separados (§5).
4. **Consumo: gratuitos primero, comprados después** (§6).
5. **Freemium de dos carriles:** el cupo gratuito procesa en cola diferida; el pago compra prioridad y privacidad, no acceso (§7).
6. **Biblioteca compartida al 80% del precio** (descuento 20%) (§8).
7. **Publicación obligatoria** para procesamientos 100% gratuitos, siempre con **auto-envío a revisión y nunca auto-publicación** (§9).
8. **Bono de publicación único del 20%**, solo para cátedras procesadas con créditos comprados (§10).
9. **Resets en hora argentina:** chat 00:00 ART; procesador lunes 01:30 ART (§11).

## 4. Unidad de Cuenta y Precio

| Concepto | cc | Equivalencia | ARS |
|---|---|---|---|
| 1 mensaje de chat | **5 cc** | 0,05 CC | **$6,25** |
| 1 crédito de chat (CC) | **100 cc** | 20 mensajes | **$125** |
| 1 crédito de procesador (CP) | **400 cc** | 4 CC · 80 mensajes · **1 división global** | **$500** |

- `1 cc = $1,25`. La conversión es contable, no operativa: **no existe botón de convertir CC → CP**.
- Los gratuitos **no se convierten** jamás. Solo los comprados son universales.
- Cátedra completa: `divisiones × 400 cc`. Ejemplos: 6 divisiones = 2.400 cc ($3.000); 9 divisiones = 3.600 cc ($4.500).

## 5. Otorgamientos

| Perfil | Chat | Procesador | Acumulable |
|---|---|---|---|
| **Común** | 1 CC/día = 20 mensajes (100 cc) | 2 CP/semana = 2 divisiones (800 cc) | No |
| **Beta — semana 1** | 3 CC/día = 60 mensajes (300 cc) | **4 cátedras gratis** (tope 10 divisiones c/u) | No |
| **Beta — desde semana 2** | 2 CC/día = 40 mensajes (200 cc) | 4 CP/semana = 4 divisiones (1.600 cc) | No |
| **Bienvenida (una vez)** | — | 1.ª cátedra, **tope 10 divisiones** | No |
| **Comprados** | Universales | Universales | **Sí, no vencen** |

Reglas:
- Los gratuitos **no se acumulan**: lo no usado se pierde en el reset.
- Los comprados **sí se acumulan**: 1 CP + compra de 5 CP = **6 CP**.
- El grant beta de 4 cátedras es **único y válido 7 días** desde la activación del rol; lo no usado vence al día 8.
- Cada cátedra del grant beta tiene el **mismo tope de 10 divisiones**. El excedente se cobra normal.
- Los grants de bienvenida y beta son de **débito 0**: consumen del bolsillo del grant, no del saldo.
- Cobertura verificada: 2 CP/semana × 36 semanas = **72 divisiones/año**, suficiente para 8 cátedras de 9 divisiones o 12 de 6. Un año estándar (8–12 cátedras) entra completo si el alumno empieza temprano.

## 6. Orden de Consumo e Idempotencia

**Chat:** `chat_free` del día → `purchased` → bloquear.
**Procesador:** `grant` (bienvenida/beta, si aplica) → `proc_free` del período → `purchased` → bloquear.

- **Regla de oro:** los gratuitos están **restringidos a su bolsillo**. El chat gratis no paga procesamiento y viceversa.
- Los comprados son universales: 1 CP comprado paga 1 división **o** 80 mensajes.
- **Idempotencia obligatoria:** el débito de procesamiento usa la clave `(user_id, catedra_id, unit_id, intent_id)`. Reintentar o reanudar un checkpoint **no cobra dos veces**.
- El débito del chat usa `(user_id, message_id)`.

Ejemplo normativo: 2 CP gratis + compra de 5 CP, se procesan 4 divisiones → se consumen 2 gratis + 2 comprados → **quedan 3 comprados**.

## 7. Freemium de Dos Carriles

> El cupo gratuito **procesa a velocidad normal en cola diferida**. El pago compra **prioridad y privacidad**, no acceso.

| | Gratis | Comprado |
|---|---|---|
| Inicio | Cola diferida (mejor esfuerzo, 24–72 h) | Entra ya, sin esperar turno nocturno |
| Prioridad WFQ | peso 1× | peso 4× |
| Garantía | Progresa siempre (piso de capacidad reservada) | Adelanta, no se hunde |
| Privacidad | Pública (obligatoria) | Privada si no publica |

- **No se promete inmediatez.** Render Free (0,1 CPU, 512 MB, spin-down a los 15 min) no puede entregarla. Una división son 3–9 puntos × 6 secciones del protocolo = **18–54 llamadas de LLM**; tarda minutos a decenas de minutos aun con prioridad.
- La cola gratuita corre **por división, con checkpoint** (`ProcessingCheckpointEntity`), para entrar en la ventana de 100 min del proxy y sobrevivir al cierre de la app.
- La prioridad se implementa con un `tier_weight` en `FairShareScheduler.acquire_turn` (~10 líneas) **más un piso de capacidad reservada para gratis** (≥30% de los slots), para que la prioridad paga nunca deje sin turno a los gratuitos.

## 8. Biblioteca Compartida

**Precio:** `80% del precio completo` (descuento del 20%).

Justificación algebraica: con bono `b`, el neto del primer comprador es `P × (1 − b)`. La biblioteca iguala ese neto cuando `d = b`. Con `b = 20%`, el descuento justo es **exactamente 20%**.

| Cátedra | Procesar | Neto del 1.º (con bono) | Biblioteca (20% off) |
|---|---|---|---|
| 6 divisiones | $3.000 | $2.400 | **$2.400** |
| 9 divisiones | $4.500 | $3.600 | **$3.600** |

- **Por debajo de 20%:** la biblioteca cuesta más que el neto del primero y encima se espera → nadie la compra.
- **Por encima de 20%:** el primero subsidia al segundo. El techo defendible es 25%, justificado por la espera cero.
- La biblioteca se entrega **instantánea** (ya está procesada) mediante token efímero de descarga.

## 9. Publicación y Moderación

**Regla:** procesamiento 100% gratuito (cupo, bienvenida o beta) → **publicación obligatoria**; procesamiento con créditos comprados → **publicación voluntaria**.

**Distinción no negociable:** *auto-envío a revisión*, **nunca auto-publicación**. Todo contenido permanece en cuarentena privada hasta la aprobación del admin.

**Consentimiento sin modal por cátedra:** la elección ocurre **antes de procesar**, en la pantalla del procesador:

- `[ Gratis — se suma a la biblioteca tras revisión ]`
- `[ Con créditos — privado ]`

Así se preserva el Principio de Soberanía del Usuario (RN-01) sin interrogar al alumno en cada cátedra.

**Stack mínimo de moderación (costo cero):**

1. **Cuarentena real:** lo no aprobado vive en tabla/bucket privado con RLS. Nunca accesible por URL.
2. **Pre-filtro automático:** blocklist de términos + clasificación con el pool existente (Workers AI `llama-3.3-70b`). Riesgo alto → rechazo automático, no llega al admin. *No existe modelo dedicado de moderación en el catálogo: es barrera, no garantía.*
3. **Revisión humana del admin** como puerta final.
4. **Auditoría:** `submitted_by`, fecha, hash de dispositivo, `approved_by`. Defensa legal y trazabilidad del infractor.
5. **Denuncia + baja inmediata** en la biblioteca.
6. **ToS explícitos:** prohibición de contenido ilegal y rol de intermediario con revisión.

**Mitigación estructural:** la biblioteca publica **solo los JSON del protocolo (Partes A–F), nunca los PDFs ni escaneos del usuario**. Las imágenes ilegales no pueden viajar en el paquete. Reduce el vector sin costo.

## 10. Bono de Publicación

**Bono = 20% de los cc efectivamente debitados al procesar la cátedra, una sola vez, acreditado al aprobar el admin la publicación.**

| Cátedra | Débito | Bono | Neto |
|---|---|---|---|
| 6 divisiones | 6 CP ($3.000) | 1,2 CP = 480 cc ($600) | $2.400 |
| 9 divisiones | 9 CP ($4.500) | 1,8 CP = 720 cc ($900) | $3.600 |

Reglas:
- **Solo aplica a cátedras procesadas con créditos comprados.** Procesar con cupo gratuito, bienvenida o grant beta **no genera bono** (el débito es 0 o es el costo de usar gratis).
- Se acredita al bolsillo **`purchased`** (universal, no vence). Es costo de adquisición de contenido de la plataforma, no conversión de gratuitos.
- **Idempotencia:** clave `(user_id, catedra_id, "bounty")`. Despublicar y republicar no paga dos veces.
- **No se ofrece** si ya existe una cátedra **completa** en biblioteca con el mismo `sim_hash_64` + `programa_year`.
- **No se ofrece** si faltan divisiones.
- **Tope:** máximo **4 publicaciones aprobadas por mes** por usuario (parámetro gobernable desde Fenix Admin).
- El fraude no conviene por construcción: para cobrar 20% hay que gastar 100%.

## 11. Resets

| Cuota | Reset |
|---|---|
| Chat diario | **00:00 ART** (UTC−3) |
| Procesador semanal (común) | **Lunes 01:30 ART** |
| Beta diario/semanal | Mismos cortes que el perfil que corresponda |
| Comprados | **No vencen** |

> Nota [VERIFY]: el reset de cuotas gratuitas de Gemini ocurre a medianoche del Pacífico (04:00–05:00 ART). Si el objetivo es alinearse con ese proveedor, el valor no es 01:30 ART. Se mantiene 01:30 ART por decisión de producto del creador.

## 12. Anti-Reventa

1. **Créditos atados a la cuenta.** No existe mecanismo de transferencia entre usuarios.
2. **Gratuitos no convertibles** a comprados ni transferibles.
3. **Revocabilidad:** el rol beta es revocable en cualquier momento desde Fenix Admin (ADR-0003).
4. **Auditoría de publicaciones:** quién procesó, quién publicó, quién aprobó.
5. **Trazabilidad del paquete:** el export incluye metadatos del procesamiento; un paquete revendido es rastreable a la cuenta de origen.

La comisión perpetua por reventa queda **descartada**. El bono único de §10 es el único incentivo a publicar.

## 13. Ganchos Técnicos

**Ya existe (reusar):**

| Elemento | Ubicación | Estado |
|---|---|---|
| RPC `canjear_descarga_biblioteca` | `SupabaseAuthAndDataGateway.kt:166` | Existe; falta lógica de precio 80% |
| `RedeemDownloadResult.credits_remaining` | `SupabaseAuthAndDataGateway.kt:72` | Existe |
| `UsuarioPerfilRemote.processor_usage_weekly` | `SupabaseAuthAndDataGateway.kt:52` | Existe |
| `ClassroomEntity.simHash64` (columna + índice) | `ClassroomEntity.kt:56-57` | **Declarado pero nunca calculado** (siempre `0L`) |
| Export JSON sin PDFs (100–250 KB) | Protocolo v3.0 | Existe |
| `SyllabusUnitEntity` / `SyllabusPointEntity` | `unidades_programa` / `puntos_programa` | Es la unidad de cobro |
| `ProcessingCheckpointEntity` | `core:database` | Existe |

**Falta (P1.5):**

1. `CurricularSyllabusParser`: reconocer `UNIDAD N ° 1` (hoy devuelve **0 unidades** en Economía Política) y **conectarlo al flujo de producción** (hoy solo lo usa un test).
2. `credits: Int` → `Long` en cc (migración Room + Supabase).
3. `SignedQuotaLedgerEntry`: agregar `bucket` (`chat_free` / `proc_free` / `grant` / `purchased`) y `amountCc: Long`.
4. Persistir el ledger (hoy `ConcurrentHashMap`, se resetea al reiniciar).
5. `RemoteToolConfigRule`: `freeChatCreditsPerDay`, `freeProcessorCreditsPerWeek`, `messageCostCc`, `processorUnitCostCc`, `welcomeCapDivisions`, `libraryDiscountPct`, `publicationBountyPct`, `publicationsPerMonthCap`.
6. Reset a 00:00 ART / lunes 01:30 ART (`QuotaAndCreditEnforcer.kt:471` usa `ZoneOffset.UTC`; `:211` hardcodea `"00:00 UTC"`).
7. Enganchar `evaluateAndConsumeQuota(INITIAL_CHAT)` en `HomeChatViewModel.onSubmitPrompt`.
8. Calcular `simHash64` en la pre-ingesta.
9. `publication_id` + `moderation_status` (`PENDING_REVIEW` / `APPROVED` / `REJECTED`).
10. `tier_weight` + piso de capacidad en `FairShareScheduler.acquire_turn`.
11. RPC `admin_aprobar_publicacion()` atómica + tipo de asiento `PUBLICATION_BOUNTY`.

## 14. Criterios de Aceptación

1. Común: 20 mensajes; el 21.º se bloquea; al día siguiente vuelve a 20, **no 40**.
2. Común: procesa 2 divisiones gratis; la 3.ª se bloquea; a la semana vuelve a 2, **no 4**.
3. Compra: 1 CP + 5 CP = **6 CP** (2.400 cc).
4. Beta semana 1: 60 mensajes/día + 4 cátedras; día 8 cae a 40 mensajes/día + 4 CP/semana.
5. Bienvenida: primera cátedra gratis hasta 10 divisiones; la división 11 se cobra.
6. Bono: cátedra de 9 divisiones pagada → 720 cc al aprobarse; repetir la publicación **no** paga de nuevo.
7. Cátedra gratuita: **no** genera bono.
8. Biblioteca: cátedra de 9 divisiones = **2.880 cc ($3.600)** (80% de 3.600 cc), instantánea.
9. Duplicado: si ya existe completa en biblioteca, no se ofrece publicar ni bono.
10. Reinstalar: los comprados persisten; los gratuitos del día se recalculan.
11. Idempotencia: reintentar el mismo mensaje o reanudar un checkpoint no cobra dos veces.
12. Edge: 4.º mensaje en <1 min con ráfaga 3 → `429` con `Retry-After`; ningún proveedor supera su RPM.
13. Admin cambia 20 → 5 mensajes/día y el efecto se ve en <60 s.
14. Moderación: una cátedra con contenido prohibido se rechaza antes de ser visible.

## 15. Consecuencias y Riesgos

- **La biblioteca dominará el procesamiento pagado.** A igual neto y sin espera, conviene descargar. El procesamiento pago queda como actividad de *siembra* y como opción de **privacidad**. Es coherente con la política de que los ingresos principales provengan de anuncios y no de créditos.
- **La biblioteca es de margen casi total:** el procesamiento gratuito usa proveedores a costo cero; cada venta de biblioteca es ingreso sobre un costo marginal nulo. El cupo gratuito no es un centro de costo, es la **fábrica de contenido**.
- **Moderación:** el riesgo legal (contenido de abuso, Código Penal arg. art. 128) se controla con cuarentena + pre-filtro + admin + auditoría. La revisión humana sola no alcanza si hay publicación previa.
- **Migración `Int` → `Long`:** obligatoria antes de conectar el ledger; el esquema actual no soporta cc sin desborde conceptual.
- **Render Free:** aceptable para la cola diferida, incompatible con promesas de inmediatez.
- **Dependencia de proveedores gratuitos:** el piso de capacidad y el presupuesto global por proveedor (documentado en D-001) son obligatorios para que el freemium no reviente las APIs.

---

**Documentos relacionados:** ADR-0002 (router pool), ADR-0003 (beta), ADR-0005 (monetización), ADR-0006 (Supabase autoritativo).  
**Reglas actualizadas:** RN-11 (reset ART + economía cc), RN-12 (contadores edge), RN-25 (enlace).
