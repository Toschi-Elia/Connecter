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
            // Per Android 12 e precedenti: usiamo il vecchio metodo e "sopprimiamo" l'avviso
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

            // Confrontiamo l'indirizzo MAC al posto del nome, così se rinominiamo non si rompe
            if (device.address == targetDeviceMac) {

                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                when (action) {

                    // CASO 1: LA MACCHINA SI È CONNESSA (Profilo Audio A2DP)
                    android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(
                            android.bluetooth.BluetoothProfile.EXTRA_STATE,
                            android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
                        )

                        if (state == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                            
                            // Siccome aspettiamo il profilo Audio, l'audio Bluetooth è già pronto.
                            // Possiamo fare tutto subito senza aspettare troppo
                            
                            // 1. Imposta il volume
                            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val targetVolume = sharedPrefs.getInt("TARGET_VOLUME", maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)

                            // 2. Apri Spotify sulla playlist
                            val targetPlaylistUri = sharedPrefs.getString("TARGET_PLAYLIST_URI", "spotify:collection:tracks")
                            val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetPlaylistUri))
                            spotifyIntent.setPackage("com.spotify.music")
                            spotifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                            try {
                                context.startActivity(spotifyIntent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            // 3. Premi Play (un piccolo ritardo serve solo a dare il tempo a Spotify di aprirsi in RAM)
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
                            }, 1500) // Ridotto a 1.5s dato che l'audio BT è già pronto
                        }
                    }

                    // CASO 2: LA MACCHINA SI È DISCONNESSA
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {

                        // Riportiamo il volume dei media a zero (muto) o a un valore minimo
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)

                        // OPZIONALE: Invia il comando "Pausa" a Spotify
                        // Spesso Spotify va in pausa da solo quando si scollega il Bluetooth,
                        // ma se vuoi essere sicuro al 100%, puoi forzare la pausa così:
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