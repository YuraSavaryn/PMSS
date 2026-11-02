package com.lab3.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Одне місце для даних: ViewModel не знає, куди і як вони відправляються. */
class SensorRepository(
    private val api: CloudApi,
    private val deviceId: String
) {

    suspend fun save(reading: SensorReading): Result<Unit> =
        withContext(Dispatchers.IO) { // мережа — не в головному потоці
            runCatching { api.upload(reading.toPayload(deviceId)) }
        }
}
