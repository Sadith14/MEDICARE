package com.example.medicare

data class Medicamento(
    var id: Long = 0,
    var nombre: String="",                // requerido por el usuario
    var cantidad: Int=0,                 // requerido por el usuario
    var horarioHoras: Int=0,            // requerido por el usuario
    var fechaCreacion: Long = System.currentTimeMillis(),
    var horaInicio: Long? = fechaCreacion, // usa fechaCreacion si no se pasa nada
    var activo: Boolean = true
) {
    // Función para obtener la próxima hora de toma
    fun obtenerProximaHoraToma(): Long {
        val horaBase = horaInicio ?: fechaCreacion
        val ahora = System.currentTimeMillis()
        val intervalosMillis = horarioHoras * 60 * 60 * 1000L

        if (horaBase > ahora) {
            return horaBase
        }

        val tiempoTranscurrido = ahora - horaBase
        val intervalosCompletos = (tiempoTranscurrido / intervalosMillis).toInt()

        return horaBase + ((intervalosCompletos + 1) * intervalosMillis)
    }

    // Función para obtener todas las horas de toma del día
    fun obtenerHorasDelDia(): List<String> {
        val horasDelDia = mutableListOf<String>()
        val calendario = java.util.Calendar.getInstance()
        val horaBase = horaInicio ?: fechaCreacion

        calendario.timeInMillis = horaBase
        val horaInicial = calendario.get(java.util.Calendar.HOUR_OF_DAY)
        val minutoInicial = calendario.get(java.util.Calendar.MINUTE)

        var horaActual = horaInicial
        var minutoActual = minutoInicial

        for (i in 0 until (24 / horarioHoras)) {
            val hora = String.format("%02d:%02d", horaActual, minutoActual)
            horasDelDia.add(hora)

            horaActual += horarioHoras
            if (horaActual >= 24) {
                horaActual -= 24
            }
        }

        return horasDelDia
    }

    // Función para formatear la información del medicamento
    fun obtenerResumen(): String {
        val horas = obtenerHorasDelDia().joinToString(", ")
        return "$nombre - Cada $horarioHoras horas ($horas) - Quedan: $cantidad unidades"
    }
    fun getCantidadTexto(): String {
        return "$cantidad unidades"
    }

    fun getHorarioTexto(): String {
        return "Cada $horarioHoras h"
    }
    fun estaPorAgotarse(): Boolean {
        return cantidad <= 3
    }
    fun reducirCantidadEnUno() {
        if (cantidad > 0) {
            cantidad -= 1
        }
    }
    fun agregarCantidad(unidades: Int) {
        cantidad += unidades
    }

}