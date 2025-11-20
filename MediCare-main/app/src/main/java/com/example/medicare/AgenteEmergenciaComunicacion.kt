package com.example.medicare

import android.app.AlarmManager
import android.os.Handler
import android.os.Looper
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlinx.coroutines.*
import okhttp3.*
import com.google.gson.Gson
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AgenteEmergenciaComunicacion(private val context: Context) {

    private val tag = "AgenteEmergencia"
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Configuración por defecto para Telegram (debe ser configurado por el usuario)
    private var configuracionTelegram: ConfiguracionTelegram? = null

    init {
        try {
            // Registrar el agente en EventBus
            EventBus.getDefault().register(this)
            Log.d(tag, "✅ Agente de Emergencia inicializado correctamente")
            Log.i(tag, "📊 Estado inicial - EventBus registrado, Cliente HTTP configurado")

            // Mostrar mensaje en pantalla
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "🚨 Sistema de Emergencia INICIADO", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ ERROR al inicializar Agente de Emergencia", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "❌ Error al iniciar sistema emergencia", Toast.LENGTH_LONG).show()
            }
        }
    }

    // === MANEJO DE EVENTOS ===

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMedicamentoPostergado(event: MedicamentoPostergadoEvent) {
        Log.d(tag, "📋 Evento recibido: Medicamento postergado")
        Log.i(tag, "💊 Medicamento: ${event.nombreMedicamento}")
        Log.i(tag, "⏱️ Tiempo postergado: ${event.tiempoPostergado} minutos")
        Log.i(tag, "🔄 Postergación número: ${event.numeroPostergacion}")

        when {
            event.numeroPostergacion >= 4 -> {
            Log.e(tag, "🆘 ACTIVANDO PROTOCOLO CRÍTICO: Llamada de emergencia (60+ min, 4+ postergaciones)")
            realizarLlamadaEmergencia(event)
            }
            event.numeroPostergacion >= 3 -> {
                Log.w(tag, "⚠️ ACTIVANDO PROTOCOLO: Mensaje de emergencia (45+ min, 3+ postergaciones)")
                enviarMensajeEmergencia(event)
            }
            else -> {
                Log.d(tag, "📊 Condiciones no cumplidas para emergencia - continuando monitoreo")
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMedicamentoNoTomado(event: MedicamentoNoTomadoEvent) {
        Log.d(tag, "📋 Evento recibido: Medicamento no tomado")
        Log.i(tag, "💊 Medicamento: ${event.nombreMedicamento}")
        Log.i(tag, "⏱️ Tiempo transcurrido: ${event.tiempoTranscurrido} minutos")

        if (event.tiempoTranscurrido >= 15) {
            Log.w(tag, "⚠️ ACTIVANDO PROTOCOLO: Mensaje por medicamento no tomado (45+ minutos)")
            enviarMensajeEmergenciaMedicamentoNoTomado(event)
        } else {
            Log.d(tag, "📊 Aún no se cumple tiempo límite para emergencia (${event.tiempoTranscurrido}/45 min)")
        }
    }

    // === CONFIGURACIÓN ===

    fun configurarTelegram(botToken: String, chatId: String) {
        try {
            configuracionTelegram = ConfiguracionTelegram(botToken, chatId)
            Log.d(tag, "✅ Telegram configurado correctamente")
            Log.i(tag, "🤖 Bot Token: ${botToken.take(10)}...")
            Log.i(tag, "💬 Chat ID: $chatId")

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "✅ Telegram configurado", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Error al configurar Telegram", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "❌ Error config Telegram", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // === OBTENCIÓN DE CONTACTO DE EMERGENCIA ===

    private fun obtenerContactoEmergencia(): ContactoEmergencia? {
        Log.d(tag, "🔍 Buscando contacto de emergencia en agenda...")

        try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%contacto emergencia%"),
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val nombre = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    val telefono = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))

                    Log.d(tag, "✅ Contacto de emergencia encontrado: $nombre - $telefono")
                    return ContactoEmergencia(nombre, telefono.replace("\\s".toRegex(), ""))
                } else {
                    Log.w(tag, "⚠️ Cursor vacío - no hay contactos que coincidan")
                }
            } ?: run {
                Log.w(tag, "⚠️ Cursor nulo - problema de permisos o acceso a contactos")
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Error al obtener contacto de emergencia", e)
        }

        Log.e(tag, "❌ No se encontró contacto de emergencia")
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "❌ Sin contacto emergencia", Toast.LENGTH_SHORT).show()
        }
        return null
    }

    // === ENVÍO DE MENSAJES POR TELEGRAM ===

    private fun enviarMensajeEmergencia(event: MedicamentoPostergadoEvent) {
        Log.d(tag, "📤 Iniciando envío de mensaje de emergencia...")

        val contacto = obtenerContactoEmergencia()
        if (contacto == null) {
            Log.e(tag, "❌ No se puede enviar mensaje: contacto no encontrado")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "❌ Sin contacto para emergencia", Toast.LENGTH_LONG).show()
            }
            return
        }

        val fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date(event.fechaHoraOriginal))

        val mensaje = """
            🚨 ALERTA MEDICAMENTO 🚨
            
            El usuario no ha tomado su medicamento:
            💊 Medicamento: ${event.nombreMedicamento}
            ⏰ Hora programada: $fechaHora
            ⏱️ Tiempo transcurrido: ${event.tiempoPostergado} minutos
            📊 Postergaciones: ${event.numeroPostergacion}
            
            Por favor, contacte al usuario para verificar su estado.
            
            Contacto de emergencia: ${contacto.nombre}
        """.trimIndent()

        Log.i(tag, "📝 Mensaje generado: ${mensaje.length} caracteres")
        enviarMensajeTelegram(mensaje)

        // Notificar al sistema sobre la acción tomada
        EventBus.getDefault().post(
            ContactoEmergenciaEvent(
                contacto.nombre,
                contacto.telefono,
                "MENSAJE"
            )
        )

        Log.d(tag, "📨 Evento ContactoEmergenciaEvent enviado (MENSAJE)")
    }

    private fun enviarMensajeEmergenciaMedicamentoNoTomado(event: MedicamentoNoTomadoEvent) {
        Log.d(tag, "📤 Iniciando mensaje por medicamento no tomado...")

        val contacto = obtenerContactoEmergencia()
        if (contacto == null) {
            Log.e(tag, "❌ No se puede enviar mensaje: contacto no encontrado")
            return
        }

        val fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date(event.fechaHoraOriginal))

        val mensaje = """
            ⚠️ MEDICAMENTO NO TOMADO ⚠️
            
            💊 Medicamento: ${event.nombreMedicamento}
            ⏰ Hora programada: $fechaHora
            ⏱️ Tiempo sin tomar: ${event.tiempoTranscurrido} minutos
            
            El usuario no ha confirmado la toma del medicamento.
            Por favor, verifique su estado.
            
            Contacto de emergencia: ${contacto.nombre}
        """.trimIndent()

        Log.i(tag, "📝 Mensaje no tomado generado: ${mensaje.length} caracteres")
        enviarMensajeTelegram(mensaje)
    }

    private fun enviarMensajeTelegram(mensaje: String) {
        val config = configuracionTelegram
        if (config == null) {
            Log.e(tag, "❌ Telegram no está configurado - no se puede enviar mensaje")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "❌ Telegram no configurado", Toast.LENGTH_SHORT).show()
            }
            return
        }

        Log.d(tag, "🚀 Enviando mensaje a Telegram...")
        Log.i(tag, "🌐 URL: https://api.telegram.org/bot[TOKEN]/sendMessage")

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot${config.botToken}/sendMessage"

                val requestBody = FormBody.Builder()
                    .add("chat_id", config.chatId)
                    .add("text", mensaje)
                    .add("parse_mode", "HTML")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                Log.d(tag, "📡 Realizando petición HTTP...")

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(tag, "✅ Mensaje de Telegram enviado exitosamente")
                        Log.i(tag, "📊 Código respuesta: ${response.code}")

                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "✅ Mensaje enviado", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e(tag, "❌ Error al enviar mensaje de Telegram: ${response.code}")
                        Log.e(tag, "📄 Respuesta: ${response.body?.string()}")

                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "❌ Error envío: ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(tag, "❌ Error de red al enviar mensaje de Telegram", e)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "❌ Error de conexión", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ Error inesperado al enviar mensaje", e)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "❌ Error inesperado", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // === LLAMADAS DE EMERGENCIA ===

    private fun realizarLlamadaEmergencia(event: MedicamentoPostergadoEvent) {
        Log.e(tag, "🆘 INICIANDO LLAMADA DE EMERGENCIA")
        Log.e(tag, "💊 Medicamento: ${event.nombreMedicamento}")
        Log.e(tag, "⏱️ Tiempo crítico: ${event.tiempoPostergado} minutos")

        val contacto = obtenerContactoEmergencia()
        if (contacto == null) {
            Log.e(tag, "❌ CRÍTICO: No se puede realizar llamada - contacto no encontrado")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "🆘 SIN CONTACTO PARA LLAMADA", Toast.LENGTH_LONG).show()
            }
            return
        }

        try {
            // Primero enviar mensaje crítico
            val mensajeCritico = """
                🆘 EMERGENCIA MEDICAMENTO 🆘
                
                SITUACIÓN CRÍTICA:
                💊 Medicamento: ${event.nombreMedicamento}
                ⏱️ Sin tomar por: ${event.tiempoPostergado} minutos
                📊 Postergaciones: ${event.numeroPostergacion}
                
                ☎️ LLAMADA AUTOMÁTICA INICIADA
                
                CONTACTE INMEDIATAMENTE AL USUARIO
            """.trimIndent()

            Log.w(tag, "📤 Enviando mensaje crítico antes de llamar...")
            enviarMensajeTelegram(mensajeCritico)

            // Realizar llamada automática
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${contacto.telefono}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            Log.d(tag, "☎️ Iniciando llamada a: ${contacto.telefono}")
            context.startActivity(intent)

            Log.d(tag, "✅ Llamada de emergencia iniciada a: ${contacto.nombre}")

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "☎️ LLAMANDO: ${contacto.nombre}", Toast.LENGTH_LONG).show()
            }

            // Notificar al sistema
            EventBus.getDefault().post(ContactoEmergenciaEvent(
                contacto.nombre,
                contacto.telefono,
                "LLAMADA"
            ))

            Log.d(tag, "📨 Evento ContactoEmergenciaEvent enviado (LLAMADA)")

        } catch (e: Exception) {
            Log.e(tag, "❌ CRÍTICO: Error al realizar llamada de emergencia", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "❌ ERROR AL LLAMAR", Toast.LENGTH_LONG).show()
            }
        }
    }


    fun procesarPostergacionMedicamento(
        medicamentoId: Long,
        nombreMedicamento: String,
        fechaHoraOriginal: Long,
        numeroPostergacion: Int
    ) {
        Log.d(tag, "🔄 Procesando postergación de medicamento...")
        Log.i(tag, "🆔 ID: $medicamentoId")
        Log.i(tag, "💊 Nombre: $nombreMedicamento")
        Log.i(tag, "🔢 Postergación #: $numeroPostergacion")

        val tiempoTranscurrido = ((System.currentTimeMillis() - fechaHoraOriginal) / (1000 * 60)).toInt()
        Log.i(tag, "⏱️ Tiempo calculado: $tiempoTranscurrido minutos")

        val event = MedicamentoPostergadoEvent(
            medicamentoId = medicamentoId,
            nombreMedicamento = nombreMedicamento,
            tiempoPostergado = tiempoTranscurrido,
            fechaHoraOriginal = fechaHoraOriginal,
            numeroPostergacion = numeroPostergacion
        )

        Log.d(tag, "📨 Enviando evento MedicamentoPostergadoEvent...")
        EventBus.getDefault().post(event)
        Log.d(tag, "✅ Evento enviado correctamente")
    }

    fun procesarMedicamentoNoTomado(
        medicamentoId: Long,
        nombreMedicamento: String,
        fechaHoraOriginal: Long
    ) {
        Log.d(tag, "⚠️ Procesando medicamento no tomado...")
        Log.i(tag, "🆔 ID: $medicamentoId")
        Log.i(tag, "💊 Nombre: $nombreMedicamento")

        val tiempoTranscurrido = ((System.currentTimeMillis() - fechaHoraOriginal) / (1000 * 60)).toInt()
        Log.i(tag, "⏱️ Tiempo sin tomar: $tiempoTranscurrido minutos")

        val event = MedicamentoNoTomadoEvent(
            medicamentoId = medicamentoId,
            nombreMedicamento = nombreMedicamento,
            fechaHoraOriginal = fechaHoraOriginal,
            tiempoTranscurrido = tiempoTranscurrido
        )

        Log.d(tag, "📨 Enviando evento MedicamentoNoTomadoEvent...")
        EventBus.getDefault().post(event)
        Log.d(tag, "✅ Evento enviado correctamente")
    }

    fun destruir() {
        try {
            EventBus.getDefault().unregister(this)
            coroutineScope.cancel()
            Log.d(tag, "✅ Agente de Emergencia destruido correctamente")

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "🔴 Sistema emergencia detenido", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Error al destruir Agente de Emergencia", e)
        }
    }
}

// === SERVICIO ACTUALIZADO ===
class ServicioMonitorMedicamentosActualizado : Service() {

    private val tag = "ServicioMonitor"
    private lateinit var agenteEmergencia: AgenteEmergenciaComunicacion
    private lateinit var dbHelper: MedicamentosDBHelper
    private lateinit var handler: Handler
    private val intervaloChequeo = 2 * 60 * 1000L // 2 minutos para mayor precisión

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onContactoEmergencia(event: ContactoEmergenciaEvent) {
        Log.d(tag, "📞 Acción de emergencia realizada: ${event.tipoAccion} a ${event.contactoNombre}")
        Toast.makeText(this, "🚨 ${event.tipoAccion}: ${event.contactoNombre}", Toast.LENGTH_SHORT).show()
    }

    private val runnable = object : Runnable {
        override fun run() {
            Log.d(tag, "🔍 Ejecutando verificación de medicamentos...")
            verificarMedicamentosEmergencia()
            handler.postDelayed(this, intervaloChequeo)
            Log.d(tag, "⏰ Próxima verificación en ${intervaloChequeo/1000/60} minutos")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "🚀 Iniciando ServicioMonitorMedicamentos...")

        try {
            agenteEmergencia = AgenteEmergenciaComunicacion(this)
            dbHelper = MedicamentosDBHelper(this)
            handler = Handler(Looper.getMainLooper())

            // Registrarse para escuchar eventos
            EventBus.getDefault().register(this)

            Log.d(tag, "✅ Servicio iniciado correctamente")
            Log.i(tag, "📊 Intervalo de chequeo: ${intervaloChequeo/1000/60} minutos")

            // Iniciar monitoreo
            handler.post(runnable)
            Log.d(tag, "✅ Monitoreo automático iniciado")

            Toast.makeText(this, "✅ Monitor emergencia activo", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(tag, "❌ Error al iniciar servicio", e)
            Toast.makeText(this, "❌ Error monitor emergencia", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "🔄 onStartCommand llamado - startId: $startId")
        return START_STICKY // Reiniciar si el sistema mata el servicio
    }

    private fun verificarMedicamentosEmergencia() {
        try {
            Log.d(tag, "🔍 Obteniendo recordatorios de emergencia...")
            val recordatoriosEmergencia = dbHelper.obtenerRecordatoriosParaEmergencia()
            val tiempoActual = System.currentTimeMillis()

            Log.i(tag, "📊 Recordatorios encontrados: ${recordatoriosEmergencia.size}")

            for (recordatorio in recordatoriosEmergencia) {
                val tiempoTranscurrido = ((tiempoActual - recordatorio.fechaOriginal) / (1000 * 60)).toInt()

                Log.d(tag, "💊 Analizando: ${recordatorio.nombreMedicamento}")
                Log.d(tag, "⏱️ Tiempo transcurrido: $tiempoTranscurrido min")
                Log.d(tag, "🔄 Postergaciones: ${recordatorio.numeroPostergaciones}")
                Log.d(tag, "📧 Notificación enviada: ${recordatorio.notificacionEnviada}")

                // Lógica de emergencia
                when {
                    // 45+ minutos sin tomar y no se ha enviado notificación
                    tiempoTranscurrido >= 20 && !recordatorio.notificacionEnviada -> {
                        Log.w(tag, "⚠️ ACTIVANDO PROTOCOLO DE 45 MINUTOS")

                        if (recordatorio.numeroPostergaciones > 0) {
                            Log.i(tag, "🔄 Procesando como postergación múltiple")
                            agenteEmergencia.procesarPostergacionMedicamento(
                                recordatorio.medicamentoId,
                                recordatorio.nombreMedicamento,
                                recordatorio.fechaOriginal,
                                recordatorio.numeroPostergaciones
                            )
                        } else {
                            Log.i(tag, "❗ Procesando como primera detección no tomado")
                            agenteEmergencia.procesarMedicamentoNoTomado(
                                recordatorio.medicamentoId,
                                recordatorio.nombreMedicamento,
                                recordatorio.fechaOriginal
                            )
                        }

                        // Marcar que se envió notificación
                        dbHelper.marcarNotificacionEnviada(recordatorio.id)
                        Log.d(tag, "✅ Notificación marcada como enviada")
                    }

                    // 60+ minutos (1 hora) = llamada de emergencia
                    tiempoTranscurrido >= 60 && recordatorio.numeroPostergaciones >= 4 -> {
                        Log.e(tag, "🆘 ACTIVANDO PROTOCOLO CRÍTICO DE 60 MINUTOS")

                        agenteEmergencia.procesarPostergacionMedicamento(
                            recordatorio.medicamentoId,
                            recordatorio.nombreMedicamento,
                            recordatorio.fechaOriginal,
                            recordatorio.numeroPostergaciones
                        )

                        // Marcar como notificación enviada para evitar spam
                        dbHelper.marcarNotificacionEnviada(recordatorio.id)
                        Log.d(tag, "✅ Llamada de emergencia marcada")
                    }

                    else -> {
                        Log.d(tag, "📊 Sin acción requerida para este medicamento")
                    }
                }
            }

            Log.d(tag, "✅ Verificación completada")

        } catch (e: Exception) {
            Log.e(tag, "❌ Error durante verificación de medicamentos", e)
        }
    }

    override fun onDestroy() {
        Log.d(tag, "🔴 Deteniendo ServicioMonitorMedicamentos...")

        try {
            handler.removeCallbacks(runnable)
            EventBus.getDefault().unregister(this)
            agenteEmergencia.destruir()

            Log.d(tag, "✅ Servicio detenido correctamente")
            Toast.makeText(this, "🔴 Monitor detenido", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(tag, "❌ Error al detener servicio", e)
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

// === INTEGRACIÓN CON TU SISTEMA DE ALARMAS ===

// === CONFIGURACIÓN DE TELEGRAM EN ACTIVIDAD ===
class ConfiguracionEmergenciaActivity : AppCompatActivity() {

    private val tag = "ConfigEmergencia"
    private lateinit var agenteEmergencia: AgenteEmergenciaComunicacion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(tag, "🚀 Iniciando ConfiguracionEmergenciaActivity...")

        try {
            agenteEmergencia = AgenteEmergenciaComunicacion(this)

            // Registrarse para eventos
            EventBus.getDefault().register(this)
            Log.d(tag, "✅ EventBus registrado")

            configurarTelegram()
            iniciarServicioMonitoreo()

            Log.d(tag, "✅ Actividad iniciada correctamente")
            Toast.makeText(this, "✅ Sistema configurado", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(tag, "❌ Error al iniciar actividad", e)
            Toast.makeText(this, "❌ Error en configuración", Toast.LENGTH_LONG).show()
        }
    }

    private fun configurarTelegram() {
        Log.d(tag, "🔧 Configurando Telegram...")

        try {
            // Aquí deberías implementar una interfaz para que el usuario configure
            // su bot de Telegram y chat ID. Por ahora uso valores de ejemplo:

            val botToken = obtenerTokenTelegram() // Desde SharedPreferences o configuración
            val chatId = obtenerChatIdTelegram()   // Desde SharedPreferences o configuración

            Log.d(tag, "🤖 Token obtenido: ${if (botToken.isNotEmpty()) "✅ Sí" else "❌ No"}")
            Log.d(tag, "💬 Chat ID obtenido: ${if (chatId.isNotEmpty()) "✅ Sí" else "❌ No"}")

            if (botToken.isNotEmpty() && chatId.isNotEmpty()) {
                agenteEmergencia.configurarTelegram(botToken, chatId)
                Log.d(tag, "✅ Telegram configurado correctamente")
            } else {
                Log.w(tag, "⚠️ Telegram no configurado - faltan datos")
                Toast.makeText(this, "⚠️ Configurar Telegram", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Error al configurar Telegram", e)
        }
    }

    private fun obtenerTokenTelegram(): String {
        try {
            val prefs = getSharedPreferences("emergencia_config", MODE_PRIVATE)
            val token = prefs.getString("telegram_bot_token", "") ?: ""
            Log.d(tag, "📱 Token desde prefs: ${token.take(10)}${if (token.length > 10) "..." else ""}")
            return token
        } catch (e: Exception) {
            Log.e(tag, "❌ Error al obtener token", e)
            return ""
        }
    }

    private fun obtenerChatIdTelegram(): String {
        try {
            val prefs = getSharedPreferences("emergencia_config", MODE_PRIVATE)
            val chatId = prefs.getString("telegram_chat_id", "") ?: ""
            Log.d(tag, "💬 Chat ID desde prefs: $chatId")
            return chatId
        } catch (e: Exception) {
            Log.e(tag, "❌ Error al obtener chat ID", e)
            return ""
        }
    }

    fun guardarConfiguracionTelegram(botToken: String, chatId: String) {
        Log.d(tag, "💾 Guardando configuración Telegram...")

        try {
            val prefs = getSharedPreferences("emergencia_config", MODE_PRIVATE)
            prefs.edit()
                .putString("telegram_bot_token", botToken)
                .putString("telegram_chat_id", chatId)
                .apply()

            agenteEmergencia.configurarTelegram(botToken, chatId)

            Log.d(tag, "✅ Configuración guardada correctamente")
            Toast.makeText(this, "✅ Telegram configurado", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(tag, "❌ Error al guardar configuración", e)
            Toast.makeText(this, "❌ Error al guardar config", Toast.LENGTH_SHORT).show()
        }
    }

    private fun iniciarServicioMonitoreo() {
        Log.d(tag, "🔄 Iniciando servicio de monitoreo...")

        try {
            val intent = Intent(this, ServicioMonitorMedicamentosActualizado::class.java)
            startService(intent)

            Log.d(tag, "✅ Servicio iniciado correctamente")
        } catch (e: Exception) {
            Log.e(tag, "❌ Error al iniciar servicio", e)
            Toast.makeText(this, "❌ Error servicio monitor", Toast.LENGTH_SHORT).show()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onContactoEmergencia(event: ContactoEmergenciaEvent) {
        Log.d(tag, "📞 Evento emergencia recibido: ${event.tipoAccion}")
        Log.i(tag, "👤 Contacto: ${event.contactoNombre}")
        Log.i(tag, "📱 Teléfono: ${event.contactoTelefono}")

        val mensaje = when (event.tipoAccion) {
            "MENSAJE" -> "📤 Mensaje enviado a ${event.contactoNombre}"
            "LLAMADA" -> "☎️ Llamando a ${event.contactoNombre}"
            else -> "🚨 Acción de emergencia realizada"
        }

        // Mostrar notificación al usuario
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()

        // También podrías mostrar una notificación persistente
        mostrarNotificacionEmergencia(mensaje)

        Log.d(tag, "✅ Evento procesado correctamente")
    }

    private fun mostrarNotificacionEmergencia(mensaje: String) {
        Log.d(tag, "🔔 Creando notificación de emergencia...")

        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Crear canal de notificación para emergencias
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "emergencia_channel",
                    "Emergencias Medicamentos",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificaciones críticas de medicamentos"
                }
                notificationManager.createNotificationChannel(channel)
                Log.d(tag, "📢 Canal de notificación creado")
            }

            val notification = NotificationCompat.Builder(this, "emergencia_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_alert) // Cambiado por icono del sistema
                .setContentTitle("🚨 Emergencia Medicamento")
                .setContentText(mensaje)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            notificationManager.notify(999, notification)

            Log.d(tag, "✅ Notificación mostrada correctamente")

        } catch (e: Exception) {
            Log.e(tag, "❌ Error al mostrar notificación", e)
        }
    }

    override fun onDestroy() {
        Log.d(tag, "🔴 Destruyendo ConfiguracionEmergenciaActivity...")

        try {
            EventBus.getDefault().unregister(this)
            agenteEmergencia.destruir()

            Log.d(tag, "✅ Actividad destruida correctamente")

        } catch (e: Exception) {
            Log.e(tag, "❌ Error al destruir actividad", e)
        }

        super.onDestroy()
    }

    // === MÉTODOS PARA TESTING Y DEBUGGING ===

    fun testearSistemaEmergencia() {
        Log.d(tag, "🧪 INICIANDO TEST DEL SISTEMA...")

        // Simular medicamento postergado múltiples veces
        agenteEmergencia.procesarPostergacionMedicamento(
            medicamentoId = 123L,
            nombreMedicamento = "Aspirina TEST",
            fechaHoraOriginal = System.currentTimeMillis() - (50 * 60 * 1000), // 50 minutos atrás
            numeroPostergacion = 3
        )

        Log.d(tag, "✅ Test iniciado - revisar logs para ver el flujo")
        Toast.makeText(this, "🧪 Test sistema iniciado", Toast.LENGTH_SHORT).show()
    }

    fun verificarEstadoSistema(): String {
        val estado = StringBuilder()

        estado.append("🔍 ESTADO DEL SISTEMA:\n")
        estado.append("• EventBus: ${if (EventBus.getDefault().isRegistered(this)) "✅" else "❌"}\n")
        estado.append("• Agente: ${if (::agenteEmergencia.isInitialized) "✅" else "❌"}\n")
        estado.append("• Telegram: ${if (obtenerTokenTelegram().isNotEmpty()) "✅" else "❌"}\n")

        val estadoFinal = estado.toString()
        Log.i(tag, estadoFinal)
        Toast.makeText(this, "Ver logs para estado completo", Toast.LENGTH_SHORT).show()

        return estadoFinal
    }
}