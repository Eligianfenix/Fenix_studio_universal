/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-03 22:30:00 -03:00
 * ARCHIVO: domain/src/main/kotlin/com/fenix/studio/domain/credits/CreditLedger.kt
 * ================================================================================
 */

package com.fenix.studio.domain.credits

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.IsoFields

/**
 * Constantes contables de `ADR-0008` §4.
 *
 * Toda la economía se expresa en **centicréditos (cc)** como `Long`: no existe
 * aritmética de punto flotante en ningún saldo ni precio.
 */
object CreditUnits {
    /** Costo de un mensaje de chat. */
    const val MESSAGE_COST_CC: Long = 5L

    /** 1 crédito de chat (CC) = 20 mensajes. */
    const val CHAT_CREDIT_CC: Long = 100L

    /** 1 crédito de procesador (CP) = 1 división global = 4 CC. */
    const val PROCESSOR_CREDIT_CC: Long = 400L

    const val MESSAGES_PER_CHAT_CREDIT: Int = 20
    const val CHAT_CREDITS_PER_PROCESSOR_CREDIT: Long = 4L
}

/**
 * Bolsillos del ledger. Son **no fungibles entre sí**: lo gratuito está atado a su
 * carril y lo comprado es universal (`ADR-0008` §6).
 */
enum class CreditPocket {
    /** Cupo diario de chat. Se pierde al reset; nunca paga procesamiento. */
    CHAT_FREE,

    /** Cupo semanal de procesador. Se pierde al reset; nunca paga chat. */
    PROC_FREE,

    /** Grants de bienvenida y beta: débito 0 para el usuario, con tope por cátedra. */
    GRANT,

    /** Créditos comprados: universales, se acumulan y no vencen. */
    PURCHASED,
}

/**
 * Saldos inmutables por bolsillo, en centicréditos.
 */
data class PocketBalances(
    val chatFreeCc: Long = 0L,
    val procFreeCc: Long = 0L,
    val grantCc: Long = 0L,
    val purchasedCc: Long = 0L,
) {
    init {
        require(chatFreeCc >= 0L) { "chatFreeCc no puede ser negativo" }
        require(procFreeCc >= 0L) { "procFreeCc no puede ser negativo" }
        require(grantCc >= 0L) { "grantCc no puede ser negativo" }
        require(purchasedCc >= 0L) { "purchasedCc no puede ser negativo" }
    }

    val freeCc: Long get() = chatFreeCc + procFreeCc + grantCc
    val totalCc: Long get() = freeCc + purchasedCc

    fun amountOf(pocket: CreditPocket): Long = when (pocket) {
        CreditPocket.CHAT_FREE -> chatFreeCc
        CreditPocket.PROC_FREE -> procFreeCc
        CreditPocket.GRANT -> grantCc
        CreditPocket.PURCHASED -> purchasedCc
    }

    fun withAmount(pocket: CreditPocket, amountCc: Long): PocketBalances = when (pocket) {
        CreditPocket.CHAT_FREE -> copy(chatFreeCc = amountCc)
        CreditPocket.PROC_FREE -> copy(procFreeCc = amountCc)
        CreditPocket.GRANT -> copy(grantCc = amountCc)
        CreditPocket.PURCHASED -> copy(purchasedCc = amountCc)
    }
}

/** Porción de un débito imputada a un bolsillo concreto. */
data class PocketCharge(
    val pocket: CreditPocket,
    val amountCc: Long,
)

/**
 * Resultado de un intento de débito. Nunca lanza excepciones por saldo insuficiente:
 * el llamador decide qué mostrar al estudiante.
 */
sealed interface DebitOutcome {

    data class Charged(
        val chargedCc: Long,
        val charges: List<PocketCharge>,
        val balancesAfter: PocketBalances,
        val idempotencyKey: String? = null,
    ) : DebitOutcome {
        /** cc imputados a bolsillos gratuitos (cupo o grant). */
        val freeCc: Long
            get() = charges.filter { it.pocket != CreditPocket.PURCHASED }.sumOf { it.amountCc }

        /** cc imputados a créditos comprados: única base válida para el bono de publicación. */
        val purchasedCc: Long
            get() = charges.filter { it.pocket == CreditPocket.PURCHASED }.sumOf { it.amountCc }
    }

    /** La clave de idempotencia ya fue cobrada: reintentar no vuelve a debitar. */
    data class AlreadyCharged(
        val idempotencyKey: String,
        val previous: Charged,
    ) : DebitOutcome

    data class Rejected(
        val requiredCc: Long,
        val availableCc: Long,
        val balances: PocketBalances,
    ) : DebitOutcome
}

/**
 * Consulta previa, sin cobrar, de cuántas divisiones puede procesar el estudiante con su
 * saldo actual. Es lo que alimenta la pantalla del procesador cuando el docente entregó
 * una parte del material y todavía no la cátedra completa.
 */
data class ProcessorPlan(
    val requestedDivisions: Int,
    val affordableDivisions: Int,
    val deferredDivisions: Int,
    val requestedCostCc: Long,
    val affordableCostCc: Long,
    val grantAvailableCc: Long,
    val balances: PocketBalances,
) {
    val canProcessAllRequested: Boolean get() = deferredDivisions == 0
}

/**
 * Resultado de cobrar un lote de divisiones elegido por el estudiante.
 *
 * Es **atómico por lote**: o se cubren todas las divisiones pedidas o no se cobra ninguna.
 * La atomicidad es del lote, no de la cátedra: procesar 2 de 9 divisiones es un caso
 * normal y esperado.
 */
sealed interface BatchDebitOutcome {

    data class Charged(
        val divisions: Int,
        val costCc: Long,
        val perDivision: List<DebitOutcome.Charged>,
        val balancesAfter: PocketBalances,
    ) : BatchDebitOutcome

    /** El lote ya había sido cobrado con la misma clave: reanudar no duplica el débito. */
    data class AlreadyCharged(
        val divisions: Int,
        val balancesAfter: PocketBalances,
    ) : BatchDebitOutcome

    data class Rejected(
        val plan: ProcessorPlan,
    ) : BatchDebitOutcome
}

/**
 * Núcleo contable de la economía de Fénix Studio (`ADR-0008`).
 *
 * Es deliberadamente puro: no conoce Android, Room, Supabase ni la red. La persistencia
 * y la sincronización remota son responsabilidad de la capa que lo envuelva.
 *
 * Reglas implementadas:
 * - Consumo de chat: `CHAT_FREE` → `PURCHASED` → bloqueo.
 * - Consumo de procesador: `GRANT` → `PROC_FREE` → `PURCHASED` → bloqueo.
 * - Los gratuitos no se acumulan: el refresh de cuota **reemplaza** el saldo.
 * - Los comprados se suman y no vencen.
 * - Todo débito puede ser idempotente por clave.
 */
class CreditLedger(initialBalances: PocketBalances = PocketBalances()) {

    private val lock = Any()
    private var balances: PocketBalances = initialBalances

    /** Claves ya cobradas, con cota para no crecer sin límite en sesiones largas. */
    private val chargedByKey = object : LinkedHashMap<String, DebitOutcome.Charged>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DebitOutcome.Charged>?): Boolean =
            size > MAX_REMEMBERED_KEYS
    }

    /**
     * Grant consumido por cátedra, en cc. El material llega por partes durante la cursada,
     * así que el tope de 10 divisiones debe acumularse entre entregas y no reiniciarse
     * en cada tanda.
     */
    private val grantUsedByClassroomCc = mutableMapOf<String, Long>()

    fun currentBalances(): PocketBalances = synchronized(lock) { balances }

    /**
     * Acredita centicréditos en un bolsillo (compra, grant de bienvenida o beta).
     * Los bolsillos gratuitos también admiten acreditación explícita; su pérdida al
     * reset la decide el llamador con [refreshChatDailyQuota] / [refreshProcessorWeeklyQuota].
     */
    fun credit(pocket: CreditPocket, amountCc: Long): PocketBalances = synchronized(lock) {
        require(amountCc >= 0L) { "No se acreditan montos negativos" }
        val updated = balances.withAmount(pocket, balances.amountOf(pocket) + amountCc)
        balances = updated
        updated
    }

    /**
     * Reemplaza el cupo diario de chat. Lo no usado **se pierde**: cumple el criterio
     * «al día siguiente vuelve a 20, no 40» (`ADR-0008` §14.1).
     */
    fun refreshChatDailyQuota(quotaCc: Long): PocketBalances = synchronized(lock) {
        require(quotaCc >= 0L) { "La cuota no puede ser negativa" }
        balances = balances.copy(chatFreeCc = quotaCc)
        balances
    }

    /**
     * Reemplaza el cupo semanal de procesador. Lo no usado **se pierde** (`ADR-0008` §14.2).
     */
    fun refreshProcessorWeeklyQuota(quotaCc: Long): PocketBalances = synchronized(lock) {
        require(quotaCc >= 0L) { "La cuota no puede ser negativa" }
        balances = balances.copy(procFreeCc = quotaCc)
        balances
    }

    /**
     * Debita un mensaje de chat: primero el cupo diario, luego los comprados.
     */
    fun debitChat(
        amountCc: Long = CreditUnits.MESSAGE_COST_CC,
        idempotencyKey: String? = null,
    ): DebitOutcome = debit(
        amountCc = amountCc,
        limits = listOf(
            PocketLimit(CreditPocket.CHAT_FREE),
            PocketLimit(CreditPocket.PURCHASED),
        ),
        idempotencyKey = idempotencyKey,
    )

    /**
     * Debita una división global suelta. El material de una cátedra llega por partes, así
     * que el débito **nunca** se plantea contra la cátedra completa: se cobra la división
     * que el estudiante decide procesar en ese momento.
     */
    fun debitProcessor(
        amountCc: Long = CreditUnits.PROCESSOR_CREDIT_CC,
        idempotencyKey: String? = null,
        useGrant: Boolean = true,
    ): DebitOutcome = debit(
        amountCc = amountCc,
        limits = buildList {
            if (useGrant) add(PocketLimit(CreditPocket.GRANT))
            add(PocketLimit(CreditPocket.PROC_FREE))
            add(PocketLimit(CreditPocket.PURCHASED))
        },
        idempotencyKey = idempotencyKey,
    )

    /**
     * Debita una división aplicando el tope de grant **por cátedra**, que acumula entre
     * entregas parciales: si el docente entrega 2 divisiones en marzo y 3 en abril, el
     * grant cubre hasta [grantCapCc] en total para esa cátedra, no por tanda.
     */
    fun debitProcessorForClassroom(
        classroomId: String,
        amountCc: Long = CreditUnits.PROCESSOR_CREDIT_CC,
        idempotencyKey: String? = null,
        grantCapCc: Long = CreditPolicy.CLASSROOM_GRANT_CAP_CC,
    ): DebitOutcome = debit(
        amountCc = amountCc,
        limits = buildList {
            add(PocketLimit(CreditPocket.GRANT, remainingClassroomGrantCc(classroomId, grantCapCc)))
            add(PocketLimit(CreditPocket.PROC_FREE))
            add(PocketLimit(CreditPocket.PURCHASED))
        },
        idempotencyKey = idempotencyKey,
        classroomId = classroomId,
    )

    /** Grant ya consumido por esta cátedra, en cc. */
    fun classroomGrantUsedCc(classroomId: String): Long = synchronized(lock) {
        grantUsedByClassroomCc[classroomId] ?: 0L
    }

    /**
     * Consulta previa sin cobrar: cuántas divisiones entran con el saldo actual.
     */
    fun planProcessorDivisions(
        requestedDivisions: Int,
        classroomId: String? = null,
        grantCapCc: Long = CreditPolicy.CLASSROOM_GRANT_CAP_CC,
    ): ProcessorPlan = synchronized(lock) {
        require(requestedDivisions >= 0) { "Las divisiones no pueden ser negativas" }

        val grantAvailableCc = if (classroomId != null) {
            minOf(balances.grantCc, remainingClassroomGrantCc(classroomId, grantCapCc))
        } else {
            balances.grantCc
        }
        val availableCc = grantAvailableCc + balances.procFreeCc + balances.purchasedCc
        val affordable = (availableCc / CreditUnits.PROCESSOR_CREDIT_CC).toInt()
            .coerceAtMost(requestedDivisions)

        ProcessorPlan(
            requestedDivisions = requestedDivisions,
            affordableDivisions = affordable,
            deferredDivisions = requestedDivisions - affordable,
            requestedCostCc = requestedDivisions * CreditUnits.PROCESSOR_CREDIT_CC,
            affordableCostCc = affordable * CreditUnits.PROCESSOR_CREDIT_CC,
            grantAvailableCc = grantAvailableCc,
            balances = balances,
        )
    }

    /**
     * Cobra [divisions] divisiones elegidas por el estudiante. Cada división lleva su
     * propia clave de idempotencia (`<prefix>:div-i`), así reanudar un checkpoint no
     * duplica el cobro y distintas tandas de material se cobran por separado.
     */
    fun debitProcessorDivisions(
        divisions: Int,
        idempotencyKeyPrefix: String,
        classroomId: String? = null,
        grantCapCc: Long = CreditPolicy.CLASSROOM_GRANT_CAP_CC,
    ): BatchDebitOutcome = synchronized(lock) {
        require(divisions > 0) { "Hay que procesar al menos una división" }

        val firstKey = "$idempotencyKeyPrefix:div-0"
        chargedByKey[firstKey]?.let {
            return BatchDebitOutcome.AlreadyCharged(divisions = divisions, balancesAfter = balances)
        }

        val plan = planProcessorDivisions(divisions, classroomId, grantCapCc)
        if (plan.affordableDivisions < divisions) {
            return BatchDebitOutcome.Rejected(plan = plan)
        }

        val perDivision = mutableListOf<DebitOutcome.Charged>()
        for (index in 0 until divisions) {
            val key = "$idempotencyKeyPrefix:div-$index"
            val outcome = if (classroomId != null) {
                debitProcessorForClassroom(classroomId, idempotencyKey = key, grantCapCc = grantCapCc)
            } else {
                debitProcessor(idempotencyKey = key)
            }
            when (outcome) {
                is DebitOutcome.Charged -> perDivision += outcome
                is DebitOutcome.AlreadyCharged -> perDivision += outcome.previous
                is DebitOutcome.Rejected -> return BatchDebitOutcome.Rejected(plan = plan)
            }
        }

        BatchDebitOutcome.Charged(
            divisions = divisions,
            costCc = divisions * CreditUnits.PROCESSOR_CREDIT_CC,
            perDivision = perDivision,
            balancesAfter = balances,
        )
    }

    private fun remainingClassroomGrantCc(classroomId: String, capCc: Long): Long {
        val used = grantUsedByClassroomCc[classroomId] ?: 0L
        return (capCc - used).coerceAtLeast(0L)
    }

    /**
     * Acredita el bono de publicación en el bolsillo `PURCHASED` (`ADR-0008` §10).
     *
     * @param debitedCc centicréditos **comprados** efectivamente debitados al procesar la
     *   cátedra. Si el procesamiento fue gratuito, vale 0 y no hay bono.
     * @return centicréditos acreditados (0 si no corresponde).
     */
    fun creditPublicationBounty(
        debitedCc: Long,
        percent: Int = CreditPolicy.PUBLICATION_BOUNTY_PCT,
        idempotencyKey: String? = null,
    ): Long = synchronized(lock) {
        require(debitedCc >= 0L) { "El débito base no puede ser negativo" }
        require(percent in 0..100) { "El porcentaje de bono debe estar entre 0 y 100" }

        if (debitedCc == 0L || percent == 0) return 0L
        if (idempotencyKey != null && chargedByKey.containsKey(BOUNTY_PREFIX + idempotencyKey)) {
            return 0L
        }

        val bountyCc = CreditPolicy.bountyCc(debitedCc, percent)
        if (bountyCc <= 0L) return 0L

        balances = balances.copy(purchasedCc = balances.purchasedCc + bountyCc)
        if (idempotencyKey != null) {
            chargedByKey[BOUNTY_PREFIX + idempotencyKey] = DebitOutcome.Charged(
                chargedCc = 0L,
                charges = emptyList(),
                balancesAfter = balances,
                idempotencyKey = idempotencyKey,
            )
        }
        bountyCc
    }

    private fun debit(
        amountCc: Long,
        limits: List<PocketLimit>,
        idempotencyKey: String?,
        classroomId: String? = null,
    ): DebitOutcome = synchronized(lock) {
        require(amountCc > 0L) { "El monto a debitar debe ser positivo" }

        if (idempotencyKey != null) {
            chargedByKey[idempotencyKey]?.let { previous ->
                return DebitOutcome.AlreadyCharged(idempotencyKey, previous)
            }
        }

        val charges = allocate(amountCc, limits)
            ?: return DebitOutcome.Rejected(
                requiredCc = amountCc,
                availableCc = limits.sumOf { limit ->
                    minOf(balances.amountOf(limit.pocket), limit.maxCc)
                },
                balances = balances,
            )

        var updated = balances
        charges.forEach { charge ->
            updated = updated.withAmount(charge.pocket, updated.amountOf(charge.pocket) - charge.amountCc)
        }
        balances = updated

        if (classroomId != null) {
            val grantTakenCc = charges
                .filter { it.pocket == CreditPocket.GRANT }
                .sumOf { it.amountCc }
            if (grantTakenCc > 0L) {
                grantUsedByClassroomCc[classroomId] =
                    (grantUsedByClassroomCc[classroomId] ?: 0L) + grantTakenCc
            }
        }

        val outcome = DebitOutcome.Charged(
            chargedCc = amountCc,
            charges = charges,
            balancesAfter = updated,
            idempotencyKey = idempotencyKey,
        )
        if (idempotencyKey != null) {
            chargedByKey[idempotencyKey] = outcome
        }
        outcome
    }

    /**
     * Reparte [amountCc] sobre los bolsillos permitidos, respetando el máximo imputable
     * de cada uno. Devuelve `null` si el total disponible no alcanza: **nunca** deja un
     * cobro parcial de la división pedida.
     */
    private fun allocate(amountCc: Long, limits: List<PocketLimit>): List<PocketCharge>? {
        var remaining = amountCc
        val charges = mutableListOf<PocketCharge>()
        for (limit in limits) {
            if (remaining <= 0L) break
            val available = minOf(balances.amountOf(limit.pocket), limit.maxCc)
            if (available <= 0L) continue
            val take = minOf(available, remaining)
            charges += PocketCharge(limit.pocket, take)
            remaining -= take
        }
        return if (remaining == 0L) charges else null
    }

    /** Bolsillo habilitado para un débito y su tope imputable en esta operación. */
    private data class PocketLimit(
        val pocket: CreditPocket,
        val maxCc: Long = Long.MAX_VALUE,
    )

    companion object {
        /** Cota de claves de idempotencia recordadas en memoria. */
        const val MAX_REMEMBERED_KEYS: Int = 50_000

        private const val BOUNTY_PREFIX = "bounty:"
    }
}

/**
 * Parámetros de producto de `ADR-0008`, gobernables desde `Fenix Admin` en la capa que
 * consuma este núcleo. Aquí son los valores por defecto aprobados.
 */
object CreditPolicy {
    /** Común: 1 CC/día de chat = 20 mensajes. */
    const val CHAT_DAILY_CC_COMMON: Long = CreditUnits.CHAT_CREDIT_CC

    /** Común: 2 CP/semana = 2 divisiones. */
    const val PROCESSOR_WEEKLY_CC_COMMON: Long = 2L * CreditUnits.PROCESSOR_CREDIT_CC

    /** Beta semana 1: 3 CC/día = 60 mensajes. */
    const val CHAT_DAILY_CC_BETA_WEEK_1: Long = 3L * CreditUnits.CHAT_CREDIT_CC

    /** Beta desde semana 2: 2 CC/día = 40 mensajes. */
    const val CHAT_DAILY_CC_BETA_WEEK_2: Long = 2L * CreditUnits.CHAT_CREDIT_CC

    /** Beta desde semana 2: 4 CP/semana = 4 divisiones. */
    const val PROCESSOR_WEEKLY_CC_BETA: Long = 4L * CreditUnits.PROCESSOR_CREDIT_CC

    /** Bienvenida y cada cátedra del grant beta: tope de 10 divisiones. */
    const val CLASSROOM_GRANT_CAP_DIVISIONS: Int = 10

    /** El mismo tope expresado en centicréditos: 10 divisiones × 1 CP. */
    const val CLASSROOM_GRANT_CAP_CC: Long = 10L * CreditUnits.PROCESSOR_CREDIT_CC

    /** Beta semana 1: 4 cátedras gratis. */
    const val BETA_WEEK_1_FREE_CLASSROOMS: Int = 4

    /** Vigencia del grant beta desde la activación del rol. */
    const val BETA_GRANT_VALID_DAYS: Int = 7

    /** Biblioteca: 80% del precio completo. */
    const val LIBRARY_DISCOUNT_PCT: Int = 20

    /** Bono único de publicación sobre lo efectivamente debitado. */
    const val PUBLICATION_BOUNTY_PCT: Int = 20

    /** Tope de publicaciones bonificadas por mes. */
    const val PUBLICATIONS_PER_MONTH_CAP: Int = 4

    fun classroomPriceCc(divisions: Int): Long {
        require(divisions >= 0) { "Las divisiones no pueden ser negativas" }
        return divisions * CreditUnits.PROCESSOR_CREDIT_CC
    }

    fun libraryPriceCc(
        fullPriceCc: Long,
        discountPct: Int = LIBRARY_DISCOUNT_PCT,
    ): Long {
        require(fullPriceCc >= 0L) { "El precio no puede ser negativo" }
        require(discountPct in 0..100) { "El descuento debe estar entre 0 y 100" }
        return fullPriceCc * (100 - discountPct) / 100
    }

    fun bountyCc(
        debitedCc: Long,
        percent: Int = PUBLICATION_BOUNTY_PCT,
    ): Long {
        require(debitedCc >= 0L) { "El débito base no puede ser negativo" }
        require(percent in 0..100) { "El porcentaje debe estar entre 0 y 100" }
        return debitedCc * percent / 100
    }

    /** Grant de una cátedra gratis: `divisiones × 1 CP`, con tope de 10 divisiones. */
    fun classroomGrantCc(
        divisions: Int,
        capDivisions: Int = CLASSROOM_GRANT_CAP_DIVISIONS,
    ): Long = CreditPolicy.classroomPriceCc(minOf(divisions, capDivisions))
}

/**
 * Calendario de resets en hora argentina (`ADR-0008` §11).
 *
 * - Chat: 00:00 ART.
 * - Procesador: lunes 01:30 ART.
 *
 * El corte semanal se modela desplazando el instante 90 minutos hacia atrás y tomando
 * la semana ISO de esa fecha local: así el cambio de clave ocurre exactamente a las
 * 01:30 del lunes, sin horarios especiales.
 */
class QuotaCalendar(
    private val zone: ZoneId = ART_ZONE,
) {
    fun chatDayKey(now: Instant): String = now.atZone(zone).toLocalDate().toString()

    fun processorWeekKey(now: Instant): String {
        val shifted = now.atZone(zone).minusMinutes(PROCESSOR_RESET_OFFSET_MINUTES)
        val date = shifted.toLocalDate()
        val weekBasedYear = date.get(IsoFields.WEEK_BASED_YEAR)
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return "%d-W%02d".format(weekBasedYear, week)
    }

    companion object {
        val ART_ZONE: ZoneId = ZoneId.of("America/Argentina/San_Juan")

        /** 01:30 ART respecto de la medianoche del lunes. */
        const val PROCESSOR_RESET_OFFSET_MINUTES: Long = 90L
    }
}
