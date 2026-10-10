package com.hirolifecreative.floatingmap

enum class DestinationKind(val color: String) {
    PICKUP("#16A765"),DELIVERY("#FF3B30"),OTHER("#172A4B");
    companion object {
        fun fromName(name: String): DestinationKind = when {
            name.startsWith("Dasher · Recogida:",true) -> PICKUP
            name.startsWith("Dasher · Entrega:",true) -> DELIVERY
            else -> OTHER
        }
        fun restore(saved: String,name: String): DestinationKind = entries.firstOrNull { it.name==saved } ?: fromName(name)
    }
}
