package com.example.medicare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import com.example.medicare.R
import android.annotation.SuppressLint
import java.util.*
import android.util.Log
import androidx.core.content.ContextCompat

class MedicamentosAdapter(
    private val medicamentos: MutableList<Medicamento>
) : RecyclerView.Adapter<MedicamentosAdapter.MedicamentoViewHolder>() {

    class MedicamentoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNombre: TextView = itemView.findViewById(R.id.tvNombreMedicamento)
        val tvCantidad: TextView = itemView.findViewById(R.id.tvCantidadMedicamento)
        val tvHorario: TextView = itemView.findViewById(R.id.tvHorarioMedicamento)
        val tvProximaToma: TextView = itemView.findViewById(R.id.tvProximaToma)
        val tvFecha: TextView = itemView.findViewById(R.id.tvFechaMedicamento)
        val vIndicadorCantidad: View = itemView.findViewById(R.id.vIndicadorCantidad)
        val ivEstadoMedicamento: ImageView = itemView.findViewById(R.id.ivEstadoMedicamento)
        val ivEstadoGlow: ImageView = itemView.findViewById(R.id.ivEstadoGlow)
        val tvEstadoTexto: TextView = itemView.findViewById(R.id.tvEstadoTexto)
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
        Log.d("MedicamentosAdapter", "ID: ${medicamento.id}, Cantidad: ${medicamento.cantidad}")

        // 1. NOMBRE
        holder.tvNombre.text = medicamento.nombre

        // 2. CANTIDAD con indicadores visuales mejorados
        val cantidadTexto = when {
            medicamento.cantidad == 0 -> "❌ AGOTADO"
            medicamento.estaPorAgotarse() -> "⚠️ ${medicamento.getCantidadTexto()} (BAJO STOCK)"
            else -> "📦 ${medicamento.getCantidadTexto()}"
        }
        holder.tvCantidad.text = cantidadTexto

        // Cambiar color según stock
        val colorTexto = when {
            medicamento.cantidad == 0 -> android.R.color.holo_red_dark
            medicamento.estaPorAgotarse() -> android.R.color.holo_orange_dark
            else -> android.R.color.white
        }
        holder.tvCantidad.setTextColor(
            ContextCompat.getColor(holder.itemView.context, colorTexto)
        )

        // 3. INDICADOR VISUAL DE CANTIDAD (barra de color)
        val indicadorColor = when {
            medicamento.cantidad == 0 -> android.R.color.holo_red_dark
            medicamento.estaPorAgotarse() -> android.R.color.holo_orange_dark
            else -> R.color.accent_green2
        }
        holder.vIndicadorCantidad.setBackgroundColor(
            ContextCompat.getColor(holder.itemView.context, indicadorColor)
        )

        // 4. HORARIO - CORREGIDO PARA MOSTRAR LAS HORAS CORRECTAS
        val horarioTexto = if (medicamento.horarioHoras > 0) {
            "⏰ Cada ${medicamento.horarioHoras} horas"
        } else {
            "⏰ Sin horario establecido"
        }
        holder.tvHorario.text = horarioTexto

        // 5. PRÓXIMA TOMA - NUEVO: Ahora sí se muestra la hora
        if (medicamento.activo && medicamento.horaInicio != null && medicamento.horarioHoras > 0) {
            val proximaToma = medicamento.obtenerProximaHoraToma()
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val horaProximaToma = formatter.format(Date(proximaToma))

            // Calcular si es hoy o mañana
            val calProxima = Calendar.getInstance().apply { timeInMillis = proximaToma }
            val calHoy = Calendar.getInstance()

            val diaTexto = when {
                calProxima.get(Calendar.DAY_OF_YEAR) == calHoy.get(Calendar.DAY_OF_YEAR) -> "Hoy"
                calProxima.get(Calendar.DAY_OF_YEAR) == calHoy.get(Calendar.DAY_OF_YEAR) + 1 -> "Mañana"
                else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(proximaToma))
            }

            holder.tvProximaToma.text = "🔔 Próxima toma: $diaTexto a las $horaProximaToma"
            holder.tvProximaToma.visibility = View.VISIBLE
        } else {
            if (medicamento.cantidad == 0) {
                holder.tvProximaToma.text = "⏸️ Pausado (Sin stock)"
            } else if (!medicamento.activo) {
                holder.tvProximaToma.text = "⏸️ Inactivo"
            } else {
                holder.tvProximaToma.text = "⚙️ Configurar horario"
            }
            holder.tvProximaToma.visibility = View.VISIBLE
        }

        // 6. FECHA DE CREACIÓN
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val fecha = Date(medicamento.fechaCreacion)
        holder.tvFecha.text = "📅 Agregado: ${sdf.format(fecha)}"

        // 7. INDICADOR DE ESTADO (icono y texto)
        when {
            medicamento.cantidad == 0 -> {
                holder.ivEstadoMedicamento.setImageResource(R.drawable.ic_error)
                holder.ivEstadoGlow.setImageResource(R.drawable.ic_error)
                holder.ivEstadoMedicamento.setColorFilter(
                    ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark)
                )
                holder.ivEstadoGlow.setColorFilter(
                    ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark)
                )
                holder.tvEstadoTexto.text = "AGOTADO"
                holder.tvEstadoTexto.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark)
                )
            }
            medicamento.estaPorAgotarse() -> {
                holder.ivEstadoMedicamento.setImageResource(R.drawable.ic_warning_circle)
                holder.ivEstadoGlow.setImageResource(R.drawable.ic_warning_circle)
                holder.ivEstadoMedicamento.setColorFilter(
                    ContextCompat.getColor(holder.itemView.context, android.R.color.holo_orange_dark)
                )
                holder.ivEstadoGlow.setColorFilter(
                    ContextCompat.getColor(holder.itemView.context, android.R.color.holo_orange_dark)
                )
                holder.tvEstadoTexto.text = "BAJO"
                holder.tvEstadoTexto.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, android.R.color.holo_orange_dark)
                )
            }
            medicamento.activo -> {
                holder.ivEstadoMedicamento.setImageResource(R.drawable.ic_check_circle)
                holder.ivEstadoGlow.setImageResource(R.drawable.ic_check_circle)
                holder.ivEstadoMedicamento.setColorFilter(
                    ContextCompat.getColor(holder.itemView.context, R.color.accent_green2)
                )
                holder.ivEstadoGlow.setColorFilter(
                    ContextCompat.getColor(holder.itemView.context, R.color.accent_green2)
                )
                holder.tvEstadoTexto.text = "OK"
                holder.tvEstadoTexto.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.accent_green2)
                )
            }
            else -> {
                holder.ivEstadoMedicamento.setImageResource(R.drawable.ic_pause)
                holder.ivEstadoGlow.setImageResource(R.drawable.ic_pause)
                holder.ivEstadoMedicamento.setColorFilter(
                    ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray)
                )
                holder.ivEstadoGlow.setColorFilter(
                    ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray)
                )
                holder.tvEstadoTexto.text = "PAUSADO"
                holder.tvEstadoTexto.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray)
                )
            }
        }

        Log.d("MedicamentosAdapter", "Horario mostrado: $horarioTexto")
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