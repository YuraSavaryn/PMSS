package com.lab3

import android.app.Application
import android.provider.Settings
import com.lab3.repository.CloudApi
import com.lab3.repository.SensorRepository

/** Тримає єдиний на весь додаток репозиторій. */
class Lab3App : Application() {

    val sensorRepository: SensorRepository by lazy {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        SensorRepository(CloudApi(), deviceId)
    }
}
