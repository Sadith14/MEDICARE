package com.example.medicare

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.medicare.Medicamento
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class AIAssistantManager(private val context: Context) {

    private val openAIApiKey = "" // Mover a recursos seguros
    private val openAIBaseUrl = ""

    // Estado de la conversación
    private var conversationHistory = mutableListOf<ChatMessage>()
    private var isInEmergencyMode = false
    private var userProfile: UserProfile? = null

    data class ChatMessage(
        val role: String, // "user", "assistant", "system"
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class UserProfile(
        val name: String,
        val age: Int?,
        val medications: List<String>,
        val emergencyContacts: List<String>,
        val medicalConditions: List<String>
    )

    data class AIResponse(
        val message: String,
        val shouldCallEmergency: Boolean = false,
        val suggestedActions: List<String> = emptyList(),
        val confidence: Float = 0.0f,
        val isOnlineResponse: Boolean = true
    )

    /**
     * Inicializar el asistente en modo emergencia
     */
    fun initializeEmergencyMode(medications: List<Medicamento>) {
        isInEmergencyMode = true
        userProfile = UserProfile(
            name = "Usuario",
            age = null,
            medications = medications.map { "${it.nombre} cada ${it.horarioHoras}h" },
            emergencyContacts = listOf("Familiar: 924783017"),
            medicalConditions = emptyList()
        )

        // Mensaje inicial del sistema
        val systemPrompt = createEmergencySystemPrompt()
        conversationHistory.clear()
        conversationHistory.add(ChatMessage("system", systemPrompt))

        Log.d("AIAssistant", "Modo emergencia activado")
    }

    /**
     * Inicializar conversación normal
     */
    fun initializeNormalMode() {
        isInEmergencyMode = false
        val systemPrompt = createNormalSystemPrompt()
        conversationHistory.clear()
        conversationHistory.add(ChatMessage("system", systemPrompt))

        Log.d("AIAssistant", "Modo normal activado")
    }

    /**
     * Procesar mensaje del usuario y obtener respuesta de la IA
     */
    suspend fun processUserMessage(userMessage: String): AIResponse {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AIAssistant", "Procesando mensaje: $userMessage")

                // Verificar conexión a internet primero
                if (!isInternetAvailable()) {
                    Log.w("AIAssistant", "Sin conexión a internet, usando respuesta offline")
                    return@withContext getOfflineResponse(userMessage)
                }

                // Agregar mensaje del usuario al historial
                conversationHistory.add(ChatMessage("user", userMessage))
                Log.d("AIAssistant", "Mensaje agregado al historial. Total mensajes: ${conversationHistory.size}")

                // Llamar a la API de OpenAI
                Log.d("AIAssistant", "Llamando a OpenAI API...")
                val response = callOpenAIAPI()
                Log.d("AIAssistant", "Respuesta recibida de OpenAI")

                // Procesar la respuesta
                val aiResponse = parseAIResponse(response)

                // Agregar respuesta al historial
                conversationHistory.add(ChatMessage("assistant", aiResponse.message))

                // Mantener historial manejable (últimos 10 mensajes)
                if (conversationHistory.size > 10) {
                    conversationHistory.removeAt(1) // Mantener el system prompt
                }

                Log.d("AIAssistant", "Respuesta procesada exitosamente")
                aiResponse

            } catch (e: Exception) {
                Log.e("AIAssistant", "Error procesando mensaje: ${e.message}", e)

                // Si hay error con la API, usar respuesta offline
                getOfflineResponse(userMessage).copy(
                    message = "Tengo problemas de conexión. ${getOfflineResponse(userMessage).message}",
                    isOnlineResponse = false
                )
            }
        }
    }

    /**
     * Crear prompt del sistema para modo normal
     */
    private fun createNormalSystemPrompt(): String {
        return """
        Eres un asistente virtual amigable y conversacional. Puedes ayudar con:
        - Conversación general
        - Responder preguntas
        - Dar consejos básicos
        - Proporcionar información
        - Mantener una charla natural
        
        INSTRUCCIONES:
        1. Sé natural y conversacional
        2. Responde en español
        3. Sé útil y empático
        4. Puedes hacer preguntas para mantener la conversación
        5. Si no sabes algo, admítelo
        
        Mantén un tono amigable y ayuda al usuario con lo que necesite.
        """.trimIndent()
    }

    /**
     * Crear prompt del sistema para modo emergencia
     */
    private fun createEmergencySystemPrompt(): String {
        val medicationsText = userProfile?.medications?.joinToString(", ") ?: "ninguno"

        return """
        Eres un asistente de emergencia médica virtual. El usuario está en una situación donde no pudo contactar a sus familiares.

        INFORMACIÓN DEL USUARIO:
        - Medicamentos actuales: $medicationsText
        - Contactos de emergencia: No disponibles actualmente

        INSTRUCCIONES IMPORTANTES:
        1. Mantén una conversación con el usuario
        2. Si detectas emergencia grave (dolor en el pecho, dificultad para respirar, pérdida de conciencia), recomienda llamar al 911 inmediatamente
        3. Puedes dar consejos básicos de primeros auxilios
        4. Pregunta sobre síntomas específicos para evaluar la situación
        5. Recuerda los medicamentos que toma para evitar interacciones
        6. Mantén conversación fluida en español
        7. Si no es emergencia grave, ofrece compañía y apoyo emocional

        NUNCA:
        - Des diagnósticos médicos definitivos
        - Recomiendes cambiar medicación sin consulta médica
        - Minimices síntomas graves

        Responde de manera empática, clara y útil.
        """.trimIndent()
    }

    /**
     * Llamar a la API de OpenAI
     */
    private suspend fun callOpenAIAPI(): String {
        return withContext(Dispatchers.IO) {
            val url = URL(openAIBaseUrl)
            val connection = url.openConnection() as HttpsURLConnection

            try {
                Log.d("AIAssistant", "Configurando conexión HTTP...")

                // Configurar conexión
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $openAIApiKey")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 30000

                // Crear payload JSON
                val payload = createChatPayload()
                Log.d("AIAssistant", "Payload creado: ${payload.toString().take(200)}...")

                // Enviar request
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(payload.toString())
                writer.flush()
                writer.close()

                Log.d("AIAssistant", "Request enviado, esperando respuesta...")

                // Leer respuesta
                val responseCode = connection.responseCode
                Log.d("AIAssistant", "Código de respuesta: $responseCode")

                val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val reader = BufferedReader(InputStreamReader(inputStream))
                val response = reader.readText()
                reader.close()

                Log.d("AIAssistant", "Respuesta recibida: ${response.take(200)}...")

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("AIAssistant", "Error de API: $responseCode - $response")
                    throw Exception("API Error: $responseCode - $response")
                }

                response

            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * Crear payload para la API de chat
     */
    private fun createChatPayload(): JSONObject {
        val payload = JSONObject()
        payload.put("model", "gpt-3.5-turbo")
        payload.put("max_tokens", 300)
        payload.put("temperature", 0.7)

        val messagesArray = JSONArray()

        conversationHistory.forEach { message ->
            val messageObj = JSONObject()
            messageObj.put("role", message.role)
            messageObj.put("content", message.content)
            messagesArray.put(messageObj)
        }

        payload.put("messages", messagesArray)

        Log.d("AIAssistant", "Mensajes en el payload: ${messagesArray.length()}")

        return payload
    }

    /**
     * Parsear respuesta de OpenAI
     */
    private fun parseAIResponse(response: String): AIResponse {
        try {
            val jsonResponse = JSONObject(response)
            val choices = jsonResponse.getJSONArray("choices")
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            val content = message.getString("content")

            // Analizar si sugiere llamar emergencia (solo en modo emergencia)
            val shouldCallEmergency = if (isInEmergencyMode) {
                content.lowercase().let { text ->
                    text.contains("911") ||
                            text.contains("emergencia") ||
                            text.contains("llama al hospital") ||
                            text.contains("urgente")
                }
            } else false

            // Extraer acciones sugeridas
            val suggestedActions = if (isInEmergencyMode) {
                extractSuggestedActions(content)
            } else emptyList()

            return AIResponse(
                message = content,
                shouldCallEmergency = shouldCallEmergency,
                suggestedActions = suggestedActions,
                confidence = 0.8f,
                isOnlineResponse = true
            )

        } catch (e: Exception) {
            Log.e("AIAssistant", "Error parsing response: ${e.message}")
            throw e
        }
    }

    /**
     * Extraer acciones sugeridas del texto
     */
    private fun extractSuggestedActions(content: String): List<String> {
        val actions = mutableListOf<String>()
        val lowerContent = content.lowercase()

        if (lowerContent.contains("respira") || lowerContent.contains("respiración")) {
            actions.add("Ejercicio de respiración")
        }

        if (lowerContent.contains("agua") || lowerContent.contains("hidratar")) {
            actions.add("Beber agua")
        }

        if (lowerContent.contains("sientat") || lowerContent.contains("descansar")) {
            actions.add("Sentarse y descansar")
        }

        if (lowerContent.contains("medicament") || lowerContent.contains("medicina")) {
            actions.add("Revisar medicamentos")
        }

        return actions
    }

    /**
     * Obtener respuesta offline según el modo
     */
    private fun getOfflineResponse(userMessage: String): AIResponse {
        return if (isInEmergencyMode) {
            getOfflineEmergencyResponse(userMessage)
        } else {
            getOfflineNormalResponse(userMessage)
        }
    }

    /**
     * Respuestas offline para conversación normal
     */
    private fun getOfflineNormalResponse(userMessage: String): AIResponse {
        val lowerMessage = userMessage.lowercase()

        return when {
            lowerMessage.contains("hola") || lowerMessage.contains("buenos") -> {
                AIResponse(
                    message = "¡Hola! ¿Cómo estás? ¿En qué puedo ayudarte hoy?",
                    isOnlineResponse = false
                )
            }

            lowerMessage.contains("cómo estás") || lowerMessage.contains("como estas") -> {
                AIResponse(
                    message = "Estoy bien, gracias por preguntar. Estoy aquí para ayudarte con lo que necesites. ¿Qué tal tú?",
                    isOnlineResponse = false
                )
            }

            lowerMessage.contains("gracias") -> {
                AIResponse(
                    message = "¡De nada! Estoy aquí para ayudarte. ¿Hay algo más en lo que pueda asistirte?",
                    isOnlineResponse = false
                )
            }

            lowerMessage.contains("adiós") || lowerMessage.contains("chau") -> {
                AIResponse(
                    message = "¡Hasta luego! Ha sido un placer conversar contigo. Que tengas un buen día.",
                    isOnlineResponse = false
                )
            }

            else -> {
                AIResponse(
                    message = "Interesante lo que me dices. Cuéntame más sobre eso, me gustaría escucharte.",
                    isOnlineResponse = false
                )
            }
        }
    }

    /**
     * Obtener respuesta de emergencia offline (fallback)
     */
    fun getOfflineEmergencyResponse(userMessage: String): AIResponse {
        val lowerMessage = userMessage.lowercase()

        return when {
            lowerMessage.contains("dolor") && lowerMessage.contains("pecho") -> {
                AIResponse(
                    message = "Si tienes dolor en el pecho, es importante que llames al 911 inmediatamente. Mientras tanto, siéntate y trata de mantener la calma.",
                    shouldCallEmergency = true,
                    suggestedActions = listOf("Llamar 911", "Sentarse", "Mantener la calma"),
                    isOnlineResponse = false
                )
            }

            lowerMessage.contains("mareo") || lowerMessage.contains("mareado") -> {
                AIResponse(
                    message = "Si te sientes mareado, siéntate inmediatamente o acuéstate con las piernas elevadas. Bebe un poco de agua si puedes.",
                    shouldCallEmergency = false,
                    suggestedActions = listOf("Sentarse", "Elevar piernas", "Beber agua"),
                    isOnlineResponse = false
                )
            }

            lowerMessage.contains("caída") || lowerMessage.contains("caí") -> {
                AIResponse(
                    message = "¿Te encuentras bien después de la caída? ¿Puedes moverte sin dolor? Si tienes dolor intenso o no puedes moverte, necesitas atención médica.",
                    shouldCallEmergency = false,
                    suggestedActions = listOf("Evaluar dolor", "Verificar movilidad"),
                    isOnlineResponse = false
                )
            }

            lowerMessage.contains("solo") || lowerMessage.contains("miedo") -> {
                AIResponse(
                    message = "Entiendo que te sientes solo y asustado. Estoy aquí contigo. Respira profundo y cuéntame qué está pasando.",
                    shouldCallEmergency = false,
                    suggestedActions = listOf("Ejercicio de respiración", "Contar la situación"),
                    isOnlineResponse = false
                )
            }

            else -> {
                AIResponse(
                    message = "Estoy aquí para ayudarte. ¿Puedes contarme qué está pasando y cómo te sientes?",
                    shouldCallEmergency = false,
                    suggestedActions = listOf("Describir síntomas"),
                    isOnlineResponse = false
                )
            }
        }
    }

    /**
     * Reiniciar conversación
     */
    fun resetConversation() {
        conversationHistory.clear()
        isInEmergencyMode = false
        userProfile = null
    }

    /**
     * Verificar si hay conexión a internet usando ConnectivityManager
     */
    fun isInternetAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)

            networkCapabilities != null &&
                    (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))

        } catch (e: Exception) {
            Log.e("AIAssistant", "Error verificando conexión: ${e.message}")
            false
        }
    }

    /**
     * Verificar estado de la API Key
     */
    suspend fun testAPIKey(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Crear una conversación de prueba simple
                conversationHistory.clear()
                conversationHistory.add(ChatMessage("system", "Responde solo con 'OK'"))
                conversationHistory.add(ChatMessage("user", "test"))

                val response = callOpenAIAPI()
                val jsonResponse = JSONObject(response)

                // Si llegamos aquí sin excepción, la API Key funciona
                Log.d("AIAssistant", "API Key válida")
                true

            } catch (e: Exception) {
                Log.e("AIAssistant", "API Key inválida o error de conexión: ${e.message}")
                false
            }
        }
    }
}