package dev.axkit.app.signals

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Telephony
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.axkit.app.AxApp
import dev.axkit.core.ApiClient
import dev.axkit.core.BatteryRule
import dev.axkit.core.SignalFlush
import dev.axkit.core.SignalItem
import dev.axkit.core.SignalKind
import dev.axkit.core.SignalQueue
import dev.axkit.core.Signals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * 휴대폰 신호 (MacroDroid 대신): settings, the retry queue and the sender. Nothing is sent unless the owner turned that
 * kind on in 설정 › 휴대폰 신호; everything goes only to the paired Mac.
 */
object PhoneSignals {
    private const val PREFS = "signals"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendLock = Mutex()
    @Volatile private var queue: SignalQueue? = null

    fun queue(context: Context): SignalQueue = queue ?: synchronized(this) {
        queue ?: SignalQueue(File(context.applicationContext.filesDir, "signals-queue.json")).also { queue = it }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // MARK: switches (all off until the owner turns them on)

    fun isOn(context: Context, kind: SignalKind) = prefs(context).getBoolean("on.${kind.wire}", false)

    fun setOn(context: Context, kind: SignalKind, on: Boolean) {
        prefs(context).edit().putBoolean("on.${kind.wire}", on).apply()
        refresh(context)
    }

    fun anyOn(context: Context) = SignalKind.entries.any { isOn(context, it) }

    /** "모든 앱" or the chosen list (null until the owner changes it: then the defaults apply). */
    fun allApps(context: Context) = prefs(context).getBoolean("apps.all", false)
    fun setAllApps(context: Context, all: Boolean) = prefs(context).edit().putBoolean("apps.all", all).apply()
    fun chosenApps(context: Context): Set<String>? = prefs(context).getStringSet("apps.chosen", null)?.toSet()
    fun setChosenApps(context: Context, apps: Set<String>) = prefs(context).edit().putStringSet("apps.chosen", apps).apply()

    fun smsApp(context: Context): String? = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()

    /** Is this app's notice wanted? */
    fun wants(context: Context, packageName: String, label: String): Boolean {
        if (allApps(context)) return true
        val chosen = chosenApps(context) ?: return Signals.isDefaultApp(packageName, label, smsApp(context))
        return packageName in chosen
    }

    fun lastSent(context: Context, kind: SignalKind): Long = prefs(context).getLong("sent.${kind.wire}", 0)
    private fun markSent(context: Context, kinds: Set<String>, at: Long) {
        val e = prefs(context).edit()
        kinds.forEach { e.putLong("sent.$it", at) }
        e.apply()
    }
    fun lastProblem(context: Context): String? = prefs(context).getString("problem", null)
    private fun setProblem(context: Context, text: String?) = prefs(context).edit().putString("problem", text).apply()

    // MARK: permissions

    fun hasNotificationAccess(context: Context) = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun hasLocation(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(context: Context) = hasLocation(context) &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun ignoresBatteryOptimizations(context: Context) =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    // MARK: queue and send

    /** Keeps the item (only if its kind is on) and tries to send at once; WorkManager retries when offline. */
    fun record(context: Context, item: SignalItem) {
        val kind = SignalKind.of(item.kind) ?: return
        if (!isOn(context, kind)) return
        val app = context.applicationContext
        scope.launch {
            queue(app).add(item)
            sendNow(app)
        }
    }

    /** Sends what is queued; on a passing problem hands the retry to WorkManager. */
    suspend fun sendNow(context: Context): SignalFlush? = sendLock.withLock {
        val credentials = (context.applicationContext as? AxApp)?.model?.credentials ?: return null
        val client = ApiClient(credentials)
        val result = queue(context).flush { client.ingest(it) }
        if (result.sentKinds.isNotEmpty()) markSent(context, result.sentKinds, System.currentTimeMillis())
        when {
            result.unauthorized -> setProblem(context, "맥이 이 휴대폰을 더 이상 알지 못해요. 설정에서 다시 연결해 주세요.")
            result.retryLater -> { setProblem(context, "맥에 닿지 않아서 휴대폰에 모아 두었어요. 닿으면 바로 보내요."); retryLater(context) }
            else -> setProblem(context, null)
        }
        result
    }

    private fun retryLater(context: Context) {
        val request = OneTimeWorkRequestBuilder<SignalsWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInitialDelay(30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("signals-retry", ExistingWorkPolicy.KEEP, request)
    }

    /** After a switch, pairing or boot: the periodic check and location updates follow the switches. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val paired = (app as? AxApp)?.model?.isPaired == true
        val wm = WorkManager.getInstance(app)
        if (paired && anyOn(app)) {
            val request = PeriodicWorkRequestBuilder<SignalsWorker>(15, TimeUnit.MINUTES).build()
            wm.enqueueUniquePeriodicWork("signals-periodic", ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            wm.cancelUniqueWork("signals-periodic")
        }
        if (paired && isOn(app, SignalKind.Location) && hasLocation(app)) LocationSignals.start(app) else LocationSignals.stop(app)
    }

    /** 연결 끊기: nothing is kept for the next Mac. */
    fun forget(context: Context) {
        scope.launch { queue(context).clear() }
        LocationSignals.stop(context)
        WorkManager.getInstance(context).cancelUniqueWork("signals-periodic")
        WorkManager.getInstance(context).cancelUniqueWork("signals-retry")
    }

    // MARK: battery

    fun batteryLevel(context: Context): Pair<Int, Boolean>? {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        return (level * 100 / scale) to plugged
    }

    /** One notice below 20%. */
    fun checkBattery(context: Context) {
        if (!isOn(context, SignalKind.Battery)) return
        val (level, charging) = batteryLevel(context) ?: return
        val armed = prefs(context).getBoolean("battery.armed", true)
        val (send, stillArmed) = BatteryRule.check(level, charging, armed)
        if (stillArmed != armed) prefs(context).edit().putBoolean("battery.armed", stillArmed).apply()
        if (send) record(context, SignalItem(kind = "battery", at = System.currentTimeMillis(), level = level))
    }

    /** 지금 보내 보기: the battery level now (and where the phone is, if 위치 is on), sent at once. */
    suspend fun test(context: Context): String {
        if ((context.applicationContext as? AxApp)?.model?.isPaired != true) return "먼저 맥과 연결해 주세요."
        val now = System.currentTimeMillis()
        batteryLevel(context)?.let { (level, _) -> queue(context).add(SignalItem(kind = "battery", at = now, level = level)) }
        if (isOn(context, SignalKind.Location) && hasLocation(context)) {
            LocationSignals.current(context)?.takeIf { LocationSignals.isNew(context, it) }?.let { queue(context).add(LocationSignals.item(context, it)) }
        }
        if (isOn(context, SignalKind.Steps)) StepsSignals.today(context)?.let { queue(context).add(it) }
        val result = sendNow(context) ?: return "먼저 맥과 연결해 주세요."
        return when {
            result.unauthorized -> "맥이 이 휴대폰을 더 이상 알지 못해요. 설정에서 다시 연결해 주세요."
            result.retryLater -> "맥에 닿지 않아요. 휴대폰에 모아 두었다가 닿으면 보낼게요."
            result.sent > 0 -> "맥이 받았어요 (${result.sent}개)."
            else -> "보낼 것이 없었어요."
        }
    }
}

/** Every 15 minutes (and on a retry): battery, today's steps, and whatever is still queued. */
class SignalsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        if ((app as? AxApp)?.model?.isPaired != true) return Result.success()
        PhoneSignals.checkBattery(app)
        StepsSignals.maybeRecord(app)
        val result = PhoneSignals.sendNow(app) ?: return Result.success()
        return if (result.retryLater && runAttemptCount < 8) Result.retry() else Result.success()
    }
}

/** After a restart or an app update, location updates are asked for again (WorkManager keeps itself). */
class SignalsBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) PhoneSignals.refresh(context)
    }
}

/**
 * 위치: about every 30 minutes while the phone moves (and sooner on a big move), balanced power, delivered to
 * [LocationReceiver] even with the app closed. Google Play's fused location; the platform's own on a phone without it.
 */
object LocationSignals {
    private const val INTERVAL = 30 * 60_000L
    private const val FASTEST = 5 * 60_000L
    private const val DISTANCE = 250f

    private fun usePlay(context: Context) =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 7, Intent(context, LocationReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (!PhoneSignals.hasLocation(context)) return
        if (usePlay(context)) {
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL)
                .setMinUpdateIntervalMillis(FASTEST)
                .setMinUpdateDistanceMeters(DISTANCE)
                .build()
            LocationServices.getFusedLocationProviderClient(context).requestLocationUpdates(request, pending(context))
        } else {
            val lm = context.getSystemService(LocationManager::class.java) ?: return
            val provider = if (Build.VERSION.SDK_INT >= 31 && lm.hasProvider(LocationManager.FUSED_PROVIDER)) LocationManager.FUSED_PROVIDER else LocationManager.NETWORK_PROVIDER
            runCatching { lm.requestLocationUpdates(provider, INTERVAL, DISTANCE, pending(context)) }
        }
    }

    fun stop(context: Context) {
        runCatching { LocationServices.getFusedLocationProviderClient(context).removeLocationUpdates(pending(context)) }
        runCatching { context.getSystemService(LocationManager::class.java)?.removeUpdates(pending(context)) }
    }

    /** One fix now (for 지금 보내 보기), at most 20 seconds. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? = withTimeoutOrNull(20_000) {
        if (usePlay(context)) suspendCancellableCoroutine { cont ->
            LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnCompleteListener { task -> if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null) }
        } else {
            val lm = context.getSystemService(LocationManager::class.java)
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstNotNullOfOrNull { p -> runCatching { lm?.getLastKnownLocation(p) }.getOrNull() }
        }
    }

    /** The same fix can come twice (a one-off request also feeds the standing updates): send each fix once. */
    @Synchronized fun isNew(context: Context, location: Location): Boolean {
        val prefs = context.getSharedPreferences("signals", Context.MODE_PRIVATE)
        val key = "${location.time}:${location.latitude}:${location.longitude}"
        if (prefs.getString("location.last", null) == key) return false
        prefs.edit().putString("location.last", key).commit()
        return true
    }

    fun item(context: Context, location: Location) = SignalItem(
        kind = "location",
        at = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
        lat = location.latitude,
        lng = location.longitude,
        accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
        label = placeLabel(context, location),
    )

    /** "서울특별시 강남구 역삼동" when the phone can name the place; nothing otherwise. */
    @Suppress("DEPRECATION")
    fun placeLabel(context: Context, location: Location): String? {
        if (!Geocoder.isPresent()) return null
        val address: Address = runCatching { Geocoder(context, Locale.KOREA).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull() }.getOrNull() ?: return null
        val parts = listOf(address.adminArea, address.locality, address.subLocality, address.thoroughfare)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        return parts.joinToString(" ").takeIf { it.isNotEmpty() } ?: address.featureName
    }
}

class LocationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val location = LocationResult.extractResult(intent)?.lastLocation
            ?: @Suppress("DEPRECATION") intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED) as? Location
            ?: return
        val app = context.applicationContext
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (LocationSignals.isNew(app, location)) PhoneSignals.record(app, LocationSignals.item(app, location))
                PhoneSignals.checkBattery(app)
            } finally { done.finish() }
        }
    }
}

/** The listener's own component, for opening its settings page. */
fun listenerComponent(context: Context) = ComponentName(context, SignalListener::class.java)
