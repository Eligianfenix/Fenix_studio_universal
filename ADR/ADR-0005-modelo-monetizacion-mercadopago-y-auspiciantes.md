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
2. **Opción B (Adoptada):** **Transferencia Directa por Alias Oficial + Red de Auspiciantes Directa**, con distribución libre mediante GitHub Releases.

## 3. Decisión
1. **Adquisición de Créditos:**
   - Se gestiona out-of-app mediante **Transferencia Bancaria Directa al Alias oficial (`fenix.academico`)** con 0% comisiones y 0 retenciones impositivas de pasarela.
   - El estudiante adjunta su comprobante y la aplicación ejecuta escaneo OCR local en el dispositivo a Costo $0 (Google ML Kit en Android / Tesseract.js en Web), extrayendo automáticamente el titular que transfirió y el número de operación.
   - El creador valida el pago en 2 segundos desde `Fenix Admin` y lo aprueba con 1 solo tap mediante la función RPC `admin_aprobar_pago()`, acreditando los créditos en tiempo real.
   - La interfaz advierte formalmente: *"La acreditación está sujeta a comprobación y puede demorar hasta 24 hs."*
2. **Red Ética de Auspiciantes (Diamante, Plata, Bronce):**
   - El creador gestiona auspiciantes mediante diálogo directo y los habilita desde la App Admin con 3 niveles ponderados de visibilidad:
     - **Diamante:** Máxima rotación de aparición.
     - **Plata:** Un tercio menos de probabilidad de impresión respecto a Diamante.
     - **Bronce:** Mínima frecuencia.
   - **Formato No Invasivo:** Los avisos se presentan exclusivamente como pequeñas tarjetas informativas al finalizar una respuesta de chat de IA o al terminar el procesamiento de una cátedra.

## 4. Impacto
- **Costo Cero Absoluto:** Independencia total de Google Play Console o tarifas de desarrollador.
- **Experiencia de Estudio Ininterrumpida:** Cero banners molestos, videos bloqueantes o popups intrusivos.

