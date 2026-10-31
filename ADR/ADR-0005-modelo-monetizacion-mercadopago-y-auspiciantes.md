# ADR-0005 — Modelo de Monetización Out-of-App con Mercado Pago y Auspiciantes

**ID:** ADR-0005  
**Fecha:** 2026-10-31  
**Estado:** APROBADO (Decisión Vinculante del Creador — A4 / DEC-12)  
**Autoridad:** Eli Gian Cortez Leonardi (Creador)  
**Área:** Monetización y Facturación (:app/billing, :core:network-ai)  

---

## 1. Contexto y Conflicto
Existía debate entre integrar pasarelas in-app sujetas a comisiones y requerimientos de Google Play Console (violando la Regla de Costo Cero para el proyecto) vs un esquema soberano out-of-app.

## 2. Opciones Consideradas
1. **Opción A:** Google Play Billing (Rechazada por costo anual de Play Console y 30% de comisiones obligatorias).
2. **Opción B (Adoptada):** **API de Mercado Pago externa + Red de Auspiciantes Directa**, con distribución libre mediante GitHub Releases.

## 3. Decisión
1. **Adquisición de Créditos:**
   - Se gestiona out-of-app mediante la **API de Mercado Pago**, generando enlaces de pago directos y validación webhook automatizada.
   - Cero cobros directos de tarjeta dentro del APK (cumplimiento PCI-DSS por delegación segura a Mercado Pago).
2. **Red Ética de Auspiciantes (Diamante, Plata, Bronce):**
   - El creador gestiona auspiciantes mediante diálogo directo y los habilita desde la App Admin con 3 niveles ponderados de visibilidad:
     - **Diamante:** Máxima rotación de aparición.
     - **Plata:** Un tercio menos de probabilidad de impresión respecto a Diamante.
     - **Bronce:** Mínima frecuencia.
   - **Formato No Invasivo:** Los avisos se presentan exclusivamente como pequeñas tarjetas informativas al finalizar una respuesta de chat de IA o al terminar el procesamiento de una cátedra.

## 4. Impacto
- **Costo Cero Absoluto:** Independencia total de Google Play Console o tarifas de desarrollador.
- **Experiencia de Estudio Ininterrumpida:** Cero banners molestos, videos bloqueantes o popups intrusivos.
