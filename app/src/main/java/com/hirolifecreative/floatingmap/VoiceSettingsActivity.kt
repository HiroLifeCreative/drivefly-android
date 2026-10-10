package com.hirolifecreative.floatingmap
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class VoiceSettingsActivity: Activity() {
    private var config=VoiceConfig()
    private var status: TextView?=null
    private val listener: (String)->Unit = { status?.text=it }
    override fun onCreate(state: Bundle?) { super.onCreate(state);config=VoicePreferences.read(this);render() }
    override fun onResume() { super.onResume();config=VoicePreferences.read(this);render();NavigationVoice.observe(listener) }
    override fun onPause() { NavigationVoice.remove(listener);super.onPause() }
    override fun onWindowFocusChanged(focus: Boolean) { super.onWindowFocusChanged(focus);if(focus) SettingsUi.chrome(this) }
    private fun save(value: VoiceConfig,refresh: Boolean=true): Boolean {
        return try { VoicePreferences.save(this,value);config=value.normalized();if(refresh) render();true }
        catch (_: Exception) { Toast.makeText(this,"No se pudo guardar la voz",Toast.LENGTH_SHORT).show();false }
    }
    private fun render() {
        val ui=SettingsUi.Page(this,"Voz de navegación")
        val voices=ui.section("Voz")
        listOf(Triple("claude","Claude High","Español de México"),Triple("android","Voz Android","Voz del dispositivo"),Triple("off","Sin voz","")).forEach { (mode,title,description) ->
            SettingsUi.row(voices,title,"voice",description=description,selected=config.mode==mode) { save(config.copy(mode=mode)) }
        }
        val packageGroup=ui.section("Paquete de voz")
        SettingsUi.row(packageGroup,"Claude High","voice",if(NavigationVoice.claudeInstalled(this)) "Instalado" else "Descargar") {
            try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(NavigationVoice.CLAUDE_APK))) }
            catch (_: Exception) { Toast.makeText(this,"No se pudo abrir la descarga",Toast.LENGTH_SHORT).show() }
        }
        ui.note("Instala es_MX-claude-high una vez para usar esta voz sin internet. Mientras falta el paquete, se utiliza la voz Android.")
        val announcements=ui.section("Pedidos y llegada")
        SettingsUi.toggle(announcements,"Anunciar nuevos pedidos","voice",config.orderAnnouncements,"Nombre de la tienda, cuando Dasher lo muestra") { save(config.copy(orderAnnouncements=it)) }
        SettingsUi.toggle(announcements,"Detalles al llegar","pin",config.arrivalDetails,"Número de casa, apartamento y lado confirmado") { save(config.copy(arrivalDetails=it)) }
        val speed=ui.section("Lectura")
        val value=SettingsUi.text(this,"${config.speedPercent}%",13f,secondary=true)
        SettingsUi.row(speed,"Velocidad","sliders",trailing=value)
        SettingsUi.body(speed).addView(CameraSlider(this,80,120,10,config.speedPercent) { number ->
            if(save(config.copy(speedPercent=number),false)) { value.text="${config.speedPercent}%";status?.text="Velocidad guardada" }
        }.apply { contentDescription="Velocidad de voz" },LinearLayout.LayoutParams(-1,SettingsUi.dp(this,44)))
        SettingsUi.row(ui.section("Prueba"),"Probar voz","voice") {
            if(config.mode!="off") NavigationVoice.speak(this,"Nuevo pedido. Recoge en McDonald's. Llegaste. Casa 327, a la derecha. Apartamento 2 B.",finish=true)
        }.apply { isEnabled=config.mode!="off";alpha=if(isEnabled) 1f else .45f }
        status=ui.note(if(config.mode=="off") "La voz está desactivada." else "Pulsa Probar voz para escucharla.")
        ui.note("Los cambios se guardan automáticamente para ambos mapas.")
    }
}
