package com.hirolifecreative.floatingmap
import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast

class OfferFilterActivity: Activity() {
    private var draft=OfferFilterConfig()
    private lateinit var pay: EditText
    private lateinit var miles: EditText
    private lateinit var rate: EditText
    private var message: android.widget.TextView?=null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state);draft=OfferFilterPreferences.read(this)
        if(state!=null) draft=draft.copy(enabled=state.getBoolean("enabled",draft.enabled),minimumPayEnabled=state.getBoolean("payOn",draft.minimumPayEnabled),maximumMilesEnabled=state.getBoolean("milesOn",draft.maximumMilesEnabled),minimumRateEnabled=state.getBoolean("rateOn",draft.minimumRateEnabled))
        val ui=SettingsUi.Page(this,"Filtros de pedidos",save={ save() })
        SettingsUi.toggle(ui.section("Aceptación"),"Aplicar filtros","sliders",draft.enabled) { draft=draft.copy(enabled=it) }
        ui.note("Si desactivas los filtros, la aceptación automática acepta todas las ofertas. El interruptor de aceptación automática se mantiene independiente.")
        fun field(title: String,unit: String,checked: Boolean,value: Double,key: String,change: (Boolean)->Unit): EditText {
            val group=ui.section(title)
            SettingsUi.toggle(group,"Usar este límite","sliders",checked,change=change)
            return SettingsUi.input(this,unit).apply {
                inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                contentDescription="$title · $unit";setText(state?.getString(key) ?: value.toString());SettingsUi.body(group).addView(this)
            }
        }
        pay=field("Pago mínimo","USD",draft.minimumPayEnabled,draft.minimumPay,"pay") { draft=draft.copy(minimumPayEnabled=it) }
        miles=field("Distancia máxima","Millas",draft.maximumMilesEnabled,draft.maximumMiles,"miles") { draft=draft.copy(maximumMilesEnabled=it) }
        rate=field("Pago mínimo por milla","USD por milla",draft.minimumRateEnabled,draft.minimumRate,"rate") { draft=draft.copy(minimumRateEnabled=it) }
        message=ui.note("Se deben cumplir todos los límites activados. Si faltan datos necesarios o son ambiguos, la oferta queda para revisión manual.")
        SettingsUi.body(ui.section("Ejemplo")).addView(SettingsUi.text(this,"$4 por 10 millas = $0.40/mi. Con un mínimo de $1.50/mi, DriveFly no pulsa Aceptar. No pulsa Rechazar ni estima propinas futuras.",13f,secondary=true))
    }
    private fun save() {
        fun value(input: EditText)=input.text.toString().trim().replace(',','.').toDoubleOrNull()
        val p=value(pay);val m=value(miles);val r=value(rate)
        if(p==null||!p.isFinite()||p !in 0.0..500.0||m==null||!m.isFinite()||m !in 0.1..200.0||r==null||!r.isFinite()||r !in 0.1..100.0) {
            message?.text="Usa pago de $0–$500, distancia de 0.1–200 mi y tarifa de $0.10–$100/mi.";return
        }
        if(OfferFilterPreferences.save(this,draft.copy(minimumPay=p,maximumMiles=m,minimumRate=r))) {
            Toast.makeText(this,"Filtros guardados",Toast.LENGTH_SHORT).show();finish()
        } else message?.text="No se pudieron guardar los filtros."
    }
    override fun onWindowFocusChanged(focus: Boolean) { super.onWindowFocusChanged(focus);if(focus) SettingsUi.chrome(this) }
    override fun onSaveInstanceState(s: Bundle) {
        s.putBoolean("enabled",draft.enabled);s.putBoolean("payOn",draft.minimumPayEnabled);s.putBoolean("milesOn",draft.maximumMilesEnabled);s.putBoolean("rateOn",draft.minimumRateEnabled)
        s.putString("pay",pay.text.toString());s.putString("miles",miles.text.toString());s.putString("rate",rate.text.toString());super.onSaveInstanceState(s)
    }
}
