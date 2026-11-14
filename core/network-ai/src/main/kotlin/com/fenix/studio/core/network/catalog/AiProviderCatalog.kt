/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-14 19:30:00 -03:00
 * ARCHIVO: core/network-ai/src/main/kotlin/com/fenix/studio/core/network/catalog/AiProviderCatalog.kt
 * ================================================================================
 */

package com.fenix.studio.core.network.catalog

/**
 * Anillo ético del router. El anillo 1 es el de mayor cuota/calidad; los keyless
 * son la red de contención que funciona sin configurar nada.
 */
enum class ProviderRing(val label: String, val order: Int) {
    KEYLESS("Sin clave", 0),
    RING_1("Anillo 1", 1),
    RING_2("Anillo 2", 2),
    RING_3("Anillo 3", 3),
    RING_4("Anillo 4", 4),
}

/** Forma de autenticación contra el proveedor. */
enum class ProviderAuthStyle {
    NONE,
    BEARER,
}

/**
 * Definición de un proveedor de inferencia compatible con la API de OpenAI.
 *
 * Las URLs fueron relevadas del benchmark local de proveedores (octubre/noviembre 2026)
 * y del catálogo de APIs gratuitas. [chatCompletionsUrl] es el endpoint final: no se
 * concatena nada, para soportar casos como Pollinations o Perplexity que no viven bajo `/v1`.
 */
data class AiProviderSpec(
    val id: String,
    val displayName: String,
    val chatCompletionsUrl: String,
    val modelsUrl: String?,
    val authStyle: ProviderAuthStyle,
    val ring: ProviderRing,
    val keyless: Boolean,
    val defaultModelId: String? = null,
    val notes: String = "",
    /** `false` cuando la URL base todavía no fue confirmada con una llamada real. */
    val endpointVerified: Boolean = true,
)

/**
 * Catálogo único de proveedores de la app autovalente.
 *
 * No hay claves acá: el catálogo solo describe **dónde** y **cómo** llamar. Las claves
 * viven cifradas en `FenixKeystoreVault`. Los proveedores keyless funcionan sin configurar
 * nada y son el respaldo del router interno.
 */
object AiProviderCatalog {

    val providers: List<AiProviderSpec> = listOf(
        // -------------------------------------------------------------------------
        // Sin clave: la app funciona al instalar, sin pedir nada.
        // -------------------------------------------------------------------------
        AiProviderSpec(
            id = "pollinations",
            displayName = "Pollinations AI",
            chatCompletionsUrl = "https://text.pollinations.ai/openai",
            modelsUrl = "https://text.pollinations.ai/models",
            authStyle = ProviderAuthStyle.NONE,
            ring = ProviderRing.KEYLESS,
            keyless = true,
            defaultModelId = "openai",
            notes = "Comunitario keyless, best-effort por IP.",
        ),
        AiProviderSpec(
            id = "llm7",
            displayName = "LLM7.io",
            chatCompletionsUrl = "https://api.llm7.io/v1/chat/completions",
            modelsUrl = "https://api.llm7.io/v1/models",
            authStyle = ProviderAuthStyle.NONE,
            ring = ProviderRing.KEYLESS,
            keyless = true,
            defaultModelId = "deepseek-v3-0324",
            notes = "30 RPM anónimo; token gratuito opcional sube a 120 RPM.",
        ),
        AiProviderSpec(
            id = "ovh_ai",
            displayName = "OVHcloud AI Endpoints",
            chatCompletionsUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions",
            modelsUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/models",
            authStyle = ProviderAuthStyle.NONE,
            ring = ProviderRing.KEYLESS,
            keyless = true,
            defaultModelId = "Mistral-Small-3.2-24B-Instruct-2506",
            notes = "2 RPM por IP y modelo en modo anónimo.",
        ),
        AiProviderSpec(
            id = "publicai",
            displayName = "PublicAI",
            chatCompletionsUrl = "https://api.publicai.co/v1/chat/completions",
            modelsUrl = "https://api.publicai.co/v1/models",
            authStyle = ProviderAuthStyle.NONE,
            ring = ProviderRing.KEYLESS,
            keyless = true,
            defaultModelId = null,
            notes = "20 RPM sin tope mensual publicado.",
            endpointVerified = false,
        ),

        // -------------------------------------------------------------------------
        // Anillo 1
        // -------------------------------------------------------------------------
        AiProviderSpec(
            id = "gemini",
            displayName = "Google Gemini",
            chatCompletionsUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            modelsUrl = "https://generativelanguage.googleapis.com/v1beta/openai/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_1,
            keyless = false,
            defaultModelId = "gemini-2.5-flash",
            notes = "1M de contexto. En capa gratuita Google puede usar los prompts para mejorar sus productos.",
        ),
        AiProviderSpec(
            id = "tokenharbor",
            displayName = "TokenHarbor",
            chatCompletionsUrl = "https://api.tokenharbor.ai/v1/chat/completions",
            modelsUrl = "https://api.tokenharbor.ai/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_1,
            keyless = false,
            defaultModelId = "deepseek-v4.1-flash:free",
            notes = "60 RPM y 1.800 RPH por cuenta.",
            endpointVerified = false,
        ),

        // -------------------------------------------------------------------------
        // Anillo 2
        // -------------------------------------------------------------------------
        AiProviderSpec(
            id = "groq",
            displayName = "Groq Cloud",
            chatCompletionsUrl = "https://api.groq.com/openai/v1/chat/completions",
            modelsUrl = "https://api.groq.com/openai/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "llama-3.3-70b-versatile",
            notes = "LPU de baja latencia; 30 RPM y 1.000 RPD en modelos pesados.",
        ),
        AiProviderSpec(
            id = "siliconflow",
            displayName = "SiliconFlow",
            chatCompletionsUrl = "https://api.siliconflow.cn/v1/chat/completions",
            modelsUrl = "https://api.siliconflow.cn/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "Qwen/Qwen3-8B",
            notes = "Modelos abiertos Qwen/DeepSeek con capa gratuita.",
        ),
        AiProviderSpec(
            id = "github_models",
            displayName = "GitHub Models",
            chatCompletionsUrl = "https://models.inference.ai.azure.com/chat/completions",
            modelsUrl = "https://models.inference.ai.azure.com/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "gpt-4.1-mini",
            notes = "Usa el token de GitHub. 150 RPD en modelos chicos.",
        ),
        AiProviderSpec(
            id = "openrouter",
            displayName = "OpenRouter",
            chatCompletionsUrl = "https://openrouter.ai/api/v1/chat/completions",
            modelsUrl = "https://openrouter.ai/api/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "nvidia/nemotron-3-super-120b-a12b:free",
            notes = "Catálogo :free con 50 RPD sin recarga.",
        ),
        AiProviderSpec(
            id = "sambanova",
            displayName = "SambaNova Cloud",
            chatCompletionsUrl = "https://api.sambanova.ai/v1/chat/completions",
            modelsUrl = "https://api.sambanova.ai/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "Meta-Llama-3.3-70B-Instruct",
            notes = "20 RPD por modelo en Free Tier estricto.",
        ),
        AiProviderSpec(
            id = "cloudflare",
            displayName = "Cloudflare Workers AI",
            chatCompletionsUrl = "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/v1/chat/completions",
            modelsUrl = "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
            notes = "Requiere reemplazar {account_id} por el ID de la cuenta.",
        ),
        AiProviderSpec(
            id = "kilocode",
            displayName = "Kilo Code Gateway",
            chatCompletionsUrl = "https://api.kilocode.ai/v1/chat/completions",
            modelsUrl = "https://api.kilocode.ai/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "poolside/laguna-s-2.1:free",
            notes = "200 RPH por hora.",
            endpointVerified = false,
        ),
        AiProviderSpec(
            id = "requesty",
            displayName = "Requesty",
            chatCompletionsUrl = "https://router.requesty.ai/v1/chat/completions",
            modelsUrl = "https://router.requesty.ai/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "Qwen/Qwen3.8-27B",
            notes = "200 RPD en modelos gratuitos.",
        ),
        AiProviderSpec(
            id = "plugsky",
            displayName = "Plugsky",
            chatCompletionsUrl = "https://api.plugsky.com/v1/chat/completions",
            modelsUrl = "https://api.plugsky.com/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "plugsky-lite",
            notes = "500 RPD permanentes; no entrena con tus datos.",
        ),
        AiProviderSpec(
            id = "ollama_cloud",
            displayName = "Ollama Cloud",
            chatCompletionsUrl = "https://ollama.com/v1/chat/completions",
            modelsUrl = "https://ollama.com/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "minimax-m2.7",
            notes = "Cuota renovable cada 7 y 30 días.",
            endpointVerified = false,
        ),
        AiProviderSpec(
            id = "agnes",
            displayName = "Agnes AI",
            chatCompletionsUrl = "https://apihub.agnes-ai.com/v1/chat/completions",
            modelsUrl = "https://apihub.agnes-ai.com/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "agnes-3.0-flash",
            notes = "256K de contexto; URL reportada por la comunidad.",
            endpointVerified = false,
        ),
        AiProviderSpec(
            id = "naga",
            displayName = "NagaAI",
            chatCompletionsUrl = "https://api.naga.ac/v1/chat/completions",
            modelsUrl = "https://api.naga.ac/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "Dots3 Note Preview (free)",
            notes = "Renovación diaria de límites gratuitos.",
        ),
        AiProviderSpec(
            id = "unorouter",
            displayName = "UnoRouter",
            chatCompletionsUrl = "https://api.unorouter.com/v1/chat/completions",
            modelsUrl = "https://api.unorouter.com/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "unorouter/free",
            notes = "200 RPD permanentes en modelos :free.",
        ),
        AiProviderSpec(
            id = "airforce",
            displayName = "Api.Airforce",
            chatCompletionsUrl = "https://api.airforce/v1/chat/completions",
            modelsUrl = "https://api.airforce/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "gpt-4.1-mini",
            notes = "Rutas comunitarias variables.",
        ),
        AiProviderSpec(
            id = "mancer",
            displayName = "Mancer AI",
            chatCompletionsUrl = "https://neuro.mancer.tech/oai/v1/chat/completions",
            modelsUrl = "https://neuro.mancer.tech/oai/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "mancer-free-route",
            notes = "Modelo mytholite permanente a costo 0.",
        ),
        AiProviderSpec(
            id = "routeway",
            displayName = "Routeway AI",
            chatCompletionsUrl = "https://api.routeway.ai/v1/chat/completions",
            modelsUrl = "https://api.routeway.ai/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = "experimental-free",
            notes = "5 RPM y 200 RPD en modelos :free.",
        ),
        AiProviderSpec(
            id = "anyapi",
            displayName = "AnyAPI Gateway",
            chatCompletionsUrl = "https://api.anyapi.ai/v1/chat/completions",
            modelsUrl = "https://api.anyapi.ai/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_2,
            keyless = false,
            defaultModelId = null,
            notes = "20 RPM y 200 RPD con una sola clave para todo el catálogo.",
            endpointVerified = false,
        ),

        // -------------------------------------------------------------------------
        // Anillo 3
        // -------------------------------------------------------------------------
        AiProviderSpec(
            id = "cohere",
            displayName = "Cohere",
            chatCompletionsUrl = "https://api.cohere.ai/compatibility/v1/chat/completions",
            modelsUrl = "https://api.cohere.ai/compatibility/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_3,
            keyless = false,
            defaultModelId = "command-a-03-2025",
            notes = "Capa trial: 1.000 llamadas por mes.",
        ),

        // -------------------------------------------------------------------------
        // Anillo 4
        // -------------------------------------------------------------------------
        AiProviderSpec(
            id = "nvidia",
            displayName = "NVIDIA NIM",
            chatCompletionsUrl = "https://integrate.api.nvidia.com/v1/chat/completions",
            modelsUrl = "https://integrate.api.nvidia.com/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_4,
            keyless = false,
            defaultModelId = "deepseek-ai/deepseek-v4-flash",
            notes = "1.000 créditos por mes o límite dinámico.",
        ),
        AiProviderSpec(
            id = "fastrouter",
            displayName = "FastRouter",
            chatCompletionsUrl = "https://api.fastrouter.ai/api/v1/chat/completions",
            modelsUrl = "https://api.fastrouter.ai/api/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_4,
            keyless = false,
            defaultModelId = "fastrouter/auto",
            notes = "Créditos iniciales únicos de bienvenida.",
        ),
        AiProviderSpec(
            id = "baseten",
            displayName = "Baseten",
            chatCompletionsUrl = "https://bridge.baseten.co/v1/chat/completions",
            modelsUrl = "https://bridge.baseten.co/v1/models",
            authStyle = ProviderAuthStyle.BEARER,
            ring = ProviderRing.RING_4,
            keyless = false,
            defaultModelId = "baseten-oss",
            notes = "30 USD de crédito inicial sin tarjeta.",
            endpointVerified = false,
        ),
    )

    fun byId(providerId: String): AiProviderSpec? =
        providers.firstOrNull { it.id == providerId }

    fun keylessProviders(): List<AiProviderSpec> =
        providers.filter { it.keyless }

    fun byokProviders(): List<AiProviderSpec> =
        providers.filterNot { it.keyless }
            .sortedBy { it.ring.order }
}
