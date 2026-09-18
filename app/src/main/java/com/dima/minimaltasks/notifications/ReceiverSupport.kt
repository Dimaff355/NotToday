package com.dima.minimaltasks.notifications

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal fun BroadcastReceiver.runBoundedAsync(block: suspend CoroutineScope.() -> Unit) {
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            withTimeout(RECEIVER_TIMEOUT_MILLIS) { block() }
        } catch (_: Throwable) {
            // A receiver must finish even when the database or system service fails.
        } finally {
            pendingResult.finish()
        }
    }
}

private const val RECEIVER_TIMEOUT_MILLIS = 8_000L
