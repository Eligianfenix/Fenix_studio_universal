# ADR-0007 — Protocolo UI Shell First y Pruebas en Dispositivo Real

**ID:** ADR-0007  
**Fecha:** 2026-10-31  
**Estado:** APROBADO (Decisión Vinculante del Creador — CONF-14 / Regla UI Shell First)  
**Autoridad:** Eli Gian Cortez Leonardi (Creador)  
**Área:** Arquitectura de Interfaz, Frontend y Aseguramiento de Calidad  

---

## 1. Contexto y Conflicto
Existía riesgo de que agentes de IA implementaran lógica de negocio o integraciones complejas asociadas a pantallas sin que el creador hubiera validado previamente el diseño visual, las áreas táctiles, la paleta de colores y la navegación de la interfaz.

## 2. Opciones Consideradas
1. **Opción A:** Desarrollar frontend y backend simultáneamente en un único paso monolítico (Rechazada por generar retrabajos y divergencias con las especificaciones de diseño).
2. **Opción B (Adoptada):** **UI Shell First innegociable** — Toda fase con interfaz debe entregar primero una carcasa visual funcional aprobada antes de escribir lógica.

## 3. Decisión
1. **Regla de Carcasa Primero (UI Shell First):**
   - Toda fase que genere o altere pantallas debe compilar y entregar previamente un APK con la carcasa de interfaz (UI shell).
   - El Creador debe probar y aprobar explícitamente el APK de UI Shell (evidencia E3) antes de habilitar la escritura de casos de uso o lógica de negocio.
2. **Pruebas en Dispositivo Real:**
   - La validación final no se conforma con pruebas en emuladores o JVM; debe ser verificada en los dispositivos de referencia: **Redmi 14C** (y Moto G77).
   - Para trabajo remoto, se proveen enlaces de descarga directa de cada APK para instalación y sign-off del creador.

## 4. Impacto
- **Control Total del Producto:** El Creador valida visualmente cada pantalla antes de invertir esfuerzo en el motor subyacente.
- **Accesibilidad Garantizada:** Se verifica TalkBack, contraste de colores (7:1) y touch targets mínimos de 48dp en hardware real.

