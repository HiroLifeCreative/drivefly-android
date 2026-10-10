package com.hirolifecreative.floatingmap

import java.text.Normalizer
import java.util.Locale

/** Compare institution identities while keeping branches and individual campus buildings distinct. */
object NamedPlaceMatcher {
    fun hotelIdentity(value: String): String {
        if (!Regex("\\b(?:hotel|motel|inn|resort|suites)\\b",RegexOption.IGNORE_CASE).containsMatchIn(value)) return value
        return value.replace(Regex(",?\\s+a member of\\s+.+$",RegexOption.IGNORE_CASE),"").trim()
    }
    fun normalized(value: String): String = Normalizer.normalize(hotelIdentity(value),Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("\\bst\\.?\\b"), "saint")
        .replace(Regex("\\b(?:residence halls?|residential halls?|dormitories|dormitory|dorms?|student housing)\\b"), "housing")
        .replace(Regex("\\bschools\\b"),"school")
        .replace(Regex("[^a-z0-9]+")," ").trim()

    fun matches(requested: String, candidate: String): Boolean {
        // A named tower is more specific than its containing institution, never substitute another tower.
        val identity = requested.substringAfter('·',requested).trim()
        val wanted = normalized(identity).split(' ').filter { it.isNotBlank() && it !in setOf("the","of","at") }
        val actual = normalized(candidate).split(' ').toSet()
        val generic = setOf("bank","school","college","university","housing","hospital","park","tower","building")
        if (wanted.none { it !in generic }) return false
        return wanted.all { it in actual }
    }

    fun queries(address: String): List<String> {
        val (name,locality) = DasherAddressParser.namedPlaceParts(address) ?: return listOf(address)
        val building = name.substringAfter('·', "").trim()
        val parent = name.substringBefore('·').trim()
        val variants = mutableListOf(if (building.isNotEmpty()) "$building, $locality" else address)
        variants += "$parent, $locality"
        variants += "${hotelIdentity(parent)}, $locality"
        val housing = parent.replace(Regex("\\b(?:dorms?|dormitory|dormitories)\\b",RegexOption.IGNORE_CASE),"Residence Halls")
        if (housing != parent) variants += "$housing, $locality"
        return variants.distinct().take(3)
    }
}
