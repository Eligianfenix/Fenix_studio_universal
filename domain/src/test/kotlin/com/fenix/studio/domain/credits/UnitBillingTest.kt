/*
 * ================================================================================
 * PROYECTO: FÉNIX STUDIO — PLATAFORMA DE ACCESIBILIDAD Y PROCESAMIENTO ACADÉMICO
 * PROPIEDAD INTELECTUAL Y AUTORÍA ORIGINAL: Eli Gian Cortez Leonardi
 * LOCALIDAD: San Juan, Argentina
 * FECHA Y HORA: 2026-11-03 23:25:00 -03:00
 * ARCHIVO: domain/src/test/kotlin/com/fenix/studio/domain/credits/UnitBillingTest.kt
 * ================================================================================
 */

package com.fenix.studio.domain.credits

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Decisión de producto: **1 CP por unidad, una sola vez**. Si el docente entrega más
 * material de la misma unidad meses después, ampliarla no vuelve a costar créditos.
 */
class UnitBillingTest {

    @Test
    fun `paying a unit once makes later material of the same unit free`() {
        val ledger = CreditLedger()
        ledger.credit(CreditPocket.PURCHASED, 2L * CreditUnits.PROCESSOR_CREDIT_CC)

        val april = ledger.debitProcessorUnits(
            classroomId = "eco-politica",
            unitIds = listOf("U3"),
            idempotencyKeyPrefix = "eco:abril",
        )
        assertTrue(april is UnitBatchOutcome.Charged)
        assertEquals(400L, (april as UnitBatchOutcome.Charged).costCc)
        assertEquals(400L, ledger.currentBalances().purchasedCc)

        // Mayo: llegan los puntos 3.2 a 3.5 de la misma unidad.
        val may = ledger.debitProcessorUnits(
            classroomId = "eco-politica",
            unitIds = listOf("U3"),
            idempotencyKeyPrefix = "eco:mayo",
        )
        assertTrue(may is UnitBatchOutcome.Charged)
        assertEquals(0L, (may as UnitBatchOutcome.Charged).costCc, "La ampliación no se cobra")
        assertEquals(listOf("U3"), may.alreadyPaidUnits)
        assertEquals(400L, ledger.currentBalances().purchasedCc, "El saldo no se toca")
    }

    @Test
    fun `a batch mixing paid and unpaid units only charges the new ones`() {
        val ledger = CreditLedger()
        ledger.credit(CreditPocket.PURCHASED, 3L * CreditUnits.PROCESSOR_CREDIT_CC)

        ledger.debitProcessorUnits("eco-politica", listOf("U1"), "eco:marzo")

        val plan = ledger.planProcessorUnits("eco-politica", listOf("U1", "U2", "U3"))
        assertEquals(3, plan.requestedUnits)
        assertEquals(1, plan.alreadyPaidUnits)
        assertEquals(2, plan.unpaidUnits)
        assertEquals(2, plan.affordableUnits)
        assertEquals(800L, plan.costCc)

        val outcome = ledger.debitProcessorUnits("eco-politica", listOf("U1", "U2", "U3"), "eco:abril")
        assertTrue(outcome is UnitBatchOutcome.Charged)
        assertEquals(800L, (outcome as UnitBatchOutcome.Charged).costCc)
        assertEquals(listOf("U2", "U3"), outcome.chargedUnits)
        assertEquals(listOf("U1"), outcome.alreadyPaidUnits)
        assertEquals(0L, ledger.currentBalances().purchasedCc, "1200 - 400 de U1 - 800 de U2 y U3")
    }

    @Test
    fun `if the balance does not cover the new units nothing is charged`() {
        val ledger = CreditLedger()
        ledger.credit(CreditPocket.PURCHASED, CreditUnits.PROCESSOR_CREDIT_CC)
        ledger.debitProcessorUnits("eco-politica", listOf("U1"), "eco:uno")

        val outcome = ledger.debitProcessorUnits("eco-politica", listOf("U1", "U2"), "eco:dos")
        assertTrue(outcome is UnitBatchOutcome.Rejected)
        assertEquals(1, (outcome as UnitBatchOutcome.Rejected).plan.unpaidUnits)
        assertEquals(0, outcome.plan.affordableUnits)
        assertEquals(0L, ledger.currentBalances().purchasedCc)
        assertFalse(ledger.isUnitPaid("eco-politica", "U2"))
    }

    @Test
    fun `paid units are tracked per classroom and never leak across them`() {
        val ledger = CreditLedger()
        ledger.credit(CreditPocket.PURCHASED, 4L * CreditUnits.PROCESSOR_CREDIT_CC)

        ledger.debitProcessorUnits("eco-politica", listOf("U1", "U2"), "eco:uno")
        assertTrue(ledger.isUnitPaid("eco-politica", "U1"))
        assertEquals(setOf("U1", "U2"), ledger.paidUnitsOfClassroom("eco-politica"))

        assertFalse(ledger.isUnitPaid("historia", "U1"), "La unidad 1 de otra cátedra no está paga")
        assertEquals(emptySet<String>(), ledger.paidUnitsOfClassroom("historia"))
    }

    @Test
    fun `welcome grant covers the first ten units of a classroom across deliveries`() {
        val ledger = CreditLedger()
        ledger.credit(CreditPocket.GRANT, CreditPolicy.classroomGrantCc(divisions = 10))

        val units = (1..10).map { "U$it" }
        val first = ledger.debitProcessorUnits("eco", units.take(4), "eco:t1") as UnitBatchOutcome.Charged
        assertEquals(0L, first.balancesAfter.purchasedCc, "Todo sale del grant")
        assertEquals(1_600L, ledger.classroomGrantUsedCc("eco"))

        val second = ledger.debitProcessorUnits("eco", units.drop(4), "eco:t2") as UnitBatchOutcome.Charged
        assertEquals(4_000L, ledger.classroomGrantUsedCc("eco"))

        val eleventh = ledger.debitProcessorUnits("eco", listOf("U11"), "eco:t3")
        assertTrue(eleventh is UnitBatchOutcome.Rejected, "La unidad 11 exige saldo propio")
        assertEquals(0L, ledger.currentBalances().procFreeCc)
    }
}
