# ADR-0009 — Catálogo interno de proveedores y orden BYOK-primero

**Fecha:** 2026-11-14
**Estado:** APROBADO (F2 implementado)
**Decisor:** Eli Gian Cortez Leonardi

## 1. Contexto

La app dejó de depender de la infraestructura propia (Supabase, Render, Cloudflare Worker)
en F1. Sin backend, el chat necesita hablar directamente con proveedores de IA. Había dos
problemas:

1. Las URLs y modelos estaban dispersos entre `FenixTeacherKeylessService`, documentos de
   investigación y pruebas manuales. Sin una fuente única, cualquier pantalla nueva inventa
   endpoints.
2. No existía forma de que la persona usuaria cargue sus propias claves (BYOK) y vea si
   funcionan, aunque `FenixKeystoreVault` ya soportaba 6 ranuras cifradas por proveedor.

## 2. Decisión

### 2.1 Catálogo único en código

`core/network-ai/.../catalog/AiProviderCatalog.kt` es la **única** fuente de verdad de
proveedores. Cada `AiProviderSpec` declara:

- `id` estable (se usa como `providerId` en la bóveda y en Room).
- `chatCompletionsUrl` **final**: no se concatena nada, porque hay proveedores fuera de `/v1`
  (Pollinations, Perplexity, Mancer).
- `modelsUrl` opcional para descubrimiento y para la sonda.
- `authStyle`: `NONE` (keyless) o `BEARER`.
- `ring`: anillo ético 1–4, más `KEYLESS` como anillo 0.
- `endpointVerified`: `false` cuando la URL proviene de un reporte comunitario y todavía no
  se confirmó con una llamada real. La UI lo advierte en vez de fingir que funciona.

Hoy el catálogo tiene 30 proveedores: 4 sin clave y 26 con clave. Los cuatro keyless
(Pollinations, LLM7, OVHcloud AI, PublicAI) son la red de contención que permite usar la app
al instalar, sin configurar nada.

### 2.2 Un solo cliente HTTP

26 de los 26 proveedores con clave hablan el dialecto OpenAI (`/v1/chat/completions`). No se
escribe un adaptador por proveedor: el router usa un cliente compatible OpenAI y el catálogo
solo aporta URL, modelo y estilo de auth. Cohere entra por su endpoint de compatibilidad.

### 2.3 Orden de enrutamiento: BYOK primero, keyless de respaldo

Decisión de producto: si la persona cargó claves, **sus claves se intentan primero** por
calidad y cuota previsible. Los keyless entran solo si fallan o si no hay claves cargadas.
Esto evita que el usuario configure una clave y nunca la vea usada, y mantiene la promesa de
que la app nunca queda muda.

### 2.4 Sonda sin gastar cuota

`ProviderConnectionProbe` implementa el botón **Probar conexión** con `GET /models`. Si el
proveedor no expone listado, hace una llamada mínima de chat con `max_tokens = 1`. Nunca
registra la clave en logs ni la incluye en el mensaje de error. Distingue:

- `OK`, `NO_KEY`, `NOT_CONFIGURED` (URL con `{account_id}` sin reemplazar),
- `UNAUTHORIZED` (401/403), `RATE_LIMITED` (429: la clave probablemente sirve),
- `NOT_FOUND` (404), `NO_NETWORK`, `HTTP_ERROR`.

### 2.5 Pantalla P-04 real

`ByokVaultScreen` separa **Sin clave · listo para usar** de **Con tu clave · N proveedores**.
Cada proveedor con clave se expande, permite elegir entre 6 ranuras, guardar cifrado, probar
y borrar. La instancia de producción (`SettingsAndByokVaultViewModel.persistent()`) usa
`AndroidHardwareKeyProvider`, de modo que las claves sobreviven al cierre de la app. Los tests
siguen usando `JvmMemoryMasterKeyProvider`.

## 3. Consecuencias

- Agregar un proveedor nuevo es una entrada en el catálogo, sin tocar la UI ni el router.
- Las URLs no verificadas quedan explícitas (`endpointVerified = false`) en lugar de
  mezclarse con las confirmadas.
- El catálogo **no contiene claves**: solo describe dónde y cómo llamar.
- Queda pendiente para F3 que el router consuma el catálogo y elimine las rutas a Render y al
  Worker edge de `FenixTeacherKeylessService`.

## 4. Alternativas descartadas

- **Lista estática por pantalla:** volvía a dispersar las URLs; ya había pasado.
- **Un adaptador por proveedor:** 26 adaptadores para 26 APIs compatibles con OpenAI.
- **Keyless primero:** gastaría cuota comunitaria antes que la clave que el usuario cargó
  a propósito, y dejaría la clave configurada sin uso visible.
- **Hardcodear claves de la máquina en la app:** viola el modelo BYOK y expone credenciales.
