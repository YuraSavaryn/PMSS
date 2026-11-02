package com.lab3.repository

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.lab3.BuildConfig
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException

/** Як запис виглядає в JSON у Firebase. Значення за замовчуванням дають конструктор без аргументів. */
data class SensorPayload(
    val deviceId: String = "",
    val sensorType: String = "",
    val value: Float = 0f,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val timestamp: Long = 0L
)

/** Запис у Firebase Realtime Database: sensor_readings/{deviceId}/{pushId}. */
class CloudApi {

    private companion object {
        const val ROOT_PATH = "sensor_readings"
        const val UPLOAD_TIMEOUT_MS = 5_000L
    }

    // Lazy: помилка конфігурації стане невдалим запитом, а не падінням додатка при запуску
    private val root: DatabaseReference by lazy {
        val url = BuildConfig.FIREBASE_DB_URL
        val database =
            if (url.isEmpty()) FirebaseDatabase.getInstance() else FirebaseDatabase.getInstance(url)
        // Записи без мережі зберігаються на диску і дійдуть, навіть якщо додаток перезапустять
        database.setPersistenceEnabled(true)
        database.getReference(ROOT_PATH)
    }

    /**
     * Без мережі Firebase не повертає помилку: Task чекає, доки зв'язок відновиться.
     * Тому чекаємо лише обмежений час і повідомляємо про тайм-аут, а сам запис
     * лишається в черзі SDK і буде доставлений автоматично.
     */
    suspend fun upload(payload: SensorPayload) {
        val task = root.child(payload.deviceId).push().setValue(payload)
        withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
            task.await()
            true
        } ?: throw TimeoutException("Firebase не підтвердив запис за $UPLOAD_TIMEOUT_MS мс")
    }
}
