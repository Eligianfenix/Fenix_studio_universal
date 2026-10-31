# ADR-0006 — Almacenamiento de Documentos en Google Drive y Saldo Oficial en Supabase

**ID:** ADR-0006  
**Fecha:** 2026-10-31  
**Estado:** APROBADO (Decisión Vinculante del Creador — A3 / CONF-10 / Prohibición Global)  
**Autoridad:** Eli Gian Cortez Leonardi (Creador)  
**Área:** Almacenamiento, Persistencia e Infraestructura Nube  

---

## 1. Contexto y Conflicto
Existía una contradicción sobre dónde deben almacenarse los documentos académicos de gran volumen (PDFs, DOCX, paquetes `.fenix`) y cómo garantizar que el saldo de créditos de usuario sea inviolable.

## 2. Opciones Consideradas
1. **Opción A:** Almacenar documentos pesados en Supabase Storage o Cloudflare R2 (Rechazada por riesgo de costos futuros y requerimientos de tarjeta de crédito).
2. **Opción B (Adoptada):** **Prohibición estricta de documentos en Supabase/Cloudflare**. Documentos pesados derivados a **Google Drive dedicado**; Supabase limitado exclusivamente a base de datos de control, créditos y autenticación.

## 3. Decisión
1. **Prohibición Global de Documentos en Supabase/Cloudflare:**
   - Queda terminantemente prohibido almacenar archivos de usuario o materiales de estudio pesados en Supabase Storage o Cloudflare R2.
   - El almacenamiento de archivos comunitarios y paquetes `.fenix` se delega a una cuenta dedicada de **Google Drive**.
2. **Saldo Oficial de Créditos en Supabase:**
   - El saldo oficial de créditos reside de forma autoritativa en la base de datos de Supabase.
   - El APK móvil mantiene una copia de trabajo local offline para agilidad inmediata; dicho contador local no puede ser manipulado ni reiniciado mediante reinstalación de la app.
3. **Segregación en Supabase:**
   - Dos entornos o esquemas separados: uno para usuarios/créditos y otro para telemetría anonimizada y auspiciantes.

## 4. Impacto
- **Garantía Costo 0:** Google Drive brinda 15 GB gratuitos por cuenta dedicada sin costo oculto ni cobros automáticos imprevistos.
- **Inviolabilidad de Créditos:** El cómputo autoritativo en Supabase previene trampas o duplicaciones locales.
