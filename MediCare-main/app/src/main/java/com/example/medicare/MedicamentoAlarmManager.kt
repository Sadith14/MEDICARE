package com.example.medicare

import android.app.AlarmManager
import com.example.medicare.Medicamento
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.medicare.MedicamentoAlarmManager.Companion.EXTRA_ALARM_ID
import com.example.medicare.MedicamentoAlarmManager.Companion.EXTRA_MEDICAMENTO_ID
import com.example.medicare.MedicamentoAlarmManager.Companion.EXTRA_NOMBRE_MEDICAMENTO
import com.example.medicare.MedicamentoAlarmManager.Companion.EXTRA_RECORDATORIO_ID
import org.greenrobot.eventbus.EventBus
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

// === GESTIÓN DE ALARMAS ===
class MedicamentoAlarmManager(private val context: Context) {
    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val dbHelper = MedicamentosDBHelper(context)
    private val alarmIdCounter = AtomicInteger(1000)

    companion object {
        const val EXTRA_MEDICAMENTO_ID = "medicamento_id"
        const val EXTRA_RECORDATORIO_ID = "recordatorio_id"
        const val EXTRA_NOMBRE_MEDICAMENTO = "nombre_medicamento"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val AUTO_POSTPONE_DELAY = 30000L // 30 segundos antes de auto-postergar
        const val ESCALAMIENTO_START_DELAY = 15 * 60 * 1000L // 15 minutos para iniciar escalamiento
    }

    fun programarRecordatoriosMedicamento(medicamento: Medicamento) {
        if (!medicamento.activo) return

        val horaInicio = medicamento.horaInicio ?: medicamento.fechaCreacion
        val intervaloPorHoras = medicamento.horarioHoras
        val ahora = System.currentTimeMillis()

        // Generar próximos recordatorios
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = horaInicio

        // Si la hora de inicio ya pasó hoy, calcular próxima toma
        if (horaInicio < ahora) {
            val tiempoTranscurrido = ahora - horaInicio
            val intervalosTranscurridos = (tiempoTranscurrido / (intervaloPorHoras * 60 * 60 * 1000)).toInt()
            calendar.add(Calendar.HOUR_OF_DAY, intervaloPorHoras * (intervalosTranscurridos + 1))
        }

        // Programar recordatorios para los próximos 30 días
        val maxRecordatorios = (30 * 24) / intervaloPorHoras // 30 días

        for (i in 0 until maxRecordatorios) {
            val fechaHoraRecordatorio = calendar.timeInMillis
            if (fechaHoraRecordatorio < ahora) {
                calendar.add(Calendar.HOUR_OF_DAY, intervaloPorHoras)
                continue
            }

            val alarmId = alarmIdCounter.incrementAndGet()
            val recordatorioId = dbHelper.insertarRecordatorio(medicamento.id, fechaHoraRecordatorio, alarmId)

            if (recordatorioId > 0) {
                programarAlarma(medicamento, recordatorioId, fechaHoraRecordatorio, alarmId)
            }

            calendar.add(Calendar.HOUR_OF_DAY, intervaloPorHoras)
        }
    }

    private fun programarAlarma(medicamento: Medicamento, recordatorioId: Long, fechaHora: Long, alarmId: Int) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_MEDICAMENTO_ID, medicamento.id)
            putExtra(EXTRA_RECORDATORIO_ID, recordatorioId)
            putExtra(EXTRA_NOMBRE_MEDICAMENTO, medicamento.nombre)
            putExtra(EXTRA_ALARM_ID, alarmId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    fechaHora,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    fechaHora,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e("MedicamentoAlarm", "Error programando alarma: ${e.message}")
        }
    }

    fun cancelarAlarma(alarmId: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun cancelarTodasAlarmasMedicamento(medicamentoId: Long) {
        val recordatorios = dbHelper.obtenerRecordatoriosPendientes()
        recordatorios.filter { it.medicamentoId == medicamentoId }
            .forEach { cancelarAlarma(it.alarmId) }

        dbHelper.eliminarRecordatoriosPorMedicamento(medicamentoId)
    }

    fun reprogramarRecordatorios() {
        val medicamentos = dbHelper.obtenerTodosMedicamentos()
        medicamentos.filter { it.activo }.forEach { medicamento ->
            programarRecordatoriosMedicamento(medicamento)
        }
    }
}

// === RECEPTOR DE ALARMAS ===
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarma recibida")

        val medicamentoId = intent.getLongExtra(MedicamentoAlarmManager.EXTRA_MEDICAMENTO_ID, -1)
        val recordatorioId = intent.getLongExtra(MedicamentoAlarmManager.EXTRA_RECORDATORIO_ID, -1)
        val nombreMedicamento = intent.getStringExtra(MedicamentoAlarmManager.EXTRA_NOMBRE_MEDICAMENTO) ?: ""
        val alarmId = intent.getIntExtra(MedicamentoAlarmManager.EXTRA_ALARM_ID, -1)
        val fechaHoraOriginal = intent.getLongExtra("fecha_hora_original", System.currentTimeMillis())

        Log.d("AlarmReceiver", "Datos: medicamento=$nombreMedicamento, id=$medicamentoId")

        if (medicamentoId != -1L && recordatorioId != -1L) {
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                putExtra(MedicamentoAlarmManager.EXTRA_MEDICAMENTO_ID, medicamentoId)
                putExtra(MedicamentoAlarmManager.EXTRA_RECORDATORIO_ID, recordatorioId)
                putExtra(MedicamentoAlarmManager.EXTRA_NOMBRE_MEDICAMENTO, nombreMedicamento)
                putExtra(MedicamentoAlarmManager.EXTRA_ALARM_ID, alarmId)
                putExtra("fecha_hora_original", fechaHoraOriginal)

            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                    Log.d("AlarmReceiver", "Servicio en primer plano iniciado")
                } else {
                    context.startService(serviceIntent)
                    Log.d("AlarmReceiver", "Servicio iniciado")
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error iniciando servicio: ${e.message}")
            }
        } else {
            Log.e("AlarmReceiver", "Datos inválidos en la alarma")
        }
    }
}

// === SERVICIO DE ALARMAS ===
class AlarmService : android.app.Service(), TextToSpeech.OnInitListener {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var textToSpeech: TextToSpeech? = null
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private var autoPostponeRunnable: Runnable? = null

    // FIXED: Added missing property declarations
    private var escalamientoId: Long = -1
    private var escalamientoStartRunnable: Runnable? = null

    private var medicamentoId: Long = -1
    private var recordatorioId: Long = -1
    private var nombreMedicamento: String = ""
    private var alarmId: Int = -1

    companion object {
        const val CHANNEL_ID = "medicamento_alarmas"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TOMADO = "ACTION_TOMADO"
        const val ACTION_POSTERGAR = "ACTION_POSTERGAR"
        const val ACTION_CANCELAR = "ACTION_CANCELAR"
        const val AUTO_POSTPONE_DELAY = 30000L // 30 segundos
        // FIXED: Added missing constant
        const val ESCALAMIENTO_START_DELAY = 15 * 60 * 1000L // 15 minutos para iniciar escalamiento
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        textToSpeech = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOMADO -> {
                marcarComoTomado()
                return START_NOT_STICKY
            }
            ACTION_POSTERGAR -> {
                postergarRecordatorio()
                return START_NOT_STICKY
            }
            ACTION_CANCELAR -> {
                cancelarAlarma()
                return START_NOT_STICKY
            }
            else -> {
                medicamentoId = intent?.getLongExtra(MedicamentoAlarmManager.EXTRA_MEDICAMENTO_ID, -1) ?: -1
                recordatorioId = intent?.getLongExtra(MedicamentoAlarmManager.EXTRA_RECORDATORIO_ID, -1) ?: -1
                nombreMedicamento = intent?.getStringExtra(MedicamentoAlarmManager.EXTRA_NOMBRE_MEDICAMENTO) ?: ""
                alarmId = intent?.getIntExtra(MedicamentoAlarmManager.EXTRA_ALARM_ID, -1) ?: -1

                if (medicamentoId != -1L && recordatorioId != -1L) {
                    startForeground(NOTIFICATION_ID, createNotification())
                    startAlarm()
                    mostrarPantallaAlarma()
                }
            }
        }
        return START_STICKY
    }

    private fun startAlarm() {
        startSound()
        startVibration()
        speakReminder()

        // Auto-postergar después de 30 segundos (existente)
        autoPostponeRunnable = Runnable {
            postergarRecordatorio()
        }
        handler.postDelayed(autoPostponeRunnable!!, AUTO_POSTPONE_DELAY)

        iniciarEscalamiento()
    }

    private fun iniciarEscalamiento() {
        escalamientoStartRunnable = Runnable {
            Log.d("AlarmService", "Iniciando escalamiento para $nombreMedicamento")

            // Crear registro de escalamiento en BD
            val dbHelper = MedicamentosDBHelper(this@AlarmService)
            escalamientoId = crearRegistroEscalamiento(dbHelper)

            if (escalamientoId > 0) {
                // Iniciar primer nivel de escalamiento
                val intent = Intent(this@AlarmService, EscalamientoAlarmService::class.java).apply {
                    action = EscalamientoAlarmService.ACTION_ESCALAMIENTO_NIVEL_1
                    putExtra(EscalamientoAlarmService.EXTRA_ESCALAMIENTO_ID, escalamientoId)
                    putExtra(EscalamientoAlarmService.EXTRA_MEDICAMENTO_NOMBRE, nombreMedicamento)
                    putExtra(EscalamientoAlarmService.EXTRA_NIVEL_ACTUAL, 1)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        }

        handler.postDelayed(escalamientoStartRunnable!!, ESCALAMIENTO_START_DELAY)
    }

    private fun crearRegistroEscalamiento(dbHelper: MedicamentosDBHelper): Long {
        // Implementar este método en tu DBHelper
        return dbHelper.crearEscalamiento(medicamentoId, recordatorioId)
    }

    private fun startSound() {
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            isPlaying = true
        } catch (e: Exception) {
            Log.e("AlarmService", "Error reproduciendo sonido: ${e.message}")
        }
    }

    private fun startVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "Error con vibración: ${e.message}")
        }
    }

    private fun speakReminder() {
        handler.postDelayed({
            val mensaje = "Es hora de tomar su medicamento $nombreMedicamento. Debe tomar una dosis de una unidad."
            textToSpeech?.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, "medicamento_reminder")
        }, 1000) // Esperar 1 segundo antes de hablar
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false

            vibrator?.cancel()

            autoPostponeRunnable?.let { handler.removeCallbacks(it) }

            // FIXED: Cancelar runnable de escalamiento
            escalamientoStartRunnable?.let { runnable ->
                handler.removeCallbacks(runnable)
            }

        } catch (e: Exception) {
            Log.e("AlarmService", "Error deteniendo alarma: ${e.message}")
        }
    }

    private fun mostrarPantallaAlarma() {
        val intent = Intent(this, AlarmOverlayActivity::class.java).apply {
            putExtra(MedicamentoAlarmManager.EXTRA_MEDICAMENTO_ID, medicamentoId)
            putExtra(MedicamentoAlarmManager.EXTRA_RECORDATORIO_ID, recordatorioId)
            putExtra(MedicamentoAlarmManager.EXTRA_NOMBRE_MEDICAMENTO, nombreMedicamento)
            putExtra(MedicamentoAlarmManager.EXTRA_ALARM_ID, alarmId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun marcarComoTomado() {
        val dbHelper = MedicamentosDBHelper(this)

        // Marcar recordatorio como completado
        dbHelper.marcarRecordatorioCompletado(recordatorioId)
        if (escalamientoId > 0) {
            cancelarEscalamiento()
        }
        escalamientoStartRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
        }

        // Registrar en historial
        dbHelper.insertarHistorialToma(
            medicamentoId,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            "tomado",
            1
        )

        // Disminuir cantidad del medicamento
        val medicamentos = dbHelper.obtenerTodosMedicamentos()
        val medicamento = medicamentos.find { med -> med.id == medicamentoId }
        medicamento?.let { med ->
            val nuevaCantidad = maxOf(0, med.cantidad - 1)
            dbHelper.actualizarCantidadMedicamento(medicamentoId, nuevaCantidad)
        }

        stopAlarm()
        stopSelf()
    }

    private fun postergarRecordatorio() {
        val dbHelper = MedicamentosDBHelper(this)
        val nuevaFechaHora = System.currentTimeMillis() + (5 * 60 * 1000) // 5 minutos
        val numeroPostergaciones = dbHelper.obtenerNumeroPostergaciones(recordatorioId) + 1
        dbHelper.actualizarNumeroPostergaciones(recordatorioId, numeroPostergaciones)

        val fechaOriginal = dbHelper.obtenerFechaOriginalRecordatorio(recordatorioId)
        val agenteEmergencia = AgenteEmergenciaComunicacion(this)
        agenteEmergencia.procesarPostergacionMedicamento(
            medicamentoId,
            nombreMedicamento,
            fechaOriginal,
            numeroPostergaciones
        )
        if (escalamientoId > 0) {
            cancelarEscalamiento()
        }

        // Cancelar el runnable de escalamiento
        escalamientoStartRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
        }

        // Actualizar recordatorio en BD
        dbHelper.postergarRecordatorio(recordatorioId, nuevaFechaHora)

        // Programar nueva alarma
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra(MedicamentoAlarmManager.EXTRA_MEDICAMENTO_ID, medicamentoId)
            putExtra(MedicamentoAlarmManager.EXTRA_RECORDATORIO_ID, recordatorioId)
            putExtra(MedicamentoAlarmManager.EXTRA_NOMBRE_MEDICAMENTO, nombreMedicamento)
            putExtra(MedicamentoAlarmManager.EXTRA_ALARM_ID, alarmId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManagerSystem = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManagerSystem.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nuevaFechaHora,
                pendingIntent
            )
        } else {
            alarmManagerSystem.setExact(
                AlarmManager.RTC_WAKEUP,
                nuevaFechaHora,
                pendingIntent
            )
        }

        stopAlarm()
        stopSelf()
    }
    private fun activarSistemaEmergencia(medicamentoId: Long, nombreMedicamento: String, fechaOriginal: Long, numeroPostergaciones: Int) {
        Log.d(TAG, "🚨 ACTIVANDO SISTEMA DE EMERGENCIA")
        Log.i(TAG, "💊 Medicamento: $nombreMedicamento")
        Log.i(TAG, "🔄 Postergación #$numeroPostergaciones")
        Log.i(TAG, "⏱️ Tiempo transcurrido desde original: ${(System.currentTimeMillis() - fechaOriginal) / (1000 * 60)} minutos")

        try {
            // Calcular tiempo transcurrido en minutos
            val tiempoTranscurrido = ((System.currentTimeMillis() - fechaOriginal) / (1000 * 60)).toInt()

            // Crear evento de medicamento postergado
            val event = MedicamentoPostergadoEvent(
                medicamentoId = medicamentoId,
                nombreMedicamento = nombreMedicamento,
                tiempoPostergado = tiempoTranscurrido,
                fechaHoraOriginal = fechaOriginal,
                numeroPostergacion = numeroPostergaciones
            )

            Log.d(TAG, "📨 Enviando evento MedicamentoPostergadoEvent via EventBus...")
            Log.i(TAG, "📊 Datos del evento:")
            Log.i(TAG, "   - ID: $medicamentoId")
            Log.i(TAG, "   - Nombre: $nombreMedicamento")
            Log.i(TAG, "   - Tiempo: $tiempoTranscurrido min")
            Log.i(TAG, "   - Postergaciones: $numeroPostergaciones")

            // Enviar evento via EventBus
            EventBus.getDefault().post(event)

            Log.d(TAG, "✅ Evento de emergencia enviado correctamente")

            // Mostrar notificación al usuario
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "🚨 Sistema emergencia: $numeroPostergaciones postergaciones", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR CRÍTICO al activar sistema de emergencia", e)

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "❌ Error sistema emergencia", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun cancelarEscalamiento() {
        if (escalamientoId > 0) {
            val intent = Intent(this, EscalamientoAlarmService::class.java).apply {
                action = EscalamientoAlarmService.ACTION_CANCELAR_ESCALAMIENTO
                putExtra(EscalamientoAlarmService.EXTRA_ESCALAMIENTO_ID, escalamientoId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            Log.d("AlarmService", "Escalamiento cancelado para $nombreMedicamento")
        }
    }

    private fun cancelarAlarma() {
        stopAlarm()
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recordatorios de Medicamentos",
                NotificationManager.IMPORTANCE_HIGH // Cambiado a HIGH
            ).apply {
                description = "Notificaciones para recordatorios de medicamentos"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setBypassDnd(true) // Importante para que aparezca aunque esté en No Molestar
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    private fun createNotification(): android.app.Notification {
        val tomadoIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_TOMADO
            putExtra(EXTRA_MEDICAMENTO_ID, medicamentoId)
            putExtra(EXTRA_RECORDATORIO_ID, recordatorioId)
            putExtra(EXTRA_NOMBRE_MEDICAMENTO, nombreMedicamento)
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val tomadoPendingIntent = PendingIntent.getService(
            this,
            alarmId * 10 + 1, // ID único
            tomadoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val postergarIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_POSTERGAR
            putExtra(EXTRA_MEDICAMENTO_ID, medicamentoId)
            putExtra(EXTRA_RECORDATORIO_ID, recordatorioId)
            putExtra(EXTRA_NOMBRE_MEDICAMENTO, nombreMedicamento)
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val postergarPendingIntent = PendingIntent.getService(
            this,
            alarmId * 10 + 2, // ID único diferente
            postergarIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent para la actividad overlay
        val overlayIntent = Intent(this, AlarmOverlayActivity::class.java).apply {
            putExtra(EXTRA_MEDICAMENTO_ID, medicamentoId)
            putExtra(EXTRA_RECORDATORIO_ID, recordatorioId)
            putExtra(EXTRA_NOMBRE_MEDICAMENTO, nombreMedicamento)
            putExtra(EXTRA_ALARM_ID, alarmId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId * 10 + 3, // ID único
            overlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("💊 Recordatorio de Medicamento")
            .setContentText("Es hora de tomar $nombreMedicamento")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MAX) // Cambiado a MAX
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(false)
            .setOngoing(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            // Acciones de la notificación
            .addAction(
                android.R.drawable.ic_menu_preferences,
                "✅ TOMADO",
                tomadoPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                "⏰ POSTERGAR 5min",
                postergarPendingIntent
            )
            // Pantalla completa
            .setFullScreenIntent(fullScreenPendingIntent, true)
            // Intent para cuando se toque la notificación
            .setContentIntent(fullScreenPendingIntent)
            .build()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.getDefault()
        }
    }

    override fun onDestroy() {
        stopAlarm()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}

// === ACTIVIDAD DE OVERLAY PARA ALARMA ===
class AlarmOverlayActivity : android.app.Activity() {
    private var medicamentoId: Long = -1
    private var recordatorioId: Long = -1
    private var nombreMedicamento: String = ""
    private var alarmId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar para mostrar sobre otras apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        medicamentoId = intent.getLongExtra(MedicamentoAlarmManager.EXTRA_MEDICAMENTO_ID, -1)
        recordatorioId = intent.getLongExtra(MedicamentoAlarmManager.EXTRA_RECORDATORIO_ID, -1)
        nombreMedicamento = intent.getStringExtra(MedicamentoAlarmManager.EXTRA_NOMBRE_MEDICAMENTO) ?: ""
        alarmId = intent.getIntExtra(MedicamentoAlarmManager.EXTRA_ALARM_ID, -1)

        createAlarmLayout()
    }

    private fun createAlarmLayout() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 100, 50, 100)
            setBackgroundColor(android.graphics.Color.parseColor("#FF1976D2"))
        }

        // Título
        val titulo = android.widget.TextView(this).apply {
            text = "⏰ RECORDATORIO DE MEDICAMENTO"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 30)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        // Nombre del medicamento
        val nombreMed = android.widget.TextView(this).apply {
            text = nombreMedicamento
            textSize = 32f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        // Mensaje
        val mensaje = android.widget.TextView(this).apply {
            text = "Es hora de tomar una dosis\n\n💊 1 unidad"
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        // Botón Tomado
        val btnTomado = android.widget.Button(this).apply {
            text = "✓ TOMADO"
            textSize = 20f
            setBackgroundColor(android.graphics.Color.parseColor("#FF4CAF50"))
            setTextColor(android.graphics.Color.WHITE)
            setPadding(20, 20, 20, 20)
            setOnClickListener {
                marcarComoTomado()
            }
        }

        // Botón Postergar
        val btnPostergar = android.widget.Button(this).apply {
            text = "⏱ POSTERGAR 5 MIN"
            textSize = 20f
            setBackgroundColor(android.graphics.Color.parseColor("#FFFF9800"))
            setTextColor(android.graphics.Color.WHITE)
            setPadding(20, 20, 20, 20)
            setOnClickListener {
                postergarRecordatorio()
            }
        }

        // Layout para botones
        val buttonLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val buttonParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            setMargins(10, 0, 10, 0)
        }

        buttonLayout.addView(btnTomado, buttonParams)
        buttonLayout.addView(btnPostergar, buttonParams)

        layout.addView(titulo)
        layout.addView(nombreMed)
        layout.addView(mensaje)
        layout.addView(buttonLayout)

        setContentView(layout)
    }

    private fun marcarComoTomado() {
        val intent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_TOMADO
            putExtra(MedicamentoAlarmManager.EXTRA_MEDICAMENTO_ID, medicamentoId)
            putExtra(MedicamentoAlarmManager.EXTRA_RECORDATORIO_ID, recordatorioId)
            putExtra(MedicamentoAlarmManager.EXTRA_NOMBRE_MEDICAMENTO, nombreMedicamento)
            putExtra(MedicamentoAlarmManager.EXTRA_ALARM_ID, alarmId)
        }
        startService(intent)
        finish()
    }

    private fun postergarRecordatorio() {
        val intent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_POSTERGAR
            putExtra(MedicamentoAlarmManager.EXTRA_MEDICAMENTO_ID, medicamentoId)
            putExtra(MedicamentoAlarmManager.EXTRA_RECORDATORIO_ID, recordatorioId)
            putExtra(MedicamentoAlarmManager.EXTRA_NOMBRE_MEDICAMENTO, nombreMedicamento)
            putExtra(MedicamentoAlarmManager.EXTRA_ALARM_ID, alarmId)
        }
        startService(intent)
        finish()
    }

    override fun onBackPressed() {

    }
}

// === UTILIDADES Y EXTENSIONES ===
object MedicamentoScheduler {
    fun programarTodosMedicamentos(context: Context) {
        val dbHelper = MedicamentosDBHelper(context)
        val alarmManager = MedicamentoAlarmManager(context)

        val medicamentos = dbHelper.obtenerTodosMedicamentos()
        medicamentos.filter { it.activo }.forEach { medicamento ->
            alarmManager.programarRecordatoriosMedicamento(medicamento)
        }
    }

    fun cancelarTodosMedicamentos(context: Context) {
        val dbHelper = MedicamentosDBHelper(context)
        val alarmManager = MedicamentoAlarmManager(context)

        val medicamentos = dbHelper.obtenerTodosMedicamentos()
        medicamentos.forEach { medicamento ->
            alarmManager.cancelarTodasAlarmasMedicamento(medicamento.id)
        }
    }

    fun programarMedicamentoIndividual(context: Context, medicamentoId: Long) {
        val dbHelper = MedicamentosDBHelper(context)
        val alarmManager = MedicamentoAlarmManager(context)

        val medicamentos = dbHelper.obtenerTodosMedicamentos()
        val medicamento = medicamentos.find { it.id == medicamentoId }

        medicamento?.let { med ->
            if (med.activo) {
                alarmManager.programarRecordatoriosMedicamento(med)
            }
        }
    }
}