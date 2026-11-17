package com.kalist.training

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock tests can move forward. */
class TestClock(var now: Instant = Instant.parse("2026-01-05T08:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
    fun advance(duration: Duration) { now = now.plus(duration) }
    fun setDate(date: LocalDate) { now = date.atTime(8, 0).toInstant(ZoneOffset.UTC) }
}
