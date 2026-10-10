package com.hirolifecreative.floatingmap

/** Explicit roles preserve semantic blue/red/green and white text on primary actions. */
object ThemePalette {
    private fun rgb(hex: String) = (0xff000000L or hex.removePrefix("#").toLong(16)).toInt()
    private val backgrounds = mapOf(
        rgb("FFFFFF") to rgb("20242C"),rgb("F2F2F7") to rgb("101318"),
        rgb("F2F3F5") to rgb("2B303A"),rgb("E8E8E8") to rgb("101318"),
        rgb("E5E5EA") to rgb("3A414D"),rgb("EAF2FF") to rgb("19324D"),
        rgb("E8F0FE") to rgb("19324D"),rgb("EDF4FF") to rgb("19324D"),
        rgb("E4F7F5") to rgb("153D3D"),rgb("F1F3F6") to rgb("2B303A"),
        rgb("F5F7FA") to rgb("101318")
    )
    private val texts = mapOf(
        rgb("1C2635") to rgb("F4F6FA"),rgb("1C1C1E") to rgb("F4F6FA"),rgb("000000") to rgb("F4F6FA"),
        rgb("333333") to rgb("F4F6FA"),rgb("636366") to rgb("AAB3C1"),
        rgb("667384") to rgb("B2BCC9"),rgb("087F8B") to rgb("5FE2DD"),
        rgb("666666") to rgb("AAB3C1"),rgb("596573") to rgb("AAB3C1"),
        rgb("8E8E93") to rgb("ABB4C2"),rgb("007AFF") to rgb("64ABFF")
    )
    private fun change(value: Int,dark: Boolean,map: Map<Int,Int>): Int = if (dark) map[value] ?: value else map.entries.firstOrNull { it.value==value }?.key ?: value
    fun background(value: Int,dark: Boolean) = change(value,dark,backgrounds)
    fun text(value: Int,dark: Boolean) = change(value,dark,texts)
}
