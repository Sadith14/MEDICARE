package com.example.medicare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.medicare.MedicamentosDBHelper
import com.example.medicare.RecordatorioData
import com.example.medicare.HistorialTomaData

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.d("BootReceiver", "Sistema reiniciado, reprogramando alarmas")

            try {
                val manager = MedicamentoManager(context)
                val medicamentos = manager.dbHelper.obtenerTodosMedicamentos()
                medicamentos.filter { it.activo }.forEach { medicamento ->
                    medicamento.id?.let { id ->
                        manager.activarDesactivarMedicamento(id, true)
                    }
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error reprogramando alarmas: ${e.message}")
            }
        }
    }
}

// === CLASE PARA GESTIONAR MEDICAMENTOS CON ALARMAS ===
class MedicamentoManager(private val context: Context) {
    val dbHelper = MedicamentosDBHelper(context) // Corregido el nombre
    private val alarmManager = MedicamentoAlarmManager(context)

    fun agregarMedicamento(
        nombre: String,
        cantidad: Int,
        horarioHoras: Int,
        horaInicio: Long? = null
    ): Long {
        val medicamento = Medicamento(
            nombre = nombre,
            cantidad = cantidad,
            horarioHoras = horarioHoras,
            fechaCreacion = System.currentTimeMillis(),
            horaInicio = horaInicio,
            activo = true
        )

        val id = dbHelper.insertarMedicamento(medicamento)

        if (id > 0) {
            // Programar recordatorios para el nuevo medicamento
            val medicamentoConId = medicamento.copy(id = id)
            alarmManager.programarRecordatoriosMedicamento(medicamentoConId)
        }

        return id
    }

    fun activarDesactivarMedicamento(medicamentoId: Long, activo: Boolean) {
        dbHelper.activarDesactivarMedicamento(medicamentoId, activo)

        if (activo) {
            // Reprogramar alarmas
            val medicamento = dbHelper.obtenerTodosMedicamentos().find { it.id == medicamentoId }
            medicamento?.let { alarmManager.programarRecordatoriosMedicamento(it) }
        } else {
            // Cancelar alarmas
            alarmManager.cancelarTodasAlarmasMedicamento(medicamentoId)
        }
    }

    fun eliminarMedicamento(medicamentoId: Long): Boolean {
        // Cancelar alarmas primero
        alarmManager.cancelarTodasAlarmasMedicamento(medicamentoId)

        // Eliminar de la base de datos
        return dbHelper.eliminarMedicamento(medicamentoId)
    }

    fun obtenerRecordatoriosPendientes(): List<RecordatorioData> {
        return dbHelper.obtenerRecordatoriosPendientes()
    }

    fun obtenerHistorialMedicamento(medicamentoId: Long): List<HistorialTomaData> {
        return dbHelper.obtenerHistorialMedicamento(medicamentoId)
    }

    fun reprogramarTodasLasAlarmas() {
        alarmManager.reprogramarRecordatorios()
    }
}