package com.example.medicare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import com.example.medicare.R
import android.annotation.SuppressLint
import java.util.*
import android.util.Log

class MedicamentosAdapter(
    private val medicamentos: MutableList<Medicamento>
) : RecyclerView.Adapter<MedicamentosAdapter.MedicamentoViewHolder>() {

    class MedicamentoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNombre: TextView = itemView.findViewById(R.id.tvNombreMedicamento)
        val tvCantidad: TextView = itemView.findViewById(R.id.tvCantidadMedicamento)
        val tvHorario: TextView = itemView.findViewById(R.id.tvHorarioMedicamento)
        val tvFecha: TextView = itemView.findViewById(R.id.tvFechaMedicamento)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicamentoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medicamento, parent, false)
        return MedicamentoViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: MedicamentoViewHolder, position: Int) {
        val medicamento = medicamentos[position]

        Log.d("MedicamentosAdapter", "=== BINDING MEDICAMENTO ${medicamento.nombre} ===")
        Log.d("MedicamentosAdapter", "Activo: ${medicamento.activo}")
        Log.d("MedicamentosAdapter", "Hora inicio: ${medicamento.horaInicio}")
        Log.d("MedicamentosAdapter", "Horario horas: ${medicamento.horarioHoras}")

        holder.tvNombre.text = medicamento.nombre
        holder.tvCantidad.text = "📦 ${medicamento.getCantidadTexto()}"

        // Mostrar horario y próxima toma
        val horaInicio = medicamento.horaInicio
        val horarioHoras = medicamento.horarioHoras

        if (medicamento.activo && horaInicio != null && horaInicio > 0 && horarioHoras > 0) {
            // Usar el método que ya existe en la clase Medicamento
            val proximaToma = medicamento.obtenerProximaHoraToma()
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val horaProximaToma = formatter.format(Date(proximaToma))

            Log.d("MedicamentosAdapter", "Próxima toma calculada: $horaProximaToma")

            holder.tvHorario.text = "⏰ ${medicamento.getHorarioTexto()} | Próxima: $horaProximaToma"
        } else {
            Log.d("MedicamentosAdapter", "Medicamento inactivo o sin hora de inicio")
            Log.d("MedicamentosAdapter", "Activo: ${medicamento.activo}, HoraInicio: $horaInicio, Horario: $horarioHoras")
            holder.tvHorario.text = "⏰ ${medicamento.getHorarioTexto()}"
        }

        // Formatear fecha
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val fecha = Date(medicamento.fechaCreacion)
        holder.tvFecha.text = "📅 Agregado: ${sdf.format(fecha)}"

        Log.d("MedicamentosAdapter", "=== FIN BINDING ===")
    }

    override fun getItemCount() = medicamentos.size

    // Método para actualizar la lista
    @SuppressLint("NotifyDataSetChanged")
    fun actualizarLista(nuevaLista: List<Medicamento>) {
        Log.d("MedicamentosAdapter", "=== ACTUALIZANDO LISTA ===")
        Log.d("MedicamentosAdapter", "Lista anterior: ${medicamentos.size} items")
        Log.d("MedicamentosAdapter", "Nueva lista: ${nuevaLista.size} items")

        medicamentos.clear()
        medicamentos.addAll(nuevaLista)
        notifyDataSetChanged()

        Log.d("MedicamentosAdapter", "Lista actualizada con ${medicamentos.size} items")
        Log.d("MedicamentosAdapter", "=== FIN ACTUALIZACIÓN ===")
    }
}