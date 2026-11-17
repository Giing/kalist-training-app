package com.kalist.training.domain

/**
 * A session is run as a circuit: round 1 goes through every exercise in order, then round 2, ...
 * An exercise with fewer target sets than the number of rounds drops out of later rounds.
 */
data class CircuitStep(
    /** Index into the session's ordered exercise list. */
    val exerciseIndex: Int,
    /** 1-based round, which is also the set index for that exercise. */
    val round: Int,
)

object Circuit {
    fun totalRounds(targetSets: List<Int>): Int = targetSets.maxOrNull() ?: 0

    fun steps(targetSets: List<Int>): List<CircuitStep> {
        val rounds = totalRounds(targetSets)
        return buildList {
            for (round in 1..rounds) {
                targetSets.forEachIndexed { index, sets -> if (round <= sets) add(CircuitStep(index, round)) }
            }
        }
    }

    /** True when moving from [current] to [next] starts a new round (rest between rounds). */
    fun isRoundBoundary(current: CircuitStep, next: CircuitStep?): Boolean =
        next != null && next.round != current.round
}
