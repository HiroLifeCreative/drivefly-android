package com.hirolifecreative.floatingmap

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.Screen
import androidx.car.app.validation.HostValidator

/** Projected Android Auto shares the :map process and its authoritative navigation session. */
class DriveFlyCarService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        // Uso personal: permitir cualquier host de Android Auto (incluye BMW real).
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = DriveFlyCarScreen(carContext)
    }
}
