package com.hirolifecreative.floatingmap

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

object DasherGeocoder {
    class SearchFailure(val explanation: String): Exception(explanation)
    private fun failure(code: Int)=SearchFailure(when(code) {
        401,403 -> "Mapbox rechazó la consulta (HTTP $code). Revisa el token y sus restricciones"
        429 -> "Mapbox alcanzó el límite de consultas (HTTP 429); reintentando después"
        else -> "Mapbox no pudo responder (HTTP $code); reintentando después"
    })
    data class Coordinate(val lat: Double,val lon: Double,val vehicleLat: Double?=null,val vehicleLon: Double?=null,val entrance: Boolean=false)
    fun searchAddress(address: String): String = address
        .replace(Regex("""\s*(?:#|\b(?:apt\.?|apartment|unit|suite)\b)\s*[^,\n]*""",RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+United States$""",RegexOption.IGNORE_CASE), "").trim().trimEnd(',')
    fun find(address: String,token: String,origin: Coordinate,entrances: Boolean=true): Coordinate? {
        if (!token.startsWith("pk.")) throw SearchFailure("Falta un token público de Mapbox válido en Configuración")
        if (DasherAddressParser.namedPlaceParts(address) != null) {
            // Search all POI categories, including schools, banks, public institutions and housing.
            val combined = org.json.JSONArray()
            var received=false
            var lastFailure: Exception?=null
            for (variant in NamedPlaceMatcher.queries(address)) {
                val query = URLEncoder.encode(variant,"UTF-8")
                val url = "https://api.mapbox.com/search/searchbox/v1/forward?q=$query&types=poi&limit=10&country=us&proximity=${origin.lon},${origin.lat}&access_token=${URLEncoder.encode(token,"UTF-8")}"
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 8000;connection.readTimeout = 10000
                    if (connection.responseCode !in 200..299) {
                        val problem=failure(connection.responseCode)
                        if(connection.responseCode in listOf(401,403,429)) throw problem
                        lastFailure=problem;continue
                    }
                    received=true
                    val features = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("features")
                    if (features != null) for (i in 0 until features.length()) combined.put(features.get(i))
                } catch (problem: java.io.IOException) {
                    lastFailure=problem;
                    // One failed alias request must not discard results from the other requests.
                } finally { connection.disconnect() }
            }
            if(!received) throw (lastFailure ?: SearchFailure("Sin respuesta de Mapbox"))
            val body = JSONObject().put("features",combined)
            val place=parseNamedPlace(body,address,origin) ?: return null
            if(!entrances) return place
            val matching=(0 until combined.length()).mapNotNull { combined.optJSONObject(it) }.firstOrNull { f ->
                val xy=f.optJSONObject("geometry")?.optJSONArray("coordinates")
                xy!=null&&distance(place,Coordinate(xy.optDouble(1),xy.optDouble(0)))<1
            }
            val fullAddress=matching?.optJSONObject("properties")?.optString("full_address").orEmpty()
            if(!Regex("^\\d{1,6}[A-Za-z]?\\s+").containsMatchIn(fullAddress)) return place
            val detailed=try { find(fullAddress,token,origin,true) } catch(_: Exception) { null }
            return detailed?.takeIf { distance(place,it)<150 } ?: place
        }
        val url = "https://api.mapbox.com/geocoding/v5/mapbox.places/${URLEncoder.encode(searchAddress(address),"UTF-8").replace("+","%20")}.json?access_token=${URLEncoder.encode(token,"UTF-8")}&autocomplete=false&types=address&limit=3&routing=true${if(entrances) "&entrances=true" else ""}&proximity=${origin.lon},${origin.lat}"
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000;connection.readTimeout = 12000
            val code=connection.responseCode
            if (code in listOf(401,403,429)) throw failure(code)
            if (code !in 200..299 && code !in listOf(400,404,422)) throw failure(code)
            val legacy=if(code in 200..299) parse(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }),address,origin,entrances) else null
            if(legacy!=null) return legacy
        } finally { connection.disconnect() }
        // Modern address matching distinguishes a real house number from a street-only result.
        val modern=URL("https://api.mapbox.com/search/geocode/v6/forward?q=${URLEncoder.encode(searchAddress(address),"UTF-8")}&types=address&autocomplete=false&country=us&limit=5&proximity=${origin.lon},${origin.lat}&access_token=${URLEncoder.encode(token,"UTF-8")}").openConnection() as HttpURLConnection
        try {
            modern.connectTimeout=10000;modern.readTimeout=12000
            if(modern.responseCode !in 200..299) throw failure(modern.responseCode)
            return parseV6(JSONObject(modern.inputStream.bufferedReader().use { it.readText() }),address,origin,entrances)
        } finally { modern.disconnect() }
    }
    fun parseV6(body: JSONObject,address: String,origin: Coordinate,entrances: Boolean=true): Coordinate? {
        val number=Regex("^\\d{1,6}[A-Za-z]?").find(address)?.value ?: return null
        val zip=Regex("\\b\\d{5}(?:-\\d{4})?\\b").find(address.substringAfter(',',""))?.value?.take(5)
        val features=body.optJSONArray("features") ?: return null
        val matches=(0 until features.length()).mapNotNull { i ->
            val feature=features.optJSONObject(i) ?: return@mapNotNull null
            val p=feature.optJSONObject("properties") ?: return@mapNotNull null
            if(p.optString("feature_type")!="address") return@mapNotNull null
            val context=p.optJSONObject("context") ?: return@mapNotNull null
            val a=context.optJSONObject("address") ?: return@mapNotNull null
            if(!a.optString("address_number").equals(number,true)) return@mapNotNull null
            val code=p.optJSONObject("match_code") ?: return@mapNotNull null
            if(code.optString("confidence") !in listOf("exact","high") || code.optString("street")!="matched" || code.optString("address_number")!="matched") return@mapNotNull null
            if(zip!=null && context.optJSONObject("postcode")?.optString("name")?.take(5)!=zip) return@mapNotNull null
            if(listOf("place","region","postcode").any { code.optString(it)=="unmatched" }) return@mapNotNull null
            val xy=feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            val center=DestinationAccess.Point(xy.optDouble(1,Double.NaN),xy.optDouble(0,Double.NaN))
            if(!center.lat.isFinite() || !center.lon.isFinite() || center.lat !in -90.0..90.0 || center.lon !in -180.0..180.0) return@mapNotNull null
            val access=DestinationAccess.parse(feature,center,entrances)
            val c=Coordinate(access.door.lat,access.door.lon,access.vehicle.lat.takeIf { access.vehicle!=access.door },access.vehicle.lon.takeIf { access.vehicle!=access.door },access.hasEntrance)
            c.takeIf { distance(origin,it)<=160000 }
        }
        val first=matches.firstOrNull() ?: return null
        return first.takeIf { matches.all { distance(first,it)<=75 } }
    }
    fun parse(body: JSONObject,address: String,origin: Coordinate,entrances: Boolean=true): Coordinate? {
        val number = Regex("^\\d{1,6}[A-Za-z]?").find(address)?.value ?: return null
        val features = body.optJSONArray("features") ?: return null
        val candidates = (0 until features.length()).mapNotNull { i ->
            val feature = features.optJSONObject(i) ?: return@mapNotNull null
            if (!feature.optString("address").equals(number,true) || feature.optDouble("relevance",0.0) < .8) return@mapNotNull null
            val center = feature.optJSONArray("center") ?: return@mapNotNull null
            val base=Coordinate(center.optDouble(1,Double.NaN),center.optDouble(0,Double.NaN))
            val access=DestinationAccess.parse(feature,DestinationAccess.Point(base.lat,base.lon),entrances)
            val coordinate=if(access.hasEntrance) Coordinate(access.door.lat,access.door.lon,access.vehicle.lat,access.vehicle.lon,true)
                else Coordinate(base.lat,base.lon,access.vehicle.lat.takeIf { access.vehicle.lat!=base.lat || access.vehicle.lon!=base.lon },access.vehicle.lon.takeIf { access.vehicle.lat!=base.lat || access.vehicle.lon!=base.lon })
            if (!coordinate.lat.isFinite() || !coordinate.lon.isFinite() || coordinate.lat !in -90.0..90.0 || coordinate.lon !in -180.0..180.0) return@mapNotNull null
            val distance = distance(origin,coordinate)
            if (distance > 160000) return@mapNotNull null
            Triple(coordinate,feature.optDouble("relevance"),distance)
        }.sortedWith(compareByDescending<Triple<Coordinate,Double,Double>> { it.second }.thenBy { it.third })
        val first = candidates.firstOrNull() ?: return null
        val second = candidates.getOrNull(1)
        if (second != null && first.second == second.second && distance(first.first,second.first) > 200) return null
        return first.first
    }
    fun parseNamedPlace(body: JSONObject,address: String,origin: Coordinate): Coordinate? {
        val (name,locality) = DasherAddressParser.namedPlaceParts(address) ?: return null
        val zip = Regex("""\b\d{5}\b""").find(locality)?.value ?: return null
        fun normalized(text: String) = text.lowercase(java.util.Locale.ROOT)
            .replace(Regex("""\bst\.?\b"""),"saint").replace(Regex("[^a-z0-9]+")," ").trim()
        val city = normalized(locality.substringBefore(',').replace(Regex("""\s+[A-Z]{2}\s+\d{5}.*$"""),""))
        val features = body.optJSONArray("features") ?: return null
        val matches = (0 until features.length()).mapNotNull { i ->
            val feature = features.optJSONObject(i) ?: return@mapNotNull null
            val p = feature.optJSONObject("properties") ?: return@mapNotNull null
            val names = mutableListOf(p.optString("name"),p.optString("name_preferred"))
            p.optJSONArray("alternate_names")?.let { aliases ->
                for (j in 0 until aliases.length()) names += aliases.optString(j)
            }
            if (names.none { NamedPlaceMatcher.matches(name,it) }) return@mapNotNull null
            val formatted = p.optString("full_address",p.optString("place_formatted"))
            val context = p.optJSONObject("context")
            val postcode = context?.optJSONObject("postcode")?.optString("name").orEmpty()
            val searchable = normalized(formatted+" "+context?.optJSONObject("place")?.optString("name").orEmpty())
            if (postcode.take(5) != zip && !Regex("""\b$zip\b""").containsMatchIn(formatted)) return@mapNotNull null
            if (!searchable.contains(city)) return@mapNotNull null
            val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            val coordinate = Coordinate(coords.optDouble(1),coords.optDouble(0))
            if (!coordinate.lat.isFinite() || !coordinate.lon.isFinite() || coordinate.lat !in -90.0..90.0 || coordinate.lon !in -180.0..180.0 || distance(origin,coordinate)>160000) return@mapNotNull null
            coordinate
        }
        val first = matches.firstOrNull() ?: return null
        if (matches.any { distance(first,it)>75 }) return null
        return first
    }
    private fun distance(a: Coordinate,b: Coordinate): Double {
        val lat = Math.toRadians((a.lat+b.lat)/2)
        val x = Math.toRadians(a.lon-b.lon)*kotlin.math.cos(lat);val y = Math.toRadians(a.lat-b.lat)
        return 6371000*kotlin.math.sqrt(x*x+y*y)
    }
}
