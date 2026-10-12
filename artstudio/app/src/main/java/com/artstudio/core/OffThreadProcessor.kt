package com.artstudio.core

import kotlinx.coroutines.*

/**
 * Narrow interface for off-thread background job dispatching.
 * Guarantees zero blocking of the Android main/render thread.
 */
interface OffThreadProcessor {
    fun <T> executeAsync(block: suspend CoroutineScope.() -> T): Job
    fun cancelAll()
}

class DefaultOffThreadProcessor(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : OffThreadProcessor {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    override fun <T> executeAsync(block: suspend CoroutineScope.() -> T): Job {
        return scope.launch {
            block()
        }
    }

    override fun cancelAll() {
        scope.coroutineContext.cancelChildren()
    }
}
