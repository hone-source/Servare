package com.spiralsoft.servare.testutil

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

// Reloj que solo avanza cuando la prueba lo dice
// el backoff y la retención de 7 días se prueban sin esperar
class RelojFalso(var ahora: Long = 1_700_000_000_000L) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(ahora)
    override fun millis(): Long = ahora

    fun avanzar(ms: Long) {
        ahora += ms
    }
}
