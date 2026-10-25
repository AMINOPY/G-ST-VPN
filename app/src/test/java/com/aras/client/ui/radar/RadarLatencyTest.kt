package com.aras.client.ui.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadarLatencyTest {
    @Test
    fun medianUsesMiddleOfOddSampleCount() {
        assertEquals(9L, RadarLatency.medianMillis(listOf(15L, 3L, 9L)))
    }

    @Test
    fun medianAveragesEvenSampleCount() {
        assertEquals(5L, RadarLatency.medianMillis(listOf(10L, 2L, 6L, 4L)))
    }

    @Test
    fun medianIgnoresNegativeInvalidSamples() {
        assertEquals(50L, RadarLatency.medianMillis(listOf(100L, -1L, 0L)))
    }

    @Test
    fun medianReturnsNullWithoutSuccessfulSamples() {
        assertNull(RadarLatency.medianMillis(emptyList()))
        assertNull(RadarLatency.medianMillis(listOf(-1L, -2L)))
    }
}
