package com.artstudio.core

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Robust uncaught crash handler that captures stack traces, device hardware stats,
 * and memory metrics to persistent disk logs before process death.
 */
class ArtStudioCrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "ArtStudioCrashHandler"
        private const val CRASH_DIR_NAME = "crashes"
        private const val MAX_CRASH_LOGS = 10

        @Volatile
        private var instance: ArtStudioCrashHandler? = null

        /**
         * Installs the crash handler as the default uncaught exception handler.
         */
        fun install(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        val current = Thread.getDefaultUncaughtExceptionHandler()
                        val handler = ArtStudioCrashHandler(context.applicationContext, current)
                        Thread.setDefaultUncaughtExceptionHandler(handler)
                        instance = handler
                        Log.i(TAG, "ArtStudioCrashHandler installed successfully.")
                    }
                }
            }
        }

        fun getCrashDirectory(context: Context): File {
            return File(context.filesDir, CRASH_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
        }

        fun getRecentCrashLogs(context: Context): List<File> {
            val dir = getCrashDirectory(context)
            return dir.listFiles { f -> f.extension == "log" }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            writeCrashLogToDisk(thread, throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing crash log to disk: ${e.message}", e)
        } finally {
            // Forward to platform/original default handler to terminate process cleanly
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLogToDisk(thread: Thread, throwable: Throwable) {
        val crashDir = getCrashDirectory(context)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val logFile = File(crashDir, "crash_$timestamp.log")

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)

        val report = buildString {
            appendLine("=== ARTSTUDIO CRASH REPORT ===")
            appendLine("Timestamp: $timestamp")
            appendLine("Thread: ${thread.name} (id: ${thread.id})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
            appendLine("Heap Usage: ${usedMemMb}MB / ${maxMemMb}MB")
            appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
            appendLine("--- STACK TRACE ---")
            appendLine(stackTrace)
            appendLine("==============================")
        }

        logFile.writeText(report)
        Log.e(TAG, "Crash report written to: ${logFile.absolutePath}")

        // Prune old logs to conserve disk space
        val allLogs = getRecentCrashLogs(context)
        if (allLogs.size > MAX_CRASH_LOGS) {
            allLogs.drop(MAX_CRASH_LOGS).forEach { it.delete() }
        }
    }
}
