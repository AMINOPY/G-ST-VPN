package com.aras.client.dto

sealed class RealPingEvent {

    /** Periodic progress update while the batch is still running. */
    data class Progress(val text: String) : RealPingEvent()

    /** A single server result is available. Country/IP are set when the
     *  measurement carried egress info back through the core dial path. */
    data class Result(
        val guid: String,
        val delayMillis: Long,
        val country: String? = null,
        val ipAddress: String? = null,
    ) : RealPingEvent()

    /** The entire batch has finished or been cancelled. */
    data class Finish(val status: String) : RealPingEvent()
}
