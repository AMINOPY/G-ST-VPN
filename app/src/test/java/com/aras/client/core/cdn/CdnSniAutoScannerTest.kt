package com.aras.client.core.cdn

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CdnSniAutoScannerTest {

    @Test
    fun `completed candidates are collected before the shared deadline`() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            val completion = ExecutorCompletionService<String>(executor)
            completion.submit<String> { "first" }
            completion.submit<String> { "second" }
            val results = mutableListOf<String>()

            val allCompleted = CdnSniAutoScanner.collectWithinDeadline(
                completion = completion,
                taskCount = 2,
                timeoutMs = 2_000,
            ) { results += it }

            assertTrue(allCompleted)
            assertEquals(setOf("first", "second"), results.toSet())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `slow candidates share one deadline instead of timing out one by one`() {
        val executor = Executors.newFixedThreadPool(2)
        val bothStarted = CountDownLatch(2)
        val completion = ExecutorCompletionService<Int>(executor)
        val futures = mutableListOf<java.util.concurrent.Future<Int>>()
        try {
            futures += completion.submit<Int> {
                bothStarted.countDown()
                Thread.sleep(5_000)
                0
            }
            futures += completion.submit<Int> {
                bothStarted.countDown()
                7
            }
            assertTrue("Both workers should start before the deadline test", bothStarted.await(1, TimeUnit.SECONDS))

            val startedAt = System.nanoTime()
            val results = mutableListOf<Int>()
            val allCompleted = CdnSniAutoScanner.collectWithinDeadline(
                completion = completion,
                taskCount = 2,
                timeoutMs = 200,
            ) { results += it }
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

            assertFalse(allCompleted)
            assertEquals(listOf(7), results)
            assertTrue("The collector exceeded its single deadline: ${elapsedMs}ms", elapsedMs < 1_500)
        } finally {
            futures.forEach { it.cancel(true) }
            executor.shutdownNow()
        }
    }
}
