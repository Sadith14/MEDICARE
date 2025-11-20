package com.example.medicare

class VoiceCommandProcessor(
    private val contactManager: ContactManager,
    private val callManager: CallManager,
    private val speechCallback: (String) -> Unit
) {

    fun procesarComandoLlamada(textoReconocido: String): Boolean {
        val textoLower = textoReconocido.lowercase()

        when {
            // Llamadas directas
            textoLower.matches(Regex(".*llama?r? a (.+)")) -> {
                val match = Regex(".*llama?r? a (.+)").find(textoLower)
                val nombreContacto = match?.groupValues?.get(1)?.trim()

                if (!nombreContacto.isNullOrEmpty()) {
                    realizarLlamadaContacto(nombreContacto)
                    return true
                }
            }

            // Emergencias
            textoLower.contains("emergencia") || textoLower.contains("911") -> {
                realizarLlamadaEmergencia()
                return true
            }

            // Comandos específicos
            textoLower.contains("llamar emergencia") || textoLower.contains("llamada emergencia") -> {
                realizarLlamadaEmergencia()
                return true
            }
        }

        return false
    }

    private fun realizarLlamadaContacto(nombreContacto: String) {
        speechCallback("Buscando a $nombreContacto en contactos...")

        callManager.llamarContacto(nombreContacto, contactManager) { exito, mensaje ->
            speechCallback(mensaje)
        }
    }

    private fun realizarLlamadaEmergencia() {
        speechCallback("Iniciando llamada de emergencia...")

        callManager.llamadaEmergencia(contactManager) { exito, mensaje ->
            speechCallback(mensaje)
        }
    }

    fun obtenerSugerenciasContactos(textoVoz: String): List<String> {
        val sugerencias = mutableListOf<String>()

        // Si menciona "llamar" pero no especifica a quién
        if (textoVoz.lowercase().contains("llamar") || textoVoz.lowercase().contains("llama")) {
            sugerencias.add("¿A quién desea llamar?")
            sugerencias.add("Puede decir 'llama a [nombre]' o 'emergencia'")
        }

        return sugerencias
    }
}