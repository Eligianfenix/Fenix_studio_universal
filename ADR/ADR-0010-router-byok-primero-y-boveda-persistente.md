# ADR-0010 — Router interno BYOK-primero y bóveda persistente

**Fecha:** 2026-11-14
**Estado:** APROBADO (F3 implementado)
**Decisor:** Eli Gian Cortez Leonardi

## 1. Contexto

F2 dejó el catálogo y la pantalla de claves, pero el chat seguía respondiendo solo con el
pool keyless. Además, `FenixKeystoreVault` cifraba correctamente las claves pero las guardaba
en un `ConcurrentHashMap`: al cerrar la app se perdían, así que el botón "Guardar cifrada"
prometía algo que no ocurría.

El pool keyless todavía tenía dos rutas a infraestructura propia (Cloudflare Worker y Render)
que dejaron de ser necesarias al adoptar el modelo autovalente.

## 2. Decisión

### 2.1 Router `ByokTeacherChatEngine`

Implementa el puerto `TeacherChatEngine` con este orden:

1. Proveedores del catálogo que tengan al menos una clave cargada.
2. Ordenados por anillo (1 → 4); dentro de cada proveedor, sus ranuras `0..5`.
3. Si no hay claves o todas fallan, delega en el pool keyless.

Un proveedor solo entra si tiene `defaultModelId`: no se inventa un modelo que el proveedor
no publicó. Si todas las rutas BYOK fallan y el keyless también, se devuelve el fallo BYOK
más informativo (por ejemplo `RATE_LIMITED`) en lugar de un genérico "sin red".

La rotación entre turnos (`startEndpointIndex`) se conserva: permite repartir carga entre
claves y proveedores sin cambiar el estado de la UI.

### 2.2 Transporte propio, testeable

`ChatCompletionTransport` abstrae el HTTP. La implementación real (`JsonChatCompletionTransport`)
usa `HttpURLConnection` y el dialecto OpenAI, sin sumar motores al APK. Los tests inyectan un
transporte falso y verifican el orden de intentos sin tocar la red.

Errores tipados ya existentes: `NO_NETWORK`, `RATE_LIMITED`, `TIMEOUT`, `SERVER_ERROR`,
`EMPTY_RESPONSE`. Una clave rechazada (401/403) no detiene el router: pasa al siguiente
proveedor.

### 2.3 Bóveda persistente

`EncryptedSlotStore` separa el almacenamiento del cifrado:

- `InMemoryEncryptedSlotStore`: tests JVM y ejecuciones sin contexto.
- `SharedPreferencesEncryptedSlotStore`: producción. Guarda solo `providerId`, `slotIndex`,
  IV y ciphertext.

La llave AES-256 sigue en el `AndroidKeyStore`. Con el teléfono bloqueado o la app
desinstalada, los sobres guardados no se pueden abrir. `persistent(context)` y
`HomeChatViewModel.initializeStorage` comparten esta configuración, de modo que la pantalla
de claves y el chat ven la misma bóveda.

### 2.4 Proveedor visible en el chat

`TeacherChatEngine.lastRespondingProviderLabel` expone qué respondió. El mensaje del
asistente muestra `Modelo · Proveedor`; si hubo error, se deja la etiqueta base para no
atribuir el fallo a un proveedor que no respondió.

### 2.5 Se eliminan las rutas a infraestructura propia

De `FenixTeacherKeylessService` se quitaron:

- `fenix_dispatcher_edge` (Worker `fenix-router-edge`).
- `fenix_dispatcher_render` (Render `fenix-router-v3`).

Quedan Pollinations y OVHcloud. También se vaciaron los defaults
`defaultAssistedGatewayUrl` / `edgeGatewayBaseUrl` de `NetworkAiModels.kt`, que apuntaban a
`academic-edge-proxy.workers.dev` y ya no se usan: si en el futuro se quiere un gateway
asistido, se inyecta explícitamente.

## 3. Consecuencias

- Sin claves configuradas, la app funciona igual que antes (keyless).
- Con claves, el chat las usa primero y el usuario ve cuál respondió.
- Las claves sobreviven al cierre y al reinicio del teléfono.
- El APK ya no contiene ninguna URL de infraestructura propia para el chat.
- Al rotar entre proveedores se pierde la conversación multi-turno en el proveedor nuevo:
  el historial se reenvía completo en cada llamada, por lo que el contexto se mantiene.

## 4. Alternativas descartadas

- **Seguir con `ConcurrentHashMap`:** claves cifradas que desaparecen al cerrar; BYOK inútil.
- **Guardar las claves en texto plano:** viola el modelo de seguridad y el ADR-0004.
- **Keyless primero:** gastaría cuota comunitaria antes que la clave cargada a propósito.
- **Un adaptador por proveedor:** los 26 proveedores con clave hablan el dialecto OpenAI.
- **Paralelizar todas las rutas BYOK (hedged racing):** multiplicaría el consumo de cuotas
  gratuitas y dificultaría saber qué proveedor respondió. Se intenta en serie y se rota.
