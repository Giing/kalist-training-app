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
