package it.elia.connecter

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val spinner: Spinner = findViewById<Spinner>(R.id.spinner)

        fun getPairedBluetoothDevices(context: Context): List<Pair<String, String>> {
            val bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter

            if (bluetoothAdapter == null) {
                return emptyList()
            }

            if (bluetoothAdapter.isEnabled != true) {
                return emptyList()
            }

            // Controllo permessi Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val permissionCheck = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
                if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                    return emptyList()
                }
            }

            return bluetoothAdapter.bondedDevices?.map { device ->
                Pair(device.name ?: "Dispositivo Sconosciuto", device.address)
            } ?: emptyList()
        }


        val deviceList = getPairedBluetoothDevices(this)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, deviceList)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                if (position >= 0) {
                    val selectedDeviceName = deviceList[position]
                    val sharedPrefs = getSharedPreferences("AutoSpotifyPrefs", Context.MODE_PRIVATE)
                    sharedPrefs.edit().putString("TARGET_DEVICE_NAME", selectedDeviceName.first)
                        .apply()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        //alzare il volume
        val volumeSeekBar: SeekBar = findViewById(R.id.seekBar)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sharedPrefs = getSharedPreferences("AutoSpotifyPrefs", Context.MODE_PRIVATE)

        val maxDeviceVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeSeekBar.max = maxDeviceVolume

        val savedVolume = sharedPrefs.getInt("TARGET_VOLUME",75 )
        volumeSeekBar.progress = savedVolume

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sharedPrefs.edit().putInt("TARGET_VOLUME", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Non ci serve fare nulla quando l'utente tocca la barra
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
        val btnStartService: Button = findViewById(R.id.btnStartService)

        btnStartService.setOnClickListener {
            // Prepariamo l'Intent per avviare il nostro Servizio
            val serviceIntent = Intent(this, AutoSpotifyService::class.java)

            // Usiamo ContextCompat.startForegroundService che gestisce in automatico
            // le differenze tra le versioni vecchie e nuove di Android
            ContextCompat.startForegroundService(this, serviceIntent)
        }
        //apertura di spotify
        val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:collection:tracks"))
        spotifyIntent.setPackage("com.spotify.music")

        // Importante:per aprire un activity da un BroadcastReceiver/Service, serve aggiungere questo flag per evitare crash
        spotifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            this.startActivity(spotifyIntent)
        } catch (e: Exception) {
            //non installato spotify
            e.printStackTrace()
        }


        val playIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        playIntent.putExtra(Intent.EXTRA_KEY_EVENT,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
        )

        val releaseIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        releaseIntent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))

        this.sendOrderedBroadcast(playIntent, null)
        this.sendOrderedBroadcast(releaseIntent, null)


    }
}
