package com.hirolifecreative.floatingmap

import android.content.Context
import android.util.AtomicFile
import java.io.File

/** One map-process writer; fresh reads across processes. Never contains addresses or tokens. */
object DasherLookupLog {
    private fun file(context: Context)=AtomicFile(File(context.filesDir,"dasher-lookup-status.txt"))
    fun record(context: Context,message: String) {
        val atomic=file(context)
        var stream: java.io.FileOutputStream?=null
        try {
            stream=atomic.startWrite()
            val time=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.ROOT).format(java.util.Date())
            stream.write("[$time] Última búsqueda Dasher: $message".toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (_: Exception) { stream?.let { atomic.failWrite(it) } }
    }
    fun read(context: Context): String=try { file(context).openRead().bufferedReader().use { it.readText() } } catch (_: Exception) { "Sin búsquedas de Dasher registradas" }
    fun clear(context: Context) { file(context).delete() }
}
