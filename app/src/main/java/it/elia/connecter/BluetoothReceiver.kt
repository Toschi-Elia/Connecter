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
        val targetDeviceName = sharedPrefs.getString("TARGET_DEVICE_NAME", "")

        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (device != null && hasPermission) {

            if (device.name == targetDeviceName) {

                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                when (action) {

                    // CASO 1: LA MACCHINA SI È CONNESSA
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {

                        // 1. Imposta il volume scelto dall'utente
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val targetVolume = sharedPrefs.getInt("TARGET_VOLUME", maxVolume)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)

                        // 2. Apri Spotify sui brani preferiti
                        val spotifyIntent =
                            Intent(Intent.ACTION_VIEW, Uri.parse("spotify:collection:tracks"))
                        spotifyIntent.setPackage("com.spotify.music")
                        spotifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        try {
                            context.startActivity(spotifyIntent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        // 3. Premi Play
                        val playIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                        playIntent.putExtra(
                            Intent.EXTRA_KEY_EVENT,
                            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
                        )
                        val releaseIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                        releaseIntent.putExtra(
                            Intent.EXTRA_KEY_EVENT,
                            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
                        )

                        context.sendOrderedBroadcast(playIntent, null)
                        context.sendOrderedBroadcast(releaseIntent, null)
                    }

                    // CASO 2: LA MACCHINA SI È DISCONNESSA
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {

                        // Riportiamo il volume dei media a zero (muto) o a un valore minimo
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)

                        // OPZIONALE: Invia il comando "Pausa" a Spotify
                        // Spesso Spotify va in pausa da solo quando si scollega il Bluetooth,
                        // ma se vuoi essere sicuro al 100%, puoi forzare la pausa così:
                        val pauseIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                        pauseIntent.putExtra(
                            Intent.EXTRA_KEY_EVENT,
                            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
                        )
                        val pauseReleaseIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
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