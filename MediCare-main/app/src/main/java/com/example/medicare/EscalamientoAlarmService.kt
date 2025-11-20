package com.example.medicare

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Locale

// === MODELO DE DATOS PARA ESCALAMIENTO ===
data class EscalamientoAlarma(
    val id: Long = 0,
    val medicamentoId: Long,
    val recordatorioId: Long,
    val nivel: Int = 1, // 1=Primera, 2=Segunda, 3=Mensaje, 4=Llamada
    val fechaCreacion: Long = System.currentTimeMillis(),
    val completado: Boolean = false
)

// === CONFIGURACIÓN DE CONTACTO DE EMERGENCIA ===
data class ContactoEmergencia(
    val nombre: String,
    val telefono: String,
    val esContactoPrincipal: Boolean = true
)

// === DATABASE HELPER ===
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "medicare.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("""
            CREATE TABLE IF NOT EXISTS escalamientos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                medicamento_id INTEGER,
                recordatorio_id INTEGER,
                nivel INTEGER,
                fecha_creacion INTEGER,
                completado INTEGER DEFAULT 0
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Handle database upgrades here
    }
}

// === SERVICIO DE ESCALAMIENTO DE ALARMAS ===
class EscalamientoAlarmService : android.app.Service(), TextToSpeech.OnInitListener {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var textToSpeech: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var dbHelper: DatabaseHelper

    companion object {
        const val ESCALAMIENTO_DELAY = 15 * 60 * 1000L // 15 minutos
        const val ACTION_ESCALAMIENTO_NIVEL_1 = "ACTION_ESCALAMIENTO_NIVEL_1"
        const val ACTION_ESCALAMIENTO_NIVEL_2 = "ACTION_ESCALAMIENTO_NIVEL_2"
        const val ACTION_ESCALAMIENTO_NIVEL_3 = "ACTION_ESCALAMIENTO_NIVEL_3"
        const val ACTION_ESCALAMIENTO_NIVEL_4 = "ACTION_ESCALAMIENTO_NIVEL_4"
        const val ACTION_CANCELAR_ESCALAMIENTO = "ACTION_CANCELAR_ESCALAMIENTO"

        const val EXTRA_ESCALAMIENTO_ID = "escalamiento_id"
        const val EXTRA_MEDICAMENTO_NOMBRE = "medicamento_nombre"
        const val EXTRA_NIVEL_ACTUAL = "nivel_actual"
    }

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        textToSpeech = TextToSpeech(this, this)
        dbHelper = DatabaseHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val escalamientoId = intent.getLongExtra(EXTRA_ESCALAMIENTO_ID, -1)
        val medicamentoNombre = intent.getStringExtra(EXTRA_MEDICAMENTO_NOMBRE) ?: ""
        val nivelActual = intent.getIntExtra(EXTRA_NIVEL_ACTUAL, 1)

        when (action) {
            ACTION_ESCALAMIENTO_NIVEL_1 -> ejecutarNivel1(escalamientoId, medicamentoNombre)
            ACTION_ESCALAMIENTO_NIVEL_2 -> ejecutarNivel2(escalamientoId, medicamentoNombre)
            ACTION_ESCALAMIENTO_NIVEL_3 -> ejecutarNivel3(escalamientoId, medicamentoNombre)
            ACTION_ESCALAMIENTO_NIVEL_4 -> ejecutarNivel4(escalamientoId, medicamentoNombre)
            ACTION_CANCELAR_ESCALAMIENTO -> cancelarEscalamiento(escalamientoId)
        }

        return START_NOT_STICKY
    }

    private fun ejecutarNivel1(escalamientoId: Long, medicamentoNombre: String) {
        Log.d("EscalamientoAlarm", "Ejecutando Nivel 1 - Primera insistencia para: $medicamentoNombre")

        // Alarma más intensa
        startIntenseAlarm()
        mostrarNotificacionIntensa("🚨 RECORDATORIO URGENTE",
            "No has respondido al medicamento $medicamentoNombre")

        speakUrgentMessage("Atención. No has tomado tu medicamento $medicamentoNombre. Es muy importante que lo tomes ahora.")

        // Programar nivel 2 en 15 minutos
        programarSiguienteNivel(escalamientoId, medicamentoNombre, 2)
    }

    private fun ejecutarNivel2(escalamientoId: Long, medicamentoNombre: String) {
        Log.d("EscalamientoAlarm", "Ejecutando Nivel 2 - Segunda insistencia para: $medicamentoNombre")

        startIntenseAlarm()
        mostrarNotificacionIntensa("⚠️ ALERTA MÉDICA",
            "SEGUNDA ALERTA: Debes tomar $medicamentoNombre INMEDIATAMENTE")

        speakUrgentMessage("Alerta médica. Esta es la segunda vez que insistimos. Debes tomar tu medicamento $medicamentoNombre inmediatamente. Tu salud está en riesgo.")

        // Programar nivel 3 en 15 minutos (mensaje a familiar)
        programarSiguienteNivel(escalamientoId, medicamentoNombre, 3)
    }

    private fun ejecutarNivel3(escalamientoId: Long, medicamentoNombre: String) {
        Log.d("EscalamientoAlarm", "Ejecutando Nivel 3 - Mensaje a familiar para: $medicamentoNombre")

        val contacto = obtenerContactoEmergencia()
        if (contacto != null) {
            enviarMensajeEmergencia(contacto, medicamentoNombre)

            mostrarNotificacionIntensa("📱 MENSAJE ENVIADO",
                "Se ha notificado a ${contacto.nombre} sobre tu medicamento")

            speakUrgentMessage("Se ha enviado un mensaje de emergencia a ${contacto.nombre} porque no has tomado tu medicamento $medicamentoNombre.")
        }

        // Programar nivel 4 en 15 minutos (llamada)
        programarSiguienteNivel(escalamientoId, medicamentoNombre, 4)
    }

    private fun ejecutarNivel4(escalamientoId: Long, medicamentoNombre: String) {
        Log.d("EscalamientoAlarm", "Ejecutando Nivel 4 - Llamada a familiar para: $medicamentoNombre")

        val contacto = obtenerContactoEmergencia()
        if (contacto != null) {
            realizarLlamadaEmergencia(contacto, medicamentoNombre)

            mostrarNotificacionIntensa("📞 LLAMANDO A FAMILIAR",
                "Llamando a ${contacto.nombre} - Emergencia médica")

            speakUrgentMessage("Iniciando llamada de emergencia a ${contacto.nombre}. No has respondido a múltiples alertas sobre tu medicamento $medicamentoNombre.")
        }

        // Marcar escalamiento como completado
        marcarEscalamientoCompletado(escalamientoId)
    }

    private fun startIntenseAlarm() {
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@EscalamientoAlarmService, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                isLooping = true
                setVolume(1.0f, 1.0f) // Volumen máximo
                prepare()
                start()
            }

            // Vibración más intensa
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000)
                vibrator?.vibrate(pattern, 0)
            }

            // Auto-detener después de 2 minutos
            handler.postDelayed({
                stopIntenseAlarm()
            }, 120000) // 2 minutos

        } catch (e: Exception) {
            Log.e("EscalamientoAlarm", "Error iniciando alarma intensa: ${e.message}")
        }
    }

    private fun stopIntenseAlarm() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("EscalamientoAlarm", "Error deteniendo alarma: ${e.message}")
        }
    }

    private fun mostrarNotificacionIntensa(titulo: String, mensaje: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear canal de alta prioridad
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "escalamiento_urgente",
                "Alertas Médicas Urgentes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas críticas por medicamentos no tomados"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, "escalamiento_urgente")
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setColor(android.graphics.Color.RED)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun speakUrgentMessage(mensaje: String) {
        handler.postDelayed({
            textToSpeech?.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, "escalamiento_urgente")
        }, 1000)
    }

    private fun programarSiguienteNivel(escalamientoId: Long, medicamentoNombre: String, siguienteNivel: Int) {
        val intent = Intent(this, EscalamientoAlarmService::class.java).apply {
            action = when (siguienteNivel) {
                2 -> ACTION_ESCALAMIENTO_NIVEL_2
                3 -> ACTION_ESCALAMIENTO_NIVEL_3
                4 -> ACTION_ESCALAMIENTO_NIVEL_4
                else -> return
            }
            putExtra(EXTRA_ESCALAMIENTO_ID, escalamientoId)
            putExtra(EXTRA_MEDICAMENTO_NOMBRE, medicamentoNombre)
            putExtra(EXTRA_NIVEL_ACTUAL, siguienteNivel)
        }

        val pendingIntent = PendingIntent.getService(
            this,
            escalamientoId.toInt() + siguienteNivel,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val tiempoEjecucion = System.currentTimeMillis() + ESCALAMIENTO_DELAY

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    tiempoEjecucion,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    tiempoEjecucion,
                    pendingIntent
                )
            }

            Log.d("EscalamientoAlarm", "Programado nivel $siguienteNivel para $medicamentoNombre en 15 minutos")
        } catch (e: Exception) {
            Log.e("EscalamientoAlarm", "Error programando siguiente nivel: ${e.message}")
        }
    }

    private fun obtenerContactoEmergencia(): ContactoEmergencia? {
        val sharedPrefs = getSharedPreferences("medicare_config", Context.MODE_PRIVATE)
        val nombre = sharedPrefs.getString("contacto_nombre", null)
        val telefono = sharedPrefs.getString("contacto_telefono", null)

        return if (nombre != null && telefono != null) {
            ContactoEmergencia(nombre, telefono)
        } else null
    }

    private fun enviarMensajeEmergencia(contacto: ContactoEmergencia, medicamentoNombre: String) {
        try {
            val mensaje = "🚨 ALERTA MÉDICA: ${obtenerNombrePaciente()} no ha respondido a múltiples recordatorios para tomar su medicamento '$medicamentoNombre'. Por favor, verifique su estado inmediatamente."

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${contacto.telefono}")
                putExtra("sms_body", mensaje)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)

            Log.d("EscalamientoAlarm", "Mensaje de emergencia enviado a ${contacto.nombre}")
        } catch (e: Exception) {
            Log.e("EscalamientoAlarm", "Error enviando mensaje: ${e.message}")
        }
    }

    private fun realizarLlamadaEmergencia(contacto: ContactoEmergencia, medicamentoNombre: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${contacto.telefono}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)

            Log.d("EscalamientoAlarm", "Llamada de emergencia iniciada a ${contacto.nombre}")
        } catch (e: Exception) {
            Log.e("EscalamientoAlarm", "Error realizando llamada: ${e.message}")
            // Fallback: abrir marcador
            val fallbackIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${contacto.telefono}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(fallbackIntent)
        }
    }

    private fun obtenerNombrePaciente(): String {
        val sharedPrefs = getSharedPreferences("medicare_config", Context.MODE_PRIVATE)
        return sharedPrefs.getString("nombre_paciente", "El paciente") ?: "El paciente"
    }

    private fun cancelarEscalamiento(escalamientoId: Long) {
        Log.d("EscalamientoAlarm", "Cancelando escalamiento $escalamientoId")
        marcarEscalamientoCompletado(escalamientoId)
        stopSelf()
    }

    private fun marcarEscalamientoCompletado(escalamientoId: Long) {
        val db = dbHelper.writableDatabase
        val valores = ContentValues().apply {
            put("completado", 1)
        }

        db.update(
            "escalamientos",         // nombre de la tabla
            valores,                 // valores a actualizar
            "id = ?",                // cláusula WHERE
            arrayOf(escalamientoId.toString()) // argumentos
        )

        db.close()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.getDefault()
        }
    }

    override fun onDestroy() {
        stopIntenseAlarm()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}