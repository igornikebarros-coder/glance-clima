package com.igorbarros.glanceclima

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.Toast
import java.util.Calendar

class GlanceWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        WeatherWorker.ensureScheduled(context)
        scheduleMidnight(context)
    }

    override fun onDisabled(context: Context) {
        WeatherWorker.cancelAll(context)
        cancelMidnight(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, buildViews(context, manager.getAppWidgetOptions(it))) }
        WeatherWorker.ensureScheduled(context)
        scheduleMidnight(context)
        val w = Prefs.weather(context)
        if (w == null || System.currentTimeMillis() - w.updatedAt > STALE_MS) WeatherWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        manager.updateAppWidget(id, buildViews(context, newOptions))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                Toast.makeText(context, R.string.refreshing, Toast.LENGTH_SHORT).show()
                WeatherWorker.refreshNow(context)
            }
            ACTION_MIDNIGHT, Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                updateAll(context)
                if (hasWidgets(context)) scheduleMidnight(context)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (hasWidgets(context)) {
                    updateAll(context)
                    scheduleMidnight(context)
                    WeatherWorker.ensureScheduled(context)
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.igorbarros.glanceclima.REFRESH"
        const val ACTION_MIDNIGHT = "com.igorbarros.glanceclima.MIDNIGHT"
        private const val STALE_MS = 4 * 60 * 1000L

        private fun component(context: Context) = ComponentName(context, GlanceWidgetProvider::class.java)

        fun hasWidgets(context: Context): Boolean =
            AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()

        /** Redesenha todos os widgets com a data de agora e o último clima salvo. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(component(context)).forEach {
                manager.updateAppWidget(it, buildViews(context, manager.getAppWidgetOptions(it)))
            }
        }

        private fun buildViews(context: Context, options: Bundle?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_glance)
            val weather = Prefs.weather(context)

            views.setTextViewText(R.id.widget_date, DateFormatter.short())
            if (weather != null) {
                views.setTextViewText(R.id.widget_temp, "${weather.tempC}°C")
                views.setImageViewResource(R.id.widget_icon, WeatherIcons.forCode(weather.code, weather.isDay))
            } else {
                views.setTextViewText(R.id.widget_temp, context.getString(R.string.empty_temp))
                views.setImageViewResource(R.id.widget_icon, R.drawable.ic_w_cloudy)
            }

            applySizes(views, options)

            // Toque na data → calendário; toque no clima → atualiza agora.
            val calIntent = PendingIntent.getActivity(
                context, 1, Intent(context, CalendarActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_date, calIntent)

            val refreshIntent = PendingIntent.getBroadcast(
                context, 2, Intent(context, GlanceWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_weather, refreshIntent)
            return views
        }

        /** Ajusta o tamanho do texto/ícone conforme o tamanho atual do widget (redimensionável). */
        private fun applySizes(views: RemoteViews, options: Bundle?) {
            val w = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            if (w <= 0 || h <= 0) return
            val byWidth = (w - 24) / 8.6f
            val byHeight = (h - 12) / 2.1f
            val date = minOf(byWidth, byHeight).coerceIn(14f, 34f)
            val temp = date * 0.8f
            views.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_SP, date)
            views.setTextViewTextSize(R.id.widget_temp, TypedValue.COMPLEX_UNIT_SP, temp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val icon = temp * 1.65f
                views.setViewLayoutWidth(R.id.widget_icon, icon, TypedValue.COMPLEX_UNIT_SP)
                views.setViewLayoutHeight(R.id.widget_icon, icon, TypedValue.COMPLEX_UNIT_SP)
            }
        }

        private fun midnightIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 3, Intent(context, GlanceWidgetProvider::class.java).setAction(ACTION_MIDNIGHT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Agenda a virada da data (alarme inexato, não precisa de permissão especial). */
        fun scheduleMidnight(context: Context) {
            val next = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 5); set(Calendar.MILLISECOND, 0)
            }
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(AlarmManager.RTC, next.timeInMillis, midnightIntent(context))
        }

        fun cancelMidnight(context: Context) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(midnightIntent(context))
        }
    }
}
