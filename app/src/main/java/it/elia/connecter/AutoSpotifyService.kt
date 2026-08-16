package it.elia.connecter
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo

class AutoSpotifyService : Service() {
    private lateinit var bluetoothReceiver: BluetoothReceiver

    override fun onCreate() {
        super.onCreate()
        bluetoothReceiver = BluetoothReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // 1. CREIAMO LA NOTIFICA (Obbligatoria per i Foreground Service)
        val channelId = "AutoSpotifyChannel"

        // Su Android 8.0+ le notifiche hanno bisogno di un "Canale"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Servizio AutoSpotify",
                NotificationManager.IMPORTANCE_LOW // LOW significa che non suona/vibra, sta solo lì silenziosa
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // Costruiamo l'aspetto della notifica
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AutoSpotify Attivo")
            .setContentText("In attesa di connettersi all'autoradio...")
            .setSmallIcon(android.R.drawable.ic_media_play) // Un'icona di sistema standard (puoi cambiarla in futuro)
            .setOngoing(true) // Rende la notifica fissa, l'utente non può strisciarla via
            .build()

        // DICIAMO AD ANDROID: "Sono un servizio importante, ecco la mia notifica, non chiudermi!"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            // Per Android 13 e inferiori, la vecchia riga funziona perfettamente
            startForeground(1, notification)
        }

        // 2. ACCENDIAMO L'ORECCHIO (Registriamo il Receiver dinamicamente)
        val filter = IntentFilter().apply {
            addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        // Registriamo l'orecchio: ora ascolterà gli eventi di sistema finché il servizio è vivo
        registerReceiver(bluetoothReceiver, filter)

        // START_STICKY dice ad Android: "Se per cause di forza maggiore mi chiudi, riavviami appena puoi"
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Quando spegniamo il servizio, dobbiamo spegnere anche l'Orecchio per evitare errori
        unregisterReceiver(bluetoothReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? {
        // I "Bound Service" sono un'altra cosa, a noi qui basta ritornare null
        return null
    }
}