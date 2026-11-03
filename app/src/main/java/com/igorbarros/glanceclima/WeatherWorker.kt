package com.igorbarros.glanceclima

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Busca o clima na Open-Meteo e atualiza o widget. */
class WeatherWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val place = Prefs.place(applicationContext)
        return try {
            Prefs.saveWeather(applicationContext, WeatherApi.fetchCurrent(place.lat, place.lon))
            GlanceWidgetProvider.updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            // Sem internet ou API fora do ar: mantém o último clima e tenta de novo.
            if (runAttemptCount >= 3) Result.success() else Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "glance_clima_periodic"
        private const val ONCE = "glance_clima_once"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Atualização periódica a cada ~30 min enquanto existir algum widget. */
        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<WeatherWorker>(30, TimeUnit.MINUTES)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }

        /** Atualização imediata (toque no clima, mudança de local, widget recém-adicionado). */
        fun refreshNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<WeatherWorker>()
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
