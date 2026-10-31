# ADR-0002 — Topología del Router Pool: 16 Proveedores Primarios a Costo $0.00 y Régimen BYOK

**ID:** ADR-0002  
**Fecha:** 2026-10-31  
**Estado:** APROBADO (Decisión Vinculante del Creador — Depuración Forense de 124 Proveedores)  
**Autoridad:** Eli Gian Cortez Leonardi (Creador)  
**Área:** Inteligencia Artificial y Red (:core:network-ai)  

---

## 1. Contexto y Conflicto
Existía una referencia histórica a "21 proveedores" en borradores iniciales. Tras la auditoría empírica forense exhaustiva de 124 proveedores (`REGISTRO_ESTADO_124_PROVEEDORES.md`), se descartaron 88 servicios por paywalls o tarjetas bancarias obligatorias, y se seleccionaron los **16 Proveedores Primarios con cuota diaria renovable a Costo Cero ($0.00 USD)** para el Master Dispatcher y los nodos de inferencia en la nube.

## 2. Los 16 Proveedores Primarios de Inferencia (Despacho Activo)
1. **Google Gemini** (Anillo 1 — Gran ventana de contexto y multimodalidad).
2. **Groq Cloud** (Anillo 2 — Ultra-baja latencia <500ms, Llama-3.3 70B, GPT-120B).
3. **SambaNova Systems** (Anillo 2 — DeepSeek-R1 671B y Llama-3.3 full precision).
4. **SiliconFlow** (Anillo 2 — Modelos $0 permanentes Qwen/DeepSeek).
5. **Cohere** (Anillo 2 — Command R+ y citaciones estructuradas).
6. **NVIDIA NIM** (Anillo 2 — Nemotron, Llama-3.3).
7. **OpenRouter** (Anillo 3 — Catálogo dinámico `:free`).
8. **FastRouter** (Anillo 3 — Auto-routing gratuito).
9. **UnoRouter** (Anillo 3 — Modelos abiertos gratuitos).
10. **RouteWay** (Anillo 3 — Modelos free).
11. **TokenHarbor** (Anillo 3 — Modelos free).
12. **Requesty** (Anillo 3 — Modelos gratuitos).
13. **Plugsky** (Anillo 3 — Modelos lite).
14. **Agnes AI** (Anillo 3 — Agnes-flash).
15. **Airforce** (Anillo 3 — Unified gateway).
16. **Baseten** (Anillo 3 — Modelos OSS).

*Proveedores Auxiliares y Especializados (Fuera del bucle principal de chat):*
- **Naga, Kilocode, Mancer:** Reserva secundaria / contingencia.
- **Jina AI y Voyage AI:** Especializados en extracción web limpia y embeddings vectoriales (Fases 6 y 7).
- **Ollama / Local:** Motores offline en hardware del usuario.

## 3. Decisión
1. **Despacho Primario en 16 Proveedores:** En primera instancia, el Master Dispatcher y los Inference Nodes en la nube operan dispersando la carga entre estos 16 proveedores estables para garantizar tiempos de respuesta < 5-10 minutos bajo concurrencia sin violar TOS.
2. **Régimen BYOK:** La aplicación móvil permite a cada usuario configurar sus propias claves de estos u otros proveedores mediante URLs preconfiguradas y botón "Probar Conexión".
3. **Desacoplamiento del MVP:** El MVP (Fase 5) opera 100% offline sin red; la asistencia conectada de estos 16 proveedores entra en juego en la Fase 9 como valor agregado opcional.

## 4. Impacto
- **Máxima Disponibilidad a Costo Cero:** Cero gasto para el usuario y el proyecto.
- **Robustez Forense:** Si un proveedor satura su cuota de minuto (429), el dispatcher conmuta en milisegundos a los 15 restantes del pool.
