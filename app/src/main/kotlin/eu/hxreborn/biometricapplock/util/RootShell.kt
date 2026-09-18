package eu.hxreborn.biometricapplock.util

import android.util.Log
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object RootShell {
    data class Result(
        val code: Int,
        val out: List<String>,
        val timedOut: Boolean = false,
    )

    private val FAILURE = Result(-1, emptyList())

    private const val STDERR_JOIN_MS = 2000L

    fun exec(
        vararg commands: String,
        timeoutMs: Long = 5000L,
    ): Result =
        runCatching { runShell(commands, timeoutMs) }
            .onFailure {
                Log.w(
                    Logger.TAG,
                    "su exec failed: ${it.message}",
                    it,
                )
            }.getOrDefault(FAILURE)

    fun isRootGranted(): Boolean = exec("true").code == 0

    private fun runShell(
        commands: Array<out String>,
        timeoutMs: Long,
    ): Result {
        val process = ProcessBuilder("su").start()

        val timedOut = AtomicBoolean(false)
        val watchdog =
            Thread {
                runCatching {
                    if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                        timedOut.set(true)
                        process.destroyForcibly()
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }

        val drainErr =
            Thread {
                runCatching { process.errorStream.bufferedReader().forEachLine { } }
            }.apply {
                isDaemon = true
                start()
            }

        val marker = "BAL_DONE_${UUID.randomUUID()}"
        runCatching {
            process.outputStream.bufferedWriter().use { stdin ->
                commands.forEach { stdin.appendLine(it) }
                stdin.appendLine("echo $marker $?")
                stdin.appendLine("exit")
            }
        }

        val out = ArrayList<String>()
        var code = -1
        val reader = process.inputStream.bufferedReader()
        while (true) {
            val line = reader.readLine() ?: break
            val idx = line.indexOf(marker)
            if (idx >= 0) {
                code = line.substring(idx + marker.length).trim().toIntOrNull() ?: -1
                break
            } else {
                out += line
            }
        }

        drainErr.join(STDERR_JOIN_MS)
        watchdog.interrupt()
        process.waitFor()
        return Result(code, out, timedOut.get())
    }
}
