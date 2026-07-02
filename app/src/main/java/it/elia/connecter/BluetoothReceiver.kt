package it.elia.connecter

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import android.util.Log
import androidx.core.content.ContextCompat

class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
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

                // Trucco di concatenazione per evitare corruzioni del testo nella chat.
                // Questa stringa diventerà esattamente il nome del pacchetto ufficiale di Spotify.
                val spotifyPackage = "com" + "." + "spotify" + "." + "music"

                when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        Log.e("AutoSpotifyTest", ">>> 3. NOMI COINCIDONO! Regolo volume e avvio Spotify...")

                        // 1. Imposta il volume scelto dall'utente
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val targetVolume = sharedPrefs.getInt("TARGET_VOLUME", maxVolume)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)

                        // 2. Apri Spotify direttamente sulla schermata dei brani che ti piacciono
                        val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:collection:tracks"))
                        spotifyIntent.setPackage(spotifyPackage)
                        spotifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        try {
                            context.startActivity(spotifyIntent)
                        } catch (e: Exception) {
                            Log.e("AutoSpotifyTest", ">>> Errore nell'apertura di Spotify: ${e.message}")
                            e.printStackTrace()
                        }

                        // 3. Invia il comando hardware di PLAY indirizzato esclusivamente a Spotify
                        val playIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                        playIntent.setPackage(spotifyPackage)
                        playIntent.putExtra(
                            Intent.EXTRA_KEY_EVENT,
                            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
                        )

                        val releaseIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                        releaseIntent.setPackage(spotifyPackage)
                        releaseIntent.putExtra(
                            Intent.EXTRA_KEY_EVENT,
                            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
                        )

                        context.sendOrderedBroadcast(playIntent, null)
                        context.sendOrderedBroadcast(releaseIntent, null)
                    }

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        Log.e("AutoSpotifyTest", ">>> Disconnessione rilevata. Metto in pausa.")
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)

                        val pauseIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                        pauseIntent.setPackage(spotifyPackage)
                        pauseIntent.putExtra(
                            Intent.EXTRA_KEY_EVENT,
                            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
                        )

                        val pauseReleaseIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                        pauseReleaseIntent.setPackage(spotifyPackage)
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