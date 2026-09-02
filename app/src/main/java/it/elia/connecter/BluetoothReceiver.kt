package it.elia.connecter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat


class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Per Android 13 e successivi: usiamo il nuovo metodo type-safe
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            // Per Android 12 e precedenti: vecchio metodo e "sopprimiamo" l'avviso
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
        }

        val sharedPrefs = context.getSharedPreferences("AutoSpotifyPrefs", Context.MODE_PRIVATE)
        val targetDeviceMac = sharedPrefs.getString("TARGET_DEVICE_MAC", "")

        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (device != null && hasPermission) {

            // Confrontiamo l'indirizzo MAC
            if (device.address == targetDeviceMac) {

                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                when (action) {

                    // dispositivo connesso (Profilo Audio A2DP)
                    android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(
                            android.bluetooth.BluetoothProfile.EXTRA_STATE,
                            android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
                        )

                        if (state == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {

                            // Imposta il volume
                            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val targetVolume = sharedPrefs.getInt("TARGET_VOLUME", maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)

                            // Apertura Spotify sulla playlist
                            val targetPlaylistUri = sharedPrefs.getString("TARGET_PLAYLIST_URI", "spotify:collection:tracks")
                            val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetPlaylistUri))
                            spotifyIntent.setPackage("com.spotify.music")
                            spotifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                            try {
                                context.startActivity(spotifyIntent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            // Premi Play, con delay per aprire spotify
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                try {
                                    val playIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                                    playIntent.setPackage("com.spotify.music")
                                    playIntent.putExtra(
                                        Intent.EXTRA_KEY_EVENT,
                                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
                                    )
                                    val releaseIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                                    releaseIntent.setPackage("com.spotify.music")
                                    releaseIntent.putExtra(
                                        Intent.EXTRA_KEY_EVENT,
                                        KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
                                    )

                                    val appContext = context.applicationContext
                                    appContext.sendOrderedBroadcast(playIntent, null)
                                    appContext.sendOrderedBroadcast(releaseIntent, null)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, 1500)
                        }
                    }

                    // Dispositivo disconnesso
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {

                        // volume dei media a zero (muto) o a un valore minimo
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)

                        // OPZIONALE: Invia il comando "Pausa" a Spotify
                        val pauseIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                        pauseIntent.setPackage("com.spotify.music")
                        pauseIntent.putExtra(
                            Intent.EXTRA_KEY_EVENT,
                            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
                        )
                        val pauseReleaseIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                        pauseReleaseIntent.setPackage("com.spotify.music")
                        pauseReleaseIntent.putExtra(
                            Intent.EXTRA_KEY_EVENT,
                            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
                        )

                        context.sendOrderedBroadcast(pauseIntent, null)
                        context.sendOrderedBroadcast(pauseReleaseIntent, null)
                    }
                }
            }
        }
    }
}