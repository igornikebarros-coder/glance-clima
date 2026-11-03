package com.igorbarros.glanceclima

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Busca o clima na Open-Meteo e atualiza o widget.
 *
 * O WorkManager periódico tem mínimo de 15 min, então a atualização a cada 5 min é uma
 * cadeia de trabalhos únicos: ao terminar, cada execução agenda a próxima com 5 min de atraso.
 * Continua funcionando após reiniciar o aparelho e respeita Doze/economia de bateria.
 */
class WeatherWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val place = Prefs.place(ctx)
        return try {
            Prefs.saveWeather(ctx, WeatherApi.fetchCurrent(place.lat, place.lon))
            GlanceWidgetProvider.updateAll(ctx)
            scheduleNext(ctx)
            Result.success()
        } catch (e: Exception) {
            // Sem internet ou API fora do ar: mantém o último clima e tenta de novo.
            if (runAttemptCount >= 3) {
                scheduleNext(ctx)
                Result.success()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        private const val CHAIN = "glance_clima_chain"
        const val INTERVAL_MIN = 5L

        // Nomes das versões 1.0 (periódico de 30 min), cancelados ao atualizar.
        private val LEGACY = listOf("glance_clima_periodic", "glance_clima_once")

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        private fun request(delayMin: Long) = OneTimeWorkRequestBuilder<WeatherWorker>()
            .setConstraints(network)
            .setInitialDelay(delayMin, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .build()

        /** Garante que a cadeia de atualizações exista (não mexe nela se já estiver agendada ou rodando). */
        fun ensureScheduled(context: Context) {
            val wm = WorkManager.getInstance(context)
            LEGACY.forEach { wm.cancelUniqueWork(it) }
            wm.enqueueUniqueWork(CHAIN, ExistingWorkPolicy.KEEP, request(INTERVAL_MIN))
        }

        /** Chamado no fim de cada execução: agenda a próxima em 5 min, se ainda houver widgets. */
        private fun scheduleNext(context: Context) {
            if (!GlanceWidgetProvider.hasWidgets(context)) return
            // REPLACE: a execução atual já terminou o trabalho útil; o resultado dela é irrelevante.
            WorkManager.getInstance(context).enqueueUniqueWork(CHAIN, ExistingWorkPolicy.REPLACE, request(INTERVAL_MIN))
        }

        fun cancelAll(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(CHAIN)
            LEGACY.forEach { wm.cancelUniqueWork(it) }
        }

        /** Atualização imediata (toque no clima, mudança de local, widget recém-adicionado); depois segue a cadeia de 5 min. */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(CHAIN, ExistingWorkPolicy.REPLACE, request(0))
        }
    }
}
