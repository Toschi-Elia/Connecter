package it.elia.connecter

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var spinner: Spinner
    private var deviceList: List<String> = emptyList()
    
    // Request permissions launcher
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // If granted, refresh the device list
        if (permissions[Manifest.permission.BLUETOOTH_CONNECT] == true || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            refreshBluetoothDevices()
        } else {
            Toast.makeText(this, "Permesso Bluetooth negato. Non posso mostrare i dispositivi.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        spinner = findViewById(R.id.spinner)

        checkAndRequestPermissions()

        val sharedPrefs = getSharedPreferences("AutoSpotifyPrefs", Context.MODE_PRIVATE)

        // 2. Volume
        val volumeSeekBar: SeekBar = findViewById(R.id.seekBar)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val maxDeviceVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeSeekBar.max = maxDeviceVolume

        val savedVolume = sharedPrefs.getInt("TARGET_VOLUME", maxDeviceVolume)
        volumeSeekBar.progress = savedVolume

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sharedPrefs.edit().putInt("TARGET_VOLUME", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 3. Playlist
        val spinnerPlaylist: Spinner = findViewById(R.id.spinnerPlaylist)
        val playlists = mutableListOf(
            Pair("Brani che ti piacciono", "spotify:collection:tracks"),
            Pair("Scoperta Settimanale", "spotify:playlist:37i9dQZEVXcQ9COmYvdajy"),
            Pair("Nuove Uscite", "spotify:playlist:37i9dQZF1DX4JAvHpjipBk"),
            Pair("Top 50 Italia", "spotify:playlist:37i9dQZEVXbIQnjwuBSPMR"),
            Pair("Top 50 Globale", "spotify:playlist:37i9dQZEVXbMDoHDwVN2tF")
        )
        
        // Carica un'eventuale playlist personalizzata salvata
        val savedCustomPlaylist = sharedPrefs.getString("CUSTOM_PLAYLIST_URI", null)
        if (savedCustomPlaylist != null) {
            playlists.add(Pair("La mia Playlist", savedCustomPlaylist))
        }

        val playlistNames = ArrayList(playlists.map { it.first })
        val playlistAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, playlistNames)
        spinnerPlaylist.adapter = playlistAdapter

        // Seleziona la playlist salvata (o la prima di default se non trovata)
        val savedPlaylistUri = sharedPrefs.getString("TARGET_PLAYLIST_URI", "spotify:collection:tracks")
        val savedIndex = playlists.indexOfFirst { it.second == savedPlaylistUri }
        if (savedIndex >= 0) {
            spinnerPlaylist.setSelection(savedIndex)
        }

        spinnerPlaylist.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position >= 0) {
                    val selectedPlaylistUri = playlists[position].second
                    sharedPrefs.edit().putString("TARGET_PLAYLIST_URI", selectedPlaylistUri).apply()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Bottone per aggiungere playlist custom
        val btnAddCustomPlaylist: Button = findViewById(R.id.btnAddCustomPlaylist)
        btnAddCustomPlaylist.setOnClickListener {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("Aggiungi Playlist Personale")
            builder.setMessage("Incolla qui l'URL o l'URI di Spotify della tua playlist:")

            val input = android.widget.EditText(this)
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT
            builder.setView(input)

            builder.setPositiveButton("Salva") { dialog, _ ->
                var url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    // Semplice conversione da https://open.spotify.com/playlist/ID a spotify:playlist:ID
                    if (url.startsWith("https://open.spotify.com/playlist/")) {
                        val id = url.substringAfter("playlist/").substringBefore("?")
                        url = "spotify:playlist:$id"
                    }

                    sharedPrefs.edit().putString("CUSTOM_PLAYLIST_URI", url).apply()
                    
                    // Rimuovi eventuale playlist custom precedente
                    if (playlists.size > 5) {
                        playlists.removeAt(5)
                    }
                    playlists.add(Pair("La mia Playlist", url))
                    
                    playlistAdapter.clear()
                    playlistAdapter.addAll(playlists.map { it.first })
                    playlistAdapter.notifyDataSetChanged()
                    
                    // Seleziona la nuova playlist
                    val newIndex = playlists.size - 1
                    spinnerPlaylist.setSelection(newIndex)
                    sharedPrefs.edit().putString("TARGET_PLAYLIST_URI", url).apply()
                    
                    Toast.makeText(this, "Playlist salvata!", Toast.LENGTH_SHORT).show()
                }
            }
            builder.setNegativeButton("Annulla") { dialog, _ ->
                dialog.cancel()
            }
            builder.show()
        }

        // 4. Bottoni Attiva / Disattiva
        val btnStartService: Button = findViewById(R.id.btnStartService)
        btnStartService.setOnClickListener {
            val serviceIntent = Intent(this, AutoSpotifyService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "Servizio attivato in background!", Toast.LENGTH_SHORT).show()
        }

        val btnStopService: Button = findViewById(R.id.btnStopService)
        btnStopService.setOnClickListener {
            val serviceIntent = Intent(this, AutoSpotifyService::class.java)
            stopService(serviceIntent)
            Toast.makeText(this, "Servizio disattivato", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            refreshBluetoothDevices()
        }
    }

    private var deviceAddresses: List<String> = emptyList()

    private fun refreshBluetoothDevices() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter ?: return

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Il Bluetooth è spento! Accendilo e riavvia l'app.", Toast.LENGTH_LONG).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val pairedDevices = bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
        deviceList = pairedDevices.map { it.name ?: "Dispositivo Sconosciuto" }
        deviceAddresses = pairedDevices.map { it.address }

        if (deviceList.isEmpty()) {
            Toast.makeText(this, "Nessun dispositivo Bluetooth associato trovato.", Toast.LENGTH_LONG).show()
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, deviceList)
        spinner.adapter = adapter

        val sharedPrefs = getSharedPreferences("AutoSpotifyPrefs", Context.MODE_PRIVATE)
        val savedDeviceMac = sharedPrefs.getString("TARGET_DEVICE_MAC", "")
        
        if (savedDeviceMac != null && deviceAddresses.contains(savedDeviceMac)) {
            spinner.setSelection(deviceAddresses.indexOf(savedDeviceMac))
        } else if (deviceList.isNotEmpty()) {
            // Se non c'è nulla di salvato, o il salvataggio è vecchio, salva il primo per sicurezza
            sharedPrefs.edit().putString("TARGET_DEVICE_MAC", deviceAddresses[0]).apply()
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position >= 0) {
                    val selectedDeviceMac = deviceAddresses[position]
                    sharedPrefs.edit().putString("TARGET_DEVICE_MAC", selectedDeviceMac).apply()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}
