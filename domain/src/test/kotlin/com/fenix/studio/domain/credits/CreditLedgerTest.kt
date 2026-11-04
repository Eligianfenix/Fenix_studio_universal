/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-03 22:30:00 -03:00
 * ARCHIVO: domain/src/test/kotlin/com/fenix/studio/domain/credits/CreditLedgerTest.kt
 * ================================================================================
 */

package com.fenix.studio.domain.credits

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Verifica los criterios de aceptación de `ADR-0008` §14 que dependen del núcleo contable.
 */
class CreditLedgerTest {

    // §14.1 — Común: 20 mensajes; el 21.º se bloquea; al día siguiente vuelve a 20, no 40.
    @Test
    fun `common profile allows 20 chat messages per day and blocks the 21st without accumulating`() {
        val ledger = CreditLedger()
        ledger.refreshChatDailyQuota(CreditPolicy.CHAT_DAILY_CC_COMMON)

        repeat(20) { index ->
            val outcome = ledger.debitChat(idempotencyKey = "msg-$index")
            assertTrue(outcome is DebitOutcome.Charged, "El mensaje ${index + 1} debería cobrarse")
        }
        assertEquals(0L, ledger.currentBalances().chatFreeCc, "El cupo diario queda agotado")

        val blocked = ledger.debitChat(idempotencyKey = "msg-20")
        assertTrue(blocked is DebitOutcome.Rejected, "El mensaje 21 debe bloquearse")

        // Reset diario: vuelve a 20, nunca a 40.
        ledger.refreshChatDailyQuota(CreditPolicy.CHAT_DAILY_CC_COMMON)
        assertEquals(100L, ledger.currentBalances().chatFreeCc)

        var charged = 0
        repeat(25) { index ->
            if (ledger.debitChat(idempotencyKey = "day2-msg-$index") is DebitOutcome.Charged) charged++
        }
        assertEquals(20, charged, "Tras el reset solo hay 20 mensajes, no 40")
    }

    // §14.2 — Común: 2 divisiones gratis; la 3.ª se bloquea; a la semana vuelve a 2.
    @Test
    fun `common profile allows two free processor divisions per week and blocks the third`() {
        val ledger = CreditLedger()
        ledger.refreshProcessorWeeklyQuota(CreditPolicy.PROCESSOR_WEEKLY_CC_COMMON)

        repeat(2) { index ->
            assertTrue(
                ledger.debitProcessor(idempotencyKey = "u-$index") is DebitOutcome.Charged,
                "La división ${index + 1} debería cobrarse",
            )
        }
        val blocked = ledger.debitProcessor(idempotencyKey = "u-3") as DebitOutcome.Rejected
        assertEquals(400L, blocked.requiredCc, "Una división cuesta 1 CP = 400 cc")
        assertEquals(0L, blocked.availableCc)

        ledger.refreshProcessorWeeklyQuota(CreditPolicy.PROCESSOR_WEEKLY_CC_COMMON)
        assertEquals(800L, ledger.currentBalances().procFreeCc, "La semana siguiente vuelve a 2 divisiones")
    }

    // §14.3 — Compra: 1 CP + 5 CP = 6 CP (2.400 cc).
    @Test
    fun `purchased processor credits accumulate to six credits`() {
        val ledger = CreditLedger()
        ledger.credit(CreditPocket.PURCHASED, CreditUnits.PROCESSOR_CREDIT_CC)
        ledger.credit(CreditPocket.PURCHASED, 5L * CreditUnits.PROCESSOR_CREDIT_CC)

        assertEquals(2_400L, ledger.currentBalances().purchasedCc)
        assertEquals(
            6,
            ledger.currentBalances().purchasedCc / CreditUnits.PROCESSOR_CREDIT_CC,
            "1 CP + 5 CP deben ser 6 CP",
        )
    }

    // §14.4 — Beta semana 1: 60 mensajes/día; desde la semana 2, 40.
    @Test
    fun `beta week one grants 60 messages and week two drops to 40`() {
        val weekOne = CreditLedger()
        weekOne.refreshChatDailyQuota(CreditPolicy.CHAT_DAILY_CC_BETA_WEEK_1)
        var weekOneCharged = 0
        repeat(70) { index ->
            if (weekOne.debitChat(idempotencyKey = "b1-$index") is DebitOutcome.Charged) weekOneCharged++
        }
        assertEquals(60, weekOneCharged)

        val weekTwo = CreditLedger()
        weekTwo.refreshChatDailyQuota(CreditPolicy.CHAT_DAILY_CC_BETA_WEEK_2)
        var weekTwoCharged = 0
        repeat(70) { index ->
            if (weekTwo.debitChat(idempotencyKey = "b2-$index") is DebitOutcome.Charged) weekTwoCharged++
        }
        assertEquals(40, weekTwoCharged)
    }

    // §14.5 — Bienvenida: primera cátedra gratis hasta 10 divisiones; la 11 se cobra.
    @Test
    fun `welcome grant covers ten divisions and the eleventh requires purchased credits`() {
        val ledger = CreditLedger()
        ledger.credit(CreditPocket.GRANT, CreditPolicy.classroomGrantCc(divisions = 10))

        repeat(10) { index ->
            val outcome = ledger.debitProcessor(idempotencyKey = "w-$index")
            assertTrue(outcome is DebitOutcome.Charged)
            assertEquals(0L, (outcome as DebitOutcome.Charged).purchasedCc, "La bienvenida no consume comprados")
        }
        assertEquals(0L, ledger.currentBalances().grantCc)

        val eleventh = ledger.debitProcessor(idempotencyKey = "w-10")
        assertTrue(eleventh is DebitOutcome.Rejected, "La división 11 debe cobrarse y no hay saldo")

        ledger.credit(CreditPocket.PURCHASED, CreditUnits.PROCESSOR_CREDIT_CC)
        val charged = ledger.debitProcessor(idempotencyKey = "w-10")
        assertTrue(charged is DebitOutcome.Charged)
        assertEquals(400L, (charged as DebitOutcome.Charged).purchasedCc)
    }

    // §14.6 — Bono: cátedra de 9 divisiones pagada → 720 cc al aprobarse; repetir no paga dos veces.
    @Test
    fun `publication bounty pays 720 cc once for a nine division paid classroom`() {
        val ledger = CreditLedger()
        ledger.credit(CreditPocket.PURCHASED, CreditPolicy.classroomPriceCc(divisions = 9))

        var purchasedDebited = 0L
        repeat(9) { index ->
            val outcome = ledger.debitProcessor(idempotencyKey = "paid-$index")
            purchasedDebited += (outcome as DebitOutcome.Charged).purchasedCc
        }
        assertEquals(3_600L, purchasedDebited)

        val bounty = ledger.creditPublicationBounty(purchasedDebited, idempotencyKey = "user-1:catedra-9")
        assertEquals(720L, bounty)
        assertEquals(720L, ledger.currentBalances().purchasedCc)

        val repeated = ledger.creditPublicationBounty(purchasedDebited, idempotencyKey = "user-1:catedra-9")
        assertEquals(0L, repeated, "Republicar no paga dos veces")
        assertEquals(720L, ledger.currentBalances().purchasedCc)
    }

    // §14.7 — Cátedra gratuita: no genera bono.
    @Test
    fun `free classroom generates no publication bounty`() {
        val ledger = CreditLedger()
        ledger.credit(CreditPocket.GRANT, CreditPolicy.classroomGrantCc(divisions = 9))

        var purchasedDebited = 0L
        repeat(9) { index ->
            val outcome = ledger.debitProcessor(idempotencyKey = "free-$index")
            purchasedDebited += (outcome as DebitOutcome.Charged).purchasedCc
        }
        assertEquals(0L, purchasedDebited)
        assertEquals(0L, ledger.creditPublicationBounty(purchasedDebited, idempotencyKey = "free-bounty"))
    }

    // §14.8 — Biblioteca: cátedra de 9 divisiones = 2.880 cc ($3.600), 20% menos.
    @Test
    fun `library price is twenty percent off the full classroom price`() {
        val nineDivisions = CreditPolicy.classroomPriceCc(divisions = 9)
        assertEquals(3_600L, nineDivisions)
        assertEquals(2_880L, CreditPolicy.libraryPriceCc(nineDivisions))

        val sixDivisions = CreditPolicy.classroomPriceCc(divisions = 6)
        assertEquals(2_400L, sixDivisions)
        assertEquals(1_920L, CreditPolicy.libraryPriceCc(sixDivisions))
    }

    // §14.11 — Idempotencia: reintentar el mismo mensaje no cobra dos veces.
    @Test
    fun `repeating a debit key never charges twice`() {
        val ledger = CreditLedger()
        ledger.refreshChatDailyQuota(CreditPolicy.CHAT_DAILY_CC_COMMON)

        val first = ledger.debitChat(idempotencyKey = "message-abc")
        assertTrue(first is DebitOutcome.Charged)

        val retry = ledger.debitChat(idempotencyKey = "message-abc")
        assertTrue(retry is DebitOutcome.AlreadyCharged)
        assertEquals(95L, ledger.currentBalances().chatFreeCc, "El reintento no descuenta de nuevo")
    }

    @Test
    fun `purchased credits survive quota refreshes while free credits are replaced`() {
        val ledger = CreditLedger()
        ledger.credit(CreditPocket.PURCHASED, 800L)
        ledger.credit(CreditPocket.GRANT, 400L)
        ledger.refreshChatDailyQuota(CreditPolicy.CHAT_DAILY_CC_COMMON)
        ledger.refreshProcessorWeeklyQuota(CreditPolicy.PROCESSOR_WEEKLY_CC_COMMON)

        val balances = ledger.currentBalances()
        assertEquals(800L, balances.purchasedCc, "Los comprados se acumulan y no vencen")
        assertEquals(400L, balances.grantCc)
        assertEquals(100L, balances.chatFreeCc)
        assertEquals(800L, balances.procFreeCc)
    }

    @Test
    fun `a debit never leaves a partial charge when funds are insufficient`() {
        val ledger = CreditLedger()
        ledger.refreshProcessorWeeklyQuota(100L)

        val outcome = ledger.debitProcessor(idempotencyKey = "partial")
        assertTrue(outcome is DebitOutcome.Rejected)
        assertEquals(100L, ledger.currentBalances().procFreeCc, "El saldo queda intacto")
    }

    // §11 — Chat: 00:00 ART. Procesador: lunes 01:30 ART.
    @Test
    fun `chat day key rolls over at midnight argentina time`() {
        val calendar = QuotaCalendar()
        // 2026-11-02 23:59 ART = 2026-11-03 02:59 UTC
        assertEquals("2026-11-02", calendar.chatDayKey(Instant.parse("2026-11-03T02:59:00Z")))
        // 2026-11-03 00:00 ART = 2026-11-03 03:00 UTC
        assertEquals("2026-11-03", calendar.chatDayKey(Instant.parse("2026-11-03T03:00:00Z")))
    }

    @Test
    fun `processor week key rolls over on monday at one thirty argentina time`() {
        val calendar = QuotaCalendar()
        // Lunes 2026-11-02 01:29 ART = 04:29 UTC → todavía semana 44
        assertEquals("2026-W44", calendar.processorWeekKey(Instant.parse("2026-11-02T04:29:00Z")))
        // Lunes 2026-11-02 01:30 ART = 04:30 UTC → semana 45
        assertEquals("2026-W45", calendar.processorWeekKey(Instant.parse("2026-11-02T04:30:00Z")))
        // Sábado de la misma semana sigue en 45
        assertEquals("2026-W45", calendar.processorWeekKey(Instant.parse("2026-11-07T15:00:00Z")))
    }
}
