package com.example.medicare

import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.SeekBar
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.Locale
import com.example.medicare.R
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.util.Log


class ReconocimientoMedicamentoMA : AppCompatActivity() {

    // Variables para CameraX
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: MaterialButton
    private lateinit var resultText: TextView

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    // 🆕 Servicio de IA y TTS
    private lateinit var medicineRecognitionService: MedicineRecognitionService
    private lateinit var tts: TextToSpeech
    private lateinit var repeatButton: Button
    private lateinit var speedSeekBar: SeekBar
    private var lastSpokenText: String = ""
    private var ttsInitialized = false
    private var waitingForMedicineConfirmation = false
    private var currentRecognizedMedicine: String? = null
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent
    private var isListening = false


    // Constantes
    companion object {
        private const val TAG = "MedicineRecognition"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.reconocimientomedicamento_activity_main)

        // 1️⃣ Inicializar vistas
        initializeViews()
        setupEdgeToEdge()

        // 2️⃣ Inicializar MedicineRecognitionService
        medicineRecognitionService = MedicineRecognitionService(this)

        // 3️⃣ Inicializar TextToSpeech
        initializeTTS()

        // 4️⃣ Configurar controles de TTS
        setupTTSControls()

        // 5️⃣ Configurar cámara
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        setupCaptureButton()
        cameraExecutor = Executors.newSingleThreadExecutor()

        // 6️⃣ Inicializar reconocimiento de voz
        inicializarReconocimientoVoz()
    }

    private fun inicializarReconocimientoVoz() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                isListening = false
                startListening() // Reinicia automáticamente si hay error
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val textoReconocido = matches[0].lowercase()
                    procesarRespuestaConfirmacion(textoReconocido)
                }
                startListening() // Escuchar continuamente
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
        }

        startListening()
    }

    private fun startListening() {
        if (!isListening) {
            isListening = true
            speechRecognizer.startListening(speechRecognizerIntent)
        }
    }

    private fun procesarRespuestaConfirmacion(textoLower: String) {
        if (!waitingForMedicineConfirmation) return

        when {
            textoLower.contains("sí") || textoLower.contains("si") || textoLower.contains("agregar") -> {
                speak("Perfecto, agregando ${currentRecognizedMedicine} a su lista de tratamientos.")
                waitingForMedicineConfirmation = false
                // Llama a tu función para registrar el medicamento aquí
            }

            textoLower.contains("no") || textoLower.contains("cancelar") -> {
                speak("Entendido, no se agregará el medicamento.")
                waitingForMedicineConfirmation = false
                currentRecognizedMedicine = null
            }
        }
    }


    private fun initializeViews() {
        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.captureButton)
        resultText = findViewById(R.id.resultText)
        repeatButton = findViewById(R.id.repeatButton)
        speedSeekBar = findViewById(R.id.speedSeekBar)
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initializeTTS() {
        tts = TextToSpeech(this) { status ->
            when (status) {
                TextToSpeech.SUCCESS -> {
                    // Fix: Use setLanguage() method which returns an int result
                    val result = tts.setLanguage(Locale("es", "ES"))

                    ttsInitialized = when (result) {
                        TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                            android.util.Log.w(TAG, "Idioma español no soportado, usando idioma por defecto")
                            tts.setLanguage(Locale.getDefault())
                            true
                        }
                        else -> true
                    }
                    android.util.Log.d(TAG, "TTS inicializado correctamente")
                }
                else -> {
                    android.util.Log.e(TAG, "Error al inicializar TTS")
                    ttsInitialized = false
                }
            }
        }
    }

    private fun setupTTSControls() {
        // Configurar botón de repetir
        repeatButton.setOnClickListener {
            if (lastSpokenText.isNotEmpty()) {
                speak(lastSpokenText)
            } else {
                Toast.makeText(this, "No hay texto para repetir", Toast.LENGTH_SHORT).show()
            }
        }

        // Configurar barra de velocidad de voz
        speedSeekBar.max = 20
        speedSeekBar.progress = 10 // Velocidad normal (1.0)
        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = (progress / 10.0f).coerceIn(0.1f, 2.0f)
                if (ttsInitialized) {
                    tts.setSpeechRate(speed)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupCaptureButton() {
        captureButton.setOnClickListener {
            captureImage()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Preview
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // ImageCapture - Configuración optimizada
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(windowManager.defaultDisplay.rotation)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

                resultText.text = "🤖 Sistema de IA listo con Gemini\n\n" +
                        "📸 Apunta al medicamento y presiona capturar\n\n" +
                        "💡 Para mejores resultados:\n" +
                        "• Buena iluminación\n" +
                        "• Texto claro y enfocado\n" +
                        "• Mantén la cámara estable\n" +
                        "• Asegúrate de tener conexión a internet"

                android.util.Log.d(TAG, "Cámara inicializada correctamente")

            } catch (exc: Exception) {
                android.util.Log.e(TAG, "Error al inicializar la cámara", exc)
                resultText.text = "❌ Error al inicializar la cámara: ${exc.localizedMessage}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return

        // Prevenir múltiples capturas simultáneas
        captureButton.isEnabled = false
        resultText.text = "📸 Capturando imagen...\n\n" +
                "⏳ Procesando con inteligencia artificial...\n" +
                "🤖 Esto puede tomar unos segundos..."

        android.util.Log.d(TAG, "Iniciando captura de imagen")

        // Capturar imagen en memoria
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exception: ImageCaptureException) {
                    android.util.Log.e(TAG, "Error al capturar imagen: ${exception.message}", exception)
                    resultText.text = "❌ Error al capturar imagen: ${exception.localizedMessage}\n\n" +
                            "💡 Intente nuevamente:\n" +
                            "• Verifique la iluminación\n" +
                            "• Mantenga la cámara estable\n" +
                            "• Asegúrese de que la lente esté limpia"
                    captureButton.isEnabled = true
                }

                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    android.util.Log.d(TAG, "Imagen capturada exitosamente: ${imageProxy.width}x${imageProxy.height}")

                    // Procesar imagen en hilo separado
                    lifecycleScope.launch(Dispatchers.Default) {
                        try {
                            val bitmap = imageProxyToBitmap(imageProxy)
                            android.util.Log.d(TAG, "Bitmap creado: ${bitmap.width}x${bitmap.height}")

                            // Cerrar ImageProxy para liberar memoria
                            imageProxy.close()

                            // Procesar con IA en hilo principal
                            withContext(Dispatchers.Main) {
                                analyzeMedicineWithAI(bitmap)
                            }

                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "Error al procesar ImageProxy", e)
                            withContext(Dispatchers.Main) {
                                resultText.text = "❌ Error al procesar imagen: ${e.localizedMessage}\n\n" +
                                        "💡 Intente nuevamente con mejor iluminación"
                                captureButton.isEnabled = true
                            }
                        }
                    }
                }
            }
        )
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Aplicar rotación si es necesaria
        return if (imageProxy.imageInfo.rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun analyzeMedicineWithAI(bitmap: Bitmap) {
        resultText.text = "🤖 ANÁLISIS CON INTELIGENCIA ARTIFICIAL\n" +
                "═══════════════════════════════════════\n\n" +
                "📸 Imagen procesada: ${bitmap.width}x${bitmap.height}\n" +
                "🔍 Extrayendo texto con ML Kit...\n" +
                "🧠 Consultando con Gemini...\n" +
                "📊 Analizando información médica...\n\n" +
                "⏳ Por favor espere..."

        android.util.Log.d(TAG, "Iniciando análisis con IA")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = medicineRecognitionService.analyzeMedicine(bitmap)
                android.util.Log.d(TAG, "Análisis completado: ${result::class.simpleName}")

                // Liberar bitmap después del análisis
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }

                withContext(Dispatchers.Main) {
                    when (result) {
                        is MedicineAnalysisResult.Success -> {
                            displayMedicineResult(result.medicineInfo)
                        }
                        is MedicineAnalysisResult.Error -> {
                            resultText.text = "❌ ${result.message}\n\n" +
                                    "💡 Sugerencias:\n" +
                                    "• Verifique la conexión a internet\n" +
                                    "• Asegúrese de que el texto sea legible\n" +
                                    "• Use mejor iluminación\n" +
                                    "• Acerque la cámara al texto"
                            android.util.Log.w(TAG, "Error en análisis: ${result.message}")
                        }
                    }
                    captureButton.isEnabled = true
                }

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error crítico en análisis de IA", e)
                withContext(Dispatchers.Main) {
                    resultText.text = "❌ Error crítico en el análisis: ${e.localizedMessage}\n\n" +
                            "💡 Intente nuevamente o reinicie la aplicación"
                    captureButton.isEnabled = true
                }
            }
        }
    }

    private fun displayMedicineResult(medicineInfo: MedicineInfo) {
        when (medicineInfo) {
            is MedicineInfo.Found -> {
                val result = buildString {
                    append("✅ MEDICAMENTO IDENTIFICADO\n")
                    append("═══════════════════════════════════\n\n")

                    append("💊 NOMBRE: ${medicineInfo.name}\n\n")

                    medicineInfo.manufacturer?.let {
                        append("🏭 FABRICANTE:\n${it.take(120)}${if (it.length > 120) "..." else ""}\n\n")
                    }

                    medicineInfo.activeIngredient?.let {
                        append("🧪 INGREDIENTE ACTIVO:\n${it.take(150)}${if (it.length > 150) "..." else ""}\n\n")
                    }

                    medicineInfo.purpose?.let {
                        append("🎯 PROPÓSITO:\n${it.take(200)}${if (it.length > 200) "..." else ""}\n\n")
                    }

                    medicineInfo.dosage?.let {
                        append("💉 DOSIFICACIÓN:\n${it.take(150)}${if (it.length > 150) "..." else ""}\n\n")
                    }

                    medicineInfo.usage?.let {
                        append("📖 MODO DE USO:\n${it.take(200)}${if (it.length > 200) "..." else ""}\n\n")
                    }

                    medicineInfo.warnings?.let {
                        append("⚠️ ADVERTENCIAS:\n${it.take(200)}${if (it.length > 200) "..." else ""}\n\n")
                    }

                    medicineInfo.contraindications?.let {
                        append("🚫 CONTRAINDICACIONES:\n${it.take(200)}${if (it.length > 200) "..." else ""}\n\n")
                    }

                    append("🔍 FUENTE: ${medicineInfo.source}\n\n")

                    append("🚨 DESCARGO DE RESPONSABILIDAD:\n")
                    append("Esta información es solo de referencia educativa.\n")
                    append("SIEMPRE consulte con un profesional médico\n")
                    append("antes de tomar cualquier medicamento.")
                }

                resultText.text = result

                // Crear mensaje de voz más natural
                val speechText = buildString {
                    append("Medicamento identificado exitosamente. ")
                    append("Nombre: ${medicineInfo.name}. ")

                    medicineInfo.purpose?.let {
                        val shortPurpose = if (it.length > 100) it.take(100) + "..." else it
                        append("Propósito: $shortPurpose. ")
                    }

                    medicineInfo.warnings?.let {
                        append("Importante: revise las advertencias en pantalla. ")
                    }

                    append("¿Desea agregar este medicamento a su lista de tratamientos?")
                }

                waitingForMedicineConfirmation = true
                currentRecognizedMedicine = medicineInfo.name
                speak(speechText)

                Handler(Looper.getMainLooper()).postDelayed({
                    returnMedicineResult(medicineInfo.name)
                }, 20000)

            }

            is MedicineInfo.PartialMatch -> {
                val result = buildString {
                    append("⚠️ COINCIDENCIA PARCIAL\n")
                    append("═══════════════════════════════════\n\n")
                    append("💊 DETECTADO: ${medicineInfo.detectedName}\n\n")
                    append("🤖 ANÁLISIS: ${medicineInfo.confidence}\n\n")

                    if (medicineInfo.possibleMatches.isNotEmpty()) {
                        append("🔍 POSIBLES COINCIDENCIAS:\n")
                        medicineInfo.possibleMatches.take(3).forEach { match ->
                            append("• ${match.replaceFirstChar { it.uppercase() }}\n")
                        }
                        append("\n")
                    }

                    append("💡 INFORMACIÓN ADICIONAL:\n${medicineInfo.suggestions}")
                }

                resultText.text = result

                val speechText = "Se encontró una coincidencia parcial. " +
                        "Medicamento detectado: ${medicineInfo.detectedName}. " +
                        "Revise la información en pantalla para más detalles."

                speak(speechText)
                Toast.makeText(this, "Coincidencia parcial - Revise detalles", Toast.LENGTH_LONG).show()
                android.util.Log.d(TAG, "Coincidencia parcial: ${medicineInfo.detectedName}")

                returnMedicineResult(Medicamento())
            }

            is MedicineInfo.NotFound -> {
                resultText.text = "❌ MEDICAMENTO NO IDENTIFICADO\n" +
                        "═══════════════════════════════════\n\n" +
                        "🔍 No se pudo identificar el medicamento en la imagen.\n\n" +
                        "💡 CONSEJOS PARA MEJORES RESULTADOS:\n\n" +
                        "📸 CALIDAD DE IMAGEN:\n" +
                        "• Use buena iluminación (luz natural preferible)\n" +
                        "• Mantenga la cámara estable\n" +
                        "• Enfoque claramente el texto del medicamento\n" +
                        "• Limpie la lente de la cámara\n\n" +
                        "📋 CONTENIDO:\n" +
                        "• Asegúrese de que el nombre esté visible\n" +
                        "• Incluya la etiqueta principal\n" +
                        "• Evite sombras sobre el texto\n" +
                        "• Tome la foto de frente (no en ángulo)\n\n" +
                        "🌐 CONECTIVIDAD:\n" +
                        "• Verifique su conexión a internet\n" +
                        "• Reintente si hay problemas de red\n\n" +
                        "🔄 Intente tomar otra fotografía"

                speak("No se pudo identificar el medicamento. " +
                        "Intente tomar otra fotografía con mejor iluminación " +
                        "y asegúrese de que el nombre del medicamento esté claramente visible.")

                Toast.makeText(this, "No identificado - Intente nuevamente", Toast.LENGTH_LONG).show()
                android.util.Log.d(TAG, "Medicamento no identificado")

                returnNoMedicineResult()
            }
        }
    }
    private fun returnMedicineResult(medicineName: String) {
        val resultIntent = Intent().apply {
            putExtra("medicine_name", medicineName)
        }
        setResult(RESULT_OK, resultIntent)
        finish() // Cierra esta activity y regresa al MainActivity
    }


    private fun speak(text: String) {
        if (ttsInitialized && text.isNotEmpty()) {
            lastSpokenText = text
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "medicine_tts")
            android.util.Log.d(TAG, "Reproduciendo TTS: ${text.take(50)}...")
        } else {
            android.util.Log.w(TAG, "TTS no disponible o texto vacío")
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
                android.util.Log.d(TAG, "Permisos de cámara concedidos")
            } else {
                Toast.makeText(
                    this,
                    "Los permisos de cámara son necesarios para el funcionamiento de la aplicación.",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.w(TAG, "Permisos de cámara denegados")
                finish()
            }
        }
    }
    private fun returnMedicineResult(medicineInfo: Medicamento) {
        val shouldReturnResult = intent.getBooleanExtra("return_result", false)

        if (shouldReturnResult) {
            val resultIntent = Intent().apply {
                putExtra("medicine_name", medicineInfo.nombre)
                putExtra("medicine_dosage", medicineInfo.cantidad.toString())
                putExtra("medicine_usage", medicineInfo.getHorarioTexto())
                putExtra("medicine_info", medicineInfo.obtenerResumen())
            }

            setResult(RESULT_OK, resultIntent)
            Toast.makeText(this, "Medicamento identificado - Regresando...", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2000)
        }
    }

    private fun returnNoMedicineResult() {
        val shouldReturnResult = intent.getBooleanExtra("return_result", false)
        
        if (shouldReturnResult) {
            val resultIntent = Intent().apply {
                putExtra("medicine_name", "")
                putExtra("medicine_info", "No se pudo identificar el medicamento")
            }
            
            setResult(RESULT_OK, resultIntent)
            
            // Cerrar después de mostrar el mensaje
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 3000)
        }
    }
    override fun onDestroy() {
        super.onDestroy()

        // Limpiar recursos
        if (ttsInitialized) {
            tts.stop()
            tts.shutdown()
        }

        // Limpiar servicio de reconocimiento
        medicineRecognitionService.clearCache()

        // Cerrar executor
        cameraExecutor.shutdown()

        android.util.Log.d(TAG, "Recursos liberados correctamente")
    }

    override fun onPause() {
        super.onPause()

        // Detener TTS si está hablando
        if (ttsInitialized) {
            tts.stop()
        }

        // Liberar SpeechRecognizer para que no bloquee el micrófono
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("ReconocimientoMedicamento", "Error al liberar SpeechRecognizer", e)
        }

        // Forzar GC opcional
        System.gc()
    }

    override fun onResume() {
        super.onResume()
        // Reactualizar estado de la interfaz si es necesario
        android.util.Log.d(TAG, "Aplicación resumida")
    }
}