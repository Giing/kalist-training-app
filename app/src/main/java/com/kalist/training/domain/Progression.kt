package com.kalist.training.domain

object Progression {
    /**
     * The "try the harder variant" hint: the previous session reached the top of the target range
     * on every target set.
     */
    fun shouldSuggestHarder(previousValues: List<Int>, targetSets: Int, targetMax: Int): Boolean {
        if (targetSets <= 0 || targetMax <= 0) return false
        if (previousValues.size < targetSets) return false
        return previousValues.take(targetSets).all { it >= targetMax }
    }
}

object Prefill {
    /**
     * Value shown in the set stepper: what was already logged for this set, else the value of the
     * same set last time, else the last set last time, else the low end of the target range.
     */
    fun value(logged: Int?, previousValues: List<Int>, setIndex: Int, targetMin: Int): Int =
        logged ?: previousValues.getOrNull(setIndex - 1) ?: previousValues.lastOrNull() ?: targetMin
}
