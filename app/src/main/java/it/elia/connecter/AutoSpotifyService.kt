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

       //CREIAMO LA NOTIFICA
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

        //aspetto della notifica
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AutoSpotify Attivo")
            .setContentText("In attesa di connettersi all'autoradio...")
            .setSmallIcon(android.R.drawable.ic_media_play) // Un'icona di sistema standard (puoi cambiarla in futuro)
            .setOngoing(true) // Rende la notifica fissa, l'utente non può strisciarla via
            .build()

        // Aumentiamo l'importanza della notifica
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            // Per Android 13 e inferiori
            startForeground(1, notification)
        }

        val filter = IntentFilter().apply {
            addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        // Registriamo l'orecchio: ascolterà gli eventi di sistema finché il servizio è vivo
        registerReceiver(bluetoothReceiver, filter)

        // START_STICKY (se viene chiuso, viene riaperto il prima possibile)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        //spegnamo anche l'orecchio
        unregisterReceiver(bluetoothReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}