package com.aras.client.ui.radar

/** Utilities for ranking measured network latency samples. */
internal object RadarLatency {
    /**
     * Returns the median of non-negative measured samples, or null when no sample succeeded.
     * For an even sample count, the midpoint is rounded down to an integer millisecond.
     */
    fun medianMillis(samples: List<Long>): Long? {
        val sorted = samples.filter { it >= 0L }.sorted()
        if (sorted.isEmpty()) return null
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            val lower = sorted[middle - 1]
            val upper = sorted[middle]
            lower + (upper - lower) / 2L
        }
    }
}
