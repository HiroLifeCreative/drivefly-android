package com.hirolifecreative.floatingmap

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

class SettingsActivity: Activity() {
    private var darkAtStart=false
    private val page get()=intent.getStringExtra("page").orEmpty()
    private val prefs get()=getSharedPreferences("mapa_flotante",MODE_PRIVATE)
    private var tokenInput: EditText?=null
    private var tokenDraft: String?=null
    override fun onCreate(state: Bundle?) { super.onCreate(state);CrashLog.install(this);darkAtStart=Appearance.dark(this);tokenDraft=state?.getString("tokenDraft");render() }
    override fun onResume() { super.onResume();if(darkAtStart!=Appearance.dark(this)) { recreate();return };if(page!="token") render() }
    override fun onWindowFocusChanged(focus: Boolean) { super.onWindowFocusChanged(focus);if(focus) SettingsUi.chrome(this) }
    override fun onSaveInstanceState(state: Bundle) { tokenInput?.let { state.putString("tokenDraft",it.text.toString()) };super.onSaveInstanceState(state) }
    private fun open(page: String) { startActivity(Intent(this,SettingsActivity::class.java).putExtra("page",page)) }
    private fun activity(type: Class<*>) { startActivity(Intent(this,type)) }
    private fun system(action: String,packageUri: Boolean=false) {
        try { startActivity(Intent(action).apply { if(packageUri) data=Uri.parse("package:$packageName") }) }
        catch (_: Exception) { Toast.makeText(this,"No se pudo abrir ese ajuste",Toast.LENGTH_SHORT).show() }
    }
    private fun dashEnabled()=Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().let { it.contains(packageName,true)&&it.contains("DashAddressService",true) }
    private fun notifEnabled()=Settings.Secure.getString(contentResolver,"enabled_notification_listeners").orEmpty().contains(packageName,true)
    private fun locationEnabled()=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    private fun bgEnabled()=Build.VERSION.SDK_INT<29 || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)==PackageManager.PERMISSION_GRANTED
    private fun running(): Boolean = try { getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE).any { it.service.className==FloatingMapService::class.java.name } } catch (_: Exception) { false }
    private fun state(value: Boolean)=if(value) "Activado" else "Desactivado"
    private fun showMap(): Boolean {
        if(!prefs.getString("mapbox_token","").orEmpty().startsWith("pk.")) { open("token");return false }
        if(!locationEnabled()) { requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),1);return false }
        if(!Settings.canDrawOverlays(this)) { system(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,true);return false }
        try { startForegroundService(Intent(this,FloatingMapService::class.java));Toast.makeText(this,"Mapa flotante activado",Toast.LENGTH_SHORT).show();return true }
        catch (e: Exception) { CrashLog.save(this,e);Toast.makeText(this,"No se pudo iniciar el mapa",Toast.LENGTH_SHORT).show();return false }
    }
    private fun locationSettings() {
        if(!locationEnabled()) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),1)
        else if(Build.VERSION.SDK_INT==29&&!bgEnabled()) requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),2)
        else system(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,true)
    }
    private fun toggle(group: LinearLayout,title: String,key: String,icon: String,description: String="") {
        SettingsUi.toggle(group,title,icon,prefs.getBoolean(key,key=="dasher_auto"),description) { checked ->
            prefs.edit().putBoolean(key,checked).apply()
            if(key=="auto_size"&&checked) system(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }
    }
    private fun render() {
        darkAtStart=Appearance.dark(this)
        when(page) { "colors"->colors();"colorPicker"->colorPicker();"map3d"->map3d();"floating"->floating();"permissions"->permissions();"token"->token();"diagnostic"->diagnostic();"help"->help();else->home() }
    }
    private fun home() {
        val ui=SettingsUi.Page(this,"Configuración")
        val general=ui.section("Personalización")
        val mode=when(Appearance.mode(this)) { "dark"->"Oscuro";"light"->"Claro";else->"Sistema" }
        SettingsUi.row(general,"Apariencia","moon",mode) { activity(AppearanceActivity::class.java) }
        SettingsUi.row(general,"Colores de ruta y pins","pin") { open("colors") }
        SettingsUi.row(general,"Cámara y puck","sliders","${CameraPreferences.reload(this).tilt}°") { activity(CameraSettingsActivity::class.java) }
        val voice=VoicePreferences.read(this)
        SettingsUi.row(general,"Voz de navegación","voice",when(voice.mode) { "off"->"Sin voz";"android"->"Android";else->"Claude High" }) { activity(VoiceSettingsActivity::class.java) }
        SettingsUi.row(general,"Mapa 3D y navegación","layers",if(MapExperience.read(this).enabled) "Standard" else "Compatible") { open("map3d") }
        SettingsUi.row(general,"Mapa flotante","map",if(running()) "Activo" else "Inactivo") { open("floating") }
        val dasher=ui.section("Dasher")
        toggle(dasher,"Destino automático","dasher_auto","bag")
        toggle(dasher,"Aceptar pedidos automáticamente","dasher_accept_all","bag","Usa los filtros guardados cuando estén activos")
        SettingsUi.row(dasher,"Filtros de pedidos","sliders",if(OfferFilterPreferences.read(this).enabled) "Activados" else "Sin filtros") { activity(OfferFilterActivity::class.java) }
        SettingsUi.row(dasher,"Lector de direcciones","pin",state(dashEnabled())) { system(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        ui.note("Detecta la dirección visible del pedido para iniciar la ruta.")
        val privacy=ui.section("Acceso y datos")
        SettingsUi.row(privacy,"Permisos","shield",if(locationEnabled()&&Settings.canDrawOverlays(this)) "Configurados" else "Revisar") { open("permissions") }
        SettingsUi.row(privacy,"Conexión con Mapbox","key",if(prefs.getString("mapbox_token","").orEmpty().startsWith("pk.")) "Configurada" else "Pendiente") { open("token") }
        val support=ui.section("Ayuda")
        SettingsUi.row(support,"Cómo usar DriveFly","info") { open("help") }
        SettingsUi.row(support,"Diagnóstico","gear") { open("diagnostic") }
        ui.note("DriveFly ${BuildConfig.VERSION_NAME}")
    }
    private fun colors() {
        val ui=SettingsUi.Page(this,"Ruta y pins")
        val c=NavigationColors.read(this)
        val route=ui.section("Ruta")
        val pins=ui.section("Pins de destino")
        for ((key,title) in listOf("route" to "Línea de la ruta","pickup" to "Restaurante · recogida","delivery" to "Cliente · entrega","other" to "Otros destinos")) {
            val swatch=android.view.View(this).apply {
                background=SettingsUi.shape(this@SettingsActivity,android.graphics.Color.parseColor(c.value(key)),8)
                layoutParams=LinearLayout.LayoutParams(SettingsUi.dp(this@SettingsActivity,24),SettingsUi.dp(this@SettingsActivity,24))
                contentDescription="Color ${c.value(key)}"
            }
            SettingsUi.row(if(key=="route") route else pins,title,if(key=="route") "map" else "pin",c.value(key),trailing=swatch) {
                startActivity(Intent(this,SettingsActivity::class.java).putExtra("page","colorPicker").putExtra("colorKey",key))
            }
        }
        ui.note("Los colores se conservan en modo claro y oscuro, en el mapa principal y flotante. Los pins mantienen su forma y aparecen según la distancia al destino.")
        val restore=ui.section("Predeterminados")
        SettingsUi.row(restore,"Restaurar colores","refresh","Azul · verde · rojo") {
            NavigationColors.save(this,NavigationColorConfig());render()
        }
    }
    private fun colorPicker() {
        val key=intent.getStringExtra("colorKey").takeIf { it in listOf("route","pickup","delivery","other") } ?: "route"
        val current=NavigationColors.read(this).value(key)
        val title=when(key) { "pickup"->"Pin de restaurante";"delivery"->"Pin de cliente";"other"->"Otros pins";else->"Color de la ruta" }
        var input: EditText?=null
        fun choose(value: String) {
            val normalized=NavigationColorConfig.normalize(value)
            if(normalized==null) { input?.error="Escribe seis dígitos, por ejemplo #007AFF";return }
            try { NavigationColors.save(this,NavigationColors.read(this).withColor(key,normalized));finish() }
            catch(_: Exception) { Toast.makeText(this,"No se pudo guardar el color",Toast.LENGTH_SHORT).show() }
        }
        val ui=SettingsUi.Page(this,title) { choose(input?.text?.toString().orEmpty()) }
        val preview=NavigationColorPreview(this,key=="route",current)
        ui.content.addView(preview,LinearLayout.LayoutParams(-1,SettingsUi.dp(this,100)))
        val custom=ui.section("Color personalizado")
        val body=SettingsUi.body(custom)
        input=SettingsUi.input(this,"#007AFF").apply {
            setText(current);filters=arrayOf(android.text.InputFilter.LengthFilter(7))
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            addTextChangedListener(object: android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) { }
                override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { NavigationColorConfig.normalize(s.toString())?.let { preview.color=it;preview.invalidate() } }
                override fun afterTextChanged(s: android.text.Editable?) { }
            })
        }
        body.addView(input)
        ui.note("Escribe un código HEX y pulsa Guardar, o elige uno de estos colores.")
        val palette=ui.section("Paleta")
        for ((name,color) in listOf("Azul" to "#007AFF","Verde" to "#16A765","Rojo" to "#FF3B30","Naranja" to "#FF9500","Amarillo" to "#FFCC00","Violeta" to "#AF52DE","Turquesa" to "#00AFAF","Gris" to "#738192")) {
            val swatch=android.view.View(this).apply {
                background=SettingsUi.shape(this@SettingsActivity,android.graphics.Color.parseColor(color),8)
                layoutParams=LinearLayout.LayoutParams(SettingsUi.dp(this@SettingsActivity,24),SettingsUi.dp(this@SettingsActivity,24))
            }
            SettingsUi.row(palette,name,"",if(current==color) "✓" else "",trailing=swatch) { choose(color) }
        }
    }
    private fun floating() {
        val ui=SettingsUi.Page(this,"Mapa flotante")
        val control=ui.section("Ventana")
        val isRunning=running()
        var sw: DriveFlySwitch?=null
        sw=SettingsUi.toggle(control,"Activar mapa","map",isRunning) { checked ->
            if(checked) { if(!showMap()) sw?.isChecked=false } else stopService(Intent(this,FloatingMapService::class.java))
        }
        SettingsUi.row(control,"Permiso sobre otras apps","shield",state(Settings.canDrawOverlays(this))) { system(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,true) }
        val opacity=ui.section("Visibilidad")
        val value=SettingsUi.text(this,"${(prefs.getFloat("window_alpha",1f)*100).toInt()}%",13f,secondary=true)
        SettingsUi.row(opacity,"Opacidad","layers",trailing=value)
        val body=SettingsUi.body(opacity)
        body.addView(CameraSlider(this,50,100,1,(prefs.getFloat("window_alpha",1f)*100).toInt()) { number ->
            prefs.edit().putFloat("window_alpha",number/100f).apply();value.text="$number%"
        }.apply { contentDescription="Opacidad del mapa flotante" },LinearLayout.LayoutParams(-1,SettingsUi.dp(this,44)))
        val extras=ui.section("Comportamiento")
        toggle(extras,"Tamaño según app","auto_size","sliders","Compacto con Dasher o Waze")
        toggle(extras,"Modo espejo HUD","hud_mode","car","Reflejo para el parabrisas")
        toggle(extras,"Alerta de velocidad","speed_alert","bell")
        SettingsUi.row(extras,"Mapa 3D y navegación","layers") { open("map3d") }
        ui.note("Reinicia el flotante para aplicar opacidad, HUD y estilo avanzado.")
        SettingsUi.row(ui.section("Acciones"),"Reiniciar mapa","map") { stopService(Intent(this,FloatingMapService::class.java));android.os.Handler(mainLooper).postDelayed({ showMap() },500) }
    }
    private fun map3d() {
        val ui=SettingsUi.Page(this,"Mapa 3D y navegación")
        val c=MapExperience.read(this)
        fun change(value: MapExperienceConfig) { MapExperience.save(this,value) }
        SettingsUi.toggle(ui.section("Estilo"),"Mapbox Standard 3D","map",c.enabled,"Edificios, vegetación e iluminación según cobertura") { change(MapExperience.read(this).copy(enabled=it)) }
        val detail=ui.section("Detalle del entorno")
        SettingsUi.toggle(detail,"Edificios 3D","layers",c.buildings) { change(MapExperience.read(this).copy(buildings=it)) }
        SettingsUi.toggle(detail,"Árboles 3D","layers",c.trees) { change(MapExperience.read(this).copy(trees=it)) }
        SettingsUi.toggle(detail,"Monumentos y edificios destacados","pin",c.landmarks) { change(MapExperience.read(this).copy(landmarks=it)) }
        SettingsUi.toggle(detail,"Fachadas detalladas","layers",c.facades) { change(MapExperience.read(this).copy(facades=it)) }
        SettingsUi.toggle(detail,"Carreteras con detalle de carriles","map",c.detailedRoads) { change(MapExperience.read(this).copy(detailedRoads=it)) }
        val lights=ui.section("Iluminación")
        val names=linkedMapOf("theme" to "Según tema claro u oscuro","clock" to "Según hora del teléfono","dawn" to "Amanecer","day" to "Día","dusk" to "Atardecer","night" to "Noche")
        names.forEach { (key,title) -> SettingsUi.row(lights,title,"moon",if(c.lighting==key) "✓" else "") { change(MapExperience.read(this).copy(lighting=key));render() } }
        ui.note("La opción por hora usa franjas horarias; no representa la posición solar exacta.")
        SettingsUi.toggle(lights,"Clima en el mapa","layers",c.weather,"Lluvia y nieve suaves según una estación cercana de NOAA/NWS") { change(MapExperience.read(this).copy(weather=it)) }
        ui.note("Clima regional en EE.UU.; puede diferir de tu calle. Si faltan observaciones recientes, no se aplican efectos.")
        SettingsUi.row(lights,"Fuente del clima: NOAA/NWS","info") { systemLink("https://www.weather.gov/documentation/services-web-api") }
        val navigation=ui.section("Llegadas e indicaciones")
        SettingsUi.toggle(navigation,"Guía de carriles","map",c.laneGuidance,"Indica los carriles permitidos cuando la ruta incluye esos datos") { change(MapExperience.read(this).copy(laneGuidance=it)) }
        SettingsUi.toggle(navigation,"Entradas de los destinos","pin",c.entrances,"Busca la puerta y el punto de acceso del vehículo cuando estén disponibles") { change(MapExperience.read(this).copy(entrances=it)) }
        ui.note("3D Lanes avanzado requiere acceso privado de Mapbox. Los detalles disponibles varían por zona. Las detecciones de otros vehículos requieren sensores y datos externos.")
        SettingsUi.row(ui.section("Mapbox"),"Información sobre 3D Lanes","info") { systemLink("https://www.mapbox.com/3d-lanes") }
    }
    private fun permissions() {
        val ui=SettingsUi.Page(this,"Permisos")
        val map=ui.section("Mapa y ubicación")
        SettingsUi.row(map,"Ubicación precisa","locate",state(locationEnabled())) { locationSettings() }
        SettingsUi.row(map,"Ubicación en segundo plano","pin",state(bgEnabled())) { locationSettings() }
        SettingsUi.row(map,"Mostrar sobre otras apps","layers",state(Settings.canDrawOverlays(this))) { system(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,true) }
        val connections=ui.section("Conexiones")
        SettingsUi.row(connections,"Lector de Dasher","bag",state(dashEnabled())) { system(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        SettingsUi.row(connections,"Acceso a notificaciones","bell",state(notifEnabled())) { system(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
        SettingsUi.row(connections,"Tamaño según aplicación","sliders") { system(Settings.ACTION_USAGE_ACCESS_SETTINGS) }
        ui.note("Android administra estos permisos. Activa el lector de Dasher si quieres detectar automáticamente las direcciones visibles.")
    }
    private fun token() {
        var message: android.widget.TextView?=null
        fun save() {
            val value=tokenInput?.text.toString().trim()
            if(!value.startsWith("pk.")||value.length<20||value.any { it.isWhitespace() }) { message?.text="Introduce un token público válido que comience con pk.";return }
            prefs.edit().putString("mapbox_token",value).apply();tokenDraft=value;message?.text="Token guardado. Reinicia el mapa flotante para aplicarlo."
        }
        val ui=SettingsUi.Page(this,"Conexión con Mapbox",save={ save() })
        val token=ui.section("Token público")
        val body=SettingsUi.body(token)
        tokenInput=SettingsUi.input(this,"pk.…").apply { inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;setText(tokenDraft ?: prefs.getString("mapbox_token","")) }
        body.addView(tokenInput)
        message=ui.note("Conecta el mapa con el token público de tu cuenta Mapbox.")
        SettingsUi.row(ui.section("Tu cuenta"),"Abrir Mapbox","key") { systemLink("https://account.mapbox.com/access-tokens/") }
    }
    private fun systemLink(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) } catch (_: Exception) { Toast.makeText(this,"No se pudo abrir el enlace",Toast.LENGTH_SHORT).show() } }
    private fun diagnostic() {
        val ui=SettingsUi.Page(this,"Diagnóstico")
        val group=ui.section("Último registro")
        val diagnostic=DasherLookupLog.read(this)+"\n\n"+CrashLog.read(this)
        val log=SettingsUi.text(this,diagnostic,12f,secondary=true).apply { typeface=android.graphics.Typeface.MONOSPACE;setTextIsSelectable(true) }
        SettingsUi.body(group).addView(log)
        val actions=ui.section("Acciones")
        SettingsUi.row(actions,"Copiar diagnóstico","info") {
            val clipboard=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Diagnóstico DriveFly",diagnostic))
            android.widget.Toast.makeText(this,"Diagnóstico copiado",android.widget.Toast.LENGTH_SHORT).show()
        }
        SettingsUi.row(actions,"Borrar registro","close") { CrashLog.clear(this);DasherLookupLog.clear(this);render() }
        ui.note("DriveFly ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE}")
    }
    private fun help() {
        val ui=SettingsUi.Page(this,"Cómo usar DriveFly")
        SettingsUi.body(ui.section("Datos del mapa")).addView(SettingsUi.text(this,"Señales: © OpenStreetMap contributors, según cobertura. Los semáforos muestran su ubicación, no la luz encendida en tiempo real. Cierres: Mapbox Traffic, cuando están registrados.",14f,secondary=true))
        listOf("Con Dasher" to "Activa el lector de direcciones en Permisos. Abre Dasher con el mapa flotante: DriveFly detecta la dirección visible del pedido.","Elegir un destino" to "Busca un lugar o mantén pulsado el mapa. Selecciona una ruta y pulsa Iniciar.","Compartir direcciones" to "En Indicaciones de Dasher, elige DriveFly. También puedes compartir un destino desde Waze.","Terminar una ruta" to "Al llegar, la ruta termina automáticamente tras la cuenta de 20 segundos. El mapa queda libre; puedes minimizarlo y abrirlo desde el botón flotante.").forEach { (title,value) -> SettingsUi.body(ui.section(title)).addView(SettingsUi.text(this,value,14f,secondary=true)) }
    }
}
