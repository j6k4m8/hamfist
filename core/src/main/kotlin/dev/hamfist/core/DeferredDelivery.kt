package dev.hamfist.core

/** An asynchronous lookup never refreshes the age or authorization of its original message. */
class DeferredDelivery(
    val sentAtMillis: Long,
    private val startedAtElapsedMs: Long,
    private val validForMs: Long,
    private val stillAllowed: () -> Boolean
) {
    fun canDeliver(nowElapsedMs: Long): Boolean =
        nowElapsedMs - startedAtElapsedMs in 0..validForMs && stillAllowed()
}
