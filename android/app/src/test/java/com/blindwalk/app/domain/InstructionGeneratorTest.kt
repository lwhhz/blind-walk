package com.blindwalk.app.domain

import com.blindwalk.app.domain.model.InstructionType
import com.blindwalk.app.domain.random.InstructionGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class InstructionGeneratorTest {

    @Test
    fun `default weights sum to 100`() {
        assertEquals(100, InstructionGenerator.Weights.DEFAULT.total)
    }

    @Test
    fun `u-turn is deliberately rare`() {
        val p = InstructionGenerator.Weights.DEFAULT.probabilityOf(InstructionType.U_TURN)
        assertTrue("u-turn probability $p should stay under 12%", p < 0.12)
    }

    @Test
    fun `seeded distribution matches weights`() {
        val generator = InstructionGenerator(Random(20261026L))
        val counts = mutableMapOf<InstructionType, Int>()
        val draws = 40_000
        repeat(draws) {
            val t = generator.next()
            counts[t] = (counts[t] ?: 0) + 1
        }

        val weights = InstructionGenerator.Weights.DEFAULT
        InstructionType.entries.forEach { type ->
            val observed = (counts[type] ?: 0).toDouble() / draws
            val expected = weights.probabilityOf(type)
            assertEquals("$type distribution off", expected, observed, 0.012)
        }
    }

    @Test
    fun `all four outcomes are reachable`() {
        val generator = InstructionGenerator(Random(7L))
        val seen = HashSet<InstructionType>()
        repeat(2_000) { seen.add(generator.next()) }
        assertEquals(InstructionType.entries.toSet(), seen)
    }
}
