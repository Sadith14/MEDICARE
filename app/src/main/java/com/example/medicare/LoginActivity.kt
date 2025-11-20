package com.example.medicare

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import java.util.*

class LoginActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var etNombre: EditText
    private lateinit var etPin: EditText
    private lateinit var btnIngresar: Button
    private lateinit var btnRegistrar: Button
    private lateinit var btnVoz: ImageButton
    private lateinit var tvInstrucciones: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var sharedPrefs: SharedPreferences
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var esperandoComandoVoz = false

    companion object {
        const val PREFS_NAME = "MediCareLogin"
        const val KEY_USUARIO_REGISTRADO = "usuario_registrado"
        const val KEY_NOMBRE = "nombre"
        const val KEY_PIN = "pin"
        const val KEY_PRIMER_USO = "primer_uso"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        initSharedPrefs()
        initTextToSpeech()
        initSpeechRecognizer()
        setupListeners()

        // Verificar si ya está logueado
        if (isUserLoggedIn()) {
            val esPrimerUso = sharedPrefs.getBoolean(KEY_PRIMER_USO, true)
            if (!esPrimerUso) {
                irAMainActivity()
            } else {
                mostrarBienvenida()
            }
        } else {
            mostrarInstruccionesIniciales()
        }
    }

    private fun initViews() {
        etNombre = findViewById(R.id.etNombre)
        etPin = findViewById(R.id.etPin)
        btnIngresar = findViewById(R.id.btnIngresar)
        btnRegistrar = findViewById(R.id.btnRegistrar)
        btnVoz = findViewById(R.id.btnVoz)
        tvInstrucciones = findViewById(R.id.tvInstrucciones)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun initSharedPrefs() {
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(this, this)
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "ES")
        }
    }

    private fun setupListeners() {
        btnIngresar.setOnClickListener {
            validarLogin()
        }

        btnRegistrar.setOnClickListener {
            registrarUsuario()
        }

        btnVoz.setOnClickListener {
            if (esperandoComandoVoz) {
                detenerReconocimientoVoz()
            } else {
                iniciarReconocimientoVoz()
            }
        }
    }

    private fun mostrarInstruccionesIniciales() {
        val mensaje = """
            Bienvenido a Medi Care
            
            Si es su primera vez:
            1. Ingrese su nombre
            2. Cree un PIN de 4 números
            3. Presione REGISTRAR
            
            Si ya tiene cuenta:
            1. Ingrese su nombre y PIN
            2. Presione INGRESAR
            
            También puede usar el botón de micrófono para ayuda por voz
        """.trimIndent()

        tvInstrucciones.text = mensaje
        hablar("Bienvenido a Medi Care. Puede registrarse o ingresar. También puede usar el micrófono para ayuda.")
    }

    private fun validarLogin() {
        val nombre = etNombre.text.toString().trim()
        val pin = etPin.text.toString().trim()

        when {
            nombre.isEmpty() -> {
                mostrarError("Por favor, ingrese su nombre")
                hablar("Por favor, ingrese su nombre")
            }
            pin.isEmpty() -> {
                mostrarError("Por favor, ingrese su PIN")
                hablar("Por favor, ingrese su PIN de cuatro dígitos")
            }
            pin.length != 4 -> {
                mostrarError("El PIN debe tener 4 dígitos")
                hablar("El PIN debe tener exactamente cuatro dígitos")
            }
            !pin.all { it.isDigit() } -> {
                mostrarError("El PIN debe contener solo números")
                hablar("El PIN debe contener solo números")
            }
            else -> {
                verificarCredenciales(nombre, pin)
            }
        }
    }

    private fun verificarCredenciales(nombre: String, pin: String) {
        val nombreGuardado = sharedPrefs.getString(KEY_NOMBRE, "")
        val pinGuardado = sharedPrefs.getString(KEY_PIN, "")

        if (nombre.equals(nombreGuardado, ignoreCase = true) && pin == pinGuardado) {
            hablar("Bienvenido ${nombreGuardado}. Accediendo a la aplicación")

            // Marcar que ya no es primer uso
            sharedPrefs.edit().putBoolean(KEY_PRIMER_USO, false).apply()

            irAMainActivity()
        } else {
            mostrarError("Nombre o PIN incorrectos")
            hablar("Nombre o PIN incorrectos. Verifique sus datos")
        }
    }

    private fun registrarUsuario() {
        val nombre = etNombre.text.toString().trim()
        val pin = etPin.text.toString().trim()

        when {
            nombre.isEmpty() -> {
                mostrarError("Por favor, ingrese su nombre")
                hablar("Por favor, ingrese su nombre")
            }
            nombre.length < 2 -> {
                mostrarError("El nombre debe tener al menos 2 caracteres")
                hablar("El nombre debe tener al menos dos caracteres")
            }
            pin.isEmpty() -> {
                mostrarError("Por favor, cree un PIN de 4 dígitos")
                hablar("Por favor, cree un PIN de cuatro dígitos")
            }
            pin.length != 4 -> {
                mostrarError("El PIN debe tener exactamente 4 dígitos")
                hablar("El PIN debe tener exactamente cuatro dígitos")
            }
            !pin.all { it.isDigit() } -> {
                mostrarError("El PIN debe contener solo números")
                hablar("El PIN debe contener solo números")
            }
            else -> {
                confirmarRegistro(nombre, pin)
            }
        }
    }

    private fun confirmarRegistro(nombre: String, pin: String) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar Registro")
            .setMessage("¿Registrar con estos datos?\n\nNombre: $nombre\nPIN: $pin\n\nPor favor, memorice su PIN")
            .setPositiveButton("SÍ, REGISTRAR") { _, _ ->
                guardarUsuario(nombre, pin)
            }
            .setNegativeButton("CANCELAR", null)
            .show()

        hablar("¿Desea registrarse con el nombre $nombre y el PIN $pin?")
    }

    private fun guardarUsuario(nombre: String, pin: String) {
        sharedPrefs.edit().apply {
            putBoolean(KEY_USUARIO_REGISTRADO, true)
            putString(KEY_NOMBRE, nombre)
            putString(KEY_PIN, pin)
            putBoolean(KEY_PRIMER_USO, true)
            apply()
        }

        hablar("Registro exitoso. Bienvenido $nombre. Ahora puede ingresar a la aplicación")
        Toast.makeText(this, "✅ Registro exitoso", Toast.LENGTH_LONG).show()

        // Auto-login después de registro
        irAMainActivity()
    }

    private fun mostrarBienvenida() {
        val nombre = sharedPrefs.getString(KEY_NOMBRE, "Usuario")

        AlertDialog.Builder(this)
            .setTitle("¡Bienvenido a Medi Care!")
            .setMessage("""
                Hola $nombre
                
                Esta aplicación le ayudará a:
                • Gestionar sus medicamentos
                • Recibir recordatorios de toma
                • Detectar emergencias por voz
                • Llamar a contactos de emergencia
                
                Puede decir "ayuda" en cualquier momento para conocer los comandos disponibles
            """.trimIndent())
            .setPositiveButton("COMENZAR") { _, _ ->
                sharedPrefs.edit().putBoolean(KEY_PRIMER_USO, false).apply()
                irAMainActivity()
            }
            .setCancelable(false)
            .show()

        hablar("Bienvenido a Medi Care, $nombre. Esta aplicación le ayudará con sus medicamentos y emergencias")
    }

    private fun iniciarReconocimientoVoz() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Reconocimiento de voz no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        esperandoComandoVoz = true
        tvInstrucciones.text = "🎤 ESCUCHANDO... Diga 'ayuda' para instrucciones"

        // Animar el botón de voz con efecto neón
        btnVoz.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(300)
            .start()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)

        hablar("Diga su comando. Puede decir 'ayuda' para conocer las instrucciones, o decir su nombre y PIN para ingresar")
    }

    private fun detenerReconocimientoVoz() {
        esperandoComandoVoz = false

        // Restaurar tamaño del botón
        btnVoz.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(300)
            .start()

        speechRecognizer?.stopListening()
    }

    private fun isUserLoggedIn(): Boolean {
        return sharedPrefs.getBoolean(KEY_USUARIO_REGISTRADO, false)
    }

    private fun irAMainActivity() {
        progressBar.visibility = View.VISIBLE

        // Pequeño delay para feedback visual
        progressBar.postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, 800)
    }

    private fun mostrarError(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }

    private fun hablar(texto: String) {
        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}