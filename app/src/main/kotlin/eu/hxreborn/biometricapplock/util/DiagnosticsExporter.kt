package eu.hxreborn.biometricapplock.util

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsExporter {
    private const val MAX_LINES = 20000

    private const val CONTACT_EMAIL = "hxreborn@duck.com"

    // system_server hooks log to LSPosed's own file while the module process logs to logcat
    // -b all reads every logcat buffer so nothing is missed if a line lands off the main one
    private const val HOOK_LOG_COMMAND =
        "( grep -h ${Logger.TAG} /data/adb/lspd/log.old/verbose_*.log; " +
            "grep -h ${Logger.TAG} /data/adb/lspd/log/verbose_*.log ) 2>/dev/null"

    private const val LOGCAT_COMMAND = "logcat -d -b all -s ${Logger.TAG} 2>/dev/null"

    private const val CRASH_LOG_COMMAND = "logcat -d -b crash 2>/dev/null"

    class NoLogsException : Exception()

    suspend fun export(
        context: Context,
        framework: String?,
    ): File =
        withContext(Dispatchers.IO) {
            val hookLog = RootShell.exec(HOOK_LOG_COMMAND)
            val appLog = RootShell.exec(LOGCAT_COMMAND)
            val crashRaw = RootShell.exec(CRASH_LOG_COMMAND)
            val crashLog = crashRaw.copy(out = ownCrashes(crashRaw.out))
            if (hookLog.out.isEmpty() && appLog.out.isEmpty() && crashLog.out.isEmpty()) {
                throw NoLogsException()
            }
            val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            File(dir, "biometricapplock-$stamp.log").apply {
                writeText(body(framework, hookLog, appLog, crashLog))
            }
        }

    // SAF write to a user-picked location keeps the export permission-free
    suspend fun saveTo(
        context: Context,
        file: File,
        destination: Uri,
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(destination)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: return@withContext false
                true
            }.getOrDefault(false)
        }

    fun share(
        context: Context,
        file: File,
    ) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = ClipDescription.MIMETYPE_TEXT_PLAIN
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, "BiometricAppLock ${BuildConfig.VERSION_NAME} logs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.diagnostics_share_title)),
        )
    }

    private fun body(
        framework: String?,
        hookLog: RootShell.Result,
        appLog: RootShell.Result,
        crashLog: RootShell.Result,
    ): String =
        buildString {
            val capturedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz", Locale.US).format(Date())
            appendLine("BiometricAppLock diagnostics")
            appendLine("captured $capturedAt")
            appendLine(
                "version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}",
            )
            appendLine("package ${BuildConfig.APPLICATION_ID}")
            appendLine("device ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android ${Build.VERSION.RELEASE} sdk ${Build.VERSION.SDK_INT}")
            appendLine("build ${Build.FINGERPRINT}")
            appendLine("xposed framework: ${framework ?: "unknown"}")
            appendSection("module process crashes", crashLog)
            appendSection("system_server hooks (LSPosed log)", hookLog)
            appendSection("module process (logcat)", appLog)
        }

    private fun ownCrashes(lines: List<String>): List<String> {
        val starts = lines.indices.filter { lines[it].contains("FATAL EXCEPTION") }
        val bounds = starts + lines.size
        return starts.indices
            .map { lines.subList(bounds[it], bounds[it + 1]) }
            .filter { block -> block.any { it.contains(BuildConfig.APPLICATION_ID) } }
            .flatten()
    }

    private fun StringBuilder.appendSection(
        title: String,
        result: RootShell.Result,
    ) {
        val lines = result.out
        appendLine("---- $title ----")
        if (result.timedOut) appendLine("(collection timed out, output may be incomplete)")
        if (lines.size > MAX_LINES) {
            appendLine("(showing last $MAX_LINES of ${lines.size} lines, older entries omitted)")
        }
        val shown = lines.takeLast(MAX_LINES)
        if (shown.isEmpty()) appendLine("(none)") else shown.forEach { appendLine(it) }
    }
}
