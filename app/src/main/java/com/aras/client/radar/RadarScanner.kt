package com.aras.client.radar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface RadarEvent {
    /** پایان دریافت یک منبع؛ [error] اگر null نباشد یعنی ناموفق بود. */
    data class SourceDone(val source: RadarSource, val count: Int, val error: String?) : RadarEvent

    /** پایان مرحله‌ی دریافت: [raw] کل لینک‌های خام، [unique] تعداد یکتا برای آزمایش. */
    data class Planned(val raw: Int, val unique: Int) : RadarEvent

    data class Result(val result: RadarResult) : RadarEvent
}

object RadarScanner {
    /**
     * منابع را موازی می‌گیرد، یکتا می‌کند، بین منابع منصفانه (round-robin) می‌چیند تا سقف [limit]،
     * و بعد با [concurrency] کار هم‌زمان می‌سنجد. نتیجه‌ها همان لحظه که آماده شدند می‌رسند.
     */
    fun scan(
        sources: List<RadarSource>,
        limit: Int,
        deep: Boolean,
        concurrency: Int = 24,
    ): Flow<RadarEvent> = channelFlow {
        val fetchGate = Semaphore(5)
        val fetched = sources.map { src ->
            async(Dispatchers.IO) {
                val r = fetchGate.withPermit { RadarFetcher.fetch(src) }
                this@channelFlow.send(RadarEvent.SourceDone(src, r.uris.size, r.error))
                src to r.uris
            }
        }.awaitAll()

        val queues = fetched.map { (src, uris) ->
            ArrayDeque(if (src.ordered) uris else uris.shuffled()) to src
        }
        val seen = HashSet<String>()
        val plan = ArrayList<Pair<ParsedConfig, RadarSource>>()
        var progress = true
        while (plan.size < limit && progress) {
            progress = false
            for ((queue, src) in queues) {
                while (queue.isNotEmpty()) {
                    val parsed = RadarParser.parse(queue.removeFirst()) ?: continue
                    if (!seen.add(parsed.key)) continue
                    plan.add(parsed to src)
                    progress = true
                    break
                }
                if (plan.size >= limit) break
            }
        }
        send(RadarEvent.Planned(fetched.sumOf { it.second.size }, plan.size))

        val gate = Semaphore(concurrency)
        for ((cfg, src) in plan) {
            launch(Dispatchers.IO) {
                gate.withPermit {
                    val r = RadarProbe.probe(cfg, deep, src.title, src.country)
                    this@channelFlow.send(RadarEvent.Result(r))
                }
            }
        }
    }
}
