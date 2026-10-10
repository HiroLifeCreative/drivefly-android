package com.hirolifecreative.floatingmap

/** Amounts are the visible offer estimate, never projected tips. */
data class OfferFilterConfig(
    val enabled: Boolean=true,
    val minimumPayEnabled: Boolean=true,val minimumPay: Double=6.0,
    val maximumMilesEnabled: Boolean=true,val maximumMiles: Double=10.0,
    val minimumRateEnabled: Boolean=true,val minimumRate: Double=1.5
)
data class OfferMetrics(val pay: Double?,val miles: Double?)
object DasherOfferFilters {
    private val money=Regex("""(?:US\s*\$|USD\s*|\$)\s*(\d+(?:[.,]\d+)*)(?!\d)|\b(\d+(?:[.,]\d+)*)\s*(?:US\s*\$|USD)""",RegexOption.IGNORE_CASE)
    private val distance=Regex("""\b(\d+(?:[.,]\d+)*)\s*(mi(?:les)?|millas|km)\b""",RegexOption.IGNORE_CASE)
    private fun number(value: String): Double? {
        val normalized=if(value.contains(',')&&value.contains('.')) value.replace(",","") else value.replace(',','.')
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite()&&it>=0 }
    }
    fun metrics(texts: List<String>): OfferMetrics {
        val lines=texts.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }
        fun payLabel(s: String)=Regex("guaranteed|garantizado|pago total|total payout|total pay|offer pay",RegexOption.IGNORE_CASE).containsMatchIn(s)
        fun distanceLabel(s: String)=Regex("total.*(?:mi|millas|km)|(?:mi|millas|km).*total",RegexOption.IGNORE_CASE).containsMatchIn(s)
        val amounts=mutableListOf<Double>();val preferredPay=mutableListOf<Double>()
        val miles=mutableListOf<Double>();val preferredMiles=mutableListOf<Double>()
        lines.forEachIndexed { i,line ->
            val lower=line.lowercase(java.util.Locale.ROOT)
            if(!Regex("per mile|por milla|/\\s*(?:mi|mile)|bonus|bono|peak pay|pago extra").containsMatchIn(lower)) {
                val values=money.findAll(line).mapNotNull { m->number(m.groupValues[1].ifBlank { m.groupValues[2] })?.takeIf { it<=5000 } }.toList()
                amounts+=values
                if(payLabel(line)||payLabel(lines.getOrNull(i-1).orEmpty())) preferredPay+=values
            }
            val values=distance.findAll(line).mapNotNull { m->number(m.groupValues[1])?.takeIf { it>0&&it<=5000 }?.let { if(m.groupValues[2].equals("km",true)) it/1.609344 else it } }.toList()
            miles+=values
            if(distanceLabel(line)) preferredMiles+=values
        }
        fun unique(all: List<Double>,preferred: List<Double>): Double? = (if(preferred.isNotEmpty()) preferred else all).distinct().singleOrNull()
        return OfferMetrics(unique(amounts,preferredPay),unique(miles,preferredMiles))
    }
    /** null means eligible; every enabled threshold must pass. */
    fun reason(config: OfferFilterConfig,m: OfferMetrics): String? {
        if(!config.enabled) return null
        if((config.minimumPayEnabled||config.minimumRateEnabled)&&(m.pay==null||!m.pay.isFinite()||m.pay<0)) return "No se puede leer el pago con claridad"
        if((config.maximumMilesEnabled||config.minimumRateEnabled)&&(m.miles==null||!m.miles.isFinite()||m.miles<=0)) return "No se puede leer la distancia con claridad"
        if(config.minimumPayEnabled&&m.pay!!+1e-9<config.minimumPay) return "Pago inferior a tu mínimo"
        if(config.maximumMilesEnabled&&m.miles!!-1e-9>config.maximumMiles) return "Distancia superior a tu máximo"
        if(config.minimumRateEnabled&&m.pay!!/m.miles!!+1e-9<config.minimumRate) return "Pago por milla inferior a tu mínimo"
        return null
    }
}
object OfferFilterPreferences {
    fun read(c: android.content.Context): OfferFilterConfig {
        val p=c.getSharedPreferences("mapa_flotante",android.content.Context.MODE_PRIVATE)
        fun value(key: String,default: Double)=p.getString(key,null)?.toDoubleOrNull()?.takeIf { it.isFinite()&&it>=0 } ?: default
        return OfferFilterConfig(p.getBoolean("offer_filters",true),p.getBoolean("offer_min_pay_on",true),value("offer_min_pay",6.0),p.getBoolean("offer_max_miles_on",true),value("offer_max_miles",10.0),p.getBoolean("offer_min_rate_on",true),value("offer_min_rate",1.5))
    }
    fun save(c: android.content.Context,v: OfferFilterConfig): Boolean=c.getSharedPreferences("mapa_flotante",android.content.Context.MODE_PRIVATE).edit()
        .putBoolean("offer_filters",v.enabled).putBoolean("offer_min_pay_on",v.minimumPayEnabled).putString("offer_min_pay",v.minimumPay.toString())
        .putBoolean("offer_max_miles_on",v.maximumMilesEnabled).putString("offer_max_miles",v.maximumMiles.toString())
        .putBoolean("offer_min_rate_on",v.minimumRateEnabled).putString("offer_min_rate",v.minimumRate.toString()).commit()
}
