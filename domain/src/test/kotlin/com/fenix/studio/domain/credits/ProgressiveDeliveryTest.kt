/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-03 23:10:00 -03:00
 * ARCHIVO: domain/src/test/kotlin/com/fenix/studio/domain/credits/ProgressiveDeliveryTest.kt
 * ================================================================================
 */

package com.fenix.studio.domain.credits

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * El programa de cátedra llega al inicio de la cursada, pero el material de cada unidad
 * lo entrega el docente durante el año, a veces por un solo punto y a veces por la unidad
 * completa. El estudiante debe poder procesar **lo que tiene**, cuando lo tiene.
 */
class ProgressiveDeliveryTest {

    @Test
    fun `student with two credits can process two of nine divisions and the plan says so`() {
        val ledger = CreditLedger()
        ledger.refreshProcessorWeeklyQuota(CreditPolicy.PROCESSOR_WEEKLY_CC_COMMON)

        val plan = ledger.planProcessorDivisions(requestedDivisions = 9, classroomId = "eco-politica")

        assertEquals(9, plan.requestedDivisions)
        assertEquals(2, plan.affordableDivisions, "2 CP/semana alcanzan para 2 divisiones")
        assertEquals(7, plan.deferredDivisions)
        assertEquals(3_600L, plan.requestedCostCc)
        assertEquals(800L, plan.affordableCostCc)
        assertEquals(false, plan.canProcessAllRequested)
        // Planificar no cobra nada.
        assertEquals(800L, ledger.currentBalances().procFreeCc)
    }

    @Test
    fun `charging two of nine divisions leaves the rest for later without failing`() {
        val ledger = CreditLedger()
        ledger.refreshProcessorWeeklyQuota(CreditPolicy.PROCESSOR_WEEKLY_CC_COMMON)

        val firstBatch = ledger.debitProcessorDivisions(
            divisions = 2,
            idempotencyKeyPrefix = "catedra-eco:marzo",
            classroomId = "eco-politica",
        )
        assertTrue(firstBatch is BatchDebitOutcome.Charged)
        assertEquals(800L, (firstBatch as BatchDebitOutcome.Charged).costCc)
        assertEquals(0L, ledger.currentBalances().procFreeCc)

        // Semanas después, ya con saldo comprado, llega la siguiente tanda.
        ledger.credit(CreditPocket.PURCHASED, CreditPolicy.classroomPriceCc(divisions = 3))
        val secondBatch = ledger.debitProcessorDivisions(
            divisions = 3,
            idempotencyKeyPrefix = "catedra-eco:abril",
            classroomId = "eco-politica",
        )
        assertTrue(secondBatch is BatchDebitOutcome.Charged)
        assertEquals(0L, ledger.currentBalances().purchasedCc)
    }

    @Test
    fun `a batch larger than the balance is rejected whole instead of charging what fits`() {
        val ledger = CreditLedger()
        ledger.refreshProcessorWeeklyQuota(CreditPolicy.PROCESSOR_WEEKLY_CC_COMMON)

        val outcome = ledger.debitProcessorDivisions(
            divisions = 9,
            idempotencyKeyPrefix = "catedra-eco:completa",
            classroomId = "eco-politica",
        )
        assertTrue(outcome is BatchDebitOutcome.Rejected)
        assertEquals(2, (outcome as BatchDebitOutcome.Rejected).plan.affordableDivisions)
        assertEquals(800L, ledger.currentBalances().procFreeCc, "No se cobra nada si no alcanza")
    }

    @Test
    fun `resuming the same batch never charges twice`() {
        val ledger = CreditLedger()
        ledger.credit(CreditPocket.PURCHASED, 4L * CreditUnits.PROCESSOR_CREDIT_CC)

        val first = ledger.debitProcessorDivisions(4, "batch-1", classroomId = "sociologia")
        assertTrue(first is BatchDebitOutcome.Charged)
        assertEquals(0L, ledger.currentBalances().purchasedCc)

        val resumed = ledger.debitProcessorDivisions(4, "batch-1", classroomId = "sociologia")
        assertTrue(resumed is BatchDebitOutcome.AlreadyCharged)
        assertEquals(0L, ledger.currentBalances().purchasedCc, "Reanudar no vuelve a cobrar")
    }

    @Test
    fun `welcome grant cap spans several deliveries of the same classroom`() {
        val ledger = CreditLedger()
        ledger.credit(CreditPocket.GRANT, CreditPolicy.classroomGrantCc(divisions = 10))

        // Tres entregas parciales del mismo docente: 3 + 4 + 3 divisiones.
        assertTrue(ledger.debitProcessorDivisions(3, "eco:t1", classroomId = "eco") is BatchDebitOutcome.Charged)
        assertTrue(ledger.debitProcessorDivisions(4, "eco:t2", classroomId = "eco") is BatchDebitOutcome.Charged)
        assertTrue(ledger.debitProcessorDivisions(3, "eco:t3", classroomId = "eco") is BatchDebitOutcome.Charged)

        assertEquals(4_000L, ledger.classroomGrantUsedCc("eco"), "El tope es 10 divisiones acumuladas")

        // La división 11 ya no usa grant: exige saldo propio.
        val eleventh = ledger.debitProcessorDivisions(1, "eco:t4", classroomId = "eco")
        assertTrue(eleventh is BatchDebitOutcome.Rejected)
        assertEquals(0L, ledger.currentBalances().grantCc)

        ledger.credit(CreditPocket.PURCHASED, CreditUnits.PROCESSOR_CREDIT_CC)
        val paid = ledger.debitProcessorDivisions(1, "eco:t4", classroomId = "eco") as BatchDebitOutcome.Charged
        assertEquals(400L, paid.perDivision.single().purchasedCc)
    }

    @Test
    fun `grant consumed by one classroom does not reduce another classroom`() {
        val ledger = CreditLedger()
        ledger.credit(CreditPocket.GRANT, CreditPolicy.classroomGrantCc(divisions = 10))

        assertTrue(ledger.debitProcessorDivisions(10, "eco:todo", classroomId = "eco") is BatchDebitOutcome.Charged)
        assertEquals(4_000L, ledger.classroomGrantUsedCc("eco"))
        assertEquals(0L, ledger.classroomGrantUsedCc("historia"))

        // El grant ya se agotó, pero otra cátedra arranca con su propio tope.
        ledger.credit(CreditPocket.GRANT, CreditPolicy.classroomGrantCc(divisions = 10))
        assertTrue(ledger.debitProcessorDivisions(10, "hist:t1", classroomId = "historia") is BatchDebitOutcome.Charged)
    }
}
