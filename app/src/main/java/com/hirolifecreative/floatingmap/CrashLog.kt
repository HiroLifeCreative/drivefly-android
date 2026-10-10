package com.hirolifecreative.floatingmap

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Guarda el stack trace de cualquier crash no atrapado en un archivo interno. */
object CrashLog {
    private const val NAME = "crash_log.txt"
    private var installed = false

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val f = File(ctx.filesDir, NAME)
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                f.writeText("[$ts] Thread: ${t.name}\n${e.javaClass.name}: ${e.message}\n${sw}\n")
            } catch (_: Exception) {
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun read(ctx: Context): String {
        return try {
            val f = File(ctx.filesDir, NAME)
            if (f.exists()) f.readText().take(4000) else "(sin errores registrados)"
        } catch (_: Exception) {
            "(no se pudo leer)"
        }
    }

    /** Guarda un error atrapado manualmente */
    fun save(ctx: Context, t: Throwable) {
        try {
            val f = File(ctx.filesDir, NAME)
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val sw = java.io.StringWriter()
            t.printStackTrace(java.io.PrintWriter(sw))
            f.writeText("[$ts] (atrapado manual)\n${t.javaClass.name}: ${t.message}\n${sw}\n")
        } catch (_: Exception) {
        }
    }

    fun clear(ctx: Context) {
        try { File(ctx.filesDir, NAME).delete() } catch (_: Exception) {}
    }
}
