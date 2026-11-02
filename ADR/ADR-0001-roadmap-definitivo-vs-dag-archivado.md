# ADR-0001 — Adopción del Roadmap Definitivo Axiom y Anulación del DAG Antiguo

**ID:** ADR-0001  
**Fecha:** 2026-10-31  
**Estado:** APROBADO (Decisión Vinculante del Creador)  
**Autoridad:** Eli Gian Cortez Leonardi (Creador)  
**Área:** Gobernanza y Planificación  

---

## 1. Contexto y Conflicto
Existía una contradicción entre los planes de ingeniería preliminares (el DAG histórico F0–F9 con 32 microfases incompletas o no migradas) y la nueva arquitectura modular blindada de Fénix Studio.

## 2. Opciones Consideradas
1. **Opción A:** Mantener el DAG antiguo e intentar parchar los módulos faltantes.
2. **Opción B (Adoptada):** Adoptar formalmente el **Roadmap Definitivo Axiom (F0 a F12)** con sus 13 Gates de calidad innegociables, declarando el DAG antiguo como material archivado de solo consulta histórica sin fuerza prescriptiva.

## 3. Decisión
Se adopta el **Roadmap Definitivo Axiom** como la única fuente de verdad para la construcción de Fénix Studio:
- **Bloque A:** Fase 0 (Blindaje Forense y Línea Base).
- **Bloque B:** Fases 1 a 5 (Hacia el MVP Offline y Accesible en Gate 5).
- **Bloque C:** Fases 6 a 12 (Implementación Completa Post-MVP).
Queda prohibido implementar código basándose en documentos archivados.

## 4. Impacto
- **Trazabilidad:** Máquina de estados determinista (DESCUBIERTO → CERRADO) por microtarea.
- **Calidad:** Cero saltos de fase sin evidencia auditable y sin superación del Gate correspondiente.

