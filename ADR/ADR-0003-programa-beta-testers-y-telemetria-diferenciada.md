# ADR-0003 — Programa de Beta Testers y Telemetría Diferenciada a las 3 AM

**ID:** ADR-0003  
**Fecha:** 2026-10-31  
**Estado:** APROBADO (Decisión Vinculante del Creador — CONF-03 / A2 / DEC-03)  
**Autoridad:** Eli Gian Cortez Leonardi (Creador)  
**Área:** Telemetría y Comunidad (:core:security-vault, :feature:community)  

---

## 1. Contexto y Conflicto
El rol y los privilegios de los beta testers aparecían en bitácoras y pruebas pero carecían de especificación formal y modelo de amenazas. Además, se debatía la frecuencia de telemetría de errores (tiempo real vs por lotes).

## 2. Opciones Consideradas
1. **Opción A:** Telemetría en tiempo real continua (Rechazada por consumo innecesario de batería, red móvil y tokens).
2. **Opción B (Adoptada):** Telemetría por lotes consolidada **una vez al día a las 3:00 AM (hora Argentina)** con segregación estricta entre usuarios estándar y beta testers voluntarios.

## 3. Decisión
1. **Programa Beta Tester:**
   - Convocados directamente por el creador mediante formulario web en la página de descarga del APK.
   - Acceden a cuotas ampliadas de servicio durante su período activo.
   - El estado de beta tester es revocable en cualquier momento desde la App Admin ante incumplimiento de feedback.
2. **Telemetría Diferenciada:**
   - **Usuarios estándar:** Envían únicamente la telemetría mínima requerida por normativas de protección de datos personales.
   - **Beta testers:** Consienten de forma anónima la recopilación de telemetría técnica completa para diagnóstico forense.
3. **Ventana de Envío (3:00 AM ART):**
   - Todos los reportes consolidados se transmiten en una única ventana de bajo impacto a las 3:00 AM horario local argentino, evitando interrupciones durante el horario de estudio diurno.

## 4. Impacto
- **Batería y Rendimiento:** Consumo casi nulo de batería durante el uso activo de la aplicación.
- **Protección de Datos:** Anonimización total previa a la transmisión.
