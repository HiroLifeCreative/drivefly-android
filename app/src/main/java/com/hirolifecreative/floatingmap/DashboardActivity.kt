package com.hirolifecreative.floatingmap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {
    private var pendingMap = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        pendingMap = savedInstanceState?.getBoolean("pending_map") ?: false
        DriveFlyUi.chrome(this)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pending_map", pendingMap)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        buildUI()
        if (pendingMap && Settings.canDrawOverlays(this)) { pendingMap = false; launchMap() }
    }
    private fun hasLocation() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasToken() = getSharedPreferences("mapa_flotante", MODE_PRIVATE).getString("mapbox_token", "").orEmpty().startsWith("pk.")
    private fun settings() { startActivity(Intent(this, SettingsActivity::class.java)) }
    private fun launchMap() {
        if (!hasToken()) { Toast.makeText(this, "Añade tu token de Mapbox para preparar el mapa", Toast.LENGTH_LONG).show(); settings(); return }
        if (!hasLocation()) {
            pendingMap = false
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 10)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            pendingMap = true
            Toast.makeText(this, "Permite que DriveFly muestre el mapa sobre otras apps", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        try {
            startForegroundService(Intent(this, FloatingMapService::class.java))
            Toast.makeText(this, "Mapa flotante activado", Toast.LENGTH_SHORT).show()
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        } catch (e: Exception) {
            CrashLog.save(this, e)
            Toast.makeText(this, "No se pudo activar el mapa. Revisa los permisos en Ajustes.", Toast.LENGTH_LONG).show()
        }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 10) {
            pendingMap = false
            if (hasLocation()) launchMap()
            else Toast.makeText(this, "Activa ubicación precisa para seguir tu recorrido", Toast.LENGTH_LONG).show()
        }
        buildUI()
    }
    private fun buildUI() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DriveFlyUi.dp(context, 20), DriveFlyUi.dp(context, 24), DriveFlyUi.dp(context, 20), DriveFlyUi.dp(context, 24))
        }
        content.addView(DriveFlyUi.button(this, "‹ Volver al mapa") { finish() })
        content.addView(DriveFlyUi.text(this, "ESTADO Y PERMISOS", 12f, true, DriveFlyUi.MUTED))
        content.addView(DriveFlyUi.text(this, "Estado del mapa", 28f, true))
        content.addView(DriveFlyUi.text(this, "Configura lo necesario para salir.", 16f, color = DriveFlyUi.MUTED).apply {
            setPadding(0, 8, 0, DriveFlyUi.dp(context, 24))
        })
        val ready = hasToken() && hasLocation() && Settings.canDrawOverlays(this)
        val setup = DriveFlyUi.card(this)
        setup.addView(DriveFlyUi.text(this, if (ready) "Listo para salir" else "Prepara tu DriveFly", 21f, true))
        setup.addView(DriveFlyUi.text(this, "${if (hasToken()) "✓" else "○"} Mapa configurado\n${if (hasLocation()) "✓" else "○"} Ubicación precisa\n${if (Settings.canDrawOverlays(this)) "✓" else "○"} Mostrar sobre otras apps", 15f, color = DriveFlyUi.MUTED).apply {
            setPadding(0, 16, 0, 0); setLineSpacing(8f, 1f)
        })
        if (!ready) setup.addView(DriveFlyUi.button(this, "Configurar mi mapa") { settings() })
        content.addView(setup)
        val map = DriveFlyUi.card(this)
        map.addView(DriveFlyUi.text(this, "Tu ruta, siempre visible", 22f, true))
        map.addView(DriveFlyUi.text(this, "Sigue el mapa mientras usas tus apps de reparto.", color = DriveFlyUi.MUTED))
        map.addView(DriveFlyUi.button(this, "Activar mapa flotante", true) { launchMap() })
        map.addView(DriveFlyUi.button(this, "Ocultar mapa") {
            stopService(Intent(this, FloatingMapService::class.java))
            Toast.makeText(this, "Mapa oculto", Toast.LENGTH_SHORT).show()
        })
        content.addView(map)
        val actions = DriveFlyUi.card(this)
        actions.addView(DriveFlyUi.text(this, "Explora y navega", 21f, true))
        actions.addView(DriveFlyUi.button(this, "Buscar un destino", true) {
            if (hasToken()) startActivity(Intent(this, NavigationActivity::class.java)) else settings()
        })
        actions.addView(DriveFlyUi.button(this, "Abrir Dasher") {
            val launch = packageManager.getLaunchIntentForPackage("com.doordash.driverapp")
                ?: packageManager.getLaunchIntentForPackage("com.dd.doordash")
            if (launch != null) startActivity(launch)
            else Toast.makeText(this, "Instala Dasher para abrirlo desde aquí", Toast.LENGTH_LONG).show()
        })
        actions.addView(DriveFlyUi.button(this, "Ajustes y conexiones") { settings() })
        content.addView(actions)
        content.addView(DriveFlyUi.text(this, "DriveFly · ${BuildConfig.VERSION_NAME}", 12f, color = DriveFlyUi.MUTED))
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor(DriveFlyUi.BACKGROUND)); isFillViewport = true; addView(content) })
        DriveFlyUi.chrome(this)
    }
}
