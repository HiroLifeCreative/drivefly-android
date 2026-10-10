package com.hirolifecreative.floatingmap

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** Screen and notifications forward addresses; the foreground map process resolves them. */
object DasherAutomation {
    val packages = setOf("com.doordash.driverapp","com.dd.doordash.dasher")
    private var pendingKey = ""
    private var lastAttempt = ""
    private var attemptTime = 0L
    fun submit(context: Context,destination: DasherAddressParser.Destination) {
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences("mapa_flotante",Context.MODE_PRIVATE)
        if (!prefs.getBoolean("dasher_auto",true)) return
        if (!Settings.canDrawOverlays(ctx) || ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            status(ctx,"Activa mapa flotante y ubicación en Configuración");return
        }
        val key = destination.address.lowercase(java.util.Locale.ROOT)+"|"+destination.stage
        val now = System.currentTimeMillis()
        if (key == pendingKey) return
        if (key == lastAttempt && now-attemptTime < 10000) return
        lastAttempt = key;pendingKey = key;attemptTime = now
        status(ctx,"Dirección de ${destination.stage.lowercase()} detectada; buscando ubicación")
        try {
            ctx.startForegroundService(Intent().setClassName(ctx.packageName,"${ctx.packageName}.FloatingMapService")
                .setAction("com.hirolifecreative.floatingmap.DASHER_ADDRESS")
                .putExtra("address",destination.address).putExtra("stage",destination.stage)
                .putExtra("merchant",destination.merchant).putExtra("unit",destination.unit))
            pendingKey = ""
        } catch (_: Exception) {
            pendingKey = ""
            status(ctx,"Pulsa Iniciar con Dasher antes de abrir el pedido")
        }
    }

    const val ORDER_VOICE="com.hirolifecreative.floatingmap.DASHER_ORDER_VOICE"
    fun announce(ctx: Context,texts: List<String>,isOffer: Boolean,pickup: Boolean) {
        val merchant=DeliverySpeech.merchant(texts,isOffer).orEmpty()
        ctx.sendBroadcast(Intent(ORDER_VOICE).setPackage(ctx.packageName).putExtra("offer",isOffer)
            .putExtra("pickup",pickup).putExtra("merchant",merchant).putExtra("offerKey",DeliverySpeech.offerKey(texts,merchant)))
    }
    private fun status(ctx: Context,message: String) {
        ctx.getSharedPreferences("mapa_flotante",Context.MODE_PRIVATE).edit().putString("dasher_status",message).apply()
    }
}
