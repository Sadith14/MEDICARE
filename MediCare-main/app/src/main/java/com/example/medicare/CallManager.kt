package com.example.medicare

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat

class CallManager(private val context: Context) {

    fun realizarLlamada(numero: String, callback: (Boolean, String) -> Unit) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
                callback(false, "Sin permisos de llamada")
                return
            }

            val numeroLimpio = limpiarNumero(numero)
            if (!esNumeroValido(numeroLimpio)) {
                callback(false, "Número inválido")
                return
            }

            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$numeroLimpio")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(intent)
            callback(true, "Llamada iniciada")

        } catch (e: Exception) {
            Log.e("CallManager", "Error realizando llamada: ${e.message}")
            callback(false, "Error al llamar: ${e.message}")
        }
    }

    fun llamarContacto(nombreContacto: String, contactManager: ContactManager,
                       callback: (Boolean, String) -> Unit) {

        val contacto = contactManager.buscarContactoPorNombre(nombreContacto)

        if (contacto != null) {
            realizarLlamada(contacto.numero) { exito, mensaje ->
                if (exito) {
                    callback(true, "Llamando a ${contacto.nombre}")
                } else {
                    callback(false, "Error llamando a ${contacto.nombre}: $mensaje")
                }
            }
        } else {
            callback(false, "No encontré a '$nombreContacto' en los contactos")
        }
    }

    fun llamadaEmergencia(contactManager: ContactManager,
                          callback: (Boolean, String) -> Unit) {

        val contactosEmergencia = contactManager.obtenerContactosEmergencia()

        if (contactosEmergencia.isNotEmpty()) {
            // Intentar primero con contactos favoritos, luego 911
            val contactoEmergencia = contactosEmergencia.firstOrNull { it.tipo == "Favorito" }
                ?: contactosEmergencia.first()

            realizarLlamada(contactoEmergencia.numero) { exito, mensaje ->
                if (exito) {
                    callback(true, "Llamada de emergencia a ${contactoEmergencia.nombre}")
                } else {
                    callback(false, "Error en llamada de emergencia: $mensaje")
                }
            }
        } else {
            realizarLlamada("911") { exito, mensaje ->
                callback(exito, if (exito) "Llamando al 911" else "Error llamando al 911")
            }
        }
    }

    private fun limpiarNumero(numero: String): String {
        return numero.replace(Regex("[^0-9+]"), "")
    }

    private fun esNumeroValido(numero: String): Boolean {
        if (numero.isEmpty()) return false

        // Números de emergencia
        if (numero in listOf("911", "105", "116")) return true

        // Números normales (mínimo 7 dígitos)
        return numero.replace("+", "").length >= 7
    }
}