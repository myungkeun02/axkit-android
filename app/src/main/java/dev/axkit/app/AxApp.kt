package dev.axkit.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class AxApp : Application() {
    lateinit var model: AppModel; private set

    override fun onCreate() {
        super.onCreate()
        model = AppModel(this, KeystoreCredentialStore(this))
        Notifier.ensureChannel(this)
        if (model.isPaired) CatchUpWorker.schedule(this)
        if (model.isPaired && model.alwaysOn) { AlwaysOnService.start(this); model.start() }
        // Back on a network (Wi-Fi ↔ LTE, out of a tunnel): reconnect at once instead of waiting out the backoff.
        getSystemService(android.net.ConnectivityManager::class.java)?.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { android.os.Handler(mainLooper).post { model.networkAvailable(network) } }
            override fun onLost(network: android.net.Network) { android.os.Handler(mainLooper).post { model.networkLost(network) } }
        })
        // The stream lives only while the app is in front (§3.1); catch-up fills the gap on return.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { model.inFront = true; model.start() }
            override fun onStop(owner: LifecycleOwner) {
                model.inFront = false
                // Keep the stream while recording (uploads) or when the owner chose to stay connected.
                if (!model.recorder.isRecording && !model.alwaysOn) model.stop()
            }
        })
    }
}
