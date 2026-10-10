package com.hirolifecreative.floatingmap
import android.content.Context
object RouteColors {
    fun line(context: Context)=NavigationColors.read(context).route
    fun casing(context: Context)=NavigationColorConfig.casing(line(context))
}
