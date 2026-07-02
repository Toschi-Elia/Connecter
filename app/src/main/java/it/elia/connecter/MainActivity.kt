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
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import it.elia.connecter.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    // Salviamo la lista dei dispositivi qui per poterla usare in tutta la classe
    private var deviceList: List<Pair<String, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 1. Chiediamo i permessi
        controllaERichiediPermessi()

        // 2. Impostiamo lo Spinner (se i permessi ci sono già, si riempirà subito)
        impostaSpinner()

        // 3. Gestione Volume
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sharedPrefs = getSharedPreferences("AutoSpotifyPrefs", Context.MODE_PRIVATE)

        val maxDeviceVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        binding.seekBar.max = maxDeviceVolume

        val savedVolume = sharedPrefs.getInt("TARGET_VOLUME", 75)
        binding.seekBar.progress = savedVolume

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sharedPrefs.edit().putInt("TARGET_VOLUME", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 4. Bottone Servizio
        binding.btnStartService.setOnClickListener {
            val serviceIntent = Intent(this, AutoSpotifyService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    // --- FUNZIONE PER IMPOSTARE LO SPINNER ---
    private fun impostaSpinner() {
        // Recuperiamo la lista di (Nome, MAC)
        deviceList = getPairedBluetoothDevices(this)

        // Estraiamo SOLO i nomi per mostrarli nello Spinner
        val deviceNames = deviceList.map { it.first }

        // Creiamo l'Adapter con la lista di soli nomi (String)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, deviceNames)
        binding.spinner.adapter = adapter

        binding.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position >= 0 && deviceList.isNotEmpty()) {
                    // Andiamo a pescare il nome originario dalla deviceList
                    val selectedDeviceName = deviceList[position].first
                    val sharedPrefs = getSharedPreferences("AutoSpotifyPrefs", Context.MODE_PRIVATE)
                    sharedPrefs.edit().putString("TARGET_DEVICE_NAME", selectedDeviceName).apply()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // --- FUNZIONE CHE SCATTA QUANDO L'UTENTE PREME "CONSENTI" SUL POPUP ---
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            // L'utente ha risposto al popup, proviamo a ricaricare i dispositivi nello Spinner!
            impostaSpinner()
        }
    }

    private fun getPairedBluetoothDevices(context: Context): List<Pair<String, String>> {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || bluetoothAdapter.isEnabled != true) {
            return emptyList()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                return emptyList() // Niente permessi, torniamo una lista vuota
            }
        }

        return bluetoothAdapter.bondedDevices?.map { device ->
            Pair(device.name ?: "Dispositivo Sconosciuto", device.address)
        } ?: emptyList()
    }

    private fun controllaERichiediPermessi() {
        val permessiMancanti = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permessiMancanti.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permessiMancanti.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permessiMancanti.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permessiMancanti.toTypedArray(), 100)
        }
    }
}