# ADR-0004 — Protocolo OPSEC, Seguridad y Ciclo de Visibilidad del Repositorio

**ID:** ADR-0004  
**Fecha:** 2026-10-31  
**Estado:** APROBADO (Decisión Vinculante del Creador — CONF-13 / A7 / DEC-04)  
**Autoridad:** Eli Gian Cortez Leonardi (Creador)  
**Área:** Seguridad Integral, OPSEC y Gobernanza  

---

## 1. Contexto y Conflicto
Existía riesgo de filtración de documentos estratégicos, propiedad intelectual, esquemas de costos y credenciales privadas hacia repositorios públicos de control de versiones.

## 2. Opciones Consideradas
1. **Opción A:** Repositorio privado permanente o público sin sanitización (Inviable por riesgo de filtración accidental o barreras para auditoría abierta).
2. **Opción B (Adoptada):** **Público durante el desarrollo activo con sanitización estricta por `.gitignore`**, pasando a privado en etapa de madurez previa a lanzamiento comercial.

## 3. Decisión
1. **Sanitización Rigurosa `.gitignore`:**
   - La carpeta de gobernanza (`axiom plan fenix studio/`), prompts internos, handovers, cátedras de prueba y documentos de diseño quedan excluidos de forma permanente del seguimiento de Git.
   - Cero secretos, tokens, contraseñas o datos de conexión en el árbol rastreado.
2. **Ciclo de Vida de Visibilidad:**
   - Repositorio oficial en GitHub (`Eligianfenix/Fenix_studio_universal`) configurado en modo **PÚBLICO** durante el proceso de construcción para máxima auditabilidad técnica.
   - Transición a modo **PRIVADO** programada para las etapas de madurez previa a producción (Gate 11/12).

## 4. Impacto
- **Blindaje Legal e Intelectual:** La propiedad intelectual del Creador permanece resguardada en almacenamiento desacoplado fuera de Git.
- **Transparencia Técnica:** El código del framework y la arquitectura multi-módulo pueden ser auditados públicamente sin riesgos de fuga.
