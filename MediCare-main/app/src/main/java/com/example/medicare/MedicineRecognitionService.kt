package com.example.medicare

// MedicineRecognitionService - VERSIÓN CON BUILDCONFIG

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// 🔥 INTERFACES PARA GEMINI API
interface GeminiAPI {
    @POST("v1beta/models/gemini-1.5-flash-latest:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: GeminiRequest
    ): GeminiResponse
}

// Data classes para Gemini (mantener las existentes)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig()
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiGenerationConfig(
    val temperature: Double = 0.2,
    val topP: Double = 0.8,
    val topK: Int = 40,
    val maxOutputTokens: Int = 800,
    val candidateCount: Int = 1
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>
)

data class GeminiCandidate(
    val content: GeminiContent,
    val finishReason: String? = null,
    val safetyRatings: List<GeminiSafetyRating>? = null
)

data class GeminiSafetyRating(
    val category: String,
    val probability: String
)

// Data classes para resultados (mantener las existentes)
sealed class MedicineAnalysisResult {
    data class Success(val medicineInfo: MedicineInfo) : MedicineAnalysisResult()
    data class Error(val message: String) : MedicineAnalysisResult()
}

sealed class MedicineInfo {
    data class Found(
        val name: String,
        val manufacturer: String?,
        val purpose: String?,
        val usage: String?,
        val warnings: String?,
        val activeIngredient: String?,
        val dosage: String?,
        val contraindications: String?,
        val source: String = "Google Gemini"
    ) : MedicineInfo()

    data class PartialMatch(
        val detectedName: String,
        val confidence: String,
        val suggestions: String,
        val possibleMatches: List<String> = emptyList()
    ) : MedicineInfo()

    object NotFound : MedicineInfo()
}

class MedicineRecognitionService(private val context: Context) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // 🔧 CONFIGURACIÓN DE GEMINI
    private val geminiRetrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val geminiAPI = geminiRetrofit.create(GeminiAPI::class.java)

    companion object {
        // 🔑 Coloca tu API key de Gemini aquí
        private const val GEMINI_API_KEY = ""
        const val CAMERA_REQUEST_CODE = 101
        const val CAMERA_PERMISSION_REQUEST_CODE = 102
        // Ejemplo: private const val GEMINI_API_KEY = "AIzaSyC-abcd1234567890..."
    }

    var lastAnalyzedMedicine: MedicineInfo? = null
        private set
    var onMedicineDetected: ((String) -> Unit)? = null

    suspend fun analyzeMedicine(bitmap: Bitmap): MedicineAnalysisResult {
        return try {
            Log.d("MedicineRecognition", "🔍 INICIANDO ANÁLISIS CON GEMINI")
            Log.d("MedicineRecognition", "🔑 API Key configurada: ${if (isGeminiConfigured()) "✅ SÍ" else "❌ NO"}")

            val extractedText = extractTextFromImage(bitmap)
            Log.d("MedicineRecognition", "📝 TEXTO EXTRAÍDO: $extractedText")

            if (extractedText.isBlank()) {
                return MedicineAnalysisResult.Error(
                    "No se pudo extraer texto de la imagen. Asegúrese de que la etiqueta esté visible y bien iluminada."
                )
            }

            val analysis = analyzeWithHybridStrategy(extractedText)
            lastAnalyzedMedicine = analysis
            if (analysis is MedicineInfo.Found) {
                onMedicineDetected?.invoke(analysis.name)
            }

            MedicineAnalysisResult.Success(analysis)

        } catch (e: Exception) {
            Log.e("MedicineRecognition", "💥 ERROR", e)
            val errorMessage = when {
                e.message?.contains("network", true) == true ->
                    "Error de conexión. Verificando con base de datos local."
                e.message?.contains("timeout", true) == true ->
                    "Tiempo de espera agotado. Intente nuevamente."
                else -> "Error al analizar: ${e.message}"
            }
            MedicineAnalysisResult.Error(errorMessage)
        }
    }

    private fun isGeminiInfoComplete(info: MedicineInfo.Found): Boolean {
        return !info.name.isNullOrBlank() &&
                !info.manufacturer.isNullOrBlank() && info.manufacturer != "No especificado" &&
                !info.purpose.isNullOrBlank() &&
                !info.usage.isNullOrBlank() &&
                !info.activeIngredient.isNullOrBlank()
    }
    private suspend fun analyzeWithHybridStrategy(text: String): MedicineInfo {
        Log.d("MedicineRecognition", "🧠 INICIANDO ANÁLISIS HÍBRIDO CON GEMINI")

        // Verificar configuración de Gemini
        if (!isGeminiConfigured()) {
            Log.w("MedicineRecognition", "⚠️ Gemini no configurado, usando solo base local")
            Log.w("MedicineRecognition", "💡 Para habilitar Gemini, configura tu API key en BuildConfig")
            return findExactMatch(text)
                ?: findByPattern(text)
                ?: performIntelligentAnalysis(text)
                ?: MedicineInfo.NotFound
        }

        // Buscar en base local primero (respuesta rápida)
        val localMatch = findExactMatch(text)

        // Consultar Gemini para información completa
        val geminiMatch = searchWithGemini(text)

        return when {
            // Si Gemini tiene info completa, usar solo esa
            geminiMatch is MedicineInfo.Found && isGeminiInfoComplete(geminiMatch) -> {
                Log.d("MedicineRecognition", "🤖 USANDO INFORMACIÓN COMPLETA DE GEMINI")
                geminiMatch
            }

            // Si Gemini tiene info pero incompleta Y hay match local, combinar
            geminiMatch is MedicineInfo.Found && localMatch is MedicineInfo.Found -> {
                Log.d("MedicineRecognition", "🔄 COMBINANDO LOCAL + GEMINI (Gemini incompleto)")
                combineLocalAndAIInfo(localMatch, geminiMatch)
            }

            // Si solo Gemini tiene info (aunque incompleta), usar esa
            geminiMatch is MedicineInfo.Found -> {
                Log.d("MedicineRecognition", "🤖 USANDO INFORMACIÓN PARCIAL DE GEMINI")
                geminiMatch
            }

            // Continuar con otras estrategias
            else -> {
                Log.d("MedicineRecognition", "🔍 BUSCANDO POR PATRONES")
                findByPattern(text) ?: performIntelligentAnalysis(text) ?: MedicineInfo.NotFound
            }
        }
    }

    private fun isGeminiConfigured(): Boolean {
        val apiKey = GEMINI_API_KEY
        val isConfigured = apiKey.isNotEmpty() &&
                apiKey != "TU_API_KEY_AQUI" &&
                apiKey != "" &&
                apiKey.length > 10 // Verificar que sea una API key válida

        Log.d("MedicineRecognition", "🔍 Verificando configuración Gemini:")
        Log.d("MedicineRecognition", "   - API Key presente: ${apiKey.isNotEmpty()}")
        Log.d("MedicineRecognition", "   - API Key válida: ${apiKey.length > 10}")
        Log.d("MedicineRecognition", "   - Estado final: ${if (isConfigured) "✅ CONFIGURADO" else "❌ NO CONFIGURADO"}")

        return isConfigured
    }

    // 🤖 BÚSQUEDA CON GEMINI
    private suspend fun searchWithGemini(text: String): MedicineInfo? {
        return try {
            val medicineNames = extractPossibleMedicineNames(text)
            val namesText = medicineNames.joinToString(", ")

            // Limpiar el texto para evitar caracteres problemáticos
            val cleanText = text.replace("\"", "'").replace("\n", " ").replace("\r", "")

            val prompt = """
Analiza este texto de medicamento y responde en JSON:

TEXTO: $cleanText

NOMBRES POSIBLES: $namesText

Responde con este formato JSON exacto:
{
  "medicamento_identificado": true,
  "nombre": "Omeprazol",
  "fabricante": "Corporación Sarepta",
  "principio_activo": "Omeprazol",
  "proposito": "Inhibidor de bomba de protones para acidez gastrica",
  "uso_dosificacion": "Una capsula al dia antes del desayuno",
  "advertencias": "Tomar con estomago vacio",
  "contraindicaciones": "Hipersensibilidad al omeprazol",
  "dosificacion": "20mg capsulas"
}

Si no identificas el medicamento con certeza, usa "medicamento_identificado": false.
Responde SOLO el JSON, nada mas.
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = prompt))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.1,
                    topP = 0.8,
                    topK = 40,
                    maxOutputTokens = 500,
                    candidateCount = 1
                )
            )

            Log.d("MedicineRecognition", "🤖 Consultando Gemini...")
            Log.d("MedicineRecognition", "📤 Request: ${request.contents.first().parts.first().text.take(200)}")

            val response = geminiAPI.generateContent(
                apiKey = GEMINI_API_KEY,
                request = request
            )

            val geminiResponse = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            Log.d("MedicineRecognition", "🤖 Respuesta Gemini recibida: ${geminiResponse?.take(100)}")

            return parseGeminiResponse(geminiResponse)

        } catch (e: Exception) {
            Log.e("MedicineRecognition", "🤖 Error Gemini: ${e.message}")

            // Logging más detallado para debugging
            if (e is retrofit2.HttpException) {
                Log.e("MedicineRecognition", "🔍 HTTP Error Code: ${e.code()}")
                try {
                    Log.e("MedicineRecognition", "🔍 HTTP Error Body: ${e.response()?.errorBody()?.string()}")
                } catch (ex: Exception) {
                    Log.e("MedicineRecognition", "🔍 No se pudo leer error body: ${ex.message}")
                }
            }

            // Log específico para diferentes tipos de error
            when {
                e.message?.contains("403") == true -> {
                    Log.e("MedicineRecognition", "❌ API Key inválida o sin permisos")
                }
                e.message?.contains("429") == true -> {
                    Log.e("MedicineRecognition", "⏰ Límite de requests excedido")
                }
                e.message?.contains("400") == true -> {
                    Log.e("MedicineRecognition", "⚠️ Request inválido - Verificar formato JSON")
                }
                e.message?.contains("quota", true) == true -> {
                    Log.e("MedicineRecognition", "💳 Cuota excedida")
                }
            }

            return null
        }
    }

    // Método para testear la conexión con Gemini
    suspend fun testGeminiConnection(): String {
        return try {
            if (!isGeminiConfigured()) {
                return "❌ API Key de Gemini no configurada\n💡 Configura BuildConfig.GEMINI_API_KEY en build.gradle"
            }

            val testRequest = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = "Responde solo: 'Gemini funcionando correctamente'"))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.0,
                    maxOutputTokens = 20
                )
            )

            val response = geminiAPI.generateContent(
                apiKey = GEMINI_API_KEY,
                request = testRequest
            )

            val responseText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (responseText?.contains("Gemini funcionando", ignoreCase = true) == true) {
                "✅ Gemini conectado correctamente"
            } else {
                "⚠️ Gemini responde pero formato inesperado: $responseText"
            }

        } catch (e: Exception) {
            when {
                e.message?.contains("403") == true -> "❌ API Key inválida"
                e.message?.contains("429") == true -> "⏰ Límite excedido"
                e.message?.contains("quota", true) == true -> "💳 Cuota excedida"
                else -> "❌ Error: ${e.message}"
            }
        }
    }

    // RESTO DE MÉTODOS (mantener todos los existentes)
    private suspend fun extractTextFromImage(bitmap: Bitmap): String {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    private val commonMedicines = mapOf(
        "omeprazol" to MedicineDetails(
            description = "Inhibidor de la bomba de protones para acidez y úlceras",
            warnings = "Tomar antes de las comidas. No partir cápsulas.",
            usage = "Una vez al día antes del desayuno"
        ),
        "paracetamol" to MedicineDetails(
            description = "Analgésico y antipirético usado para dolor y fiebre",
            warnings = "No exceder 4g diarios. Evitar con alcohol.",
            usage = "Cada 6-8 horas según necesidad"
        ),
        "ibuprofeno" to MedicineDetails(
            description = "Antiinflamatorio no esteroideo para dolor e inflamación",
            warnings = "Tomar con alimentos. Evitar en úlceras gástricas.",
            usage = "Cada 8 horas con alimentos"
        ),
        "amoxicilina" to MedicineDetails(
            description = "Antibiótico de amplio espectro para infecciones bacterianas",
            warnings = "Completar tratamiento aunque se sienta mejor.",
            usage = "Cada 8 horas por tiempo prescrito"
        ),
        "azitromicina" to MedicineDetails(
            description = "Antibiótico macrólido para infecciones respiratorias",
            warnings = "Puede causar diarrea. Evitar antiácidos.",
            usage = "Una vez al día por 3-5 días"
        )
    )

    private data class MedicineDetails(
        val description: String,
        val warnings: String,
        val usage: String
    )

    private fun findExactMatch(text: String): MedicineInfo? {
        val words = text.lowercase().split(Regex("[\\s\\n\\r\\t,.-]+"))

        // Buscar por nombre exacto o aproximado
        for (word in words) {
            if (word.length < 3) continue

            commonMedicines.entries.forEach { (medicine, details) ->
                if (word.contains(medicine) || medicine.contains(word) ||
                    // Manejar errores de OCR comunes
                    isApproximateMatch(word, medicine)) {
                    val dosage = extractDosage(text, word)
                    return MedicineInfo.Found(
                        name = "${medicine.replaceFirstChar { it.uppercase() }}${if (dosage.isNotEmpty()) " $dosage" else ""}",
                        manufacturer = extractManufacturer(text) ?: "Múltiples laboratorios",
                        purpose = details.description,
                        usage = details.usage,
                        warnings = details.warnings,
                        activeIngredient = medicine.replaceFirstChar { it.uppercase() },
                        dosage = dosage.ifEmpty { extractDosageFromText(text) },
                        contraindications = "Consulte prospecto y médico",
                        source = "Base de datos local"
                    )
                }
            }
        }
        return null
    }

    // Método para manejar errores comunes de OCR
    private fun isApproximateMatch(ocrWord: String, medicine: String): Boolean {
        // Casos específicos para medicamentos comunes con errores de OCR
        val approximations = mapOf(
            "omeprazzu" to "omeprazol",
            "omeprazol" to "omeprazol",
            "paracetamu" to "paracetamol",
            "ibuprofewo" to "ibuprofeno"
        )
        return approximations[ocrWord] == medicine
    }

    // Extraer fabricante del texto OCR
    private fun extractManufacturer(text: String): String? {
        val manufacturerPatterns = listOf(
            Regex("Para:\\s*([\\w\\s\\.]+)", RegexOption.IGNORE_CASE),
            Regex("Laboratorio[:\\s]*([\\w\\s\\.]+)", RegexOption.IGNORE_CASE),
            Regex("Corporation[:\\s]*([\\w\\s\\.]+)", RegexOption.IGNORE_CASE)
        )

        manufacturerPatterns.forEach { pattern ->
            pattern.find(text)?.let { match ->
                return match.groupValues[1].trim().take(50)
            }
        }
        return null
    }

    // Extraer dosificación más robusta
    private fun extractDosageFromText(text: String): String {
        val dosagePatterns = listOf(
            Regex("(\\d+\\s*mg)", RegexOption.IGNORE_CASE),
            Regex("(\\d+\\s*ml)", RegexOption.IGNORE_CASE),
            Regex("(\\d+\\s*g)", RegexOption.IGNORE_CASE),
            Regex("(\\d+\\s*mcg)", RegexOption.IGNORE_CASE)
        )

        dosagePatterns.forEach { pattern ->
            pattern.find(text)?.let { match ->
                return match.value
            }
        }
        return "Consultar empaque"
    }

    private fun findByPattern(text: String): MedicineInfo? {
        // Implementar patrones si es necesario
        return null
    }

    private fun performIntelligentAnalysis(text: String): MedicineInfo? {
        // Análisis inteligente local
        return null
    }

    private fun combineLocalAndAIInfo(local: MedicineInfo.Found, ai: MedicineInfo): MedicineInfo.Found {
        return when (ai) {
            is MedicineInfo.Found -> MedicineInfo.Found(
                name = ai.name.ifEmpty { local.name },
                manufacturer = ai.manufacturer ?: local.manufacturer,
                purpose = ai.purpose ?: local.purpose,
                usage = ai.usage ?: local.usage,
                warnings = ai.warnings ?: local.warnings,
                activeIngredient = ai.activeIngredient ?: local.activeIngredient,
                dosage = ai.dosage ?: local.dosage,
                contraindications = ai.contraindications,
                source = "Gemini + Base Local"
            )
            else -> local
        }
    }

    private fun createMedicineInfoFromText(response: String?): MedicineInfo? {
        return if (!response.isNullOrBlank() && response.length > 50) {
            MedicineInfo.PartialMatch(
                detectedName = "Información obtenida por IA",
                confidence = "Procesado con Google Gemini",
                suggestions = buildString {
                    appendLine("Información obtenida del análisis:")
                    appendLine()
                    appendLine(response.take(300))
                    if (response.length > 300) appendLine("...")
                    appendLine()
                    appendLine("⚠️ Esta información es orientativa.")
                    appendLine("Consulte con un profesional de la salud.")
                }
            )
        } else null
    }

    // 🔧 PARSING DE RESPUESTA GEMINI
    // 🔧 PARSING DE RESPUESTA GEMINI - VERSIÓN CORREGIDA
    private fun parseGeminiResponse(response: String?): MedicineInfo? {
        return try {
            if (response.isNullOrBlank()) return null

            Log.d("MedicineRecognition", "🔍 Respuesta completa de Gemini: $response")

            // Limpiar la respuesta y extraer JSON
            val cleanResponse = response.trim()
            val jsonStart = cleanResponse.indexOf("{")
            val jsonEnd = cleanResponse.lastIndexOf("}") + 1

            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                Log.w("MedicineRecognition", "⚠️ No se encontró JSON válido en respuesta Gemini")
                return createMedicineInfoFromText(response)
            }

            val jsonResponse = cleanResponse.substring(jsonStart, jsonEnd)
            Log.d("MedicineRecognition", "📄 JSON extraído: $jsonResponse")

            val medicamentoIdentificado = extractJsonValue(jsonResponse, "medicamento_identificado")
            Log.d("MedicineRecognition", "🔍 medicamento_identificado: $medicamentoIdentificado")

            if (medicamentoIdentificado?.contains("true") == true) {
                val nombre = extractJsonValue(jsonResponse, "nombre")
                Log.d("MedicineRecognition", "🔍 nombre extraído: $nombre")

                if (nombre.isNullOrBlank()) {
                    Log.w("MedicineRecognition", "⚠️ Gemini no proporcionó nombre del medicamento")
                    return null
                }

                // ✅ AQUÍ ESTÁ LA CORRECCIÓN: Proporcionar valores por defecto cuando son null
                val fabricante = extractJsonValue(jsonResponse, "fabricante") ?: "No especificado"
                val proposito = extractJsonValue(jsonResponse, "proposito") ?: getDefaultPurpose(nombre)
                val usoDosificacion = extractJsonValue(jsonResponse, "uso_dosificacion") ?: getDefaultUsage(nombre)
                val advertencias = extractJsonValue(jsonResponse, "advertencias") ?: getDefaultWarnings(nombre)
                val principioActivo = extractJsonValue(jsonResponse, "principio_activo") ?: nombre
                val dosificacion = extractJsonValue(jsonResponse, "dosificacion") ?: "Consultar empaque"
                val contraindicaciones = extractJsonValue(jsonResponse, "contraindicaciones") ?: "Consulte prospecto y médico"

                Log.d("MedicineRecognition", "✅ CREANDO MedicineInfo.Found con:")
                Log.d("MedicineRecognition", "   - Nombre: $nombre")
                Log.d("MedicineRecognition", "   - Fabricante: $fabricante")
                Log.d("MedicineRecognition", "   - Propósito: $proposito")

                return MedicineInfo.Found(
                    name = nombre,
                    manufacturer = fabricante,
                    purpose = proposito,
                    usage = usoDosificacion,
                    warnings = advertencias,
                    activeIngredient = principioActivo,
                    dosage = dosificacion,
                    contraindications = contraindicaciones,
                    source = "Google Gemini AI"
                )
            } else {
                Log.d("MedicineRecognition", "🤖 medicamento_identificado es false o null")
                return createMedicineInfoFromText(response)
            }

        } catch (e: Exception) {
            Log.e("MedicineRecognition", "❌ Error parsing respuesta Gemini", e)
            return createMedicineInfoFromText(response)
        }
    }

    // ✅ MÉTODOS AUXILIARES PARA VALORES POR DEFECTO
    private fun getDefaultPurpose(medicineName: String): String {
        return when (medicineName.lowercase()) {
            "amoxicilina" -> "Antibiótico de amplio espectro para infecciones bacterianas"
            "omeprazol" -> "Inhibidor de la bomba de protones para acidez y úlceras"
            "paracetamol" -> "Analgésico y antipirético para dolor y fiebre"
            "ibuprofeno" -> "Antiinflamatorio no esteroideo para dolor e inflamación"
            else -> "Medicamento prescrito - Consulte con su médico"
        }
    }

    private fun getDefaultUsage(medicineName: String): String {
        return when (medicineName.lowercase()) {
            "amoxicilina" -> "Según prescripción médica, generalmente cada 8 horas"
            "omeprazol" -> "Una vez al día antes del desayuno"
            "paracetamol" -> "Cada 6-8 horas según necesidad"
            "ibuprofeno" -> "Cada 8 horas con alimentos"
            else -> "Según prescripción médica"
        }
    }

    private fun getDefaultWarnings(medicineName: String): String {
        return when (medicineName.lowercase()) {
            "amoxicilina" -> "Completar tratamiento aunque se sienta mejor. Informar alergias."
            "omeprazol" -> "Tomar antes de las comidas. No partir cápsulas."
            "paracetamol" -> "No exceder 4g diarios. Evitar con alcohol."
            "ibuprofeno" -> "Tomar con alimentos. Evitar en úlceras gástricas."
            else -> "Seguir indicaciones médicas. Informar efectos adversos."
        }
    }

    private fun extractJsonValue(json: String, key: String): String? {
        // Para valores con comillas (strings)
        val stringPattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
        val stringRegex = Regex(stringPattern)
        stringRegex.find(json)?.let { match ->
            return match.groupValues[1].takeIf { it.isNotBlank() && it != "null" }
        }

        // Para valores sin comillas (booleanos, números)
        val valuePattern = "\"$key\"\\s*:\\s*([^,}\\s]+)"
        val valueRegex = Regex(valuePattern)
        valueRegex.find(json)?.let { match ->
            return match.groupValues[1].trim().takeIf { it.isNotBlank() && it != "null" }
        }

        return null
    }

    private fun extractDosage(text: String, medicineName: String): String {
        val dosagePatterns = listOf(
            Regex("(\\d+\\s*(mg|ml|g|mcg))", RegexOption.IGNORE_CASE)
        )

        dosagePatterns.forEach { pattern ->
            pattern.find(text)?.let { match ->
                return match.value
            }
        }
        return ""
    }

    private fun extractPossibleMedicineNames(text: String): List<String> {
        return text.split(Regex("[\\s\\n\\r\\t,.-]+"))
            .filter { it.length >= 3 && it.matches(Regex("[a-zA-ZáéíóúÁÉÍÓÚñÑ]+")) }
            .map { it.lowercase() }
            .distinct()
            .take(5)
    }
    fun iniciarReconocimientoConCamara(activity: Activity) {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivityForResult(intent, CAMERA_REQUEST_CODE)
        } else {
            Toast.makeText(activity, "No se pudo abrir la cámara", Toast.LENGTH_SHORT).show()
        }
    }
    private fun extraerNombreMedicamento(texto: String): String? {
        // Simula una detección simple, por ejemplo usando una lista de medicamentos conocidos
        val medicamentosConocidos = listOf("Paracetamol", "Ibuprofeno", "Amoxicilina")
        return medicamentosConocidos.firstOrNull { texto.contains(it, ignoreCase = true) }
    }
    fun analizarImagen(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val nombre = extraerNombreMedicamento(visionText.text)
                if (nombre != null) {
                    onMedicineDetected?.invoke(nombre)
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al analizar la imagen", Toast.LENGTH_SHORT).show()
            }
    }


    fun clearCache() {
        lastAnalyzedMedicine = null
        Log.d("MedicineRecognition", "🧹 Caché limpiado")
    }
}