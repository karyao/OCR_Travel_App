package com.karen_yao.chinesetravel.shared.utils

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationUtilsTest {
    @Test(timeout = TEST_TIMEOUT)
    fun completedTasksReturnTranslationAndCloseOnce() = withFixture {
        client.download.setResult(null)
        client.translation.setResult("Restaurant")
        assertEquals("Restaurant", translate())
        assertEquals(listOf("download", "translate:餐厅", "close"), client.events)
        assertEquals(1, factoryCalls)
        assertEquals(1, client.closeCount)
    }

    @Test(timeout = TEST_TIMEOUT)
    fun pendingTasksCompleteInOrderAndKeepClientOpenUntilFinished() = withFixture {
        start()
        awaitPending(Stage.DOWNLOAD)
        assertEquals(listOf("download"), client.events)
        client.download.setResult(null)
        awaitPending(Stage.TRANSLATION)
        assertEquals(listOf("download", "translate:餐厅"), client.events)
        client.translation.setResult("Restaurant")
        assertEquals("Restaurant", withTimeout(PHASE_TIMEOUT) { worker!!.await() })
        assertEquals("Restaurant", returned)
        assertNull(cancellation)
        assertEquals(listOf("download", "translate:餐厅", "close"), client.events)
        assertEquals(1, client.closeCount)
    }

    @Test(timeout = TEST_TIMEOUT)
    fun completedDownloadFailureReturnsFallbackAndCloses() = taskFailure(Stage.DOWNLOAD, pending = false)

    @Test(timeout = TEST_TIMEOUT)
    fun pendingDownloadFailureReturnsFallbackAndCloses() = taskFailure(Stage.DOWNLOAD, pending = true)

    @Test(timeout = TEST_TIMEOUT)
    fun completedTranslationFailureReturnsFallbackAndCloses() = taskFailure(Stage.TRANSLATION, pending = false)

    @Test(timeout = TEST_TIMEOUT)
    fun pendingTranslationFailureReturnsFallbackAndCloses() = taskFailure(Stage.TRANSLATION, pending = true)

    @Test(timeout = TEST_TIMEOUT)
    fun downloadCancellationClosesBeforeLateSuccess() = coroutineCancellation(Stage.DOWNLOAD, lateFailure = false)

    @Test(timeout = TEST_TIMEOUT)
    fun downloadCancellationClosesBeforeLateFailure() = coroutineCancellation(Stage.DOWNLOAD, lateFailure = true)

    @Test(timeout = TEST_TIMEOUT)
    fun translationCancellationClosesBeforeLateSuccess() = coroutineCancellation(Stage.TRANSLATION, lateFailure = false)

    @Test(timeout = TEST_TIMEOUT)
    fun translationCancellationClosesBeforeLateFailure() = coroutineCancellation(Stage.TRANSLATION, lateFailure = true)

    @Test(timeout = TEST_TIMEOUT)
    fun downloadTaskCancellationPropagatesAndCloses() = taskCancellation(Stage.DOWNLOAD)

    @Test(timeout = TEST_TIMEOUT)
    fun translationTaskCancellationPropagatesAndCloses() = taskCancellation(Stage.TRANSLATION)

    @Test(timeout = TEST_TIMEOUT)
    fun synchronousDownloadFailureReturnsFallbackAndCloses() = synchronousFailure(Stage.DOWNLOAD)

    @Test(timeout = TEST_TIMEOUT)
    fun synchronousTranslationFailureReturnsFallbackAndCloses() = synchronousFailure(Stage.TRANSLATION)

    @Test(timeout = TEST_TIMEOUT)
    fun blankInputDoesNotCreateOrCloseClient() = withFixture {
        for (text in listOf("", " \t\n")) {
            assertEquals("No text", translate(text))
        }
        assertEquals(0, factoryCalls)
        assertEquals(0, client.closeCount)
        assertTrue(client.events.isEmpty())
        // Also exercise the public entry point without creating a real client.
        assertEquals("No text", TranslationUtils.translateChineseToEnglish(""))
    }

    @Test(timeout = TEST_TIMEOUT)
    fun factoryFailureReturnsFallbackWithoutClosingClient() = withFixture {
        factoryFailure = IllegalStateException("client unavailable")
        assertEquals("Translation unavailable (fallback)", translate("未收录的词"))
        assertEquals(1, factoryCalls)
        assertEquals(0, client.closeCount)
        assertTrue(client.events.isEmpty())
    }

    @Test(timeout = TEST_TIMEOUT)
    fun factoryCancellationPropagatesWithoutClosingClient() = withFixture {
        val expected = CancellationException("client creation cancelled")
        factoryFailure = expected
        start()
        withTimeout(PHASE_TIMEOUT) { worker!!.join() }
        assertSame(expected, cancellation)
        assertNull(returned)
        assertEquals(1, factoryCalls)
        assertEquals(0, client.closeCount)
        assertTrue(client.events.isEmpty())
    }

    private fun taskFailure(stage: Stage, pending: Boolean) = withFixture {
        if (stage == Stage.TRANSLATION) client.download.setResult(null)
        if (pending) {
            start()
            awaitPending(stage)
            failTask(stage)
            assertEquals("Restaurant (fallback)", withTimeout(PHASE_TIMEOUT) { worker!!.await() })
        } else {
            failTask(stage)
            assertEquals("Restaurant (fallback)", translate())
        }
        assertClosedAt(stage)
    }

    private fun synchronousFailure(stage: Stage) = withFixture {
        if (stage == Stage.DOWNLOAD) {
            client.downloadFailure = IllegalStateException("download invocation failed")
        } else {
            client.download.setResult(null)
            client.translationFailure = IllegalStateException("translation invocation failed")
        }
        assertEquals("Restaurant (fallback)", translate())
        assertClosedAt(stage)
    }

    private fun coroutineCancellation(stage: Stage, lateFailure: Boolean) = withFixture {
        if (stage == Stage.TRANSLATION) client.download.setResult(null)
        start()
        awaitPending(stage)
        withTimeout(PHASE_TIMEOUT) { worker!!.cancelAndJoin() }
        // Observe the adapter's outcome: a cancelled Deferred alone also masks a fallback return.
        assertNotNull(cancellation)
        assertNull(returned)
        assertFalse(task(stage).isComplete)
        assertClosedAt(stage)
        val eventsAtExit = client.events.toList()
        val completed = completionSignal(task(stage))
        if (lateFailure) failTask(stage) else completeTask(stage)
        withTimeout(PHASE_TIMEOUT) { completed.await() }
        yield()
        assertEquals(eventsAtExit, client.events)
        assertNull(returned)
        assertClosedAt(stage)
    }

    private fun taskCancellation(stage: Stage) = withFixture {
        if (stage == Stage.TRANSLATION) client.download.setResult(null)
        start()
        awaitPending(stage)
        val completed = completionSignal(task(stage))
        // CancellationTokenSource dispatches through Android's main thread; do not assert immediately.
        if (stage == Stage.DOWNLOAD) client.downloadCancellation.cancel() else client.translationCancellation.cancel()
        withTimeout(PHASE_TIMEOUT) {
            completed.await()
            worker!!.join()
        }
        assertTrue(task(stage).isCanceled)
        assertNotNull(cancellation)
        assertNull(returned)
        assertClosedAt(stage)
    }

    private fun withFixture(test: suspend Fixture.() -> Unit): Unit = runBlocking {
        assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
        supervisorScope {
            val fixture = Fixture(this)
            try {
                fixture.test()
            } finally {
                // Release pending tasks even when an assertion or timeout fails, before joining children.
                fixture.client.download.trySetResult(null)
                fixture.client.translation.trySetResult("cleanup")
                withContext(NonCancellable) {
                    withTimeout(PHASE_TIMEOUT) { fixture.worker?.cancelAndJoin() }
                }
            }
        }
    }

    private class Fixture(private val scope: CoroutineScope) {
        val client = RecordingTranslator()
        var factoryCalls = 0
        var factoryFailure: Exception? = null
        var worker: Deferred<String>? = null
        var returned: String? = null
        var cancellation: CancellationException? = null

        suspend fun translate(text: String = "餐厅"): String =
            TranslationUtils.translateChineseToEnglish(text) { options ->
                factoryCalls++
                val expected = TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.CHINESE)
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build()
                assertEquals(expected, options)
                factoryFailure?.let { throw it }
                client
            }

        fun start() {
            check(worker == null)
            worker = scope.async(start = CoroutineStart.UNDISPATCHED) {
                try {
                    translate().also { returned = it }
                } catch (error: CancellationException) {
                    cancellation = error
                    throw error
                }
            }
        }

        suspend fun awaitPending(stage: Stage) {
            withTimeout(PHASE_TIMEOUT) {
                if (stage == Stage.DOWNLOAD) client.downloadStarted.await() else client.translationStarted.await()
            }
            yield()
            assertFalse(task(stage).isComplete)
            assertFalse(worker!!.isCompleted)
            assertEquals(0, client.closeCount)
        }

        fun task(stage: Stage): Task<*> = if (stage == Stage.DOWNLOAD) client.download.task else client.translation.task

        fun failTask(stage: Stage) {
            val error = IllegalStateException("task failed")
            if (stage == Stage.DOWNLOAD) client.download.setException(error) else client.translation.setException(error)
        }

        fun completeTask(stage: Stage) {
            if (stage == Stage.DOWNLOAD) client.download.setResult(null) else client.translation.setResult("late result")
        }

        fun assertClosedAt(stage: Stage) {
            val expected = if (stage == Stage.DOWNLOAD) listOf("download", "close")
            else listOf("download", "translate:餐厅", "close")
            assertEquals(expected, client.events)
            assertEquals(1, client.closeCount)
        }
    }

    private class RecordingTranslator : Translator {
        val downloadCancellation = CancellationTokenSource()
        val translationCancellation = CancellationTokenSource()
        val download = TaskCompletionSource<Void>(downloadCancellation.token)
        val translation = TaskCompletionSource<String>(translationCancellation.token)
        val downloadStarted = CompletableDeferred<Unit>()
        val translationStarted = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var downloadFailure: Exception? = null
        var translationFailure: Exception? = null
        var closeCount = 0

        override fun downloadModelIfNeeded(): Task<Void> {
            events += "download"
            downloadStarted.complete(Unit)
            downloadFailure?.let { throw it }
            return download.task
        }

        override fun downloadModelIfNeeded(conditions: DownloadConditions): Task<Void> =
            error("The adapter should use downloadModelIfNeeded()")

        override fun translate(text: String): Task<String> {
            events += "translate:$text"
            translationStarted.complete(Unit)
            translationFailure?.let { throw it }
            return translation.task
        }

        override fun close() {
            events += "close"
            closeCount++
        }
    }

    private enum class Stage { DOWNLOAD, TRANSLATION }

    companion object {
        private const val PHASE_TIMEOUT = 5_000L
        private const val TEST_TIMEOUT = 30_000L
        private val directExecutor = Executor { it.run() }

        private fun completionSignal(task: Task<*>): CompletableDeferred<Unit> =
            CompletableDeferred<Unit>().also { signal ->
                task.addOnCompleteListener(directExecutor) { signal.complete(Unit) }
            }
    }
}
