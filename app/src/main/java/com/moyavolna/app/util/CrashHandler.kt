package com.moyavolna.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.moyavolna.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Глобальный обработчик необработанных исключений: при крэше пишет отчёт в
 * публичную папку Download/letaa-logi/, чтобы его было удобно найти из
 * проводника и переслать. UI не показывает — работает молча.
 *
 * Имя файла: crash_yyyy-MM-dd_HH-mm-ss.txt
 */
object CrashHandler {

    private const val TAG = "CrashHandler"
    const val LOG_DIR_NAME = "letaa-logi"

    fun install(context: Context) {
        val app = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildReport(throwable, thread)
                writeReport(app, report)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to save crash report", t)
            }
            prev?.uncaughtException(thread, throwable)
        }
    }

    private fun writeReport(context: Context, report: String) {
        val fileName = "crash_" + SimpleDateFormat(
            "yyyy-MM-dd_HH-mm-ss", Locale.US
        ).format(Date()) + ".txt"

        // Android 10+: пишем в публичную папку Download через MediaStore.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + LOG_DIR_NAME
                )
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(report.toByteArray(Charsets.UTF_8)) }
                Log.i(TAG, "Crash saved to Download/$LOG_DIR_NAME/$fileName")
                return
            }
        }

        // Fallback (Android 9 и ниже, либо если MediaStore не сработал):
        // пишем напрямую в /storage/emulated/0/Download/letaa-logi/.
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            LOG_DIR_NAME
        )
        if (!dir.exists()) dir.mkdirs()
        val out = File(dir, fileName)
        out.writeText(report, Charsets.UTF_8)
        Log.i(TAG, "Crash saved to ${out.absolutePath}")
    }

    private fun buildReport(throwable: Throwable, thread: Thread): String {
        val sw = StringWriter()
        PrintWriter(sw).use { throwable.printStackTrace(it) }
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("MoyaVolna crash report")
            appendLine("Time: $ts")
            appendLine("App: ${BuildConfig.APPLICATION_ID} v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build: ${BuildConfig.BUILD_TYPE}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine("--- Stacktrace ---")
            append(sw.toString())
        }
    }
}
