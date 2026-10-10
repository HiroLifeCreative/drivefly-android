package com.hirolifecreative.floatingmap
import android.app.Activity
import android.os.Bundle
import android.widget.Toast

class AppearanceActivity : Activity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state);CrashLog.install(this);render() }
    override fun onWindowFocusChanged(focus: Boolean) { super.onWindowFocusChanged(focus);if(focus) SettingsUi.chrome(this) }
    private fun render() {
        val ui=SettingsUi.Page(this,"Apariencia")
        val group=ui.section("Tema")
        listOf(Triple("light","Claro","layers"),Triple("dark","Oscuro","moon"),Triple("system","Seguir el sistema","gear")).forEach { (mode,title,icon) ->
            SettingsUi.row(group,title,icon,selected=Appearance.mode(this)==mode) {
                try { Appearance.save(this,mode);render() }
                catch (_: Exception) { Toast.makeText(this,"No se pudo guardar el tema",Toast.LENGTH_SHORT).show() }
            }
        }
        ui.note("Se aplica al mapa principal, al flotante y a las pantallas de configuración.")
    }
}
