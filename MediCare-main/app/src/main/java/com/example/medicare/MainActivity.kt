package com.example.medicare

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.view.View
import android.widget.*
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import androidx.core.net.toUri
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.example.medicare.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import android.app.PendingIntent
import android.app.AlarmManager
import android.app.AlertDialog
import android.provider.MediaStore
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.UtteranceProgressListener
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var agenteEmergencia: AgenteEmergenciaComunicacion
    // Elementos de interfaz
    private lateinit var tvTextoReconocido: TextView
    private lateinit var tvEstado: TextView
    private lateinit var rvMedicamentos: RecyclerView
    private lateinit var ivMicrophone: ImageView
    private lateinit var layoutMedicamentos: LinearLayout
    private lateinit var btnOcultarMedicamentos: Button
    private lateinit var layoutEmergencia: LinearLayout
    private lateinit var tvEmergencia: TextView

    // Managers y servicios
    private lateinit var contactManager: ContactManager
    private lateinit var callManager: CallManager
    private lateinit var voiceCommandProcessor: VoiceCommandProcessor
    private lateinit var recognitionService: MedicineRecognitionService
    private lateinit var dbHelper: MedicamentosDBHelper
    private lateinit var medicamentosAdapter: MedicamentosAdapter
    private lateinit var aiAssistant: AIAssistantManager
    private lateinit var medicamentoAlarmManager: MedicamentoAlarmManager
    private lateinit var tts: TextToSpeech
    private var ttsInitialized = false


    // Reconocimiento de voz
    private var speechRecognizer: SpeechRecognizer? = null
    private var backgroundSpeechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var waitingForMedicineConfirmation = false
    private var currentRecognizedMedicine: String? = null

    // Estados
    private var isListening = false
    private var isBackgroundListening = false
    private var isCollectingMedication = false
    private var medicationStep = 0
    private var currentMedication = Medicamento()
    private var esperandoConfirmacion = false
    private var esperandoConfirmacionEmergencia = false
    private var isInAIConversation = false

    // Variables temporales
    private var medicamentoNombre: String = ""
    private var medicamentoCantidad: Int = 0
    private var medicamentoHorarioHoras: Int = 0
    private var medicamentosList = mutableListOf<Medicamento>()
    private var failedCallAttempts = 0
    private val maxCallAttempts = 2
    private val currentMedicationData = mutableMapOf<String, String>()


    // Handlers
    private val backgroundHandler = Handler(Looper.getMainLooper())
    private var backgroundRunnable: Runnable? = null
    private var aiConversationTimeout: Runnable? = null

    // Patrones de reconocimiento
    private val patronesNuevoTratamiento = listOf(
        "nuevo tratamiento", "nueva medicina", "nuevo medicamento",
        "tengo medicamento", "agregar medicina", "agregar medicamento"
    )

    private val patronesMedicina = listOf(
        "medicina", "medicamento", "pastilla", "tableta", "cápsula",
        "jarabe", "tratamiento", "remedio"
    )

    // Comandos de voz para navegación
    private val comandosNavegacion = mapOf(
        "ver medicamentos" to ::ejecutarVerMedicamentosVoz,
        "listar medicamentos" to ::ejecutarVerMedicamentosVoz,
        "limpiar pantalla" to ::ejecutarLimpiarPantalla,
        "limpiar" to ::ejecutarLimpiarPantalla,
        "ayuda" to ::ejecutarAyuda,
        "salir" to ::ejecutarSalir,
        "cerrar aplicacion" to ::ejecutarSalir,
    )

    companion object {
        private const val MEDICINE_RECOGNITION_REQUEST_CODE = 101
        private const val REQUEST_RECORD_PERMISSION = 100
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale("es", "ES") // o Locale.getDefault() si prefieres
                ttsInitialized = true
            }
        }

        // Configurar canal de notificaciones
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                "canal_voz",
                "Voz en segundo plano",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }

        initManagers()
        initViews()
        initDatabase()
        setupSpeechRecognizer()
        setupBackgroundEmergencyListener()
        setupTextToSpeech()
        agenteEmergencia = AgenteEmergenciaComunicacion(this)

        // Configurar Telegram automáticamente
        // Inicializar componentes
        inicializarSistemaEmergencia()
        // Verificar permisos y comenzar vigilancia
        if (checkPermissions()) {
            startBackgroundListening()
        }

        loadMedicamentos()
        setupMedicineOverlayListeners()
    }


    private fun initManagers() {
        contactManager = ContactManager(this)
        callManager = CallManager(this)
        voiceCommandProcessor = VoiceCommandProcessor(
            contactManager,
            callManager
        ) { mensaje -> speakText(mensaje) }

        recognitionService = MedicineRecognitionService(this)
        recognitionService.onMedicineDetected = { nombreDetectado ->
            runOnUiThread {
                registrarMedicamentoDesdeReconocimiento(nombreDetectado)
            }
        }

        aiAssistant = AIAssistantManager(this)
        aiAssistant.initializeNormalMode()

        // Inicializar el gestor de alarmas
        medicamentoAlarmManager = MedicamentoAlarmManager(this)
    }

    private fun initViews() {
        // Elementos existentes
        tvTextoReconocido = findViewById(R.id.tvTextoReconocido)
        tvEstado = findViewById(R.id.tvEstado)
        rvMedicamentos = findViewById(R.id.rvMedicamentos)

        // Nuevos elementos
        ivMicrophone = findViewById(R.id.ivMicrophone)
        layoutMedicamentos = findViewById(R.id.layoutMedicamentos)
        btnOcultarMedicamentos = findViewById(R.id.btnOcultarMedicamentos)
        layoutEmergencia = findViewById(R.id.layoutEmergencia)
        tvEmergencia = findViewById(R.id.tvEmergencia)

        tvEstado.text = "Vigilancia de emergencia activa - Diga 'ayuda' para comandos"

        // Configurar RecyclerView
        medicamentosAdapter = MedicamentosAdapter(medicamentosList)
        rvMedicamentos.layoutManager = LinearLayoutManager(this)
        rvMedicamentos.adapter = medicamentosAdapter

        // Configurar listeners
        setupListeners()
    }

    private fun setupListeners() {
        // Click en el micrófono para activar escucha manual
        ivMicrophone.setOnClickListener {
            if (!isListening) {
                startManualListening()
            }
        }

        // Botón para ocultar lista de medicamentos
        btnOcultarMedicamentos.setOnClickListener {
            ocultarListaMedicamentos()
            speakText("Lista de medicamentos oculta")
        }
    }

    private fun startManualListening() {
        if (!isListening && speechRecognizer != null) {
            stopBackgroundListening()

            // Cambiar apariencia del micrófono
            animateMicrophone(true)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
        }
    }

    private fun animateMicrophone(isActive: Boolean) {
        val colorFrom = if (isActive) ContextCompat.getColor(this, R.color.primary_blue)
        else ContextCompat.getColor(this, R.color.microphone_active)
        val colorTo = if (isActive) ContextCompat.getColor(this, R.color.microphone_active)
        else ContextCompat.getColor(this, R.color.primary_blue)

        // Animación simple de escala
        val scaleX = if (isActive) 1.1f else 1.0f
        val scaleY = if (isActive) 1.1f else 1.0f

        ivMicrophone.animate()
            .scaleX(scaleX)
            .scaleY(scaleY)
            .setDuration(200)
            .start()
    }

    // Función diferente para comando de voz
    private fun ejecutarVerMedicamentosVoz() {
        loadMedicamentos()
        mostrarListaMedicamentos()

        if (medicamentosList.isEmpty()) {
            speakText("No hay medicamentos guardados.")
        } else {
            val mensaje = StringBuilder()
            mensaje.append("Mostrando ${medicamentosList.size} medicamentos en pantalla. ")

            medicamentosList.forEachIndexed { index, medicamento ->
                mensaje.append("${index + 1}: ${medicamento.nombre}, ")
                mensaje.append("${medicamento.cantidad} unidades, cada ${medicamento.horarioHoras} horas. ")
            }

            speakText(mensaje.toString())
        }
    }

    // Función separada para mostrar medicamentos sin voz
    private fun ejecutarVerMedicamentos() {
        loadMedicamentos()
        if (medicamentosList.isEmpty()) {
            speakText("No hay medicamentos guardados.")
        } else {
            val mensaje = StringBuilder()
            mensaje.append("Tiene ${medicamentosList.size} medicamentos guardados. ")

            medicamentosList.forEachIndexed { index, medicamento ->
                mensaje.append("Número ${index + 1}: ${medicamento.nombre}, ")
                mensaje.append("${medicamento.cantidad} unidades, cada ${medicamento.horarioHoras} horas. ")
            }

            speakText(mensaje.toString())
        }
    }

    private fun initDatabase() {
        dbHelper = MedicamentosDBHelper(this)
    }

    private fun setupTextToSpeech() {
        textToSpeech = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("es", "ES"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(
                    this,
                    "Idioma español no soportado para síntesis de voz",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // Mensaje de bienvenida con instrucciones
                speakText(
                    "Bienvenido a Medi Care. Aplicación lista. " +
                            "Diga ayuda' para conocer todos los comandos disponibles."
                )
            }
        }
    }

    private fun speakText(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun ejecutarLimpiarPantalla() {
        tvTextoReconocido.text = ""
        tvEstado.text = "Vigilancia de emergencia activa"
        isCollectingMedication = false
        medicationStep = 0
        speakText("Pantalla limpiada.")
    }

    private fun ejecutarAyuda() {
        val comandosDisponibles = """
            Comandos disponibles:
            - 'Nuevo tratamiento' para agregar medicamentos
            - 'Ver medicamentos' para listar sus medicinas
            - 'Limpiar pantalla' para borrar el texto
            - 'Llamar a' seguido del nombre del contacto
            - 'Emergencia' o 'Ayuda' para activar asistencia
            - 'Configurar emergencia' para los números a comunicarse
            - 'Cámara' para reconocer medicamentos
            - 'Asistente' para hablar con inteligencia artificial
            - 'Salir' para cerrar la aplicación
            
        """.trimIndent()
        val comandosUnicos = comandosNavegacion.keys.distinct()

        val textoDinamico = buildString {
            append("También puedes decir: ")
            comandosUnicos.forEachIndexed { index, comando ->
                append(comando)
                if (index < comandosUnicos.size - 1) append(", ")
            }
        }

        speakText("$comandosDisponibles. $textoDinamico")

    }

    private fun ejecutarSalir() {
        speakText("Cerrando aplicación Medi Care. Que tenga un buen día.")
        finish()
    }
    private fun configurarTelegramAlInicio() {
        // Verificar si ya está configurado
        val prefs = getSharedPreferences("telegram_config", MODE_PRIVATE)
        val token = prefs.getString("bot_token", "")
        val chatId = prefs.getString("chat_id", "")

        if (!token.isNullOrEmpty() && !chatId.isNullOrEmpty()) {
            // Ya está configurado, usar valores guardados
            agenteEmergencia.configurarTelegram(token, chatId)
            android.util.Log.d("Telegram", "Configuración cargada correctamente")
        } else {

        }
    }
    private fun inicializarSistemaEmergencia() {
        dbHelper = MedicamentosDBHelper(this)
        agenteEmergencia = AgenteEmergenciaComunicacion(this)

        // Registrarse para eventos del sistema
        //EventBus.getDefault().register(this)

        // Configurar Telegram si ya está configurado
        val prefs = getSharedPreferences("emergencia_config", MODE_PRIVATE)
        val botToken = prefs.getString("telegram_bot_token", "")//Completar con el tuyo
        val chatId = prefs.getString("telegram_chat_id", "")//ID del chat a cual se le enviara mensajes

        if (!botToken.isNullOrEmpty() && !chatId.isNullOrEmpty()) {
            agenteEmergencia.configurarTelegram(botToken, chatId)
        }

        // Iniciar servicio de monitoreo
        startService(Intent(this, ServicioMonitorMedicamentosActualizado::class.java))
    }

    private fun checkPermissions(): Boolean {
        val permisosRequeridos = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permisosRequeridos.add(Manifest.permission.RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permisosRequeridos.add(Manifest.permission.SEND_SMS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permisosRequeridos.add(Manifest.permission.CALL_PHONE)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permisosRequeridos.add(Manifest.permission.READ_CONTACTS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                permisosRequeridos.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            }
        }

        if (permisosRequeridos.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permisosRequeridos.toTypedArray(),
                REQUEST_RECORD_PERMISSION
            )
            return false
        }

        return true
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                tvEstado.text = "Habla ahora..."
            }

            override fun onBeginningOfSpeech() {
                tvEstado.text = "Escuchando..."
                stopBackgroundListening()
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                tvEstado.text = "Procesando..."
            }

            override fun onError(error: Int) {
                isListening = false

                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                    SpeechRecognizer.ERROR_CLIENT -> "Error de cliente"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permisos insuficientes"
                    SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo de espera de red agotado"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No se reconoció ninguna palabra"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                    SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tiempo de espera de voz agotado"
                    else -> "Error desconocido"
                }

                tvEstado.text = "Vigilancia de emergencia activa"
                if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                }

                startBackgroundListening()
            }

            override fun onResults(results: Bundle?) {
                isListening = false

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                if (!matches.isNullOrEmpty()) {
                    val textoReconocido = matches[0].lowercase()

                    // Mostrar texto reconocido
                    val textoActual = tvTextoReconocido.text.toString()
                    val nuevoTexto = if (textoActual.isEmpty()) {
                        matches[0]
                    } else {
                        "$textoActual\n${matches[0]}"
                    }
                    tvTextoReconocido.text = nuevoTexto

                    // Procesar comandos
                    procesarComando(textoReconocido, matches[0])

                    tvEstado.text = "Vigilancia de emergencia activa"
                }

                startBackgroundListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    tvEstado.text = "Reconociendo: ${matches[0]}"
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    @OptIn(UnstableApi::class)
    private fun procesarComando(textoLower: String, textoOriginal: String) {
        // AGREGAR ESTE LOG AL INICIO
        Log.d("VoiceCommand", "Procesando comando: '$textoOriginal' -> '$textoLower'")

        when {
            isInAIConversation -> {
                Log.d("VoiceCommand", "Procesando mensaje IA")
                procesarMensajeIA(textoOriginal)
                configureAITimeout()
            }

            // Activar IA manualmente
            textoLower.contains("asistente") || textoLower.contains("atención inteligente") || textoLower.contains(
                "siento"
            ) -> {
                Log.d("VoiceCommand", "Activando asistente IA")
                activarAsistenteIA()
            }

            esperandoConfirmacionEmergencia -> {
                Log.d("VoiceCommand", "Procesando confirmación emergencia")
                procesarConfirmacionEmergencia(textoLower)
            }

            // CONFIRMACIÓN DE MEDICAMENTO RECONOCIDO
            waitingForMedicineConfirmation && (textoLower.contains("sí") || textoLower.contains("si") || textoLower.contains("yes") || textoLower.contains("agregar")) -> {
                Log.d("VoiceCommand", "Confirmando agregar medicamento reconocido")
                currentRecognizedMedicine?.let { medicineName ->
                    // Iniciar recolección de datos
                    isCollectingMedication = true
                    waitingForMedicineConfirmation = false

                    // Inicializar tanto currentMedication como currentMedicationData
                    currentMedication = Medicamento()
                    currentMedication.nombre = medicineName

                    currentMedicationData.clear()
                    currentMedicationData["nombre"] = medicineName

                    // Establecer step para cantidad
                    medicationStep = 1

                    speakText("Perfecto. Medicamento $medicineName seleccionado. Ahora dígame la cantidad que debe tomar")
                    tvEstado.text = "Diga la cantidad para $medicineName"

                    Log.d("VoiceCommand", "Medicamento $medicineName confirmado, recolectando cantidad")
                }
            }

            waitingForMedicineConfirmation && (textoLower.contains("no") || textoLower.contains("cancelar")) -> {
                Log.d("VoiceCommand", "Cancelando agregar medicamento reconocido")
                waitingForMedicineConfirmation = false
                currentRecognizedMedicine = null
                speakText("Entendido. No se agregará el medicamento.")
                tvEstado.text = "Listo para comandos"
            }

            // CÁMARA
            textoLower.contains("cámara") ||
                    textoLower.contains("camara") ||
                    textoLower.contains("tomar foto") ||
                    textoLower.contains("reconocer medicamento") ||
                    textoLower.contains("escanear medicina") -> {
                Log.d("VoiceCommand", "Abriendo cámara")
                abrirCamara()
            }

            // INVENTARIO
            textoLower.contains("inventario") ||
                    textoLower.contains("lista de medicinas") ||
                    textoLower.contains("muestra lista") ||
                    textoLower.contains("mostrar medicamentos") -> {
                Log.d("VoiceCommand", "Mostrando inventario")
                ejecutarVerMedicamentosVoz()
            }

            // OCULTAR LISTA
            textoLower.contains("ocultar lista") ||
                    textoLower.contains("cerrar lista") ||
                    textoLower.contains("esconder medicamentos") -> {
                Log.d("VoiceCommand", "Ocultando lista")
                ocultarListaMedicamentos()
                speakText("Lista de medicamentos ocultada")
            }

            // NAVEGACIÓN
            comandosNavegacion.keys.any { textoLower.contains(it) } -> {
                val comando = comandosNavegacion.keys.find { textoLower.contains(it) }
                Log.d("VoiceCommand", "Comando navegación encontrado: $comando")
                comando?.let { comandosNavegacion[it]?.invoke() }
            }

            // LLAMADAS
            voiceCommandProcessor.procesarComandoLlamada(textoOriginal) -> {
                Log.d("VoiceCommand", "Procesando comando de llamada")
                return
            }

            // NUEVO TRATAMIENTO
            !isCollectingMedication && patronesNuevoTratamiento.any { textoLower.contains(it) } -> {
                Log.d("VoiceCommand", "Iniciando nuevo tratamiento")
                iniciarNuevoTratamiento()
            }

            isCollectingMedication -> {
                Log.d("VoiceCommand", "Procesando datos medicamento - Step: $medicationStep")
                procesarDatosMedicamento(textoOriginal)
            }

            !isCollectingMedication && detectarMencionMedicamentos(textoLower) -> {
                Log.d("VoiceCommand", "Medicamento detectado")
                speakText("He detectado que mencionó medicamentos. ¿Desea agregar un nuevo tratamiento?")
                tvEstado.text = "Medicamento detectado - Diga 'sí' para agregar"
            }

            !isCollectingMedication && (textoLower.contains("sí") || textoLower.contains("si") || textoLower.contains(
                "yes"
            )) -> {
                Log.d("VoiceCommand", "Confirmación sí recibida")
                iniciarNuevoTratamiento()
            }

            // COMANDO EMERGENCIA - AGREGAR MÁS LOGS AQUÍ
            textoLower.contains("agregar contacto") ||
                    textoLower.contains("añadir contacto") ||
                    textoLower.contains("contacto emergencia") ||
                    textoLower.contains("configurar contacto") ||
                    textoLower.contains("nuevo contacto") -> {
                Log.d("VoiceCommand", "¡COMANDO AGREGAR CONTACTO DETECTADO!")
                abrirConfiguracionEmergencia()
            }

            else -> {
                Log.d("VoiceCommand", "Comando no reconocido: '$textoLower'")
                speakText("No entendí el comando. Diga 'ayuda' para conocer los comandos disponibles.")
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun abrirCamara() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Abrir tu actividad personalizada de reconocimiento
            val intent = Intent(this, ReconocimientoMedicamentoMA::class.java)
            intent.putExtra("source", "voice_command")
            intent.putExtra("return_result", true)

            startActivityForResult(intent, MEDICINE_RECOGNITION_REQUEST_CODE)
            Log.d("VoiceCommand", "Abriendo interfaz de reconocimiento de medicamentos")
        } else {
            checkCameraPermission()
        }
    }

    private fun setupMedicineOverlayListeners() {
        // Botón de cerrar (X) en el header
        findViewById<ImageView>(R.id.btnCerrarLista).setOnClickListener {
            ocultarListaMedicamentos()
            speakText("Lista de medicamentos cerrada")
        }

        // Botón "Ocultar Lista" (tu botón original)
        btnOcultarMedicamentos.setOnClickListener {
            ocultarListaMedicamentos()
            speakText("Lista de medicamentos ocultada")
        }

        // Botón "Agregar Nuevo" (opcional)
        findViewById<Button>(R.id.btnAgregarMedicamento).setOnClickListener {
            ocultarListaMedicamentos()
            iniciarNuevoTratamiento()
        }

        // Cerrar al tocar fuera del contenido (en el fondo oscuro)
        layoutMedicamentos.setOnClickListener { view ->
            // Solo cerrar si tocaron exactamente el fondo, no el contenido
            if (view.id == R.id.layoutMedicamentos) {
                ocultarListaMedicamentos()
                speakText("Lista cerrada")
            }
        }
    }

    private fun mostrarListaMedicamentos() {
        layoutMedicamentos.visibility = View.VISIBLE

        // Opcional: animación suave
        layoutMedicamentos.alpha = 0f
        layoutMedicamentos.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun ocultarListaMedicamentos() {
        // Opcional: animación suave antes de ocultar
        layoutMedicamentos.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                layoutMedicamentos.visibility = View.GONE
                layoutMedicamentos.alpha = 1f // Resetear para la próxima vez
            }
            .start()
    }

    private fun mostrarIndicadorEmergencia(mensaje: String) {
        tvEmergencia.text = mensaje
        layoutEmergencia.visibility = View.VISIBLE

        // Ocultar automáticamente después de 10 segundos
        Handler(Looper.getMainLooper()).postDelayed({
            layoutEmergencia.visibility = View.GONE
        }, 10000)
    }

    private fun procesarDatosMedicamento(texto: String) {
        when (medicationStep) {
            0 -> { // Nombre del medicamento
                if (esperandoConfirmacion) {
                    val textoLower = texto.lowercase()
                    if (textoLower.contains("sí") || textoLower.contains("si") || textoLower.contains(
                            "yes"
                        )
                    ) {
                        esperandoConfirmacion = false
                        medicationStep = 1
                        speakText("¿Cuántas unidades tiene del medicamento ${currentMedication.nombre}?")
                        tvEstado.text = "Esperando cantidad..."
                    } else if (textoLower.contains("no")) {
                        isCollectingMedication = false
                        esperandoConfirmacion = false
                        medicationStep = 0
                        speakText("Cancelando registro de medicamento.")
                        tvEstado.text = "Vigilancia de emergencia activa"
                    }
                } else {
                    currentMedication.nombre = texto
                    medicationStep = 1
                    speakText("¿Cuántas unidades tiene del medicamento $texto?")
                    tvEstado.text = "Esperando cantidad..."
                }
            }

            1 -> { // Cantidad
                val cantidad = extraerNumero(texto)
                if (cantidad > 0) {
                    currentMedication.cantidad = cantidad
                    medicationStep = 2
                    speakText("¿Cada cuántas horas debe tomar ${currentMedication.nombre}?")
                    tvEstado.text = "Esperando horario..."
                } else {
                    speakText("No entendí la cantidad. Por favor, diga un número válido.")
                }
            }

            2 -> { // Horario (frecuencia)
                val horario = extraerHorario(texto)
                if (horario > 0) {
                    currentMedication.horarioHoras = horario
                    medicationStep = 3
                    speakText(
                        "¿A qué hora desea iniciar el tratamiento de ${currentMedication.nombre}? " +
                                "Por ejemplo: 8 de la mañana, 2 de la tarde, o 20 horas."
                    )
                    tvEstado.text = "Esperando hora de inicio..."
                } else {
                    speakText("No entendí el horario. Por favor, diga cada cuántas horas.")
                }
            }

            3 -> { // Hora de inicio
                val horaInicio = extraerHoraInicio(texto)
                if (horaInicio != null) {
                    currentMedication.horaInicio = horaInicio

                    // Guardar medicamento
                    dbHelper.insertarMedicamento(currentMedication)
                    programarRecordatorios(
                        currentMedication.nombre,
                        currentMedication.horaInicio!!,
                        currentMedication.horarioHoras
                    )

                    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val horaInicioStr = formatter.format(Date(horaInicio))

                    speakText(
                        "Medicamento ${currentMedication.nombre} registrado correctamente. " +
                                "Cantidad: ${currentMedication.cantidad} unidades. " +
                                "Frecuencia: cada ${currentMedication.horarioHoras} horas. " +
                                "Inicio del tratamiento: ${horaInicioStr}."
                    )

                    // Resetear estado
                    isCollectingMedication = false
                    medicationStep = 0
                    currentMedication = Medicamento()
                    tvEstado.text = "Vigilancia de emergencia activa"

                    loadMedicamentos()
                } else {
                    speakText(
                        "No entendí la hora. Por favor, diga la hora de inicio del tratamiento. " +
                                "Por ejemplo: 8 de la mañana, 2 de la tarde, o las 20 horas."
                    )
                }
            }
        }
    }

    // Función auxiliar para extraer la hora de inicio del texto
    @OptIn(UnstableApi::class)
    private fun extraerHoraInicio(texto: String): Long? {
        val textoLower = texto.lowercase().trim()

        try {
            // Patrones para diferentes formatos de hora
            val patterns = listOf(
                // Formato: "8 de la mañana", "2 de la tarde", "9 de la noche"
                Regex("""(\d{1,2})\s*de\s*la\s*(mañana|tarde|noche)"""),
                // Formato: "8 am", "2 pm", "14 pm"
                Regex("""(\d{1,2})\s*(am|pm)"""),
                // Formato: "las 8", "a las 14", "20 horas"
                Regex("""(?:las\s*|a\s*las\s*)?(\d{1,2})(?:\s*horas?)?"""),
                // Formato: "8:30", "14:30"
                Regex("""(\d{1,2}):(\d{2})""")
            )

            for (pattern in patterns) {
                val match = pattern.find(textoLower)
                if (match != null) {
                    val hora = match.groupValues[1].toInt()
                    var minutos = 0

                    // Si tiene minutos (formato HH:MM)
                    if (match.groupValues.size > 2 && match.groupValues[2].isNotEmpty()) {
                        minutos = match.groupValues[2].toIntOrNull() ?: 0
                    }

                    // Ajustar para AM/PM o mañana/tarde/noche
                    var horaFinal = hora
                    if (match.groupValues.size > 1) {
                        val periodo =
                            if (match.groupValues.size > 2) match.groupValues[2] else match.groupValues[1]
                        when (periodo) {
                            "pm", "tarde" -> {
                                if (hora < 12) horaFinal = hora + 12
                            }

                            "noche" -> {
                                if (hora < 12) horaFinal = hora + 12
                            }

                            "am", "mañana" -> {
                                if (hora == 12) horaFinal = 0
                            }
                        }
                    }

                    // Validar hora
                    if (horaFinal in 0..23 && minutos in 0..59) {
                        // Crear timestamp para hoy a la hora especificada
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.HOUR_OF_DAY, horaFinal)
                        calendar.set(Calendar.MINUTE, minutos)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)

                        // Si la hora ya pasó hoy, programar para mañana
                        if (calendar.timeInMillis < System.currentTimeMillis()) {
                            calendar.add(Calendar.DAY_OF_MONTH, 1)
                        }

                        return calendar.timeInMillis
                    }
                }
            }

            // Intentar extraer solo números
            val numeroMatch = Regex("""\d{1,2}""").find(textoLower)
            if (numeroMatch != null) {
                val hora = numeroMatch.value.toInt()
                if (hora in 0..23) {
                    val calendar = Calendar.getInstance()
                    calendar.set(Calendar.HOUR_OF_DAY, hora)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

                    if (calendar.timeInMillis < System.currentTimeMillis()) {
                        calendar.add(Calendar.DAY_OF_MONTH, 1)
                    }

                    return calendar.timeInMillis
                }
            }

        } catch (e: Exception) {
            Log.e("MedicationProcessor", "Error al extraer hora: ${e.message}")
        }

        return null
    }

    private fun iniciarNuevoTratamiento() {
        isCollectingMedication = true
        currentMedication = Medicamento()

        if (!currentMedicationData["nombre"].isNullOrEmpty()) {
            // Si ya tenemos el nombre, saltamos directamente al paso de cantidad
            medicationStep = 1
            currentMedication.nombre = currentMedicationData["nombre"].toString()

            speakText("Perfecto. Ahora dígame la cantidad que debe tomar")
            tvEstado.text = "Diga la cantidad para ${currentMedication.nombre}"
        } else {
            // Si no tenemos nombre, empezamos desde el paso 0
            medicationStep = 0
            speakText("Perfecto. Dígame el nombre del medicamento.")
            tvEstado.text = "Esperando nombre del medicamento..."
        }
    }

    private fun detectarMencionMedicamentos(texto: String): Boolean {
        return patronesMedicina.any { texto.contains(it) }
    }

    private fun extraerNumero(texto: String): Int {
        val numerosTexto = mapOf(
            "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
            "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
            "once" to 11, "doce" to 12, "quince" to 15, "veinte" to 20,
            "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50
        )

        val textoLower = texto.lowercase()

        for ((palabra, numero) in numerosTexto) {
            if (textoLower.contains(palabra)) {
                return numero
            }
        }

        val pattern = Pattern.compile("\\d+")
        val matcher = pattern.matcher(texto)
        if (matcher.find()) {
            return matcher.group().toIntOrNull() ?: 0
        }

        return 0
    }

    private fun extraerHorario(texto: String): Int {
        val textoLower = texto.lowercase()

        val patternHoras = Pattern.compile("cada\\s+(\\d+)\\s+horas?", Pattern.CASE_INSENSITIVE)
        val matcherHoras = patternHoras.matcher(textoLower)
        if (matcherHoras.find()) {
            return matcherHoras.group(1)?.toIntOrNull() ?: 0
        }

        val numero = extraerNumero(texto)
        if (numero in 1..24) {
            return numero
        }

        return 0
    }

    @OptIn(UnstableApi::class)
    private fun programarRecordatorios(nombre: String, inicio: Long, intervaloHoras: Int) {
        Log.d("MainActivity", "=== INICIANDO programarRecordatorios ===")
        Log.d(
            "MainActivity",
            "Nombre: $nombre, Inicio: $inicio, Intervalo: $intervaloHoras horas"
        )

        // Usar el MedicamentoAlarmManager existente en lugar de lógica manual
        val medicamentoAlarmManager = MedicamentoAlarmManager(this)

        // Buscar el medicamento recién creado por nombre
        val medicamentos = dbHelper.obtenerTodosMedicamentos()
        Log.d("MainActivity", "Total medicamentos en DB: ${medicamentos.size}")

        val medicamento = medicamentos.find { it.nombre == nombre }

        if (medicamento != null) {
            Log.d("MainActivity", "Medicamento encontrado: ${medicamento.nombre}")
            Log.d("MainActivity", "ID: ${medicamento.id}, Activo: ${medicamento.activo}")
            Log.d("MainActivity", "Hora inicio original: ${medicamento.horaInicio}")
            Log.d("MainActivity", "Intervalo original: ${medicamento.horarioHoras}")

            // Asegurarse de que el medicamento esté activo
            if (!medicamento.activo) {
                Log.d("MainActivity", "Activando medicamento...")
                dbHelper.actualizarEstadoMedicamento(medicamento.id, true)
                // Actualizar el objeto medicamento
                medicamento.activo = true
            }

            // Usar el sistema existente que ya maneja todo correctamente
            Log.d("MainActivity", "Llamando a programarRecordatoriosMedicamento...")
            medicamentoAlarmManager.programarRecordatoriosMedicamento(medicamento)

            Log.d(
                "MainActivity",
                "Recordatorios programados para ${medicamento.nombre} usando MedicamentoAlarmManager"
            )

            // Forzar actualización de la interfaz
            Log.d("MainActivity", "Actualizando interfaz...")
            runOnUiThread {
                actualizarListaMedicamentos()
            }

        } else {
            Log.e(
                "MainActivity",
                "No se encontró el medicamento $nombre para programar recordatorios"
            )
            Log.e("MainActivity", "Medicamentos disponibles:")
            medicamentos.forEach { med ->
                Log.e("MainActivity", "- ${med.nombre} (ID: ${med.id})")
            }
        }

        Log.d("MainActivity", "=== FINALIZANDO programarRecordatorios ===")
    }

    @OptIn(UnstableApi::class)
    private fun actualizarListaMedicamentos() {
        Log.d("MainActivity", "=== ACTUALIZANDO LISTA MEDICAMENTOS ===")

        val medicamentos = dbHelper.obtenerTodosMedicamentos()
        Log.d("MainActivity", "Medicamentos obtenidos: ${medicamentos.size}")

        medicamentos.forEach { med ->
            Log.d("MainActivity", "Medicamento: ${med.nombre}")
            Log.d("MainActivity", "  - ID: ${med.id}")
            Log.d("MainActivity", "  - Activo: ${med.activo}")
            Log.d("MainActivity", "  - Hora inicio: ${med.horaInicio}")
            Log.d("MainActivity", "  - Horario horas: ${med.horarioHoras} horas")

            if (med.activo && med.horaInicio != null) {
                // Usar el método que ya existe en la clase Medicamento
                val proximaToma = med.obtenerProximaHoraToma()
                Log.d("MainActivity", "  - Próxima toma calculada: $proximaToma")

                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                val horaFormateada = formatter.format(Date(proximaToma))
                Log.d("MainActivity", "  - Próxima toma formateada: $horaFormateada")
            } else {
                Log.d("MainActivity", "  - Medicamento inactivo o sin hora de inicio")
            }
        }

        // Actualizar adaptador
        medicamentosAdapter.actualizarLista(medicamentos)
        Log.d("MainActivity", "Adaptador actualizado")

        Log.d("MainActivity", "=== FIN ACTUALIZACIÓN LISTA ===")
    }

    // Función auxiliar para cancelar recordatorios previos de un medicamento
    @OptIn(UnstableApi::class)
    private fun cancelarRecordatoriosMedicamento(nombre: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancelar hasta 1000 posibles recordatorios previos (ajusta según necesites)
        for (i in 0 until 1000) {
            val intent = Intent(this, AlarmReceiver::class.java)
            val requestCode = "${nombre.hashCode()}_$i".hashCode()

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }

        Log.d("MainActivity", "Recordatorios previos cancelados para $nombre")
    }

    // Función para cancelar todos los recordatorios de un medicamento (útil cuando se elimina)
    fun cancelarTodosLosRecordatorios(nombre: String) {
        cancelarRecordatoriosMedicamento(nombre)
    }

    private fun confirmarToma(nombre: String) {
        val medicamento = buscarMedicamentoPorNombre(nombre)

        if (medicamento != null) {
            // Reducir la cantidad en el objeto
            medicamento.reducirCantidadEnUno()

            // Actualizar en la base de datos usando el ID del medicamento y la nueva cantidad
            val actualizado =
                dbHelper.actualizarCantidadMedicamento(medicamento.id, medicamento.cantidad)

            if (actualizado) {
                speakText("Toma de $nombre registrada. Quedan ${medicamento.cantidad} unidades.")

                if (medicamento.estaPorAgotarse()) {
                    speakText("⚠️ Quedan pocas unidades de $nombre. Considera reponerlo.")
                }

                // Recargar la lista de medicamentos para mostrar los cambios
                loadMedicamentos()
            } else {
                speakText("Error al actualizar el medicamento en la base de datos.")
            }
        } else {
            speakText("No encontré el medicamento llamado $nombre.")
        }
    }

    @OptIn(UnstableApi::class)
    private fun abrirConfiguracionEmergencia() {
        try {
            Log.d("Emergency", "Intentando abrir ConfiguracionEmergenciaActivity")
            val intent = Intent(this, ConfiguracionEmergenciaActivity::class.java)
            startActivity(intent)
            Log.d("Emergency", "Intent enviado correctamente")

            // Opcional: agregar feedback de voz
            speakText("Abriendo configuración de emergencia")

        } catch (e: Exception) {
            Log.e("Emergency", "Error al abrir configuración de emergencia", e)
            speakText("Error al abrir la configuración de emergencia")
        }
    }

    @OptIn(UnstableApi::class)

    private fun registrarMedicamentoDesdeReconocimiento(nombre: String) {
        if (!isCollectingMedication && !waitingForMedicineConfirmation) {
            // Usar el sistema unificado
            currentRecognizedMedicine = nombre
            waitingForMedicineConfirmation = true

            speakText("¿Desea registrar el medicamento $nombre? Diga sí para continuar o no para cancelar.")
            tvEstado.text = "¿Registrar $nombre?"

            Log.d("VoiceCommand", "Medicamento reconocido: $nombre, esperando confirmación")
        }
    }

    private fun setupBackgroundEmergencyListener() {
        backgroundSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        backgroundSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (error != SpeechRecognizer.ERROR_CLIENT) {
                    restartBackgroundListening()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val textoReconocido = matches[0].lowercase()

                    val palabrasClave = listOf(
                        "ayuda",
                        "caída",
                        "accidente",
                        "emergencia",
                        "auxilio",
                        "socorro"
                    )
                    if (palabrasClave.any { textoReconocido.contains(it, ignoreCase = true) }) {
                        stopBackgroundListening()
                        activarEmergencia(this@MainActivity)

                        backgroundHandler.postDelayed({
                            startBackgroundListening()
                        }, 30000)
                        return
                    }
                }

                restartBackgroundListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val textoReconocido = matches[0].lowercase()
                    val palabrasClave = listOf(
                        "ayuda",
                        "caída",
                        "accidente",
                        "emergencia",
                        "auxilio",
                        "socorro"
                    )

                    if (palabrasClave.any { textoReconocido.contains(it, ignoreCase = true) }) {
                        backgroundSpeechRecognizer?.stopListening()
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startBackgroundListening() {
        if (!isBackgroundListening && !isListening) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    3000
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    3000
                )
            }

            backgroundSpeechRecognizer?.startListening(intent)
            isBackgroundListening = true
        }
    }

    private fun stopBackgroundListening() {
        if (isBackgroundListening) {
            backgroundSpeechRecognizer?.stopListening()
            isBackgroundListening = false
        }
    }

    private fun restartBackgroundListening() {
        if (!isListening) {
            backgroundHandler.postDelayed({
                startBackgroundListening()
            }, 2000)
        }
    }

    private fun loadMedicamentos() {
        medicamentosList.clear()
        medicamentosList.addAll(dbHelper.obtenerTodosMedicamentos())
        medicamentosAdapter.notifyDataSetChanged()
    }

    private fun activarEmergencia(context: Context) {
        esperandoConfirmacionEmergencia = true
        mostrarIndicadorEmergencia("Emergencia detectada - Preparando asistencia")

        speakText("Emergencia detectada. ¿A quién desea llamar? Puede decir 'familiar', '911', o el nombre de un contacto.")
        tvEstado.text = "Esperando confirmación de llamada de emergencia..."

        enviarSMSEmergencia()

        Handler(Looper.getMainLooper()).postDelayed({
            if (esperandoConfirmacionEmergencia) {
                esperandoConfirmacionEmergencia = false
                speakText("Llamando automáticamente por emergencia")
                callManager.llamadaEmergencia(contactManager) { exito, mensaje ->
                    speakText(mensaje)
                    if (!exito) {
                        activarAsistenteIA()
                    }
                }
            }
        }, 15000)
    }

    @OptIn(UnstableApi::class)
    private fun enviarSMSEmergencia() {
        try {
            val contactosEmergencia = contactManager.obtenerContactosEmergencia()
                .filter { it.tipo == "Favorito" }

            if (contactosEmergencia.isNotEmpty()) {
                val smsManager = SmsManager.getDefault()
                val mensaje =
                    "⚠️ EMERGENCIA: Se detectó una situación de emergencia. Ubicación: [Agregar ubicación si está disponible]"

                contactosEmergencia.forEach { contacto ->
                    try {
                        smsManager.sendTextMessage(contacto.numero, null, mensaje, null, null)
                    } catch (e: Exception) {
                        Log.e(
                            "MainActivity",
                            "Error enviando SMS a ${contacto.nombre}: ${e.message}"
                        )
                    }
                }

                speakText("Mensajes de emergencia enviados")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error en envío de SMS de emergencia: ${e.message}")
        }
    }

    private fun activarAsistenteIA() {
        isInAIConversation = true
        esperandoConfirmacionEmergencia = false
        failedCallAttempts = 0

        val medicamentos = dbHelper.obtenerTodosMedicamentos()
        aiAssistant.initializeEmergencyMode(medicamentos)

        speakText("Estoy aquí para ayudarte. Soy tu asistente Medi Care. ¿Cómo te sientes? ¿Qué está pasando?")
        tvEstado.text = "Conversando con asistente de IA - Habla ahora"

        configureAITimeout()
    }

    private fun configureAITimeout() {
        aiConversationTimeout?.let { backgroundHandler.removeCallbacks(it) }

        aiConversationTimeout = Runnable {
            if (isInAIConversation) {
                speakText("Parece que ya no necesitas ayuda. Si necesitas algo más, di 'asistente' para hablar conmigo nuevamente.")
                salirModoIA()
            }
        }

        backgroundHandler.postDelayed(aiConversationTimeout!!, 300000) // 5 minutos
    }

    private fun salirModoIA() {
        isInAIConversation = false
        aiConversationTimeout?.let { backgroundHandler.removeCallbacks(it) }
        aiAssistant.resetConversation()
        tvEstado.text = "Vigilancia de emergencia activa"
    }

    @OptIn(UnstableApi::class)
    private fun procesarMensajeIA(mensaje: String) {
        tvEstado.text = "Asistente pensando..."

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = aiAssistant.processUserMessage(mensaje)

                speakText(response.message)

                val textoActual = tvTextoReconocido.text.toString()
                val nuevoTexto = if (textoActual.isEmpty()) {
                    "Usuario: $mensaje\n\nAsistente: ${response.message}"
                } else {
                    "$textoActual\n\nUsuario: $mensaje\n\nAsistente: ${response.message}"
                }
                tvTextoReconocido.text = nuevoTexto

                val statusText = if (response.isOnlineResponse) {
                    "Conversando con asistente de IA (Online) - Habla ahora"
                } else {
                    "Conversando con asistente de IA (Offline) - Habla ahora"
                }

                if (response.shouldCallEmergency) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        speakText("Te recomiendo llamar al 911 ahora.")
                        realizarLlamada(this@MainActivity, "911")
                    }, 3000)
                }

                tvEstado.text = statusText

            } catch (e: Exception) {
                Log.e("MainActivity", "Error con IA: ${e.message}")
                speakText("Disculpa, tengo problemas técnicos. Si es una emergencia grave, llama al 911.")
                tvEstado.text = "Error de IA - Usar comandos manuales"
            }
        }
    }

    private fun procesarConfirmacionEmergencia(textoLower: String) {
        esperandoConfirmacionEmergencia = false

        when {
            textoLower.contains("911") || textoLower.contains("emergencia") -> {
                callManager.realizarLlamada("911") { exito, mensaje ->
                    speakText(mensaje)
                }
            }

            textoLower.contains("asistente") || textoLower.contains("inteligente") -> {
                activarAsistenteIA()
            }

            textoLower.contains("familiar") || textoLower.contains("familia") -> {
                callManager.llamadaEmergencia(contactManager) { exito, mensaje ->
                    speakText(mensaje)
                    if (!exito) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            activarAsistenteIA()
                        }, 2000)
                    }
                }
            }

            else -> {
                val nombre = textoLower.replace("a ", "").replace("llama a ", "").trim()
                callManager.llamarContacto(nombre, contactManager) { exito, mensaje ->
                    speakText(mensaje)
                    if (!exito) {
                        speakText("¿Desea que active el asistente inteligente?")
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!esperandoConfirmacionEmergencia) {
                                activarAsistenteIA()
                            }
                        }, 5000)
                    }
                }
            }
        }
    }

    // Métodos auxiliares
    private fun buscarMedicamentoPorNombre(nombre: String): Medicamento? {
        val medicamentos = dbHelper.obtenerTodosMedicamentos()
        return medicamentos.find { it.nombre.equals(nombre, ignoreCase = true) }
    }

    private fun registrarOModificarMedicamento(
        nombre: String,
        cantidad: Int,
        horarioHoras: Int,
        horaInicioMillis: Long
    ) {
        val existente = buscarMedicamentoPorNombre(nombre)

        if (existente != null) {
            // Agregar la cantidad al medicamento existente
            existente.agregarCantidad(cantidad)

            // Actualizar en la base de datos usando el ID y la nueva cantidad
            val actualizado =
                dbHelper.actualizarCantidadMedicamento(existente.id, existente.cantidad)

            if (actualizado) {
                speakText("Se han añadido $cantidad unidades al medicamento $nombre. Ahora tienes ${existente.cantidad} unidades.")
            } else {
                speakText("Error al actualizar el medicamento $nombre en la base de datos.")
            }
        } else {
            val nuevo = Medicamento(
                nombre = nombre,
                cantidad = cantidad,
                horarioHoras = horarioHoras,
                horaInicio = horaInicioMillis
            )
            val idInsertado = dbHelper.insertarMedicamento(nuevo)

            if (idInsertado > 0) {
                speakText("Medicamento $nombre registrado correctamente. Te avisaré cada $horarioHoras horas.")
            } else {
                speakText("Error al registrar el medicamento $nombre.")
                return // No continuar si falló la inserción
            }
        }

        programarRecordatorios(nombre, horaInicioMillis, horarioHoras)
        loadMedicamentos()
    }

    private fun realizarLlamada(context: Context, numero: String) {
        callManager.realizarLlamada(numero) { exito, mensaje ->
            if (!exito) {
                speakText("Error al realizar la llamada: $mensaje")
            }
        }
    }

    private fun checkContactPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun probarFuncionalidadContactos() {
        if (!checkContactPermissions()) {
            speakText("Necesito permisos para acceder a los contactos")
            return
        }

        val contactosEmergencia = contactManager.obtenerContactosEmergencia()
        if (contactosEmergencia.isNotEmpty()) {
            val mensaje = "Encontré ${contactosEmergencia.size} contactos de emergencia: " +
                    contactosEmergencia.joinToString(", ") { it.nombre }
            speakText(mensaje)
        } else {
            speakText("No se encontraron contactos de emergencia. Marque algunos contactos como favoritos.")
        }
    }

    @OptIn(UnstableApi::class)
    private fun convertirHoraTextoAMillis(horaTexto: String): Long {
        val formato = SimpleDateFormat("h a", Locale("es", "ES"))
        val textoLimpio = horaTexto.lowercase()
            .replace("de la ", "")
            .replace("mañana", "AM")
            .replace("tarde", "PM")
            .replace("noche", "PM")

        return try {
            val fecha = formato.parse(textoLimpio)
            val calendario = Calendar.getInstance()

            if (fecha != null) {
                val hora = Calendar.getInstance()
                hora.time = fecha

                calendario.set(Calendar.HOUR_OF_DAY, hora.get(Calendar.HOUR_OF_DAY))
                calendario.set(Calendar.MINUTE, 0)
                calendario.set(Calendar.SECOND, 0)

                // Si la hora ya pasó hoy, programar para mañana
                if (calendario.timeInMillis < System.currentTimeMillis()) {
                    calendario.add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            calendario.timeInMillis
        } catch (e: Exception) {
            Log.e("MainActivity", "Error convirtiendo hora: ${e.message}")
            System.currentTimeMillis()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Solicitar permiso
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        } else {
            // Ya tienes permiso, abrir cámara
            abrirCamara()
        }
    }

    // Métodos del ciclo de vida de la actividad
    @OptIn(UnstableApi::class)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MEDICINE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            // Detener el reconocimiento de voz para que no bloquee el micro
            stopBackgroundListening()

            val medicineName = data?.getStringExtra("medicine_name")
            if (!medicineName.isNullOrEmpty()) {
                waitingForMedicineConfirmation = true
                currentRecognizedMedicine = medicineName

                // Hablar información primero
                speakText("Medicamento reconocido: $medicineName. ¿Desea agregarlo a su lista de tratamientos?")

                // Reactivar reconocimiento después de que termine de hablar
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        runOnUiThread {
                            startBackgroundListening()
                        }
                    }
                    override fun onError(utteranceId: String?) {}
                })
            }
        }

        when (requestCode) {
            MEDICINE_RECOGNITION_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    // Obtener información del medicamento reconocido
                    val medicineName = data?.getStringExtra("medicine_name")
                    val medicineInfo = data?.getStringExtra("medicine_info")
                    val medicineManufacturer = data?.getStringExtra("medicine_manufacturer")
                    val medicinePurpose = data?.getStringExtra("medicine_purpose")

                    if (!medicineName.isNullOrEmpty()) {
                        Log.d("VoiceCommand", "Medicamento reconocido: $medicineName")

                        // Hablar el resultado
                        val speechText = buildString {
                            append("Medicamento reconocido exitosamente. ")
                            append("Nombre: $medicineName. ")
                            medicinePurpose?.let {
                                append("Propósito: ${it.take(100)}. ")
                            }
                            append("¿Desea agregar este medicamento a su lista de tratamientos?")
                        }

                        speakText(speechText)
                        tvEstado.text = "Medicamento reconocido: $medicineName"

                        // Opcional: Preguntar si quiere agregarlo al inventario
                        // Aquí puedes activar un flag para esperar confirmación
                        waitingForMedicineConfirmation = true
                        currentRecognizedMedicine = medicineName

                    } else {
                        Log.d("VoiceCommand", "No se reconoció ningún medicamento")
                        speakText("No se pudo reconocer el medicamento. Intente nuevamente con mejor iluminación.")
                        tvEstado.text = "Medicamento no reconocido"
                    }
                } else {
                    Log.d("VoiceCommand", "Reconocimiento cancelado")
                    speakText("Reconocimiento de medicamento cancelado")
                    tvEstado.text = "Reconocimiento cancelado"
                }
            }

            // Mantener otros casos que puedas tener
            // CAMERA_REQUEST_CODE -> { ... } // Solo si usas cámara nativa para otras cosas
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_RECORD_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show()
                    startBackgroundListening()
                } else {
                    Toast.makeText(
                        this,
                        "Permisos necesarios para el funcionamiento completo",
                        Toast.LENGTH_LONG
                    ).show()
                    // Intentar funcionalidad limitada sin todos los permisos
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startBackgroundListening()
                    }
                }
            }

            // Agregar este caso para el permiso de cámara
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permiso de cámara concedido", Toast.LENGTH_SHORT)
                        .show()
                    abrirCamara()
                } else {
                    Toast.makeText(
                        this,
                        "Permiso de cámara necesario para tomar fotos",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }


    @OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onStart() {
        super.onStart()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val resultado = aiAssistant.testAPIKey()
                if (!resultado) {
                    Toast.makeText(
                        this@MainActivity,
                        "API Key inválida o error de red",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@MainActivity, "API Key válida ✅", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error probando API Key: ${e.message}")
                Toast.makeText(
                    this@MainActivity,
                    "Error verificando conexión IA",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBackgroundListening()
        backgroundHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        backgroundSpeechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        aiConversationTimeout?.let { backgroundHandler.removeCallbacks(it) }
        aiAssistant.resetConversation()
    }

    override fun onPause() {
        super.onPause()
        // Mantener la vigilancia activa en segundo plano
        // No detener el reconocimiento de emergencias
    }

    override fun onResume() {
        super.onResume()
        // Asegurar que la vigilancia esté activa
        if (!isListening && !isBackgroundListening) {
            startBackgroundListening()
        }

        // Recargar medicamentos por si hubo cambios
        loadMedicamentos()
    }

    override fun onStop() {
        super.onStop()
        // Mantener servicios críticos activos
    }
}
