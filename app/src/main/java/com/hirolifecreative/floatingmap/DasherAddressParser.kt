package com.hirolifecreative.floatingmap

/** Uses only visible Dasher text. Ambiguous multi-stop screens never pick the first address. */
object DasherAddressParser {
    data class Destination(val address: String,val stage: String,val merchant: String="",val unit: String="")
    private val street = Regex("""\b\d{1,6}[A-Za-z]?\s+[\p{L}0-9 .'-]{1,65}?\s(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Drive|Dr|Lane|Ln|Court|Ct|Circle|Cir|Way|Place|Pl|Terrace|Ter|Parkway|Pkwy|Highway|Hwy|Trail|Trl|Loop|Alley|Aly)\b(?:\s+\d{1,4}[A-Za-z]?)?(?:\s+(?:N|S|E|W|NE|NW|SE|SW|North|South|East|West)\b)?\.?""",RegexOption.IGNORE_CASE)
    private val prefixedStreet = Regex("""\b\d{1,6}[A-Za-z]?\s+(?:Cll|Calle|Camino|Cam|Via|Avenida|Avda|Paseo|Plaza)\s+[\p{L}0-9 .'-]{2,65}""",RegexOption.IGNORE_CASE)
    private val locality = Regex("""^[\p{L} .'-]+,?\s+[A-Z]{2}\s+\d{5}(?:-\d{4})?(?:,?\s+United States)?$""",RegexOption.IGNORE_CASE)
    fun namedPlaceParts(query: String): Pair<String,String>? {
        // Names may contain commas; split only where the remainder is a locality.
        val split = query.indices.firstOrNull { query[it]==',' && locality.matches(query.substring(it+1).trim()) } ?: return null
        val parts = listOf(query.substring(0,split),query.substring(split+1))
        val name = parts[0].trim()
        if (name.length !in 3..160 || !name.any { it.isLetter() } || name.first().isDigit()) return null
        val ui = listOf("llamar","mensaje","indicaciones","continuar","reunirse afuera","entregar a","deliver to","call","message","directions","leave at door","hand it to me")
        if (ui.any { name.lowercase(java.util.Locale.ROOT).startsWith(it) }) return null
        return name to parts[1].trim()
    }
    fun select(texts: List<String>): Destination? {
        val lines = texts.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }
        // Some accessibility trees split the house number and street into adjacent labels.
        val joined = lines.mapIndexedNotNull { i,line ->
            val next=lines.getOrNull(i+1) ?: return@mapIndexedNotNull null
            if (Regex("^\\d{1,6}[A-Za-z]?$").matches(line) && !next.first().isDigit() &&
                (street.containsMatchIn("$line $next") || prefixedStreet.containsMatchIn("$line $next"))) "$line $next" else null
        }
        val effectiveLines=lines+joined.map { value ->
            val number=value.substringBefore(' ')
            val index=lines.indexOf(number)
            val city=lines.getOrNull(index+2).orEmpty()
            if(locality.matches(city)) "$value, $city" else value
        }
        fun destination(address: String,phase: String)=Destination(address,phase,
            if(phase=="Recogida") DeliverySpeech.merchant(texts).orEmpty() else "",DeliverySpeech.unit(texts))
        val all = lines.joinToString("\n").lowercase(java.util.Locale.ROOT)
        if (lines.any(DasherOfferPolicy::isAccept) ||
            listOf("delivery complete","entrega completada","dash paused").any { it in all }) return null
        val delivery = listOf("complete delivery","confirm delivery","arrived at customer","completar entrega","confirmar entrega","entregar a","deliver to").any { it in all }
        val pickup = listOf("confirm pickup","complete pickup","arrived at store","confirmar recogida","recoger pedido","llegué a la tienda","para llevar antes","pick up before","pick up by","recoger antes").any { it in all }
        if (delivery && pickup) return null
        val deliveryInstructions = listOf("leave at door","hand it to me","dejar en la puerta","dejar en el vestíbulo","dejar en el vestibulo","leave in lobby","leave at lobby").any { it in all }
        val stage = if (pickup) "Recogida" else if (delivery || deliveryInstructions) "Entrega" else "Destino"
        val candidates = effectiveLines.mapIndexedNotNull { i,line ->
            val previous=effectiveLines.getOrNull(i-1).orEmpty().lowercase(java.util.Locale.ROOT).replace(Regex("[^a-záéíóúñ]+")," ").trim()
            if (previous in setOf("apt suite","apt","suite","apartamento","unit","apartment","código de acceso","access code","nombre del edificio","building name")) return@mapIndexedNotNull null
            val match = prefixedStreet.find(line) ?: street.find(line) ?: return@mapIndexedNotNull null
            var address = match.value.trim()
            val tail = line.substring(match.range.last+1).trim()
            if (tail.startsWith(",") || Regex("^(?:(?:apt|unit|suite|apartment)\\b|#)",RegexOption.IGNORE_CASE).containsMatchIn(tail)) address += if (tail.startsWith(",")) tail else " $tail"
            var next = effectiveLines.getOrNull(i+1).orEmpty()
            if (Regex("^(apt|apartment|unit|suite|apartamento|#)\\s*.+$",RegexOption.IGNORE_CASE).matches(next)) {
                address += ", $next";next = effectiveLines.getOrNull(i+2).orEmpty()
            }
            if (Regex("^[\\p{L} .'-]+,?\\s+[A-Z]{2}\\s+\\d{5}(?:-\\d{4})?(?:,?\\s+United States)?$",RegexOption.IGNORE_CASE).matches(next)) address += ", $next"
            val context = effectiveLines.subList((i-3).coerceAtLeast(0),i+1).joinToString(" ").lowercase(java.util.Locale.ROOT)
            val role = when {
                listOf("delivery address","customer address","deliver to","drop off","drop-off","dirección de entrega","cliente","entregar a").any { it in context } -> "Entrega"
                listOf("pickup address","restaurant address","pick up at","pickup at","recogida","restaurante","store address").any { it in context } -> "Recogida"
                else -> "Destino"
            }
            Triple(address.take(180),role,context)
        }.groupBy {
            // Accessibility can expose the same address in a card and its child label.
            val core = prefixedStreet.find(it.first)?.value ?: street.find(it.first)?.value ?: it.first
            core.trim().trimEnd('.').lowercase(java.util.Locale.ROOT).replace(Regex("\\s+")," ")
        }.values.map { group ->
            group.maxByOrNull { it.first.length + if (it.second == stage && stage != "Destino") 1000 else 0 }!!
        }
        if (candidates.size == 1) return destination(candidates.single().first,if (stage == "Destino") candidates.single().second else stage)
        if (candidates.isEmpty() && stage != "Destino") {
            val places = lines.mapIndexedNotNull { i,line ->
                val preceding=lines.getOrNull(i-1).orEmpty()
                val wrapped=Regex("^(?:of |a member of |member of )",RegexOption.IGNORE_CASE).containsMatchIn(line)
                val name=if(wrapped && Regex("\\b(?:hotel|motel|inn|resort|suites)\\b",RegexOption.IGNORE_CASE).containsMatchIn(preceding)) "$preceding $line" else line
                val combined = if (locality.matches(lines.getOrNull(i+1).orEmpty())) "$name, ${lines[i+1]}" else line
                namedPlaceParts(combined)?.let { "${it.first}, ${it.second}" }
            }.distinctBy { it.lowercase(java.util.Locale.ROOT).replace(Regex("\\s+")," ") }
            return places.singleOrNull()?.let { place ->
                // Building names are useful for campuses; unit/floor/access-code fields are not destinations.
                val labels = setOf("nombre del edificio", "building name")
                val buildings = lines.mapIndexedNotNull { i, value ->
                    if (value.lowercase(java.util.Locale.ROOT) !in labels) return@mapIndexedNotNull null
                    lines.getOrNull(i+1)?.takeIf { candidate ->
                        candidate.length in 3..160 && candidate.any { it.isLetter() } &&
                            candidate.lowercase(java.util.Locale.ROOT) !in setOf("null", "none", "n/a") &&
                            !candidate.contains('\n')
                    }
                }.distinct()
                val parts = namedPlaceParts(place)!!
                val building = buildings.singleOrNull()
                destination(if (building == null || parts.first.contains(building,true)) place
                    else "${parts.first} · $building, ${parts.second}",stage)
            }
        }
        val matching = candidates.filter { stage != "Destino" && it.second == stage }
        return matching.singleOrNull()?.let { destination(it.first,stage) }
    }
}
