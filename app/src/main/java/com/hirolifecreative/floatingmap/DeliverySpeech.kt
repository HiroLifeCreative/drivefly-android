package com.hirolifecreative.floatingmap

import org.json.JSONObject

/** Builds brief announcements from visible labels and explicit routing data, never GPS guesses. */
object DeliverySpeech {
    private fun lines(texts: List<String>)=texts.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    private fun safeName(value: String): String? {
        val name=value.trim().trimEnd(':')
        if(name.length !in 2..80 || !name.any { it.isLetter() }) return null
        if(name.first().isDigit() && Regex("^\\d+(?:[.,]\\d+)?\\s").containsMatchIn(name)) return null
        val lower=name.lowercase(java.util.Locale.ROOT)
        val ui=listOf("aceptar","accept","rechazar","decline","entregar","deliver","llamar","call","mensaje","message","indicaciones","directions","para llevar","pick up","pickup","recoger","la orden","order includes","bebida","drink","continuar","continue","reunirse","hand it","leave at","dejar","antes de","before","total","pago","pay","garantizado","guaranteed","ganancias","earnings","nuevo pedido","new order","delivery","oferta","offer","dash","tiempo","time","restaurant address","store address","customer address","address","dirección","apt","código","access code","nombre del edificio","building name")
        if(ui.any { lower==it || lower.startsWith("$it ") || lower.startsWith("$it:") } || Regex("(?:\\b\\d{5}(?:-\\d{4})?\\b|\\d+(?:[.,]\\d+)?\\s*(?:mi|miles|millas|km)\\b|\\d{1,2}:\\d{2})",RegexOption.IGNORE_CASE).containsMatchIn(name) || name.contains('$')) return null
        if(lower in setOf("restaurante","restaurant","tienda","store","cliente","customer","recogida","recoger","entrega","entregas","comprar y entregar","shop & deliver","alcohol","food","comida","local","null","postre","postres","dessert","desserts","bebidas","drinks","artículos","items","la orden incluye","order includes")) return null
        return name
    }
    fun merchant(texts: List<String>,offer: Boolean=false): String? {
        val ls=lines(texts)
        val labels=Regex("^(?:recoger en|recoger desde|recoge en|pick up at|pickup at|pick up from|pickup from|restaurante|restaurant|tienda|store|nuevo pedido(?: (?:de|en))?|new order(?: (?:from|at))?)\\s*:?\\s*(.*)$",RegexOption.IGNORE_CASE)
        val explicit=ls.mapIndexedNotNull { i,line ->
            val match=labels.matchEntire(line) ?: return@mapIndexedNotNull null
            safeName(match.groupValues[1].ifBlank { ls.getOrNull(i+1).orEmpty() })
        }.distinct()
        if(explicit.size==1) return explicit.single()
        if(explicit.size>1) return null
        val anchors=ls.mapIndexedNotNull { i,line ->
            if(line.startsWith("La orden incluye",true)||line.startsWith("Order includes",true)) safeName(ls.getOrNull(i-1).orEmpty())
            else if(line.startsWith("Para llevar antes",true)||line.startsWith("Pick up before",true)||line.startsWith("Pick up by",true)) safeName(ls.getOrNull(i+1).orEmpty())
            else null
        }.distinct()
        if(anchors.size==1) return anchors.single()
        val beforeAddress=ls.mapIndexedNotNull { i,line ->
            if(Regex("^\\d{1,6}[A-Za-z]?\\s+\\p{L}").containsMatchIn(line)) safeName(ls.getOrNull(i-1).orEmpty()) else null
        }.distinct()
        if(beforeAddress.size==1 && ls.none { it.startsWith("La orden incluye",true) || it.startsWith("Order includes",true) }) return beforeAddress.single()
        return if(offer) ls.mapNotNull(::safeName).distinct().singleOrNull() else null
    }
    fun unit(texts: List<String>): String {
        val ls=lines(texts)
        val label=Regex("^(?:apt\\.?\\s*/?\\s*suite|apt\\.?|apartment|apartamento|departamento|unit|suite)\\s*:?\\s*(.*)$",RegexOption.IGNORE_CASE)
        return ls.mapIndexedNotNull { i,line ->
            val m=label.matchEntire(line) ?: return@mapIndexedNotNull null
            validUnit(m.groupValues[1].ifBlank { ls.getOrNull(i+1).orEmpty() })
        }.distinct().singleOrNull().orEmpty()
    }
    fun validUnit(value: String): String? {
        val unit=value.trim().removePrefix("#").trim()
        if(!Regex("[A-Za-z0-9]{1,8}(?:[-/ ][A-Za-z0-9]{1,4})?").matches(unit)) return null
        if(unit.lowercase(java.util.Locale.ROOT).split(' ').any { it in setOf("null","none","n/a","north","south","east","west","road","street","rd","st","calle") }) return null
        return unit
    }
    fun order(merchant: String)=if(merchant.isBlank()) "Nuevo pedido para recoger." else "Nuevo pedido. Recoge en $merchant."
    fun offerKey(texts: List<String>,merchant: String): String {
        val metrics=Regex("(?:\\$\\s*\\d[\\d.,]*|\\d[\\d.,]*\\s*US\\$|\\d+(?:[.,]\\d+)?\\s*(?:mi|miles|millas|km)\\b)",RegexOption.IGNORE_CASE)
            .findAll(texts.joinToString(" ")).map { it.value.lowercase(java.util.Locale.ROOT) }.distinct().sorted().joinToString("|")
        return merchant.lowercase(java.util.Locale.ROOT)+"|"+metrics
    }
    fun arrival(name: String,kind: DestinationKind,merchant: String,unit: String,routeJson: String?): String {
        if(kind==DestinationKind.PICKUP) return if(merchant.isNotBlank()) "Llegaste a $merchant." else "Llegaste al local de recogida."
        val address=(if(name.startsWith("Dasher ·",true)||name.startsWith("Waze:",true)) name.substringAfter(':',name) else name).trim()
        val house=Regex("^\\d{1,6}[A-Za-z]?(?:-\\d{1,6})?\\b").find(address)?.value
        val side=try {
            val legs=JSONObject(routeJson.orEmpty()).getJSONArray("routes").getJSONObject(0).getJSONArray("legs")
            val steps=legs.getJSONObject(legs.length()-1).getJSONArray("steps")
            val maneuver=steps.getJSONObject(steps.length()-1).getJSONObject("maneuver")
            if(maneuver.optString("type")=="arrive") when(maneuver.optString("modifier")) { "left"->"a la izquierda";"right"->"a la derecha";else->null } else null
        } catch (_: Exception) { null }
        val apartment=validUnit(unit) ?: Regex("\\b(?:apt\\.?|apartment|unit|suite)\\s*#?\\s*([A-Za-z0-9-]+)",RegexOption.IGNORE_CASE).find(address)?.groupValues?.get(1)?.let(::validUnit)
        val details=mutableListOf(if(house!=null) "Llegaste. Casa $house" else "Llegaste a tu destino")
        if(side!=null) details[0]+=", $side"
        if(apartment!=null) details+="Apartamento $apartment"
        return details.joinToString(". ")+"."
    }
}

/** Coalesces notifications, repeated accessibility scans and the accepted pickup screen. */
class OrderAnnouncementGate {
    private var offerKey: String?=null
    private var absentSince: Long?=null
    private var pickupName: String?=null
    private var spokenMerchant=""
    private var spokenAt=Long.MIN_VALUE
    fun observe(isOffer: Boolean,key: String,merchant: String,pickup: Boolean,now: Long): String? {
        if(isOffer) {
            pickupName=null
            val fresh=offerKey!=key || absentSince?.let { now-it>=8000 }==true
            absentSince=null
            if(!fresh) return null
            offerKey=key
            if(spokenAt!=Long.MIN_VALUE && merchant==spokenMerchant && now-spokenAt<8000) return null
        } else {
            if(absentSince==null) absentSince=now
            if(!pickup) { pickupName=null;return null }
            if(merchant.isBlank() || pickupName==merchant) return null
            pickupName=merchant
            if(spokenAt!=Long.MIN_VALUE && merchant==spokenMerchant && now-spokenAt<120000) return null
        }
        spokenMerchant=merchant;spokenAt=now
        return DeliverySpeech.order(merchant)
    }
}
