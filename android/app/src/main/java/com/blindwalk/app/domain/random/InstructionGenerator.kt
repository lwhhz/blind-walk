package com.blindwalk.app.domain.random

import com.blindwalk.app.domain.model.InstructionType
import java.util.Random

/**
 * 加权随机。掉头权重被刻意压低（默认 8%），避免用户被连续掉头钉在原地。
 * 没有去重约束：这是刻意的「真随机」，连续同向是允许的。
 */
class InstructionGenerator(private val random: Random = Random()) {

    fun next(weights: Weights = Weights.DEFAULT): InstructionType = weights.pick(random)

    data class Weights(
        val forward: Int,
        val left: Int,
        val right: Int,
        val uTurn: Int
    ) {
        init {
            require(forward > 0 && left > 0 && right > 0 && uTurn > 0) { "所有权重必须为正数" }
        }

        val total: Int get() = forward + left + right + uTurn

        fun pick(random: Random): InstructionType {
            var roll = random.nextInt(total)
            if (roll < forward) return InstructionType.FORWARD
            roll -= forward
            if (roll < left) return InstructionType.LEFT
            roll -= left
            if (roll < right) return InstructionType.RIGHT
            return InstructionType.U_TURN
        }

        fun probabilityOf(type: InstructionType): Double = when (type) {
            InstructionType.FORWARD -> forward.toDouble() / total
            InstructionType.LEFT -> left.toDouble() / total
            InstructionType.RIGHT -> right.toDouble() / total
            InstructionType.U_TURN -> uTurn.toDouble() / total
        }

        companion object {
            val DEFAULT = Weights(forward = 40, left = 26, right = 26, uTurn = 8)
        }
    }
}
