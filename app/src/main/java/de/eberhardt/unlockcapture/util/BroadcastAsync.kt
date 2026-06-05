package de.eberhardt.unlockcapture.util

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

object BroadcastAsync {
    fun launch(
        pendingResult: BroadcastReceiver.PendingResult,
        tag: String,
        block: suspend () -> Unit,
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                AppLog.e(tag, "Async broadcast handling failed", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
