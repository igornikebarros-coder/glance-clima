package com.igorbarros.glanceclima

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Tela de configuração (aberta ao adicionar o widget ou pelo ícone do app):
 * localização aproximada, São Paulo como padrão, ou busca de cidade pela geocodificação da Open-Meteo.
 */
class SetupActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var currentText: TextView
    private lateinit var statusText: TextView
    private lateinit var cityInput: EditText
    private lateinit var results: LinearLayout
    private var locationListener: LocationListener? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Se o usuário sair sem confirmar, o widget não é cancelado (a configuração é opcional).
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }
        buildUi()
        refreshCurrent()
    }

    override fun onDestroy() {
        locationListener?.let { (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(it) }
        io.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi() {
        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(32), pad, pad)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name); textSize = 28f; setTypeface(typeface, Typeface.BOLD); setTextColor(0xFF0F1B33.toInt())
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.setup_intro); textSize = 15f; setPadding(0, dp(6), 0, dp(16)); setTextColor(0xFF4A5568.toInt())
        })
        currentText = TextView(this).apply { textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(0xFF2D5FA8.toInt()) }
        root.addView(currentText)
        statusText = TextView(this).apply { textSize = 14f; setPadding(0, dp(6), 0, dp(10)); setTextColor(0xFF4A5568.toInt()) }
        root.addView(statusText)

        root.addView(button(R.string.btn_use_location) { requestLocation() })
        root.addView(button(R.string.btn_use_default) {
            Prefs.resetToDefault(this)
            onSaved()
        })

        cityInput = EditText(this).apply {
            hint = getString(R.string.hint_city)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine()
            setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEARCH) { searchCity(); true } else false }
        }
        root.addView(cityInput, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        root.addView(button(R.string.btn_search_city) { searchCity() })
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results)
        root.addView(TextView(this).apply {
            text = getString(R.string.add_widget_hint); textSize = 13f; setPadding(0, dp(24), 0, 0); setTextColor(0xFF718096.toInt())
        })
        setContentView(ScrollView(this).apply { addView(root); isFillViewport = true })
    }

    private fun button(textRes: Int, onClick: () -> Unit) = Button(this).apply {
        setText(textRes); isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) }
    }

    private fun refreshCurrent() {
        val p = Prefs.place(this)
        val label = when {
            p.isDefault -> getString(R.string.loc_default)
            p.fromGps -> getString(R.string.loc_gps, fmt(p.lat), fmt(p.lon))
            else -> p.name
        }
        currentText.text = getString(R.string.setup_current, label)
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)

    private fun onSaved() {
        refreshCurrent()
        statusText.setText(R.string.saved)
        WeatherWorker.refreshNow(this)
        GlanceWidgetProvider.updateAll(this)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }

    // ----- cidade (geocodificação Open-Meteo) -----
    private fun searchCity() {
        val q = cityInput.text.toString().trim()
        if (q.length < 2) return
        results.removeAllViews()
        statusText.setText(R.string.searching)
        io.execute {
            val found = try { WeatherApi.searchCities(q) } catch (e: Exception) { null }
            main.post {
                if (isDestroyed) return@post
                results.removeAllViews()
                when {
                    found == null -> statusText.setText(R.string.search_error)
                    found.isEmpty() -> statusText.setText(R.string.no_results)
                    else -> {
                        statusText.text = ""
                        found.forEach { city ->
                            results.addView(Button(this).apply {
                                isAllCaps = false
                                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                                text = if (city.detail.isEmpty()) city.name else "${city.name}\n${city.detail}"
                                setOnClickListener {
                                    Prefs.savePlace(this@SetupActivity, city.name, city.lat, city.lon, fromGps = false)
                                    onSaved()
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    // ----- localização aproximada -----
    private fun requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_LOCATION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) fetchLocation()
        else statusText.setText(R.string.location_denied)
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun fetchLocation() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) || it == LocationManager.PASSIVE_PROVIDER }

        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null) { onLocation(last); return }

        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
        if (provider == null) { statusText.setText(R.string.location_failed); return }
        statusText.setText(R.string.locating)
        var done = false
        val finish = { loc: Location? ->
            if (!done) {
                done = true
                if (loc != null) onLocation(loc) else statusText.setText(R.string.location_failed)
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.getCurrentLocation(provider, null, mainExecutor) { finish(it) }
            } else {
                val l = LocationListener { finish(it) }
                locationListener = l
                lm.requestSingleUpdate(provider, l, Looper.getMainLooper())
                main.postDelayed({ finish(null) }, 20_000)
            }
        } catch (e: Exception) {
            finish(null)
        }
    }

    private fun onLocation(loc: Location) {
        // Coordenadas arredondadas: basta uma localização aproximada para o clima.
        Prefs.savePlace(this, "Localização atual", Math.round(loc.latitude * 100) / 100.0, Math.round(loc.longitude * 100) / 100.0, fromGps = true)
        onSaved()
    }

    companion object {
        private const val REQ_LOCATION = 42
    }
}
