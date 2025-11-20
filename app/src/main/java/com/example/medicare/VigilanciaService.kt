package com.example.medicare

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import java.util.Locale
import com.example.medicare.R

class VigilanciaVozService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent

    override fun onCreate() {
        super.onCreate()

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        iniciarReconocimiento()
    }

    private fun iniciarReconocimiento() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val texto = matches?.joinToString(" ")?.lowercase() ?: ""

                if (listOf("ayuda", "accidente", "caída", "emergencia").any { texto.contains(it) }) {
                    val intent = Intent(this@VigilanciaVozService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("emergencia_detectada", true)
                    }
                    startActivity(intent)
                }

                // Reiniciar reconocimiento
                reiniciarReconocimiento()
            }

            override fun onError(error: Int) {
                reiniciarReconocimiento()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

        })

        speechRecognizer?.startListening(recognizerIntent)
    }

    private fun reiniciarReconocimiento() {
        Handler(Looper.getMainLooper()).postDelayed({
            iniciarReconocimiento()
        }, 500)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "canal_voz")
            .setContentTitle("Escuchando voz")
            .setContentText("Detección de emergencia activa")
            .setSmallIcon(R.drawable.ic_microfono)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
        return START_STICKY

    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

