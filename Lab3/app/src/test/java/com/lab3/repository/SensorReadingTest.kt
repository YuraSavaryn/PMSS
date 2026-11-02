package com.lab3.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorReadingTest {

    @Test
    fun toPayload_copiesAllFieldsAndAddsDeviceId() {
        val reading = SensorReading(TYPE_ACCELEROMETER, 9.8f, 1f, 2f, 3f, timestamp = 42L)

        val payload = reading.toPayload("device-1")

        assertEquals(SensorPayload("device-1", TYPE_ACCELEROMETER, 9.8f, 1f, 2f, 3f, 42L), payload)
    }
}
