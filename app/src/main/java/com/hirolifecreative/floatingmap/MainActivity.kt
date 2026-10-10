package com.hirolifecreative.floatingmap

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Recovery stays in the main process; the native map runs in :map. */
class MainActivity : Activity() {
    private lateinit var report: TextView
    private var pendingDasher: Intent? = null
    private val sessionHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sessionTimeout = Runnable {
        pendingDasher = null
        android.widget.Toast.makeText(this,"El flotante no confirmó el inicio. Consulta el diagnóstico.",android.widget.Toast.LENGTH_LONG).show()
        showReport()
    }
    private val sessionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?,intent: Intent?) {
            if (intent?.action != "com.hirolifecreative.floatingmap.DASHER_SESSION_READY") return
            val launch = pendingDasher ?: return
            pendingDasher = null;sessionHandler.removeCallbacks(sessionTimeout)
            if (!isFinishing && !isDestroyed) { startActivity(launch);if(intent.getBooleanExtra("start_dasher",false)) finish() }
        }
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 15f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Appearance.text(this@MainActivity,Color.parseColor(DriveFlyUi.TEXT)))
        setPadding(0,dp(8),0,dp(8))
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        CrashLog.install(this)
        androidx.core.content.ContextCompat.registerReceiver(this,sessionReceiver,
            android.content.IntentFilter("com.hirolifecreative.floatingmap.DASHER_SESSION_READY"),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        val ui=SettingsUi.Page(this,"DriveFly")
        val group=ui.section(if(intent.getBooleanExtra("start_dasher",false)) "Preparando tu turno" else "Abrir el mapa")
        SettingsUi.body(group).addView(SettingsUi.text(this,"Tu mapa y tus entregas, en un solo lugar.",14f,secondary=true))
        SettingsUi.row(group,"Continuar al mapa","map") { openMap(compatible=true) }
        SettingsUi.row(group,"Iniciar con Dasher","bag") { startDasherSession() }
        val support=ui.section("Accesos")
        SettingsUi.row(support,"Configuración","gear") { startActivity(Intent(this,SettingsActivity::class.java)) }
        SettingsUi.row(support,"Diagnóstico","info") { startActivity(Intent(this,SettingsActivity::class.java).putExtra("page","diagnostic")) }
        report=ui.note("")
        if(state==null) {
            if(intent.getBooleanExtra("start_dasher",false)) sessionHandler.post { startDasherSession() }
            else if(!getSharedPreferences("mapa_flotante",MODE_PRIVATE).getBoolean("map_start_pending",false)) openMap()
            else report.text="El mapa no completó el último inicio. Puedes continuar en modo compatible o revisar el diagnóstico."
        }
    }
    private fun openMap(compatible: Boolean=false) {
        if(compatible) {
            MapExperience.save(this,MapExperience.read(this).copy(enabled=false))
            CameraPreferences.save(this,CameraPreferences.reload(this).copy(nativePuck3D=false))
            getSharedPreferences("mapa_flotante",MODE_PRIVATE).edit().putBoolean("advanced_map",false).putBoolean("map_start_pending",false).commit()
        }
        startActivity(Intent().setClassName(packageName,"$packageName.NavigationActivity"))
    }
    override fun onResume() { super.onResume();SettingsUi.chrome(this) }
    override fun onPause() {
        pendingDasher = null;sessionHandler.removeCallbacks(sessionTimeout)
        super.onPause()
    }
    override fun onDestroy() {
        sessionHandler.removeCallbacks(sessionTimeout)
        try { unregisterReceiver(sessionReceiver) } catch (_: Exception) { }
        super.onDestroy()
    }
    private fun startDasherSession() {
        fun notice(message: String) = android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show()
        if (!getSharedPreferences("mapa_flotante",MODE_PRIVATE).getString("mapbox_token","").orEmpty().startsWith("pk.")) {
            notice("Configura tu token de Mapbox para abrir el flotante")
            startActivity(Intent().setClassName(packageName,"$packageName.SettingsActivity"));return
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            notice("Permite que DriveFly se muestre sobre otras apps")
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:$packageName")));return
        }
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION),101);return
        }
        val enabled = android.provider.Settings.Secure.getString(contentResolver,android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val reader = android.content.ComponentName(packageName,"$packageName.DashAddressService")
        if (enabled.split(':').none { android.content.ComponentName.unflattenFromString(it) == reader }) {
            notice("Activa DriveFly en Accesibilidad y vuelve a pulsar Iniciar con Dasher")
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));return
        }
        val dasher = DasherAutomation.packages.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (dasher == null) { notice("Instala la app Dasher para iniciar tu turno");return }
        getSharedPreferences("mapa_flotante",MODE_PRIVATE).edit().putBoolean("dasher_auto",true).commit()
        try {
            pendingDasher = dasher
            sessionHandler.removeCallbacks(sessionTimeout);sessionHandler.postDelayed(sessionTimeout,12000)
            startForegroundService(Intent().setClassName(packageName,"$packageName.FloatingMapService")
                .setAction("com.hirolifecreative.floatingmap.DASHER_SESSION_START"))
        } catch (_: Exception) { pendingDasher = null;sessionHandler.removeCallbacks(sessionTimeout);notice("No se pudo abrir el flotante; revisa Configuración") }
    }
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,results: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,results)
        if (requestCode == 101 && results.isNotEmpty() && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) startDasherSession()
    }
    private fun showReport() {
        val result = StringBuilder("DriveFly ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE}\n${Build.MANUFACTURER} ${Build.MODEL}\n\n")
        result.append("Dasher: ").append(getSharedPreferences("mapa_flotante",MODE_PRIVATE).getString("dasher_status","Esperando una dirección visible del pedido")).append("\n\n")
        result.append(DasherLookupLog.read(this)).append("\n\n")
        result.append(CrashLog.read(this))
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                val exits = manager.getHistoricalProcessExitReasons(packageName,0,5)
                exits.forEach { exit ->
                    result.append("\n\nProceso: ${exit.processName}\nFecha: ${java.util.Date(exit.timestamp)}\nMotivo: ${exit.reason} · estado: ${exit.status}\n${exit.description.orEmpty()}")
                    // Native tombstones may be binary protobuf. Extract readable strings
                    // from a bounded buffer rather than displaying corrupted UTF-8.
                    exit.traceInputStream?.use { stream ->
                        val bytes = ByteArray(256*1024)
                        var size = 0
                        while (size < bytes.size) {
                            val count = stream.read(bytes,size,bytes.size-size)
                            if (count <= 0) break
                            size += count
                        }
                        val readable = StringBuilder();val run = StringBuilder()
                        for (i in 0 until size) {
                            val value = bytes[i].toInt() and 255
                            if (value in 32..126) run.append(value.toChar()) else {
                                if (run.length >= 6) readable.append(run).append('\n')
                                run.setLength(0)
                            }
                        }
                        if (run.length >= 6) readable.append(run).append('\n')
                        result.append("\nExtracto legible de traza nativa (no es una pila completa):\n").append(readable.take(18000))
                    }
                }
            } catch (e: Exception) { result.append("\nNo se pudo leer el historial: ${e.javaClass.simpleName}") }
        }
        report.text = "No se pudo iniciar el turno. Revisa Diagnóstico en Configuración."
    }
}
