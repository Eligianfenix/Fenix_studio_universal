# ADR-0002 — Router Pool de 21 Proveedores de IA y Régimen BYOK

**ID:** ADR-0002  
**Fecha:** 2026-10-31  
**Estado:** APROBADO (Decisión Vinculante del Creador — CONF-06 / DEC-02)  
**Autoridad:** Eli Gian Cortez Leonardi (Creador)  
**Área:** Inteligencia Artificial y Red (:core:network-ai)  

---

## 1. Contexto y Conflicto
Existía una propuesta en borradores preliminares de crear cuentas masivas simuladas para burlar cuotas de Cloudflare/proveedores, lo cual entraba en colisión directa con la Regla Ética RN-25 y con los Términos de Servicio de los proveedores.

## 2. Opciones Consideradas
1. **Opción A:** Esquemas de pooling masivo no documentado (Rechazada por riesgo de baneo y violación de TOS).
2. **Opción B (Adoptada):** Implementar un **Router Pool transparente de 21 proveedores de IA** con conmutación dinámica ética, sumado al esquema BYOK (Bring Your Own Key) para el usuario.

## 3. Decisión
1. **Router Pool de 21 Proveedores:** Se opera un router ético que prioriza los modelos más avanzados (Anillo 1 y Anillo 2) para el procesamiento académico pesado, dejando modelos más ligeros para el chat cotidiano.
2. **Prohibición de Hardcodeo:** No se hardcodean nombres o versiones de modelos en el código fuente. Se gestionan de forma dinámica o mediante el panel administrativo de la app admin.
3. **Esquema BYOK:** Se preconfiguran las URLs de los proveedores admitidos para que el usuario pueda ingresar su propia clave API (1 por proveedor) con botón "Probar Conexión" sin gasto de cuota innecesario.
4. **Independencia del MVP:** La asistencia de IA es opcional y conectada (Fase 9); el MVP (Fase 5) opera 100% offline sin requerir IA.

## 4. Impacto
- **Cumplimiento Ético:** Cero infracciones de TOS.
- **Resiliencia:** Si un proveedor satura o cae, el router conmuta automáticamente al siguiente sin interrumpir la experiencia de estudio.
