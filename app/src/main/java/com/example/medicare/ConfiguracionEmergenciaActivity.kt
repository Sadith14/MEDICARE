package com.example.medicare

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ConfiguracionEmergenciaActivity : AppCompatActivity() {
    private lateinit var dbHelper: MedicamentosDBHelper
    private lateinit var etNombrePaciente: EditText
    private lateinit var etNombreContacto: EditText
    private lateinit var etTelefonoContacto: EditText
    private lateinit var btnGuardar: Button
    private lateinit var btnProbar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dbHelper = MedicamentosDBHelper(this)
        createLayout()
        cargarConfiguracionExistente()
    }

    private fun createLayout() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        // Título
        val titulo = TextView(this).apply {
            text = "⚙️ Configuración de Emergencia"
            textSize = 24f
            setTextColor(Color.parseColor("#1976D2"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
            typeface = Typeface.DEFAULT_BOLD
        }

        // Nombre del paciente
        val lblNombrePaciente = TextView(this).apply {
            text = "👤 Nombre del Paciente"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 20, 0, 8)
            typeface = Typeface.DEFAULT_BOLD
        }

        etNombrePaciente = EditText(this).apply {
            hint = "Ingrese el nombre del paciente"
            textSize = 16f
            setPadding(20, 15, 20, 15)
            setBackgroundColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        // Nombre del contacto
        val lblNombreContacto = TextView(this).apply {
            text = "👨‍👩‍👧‍👦 Nombre del Contacto de Emergencia"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 20, 0, 8)
            typeface = Typeface.DEFAULT_BOLD
        }

        etNombreContacto = EditText(this).apply {
            hint = "Ej: María González (hija)"
            textSize = 16f
            setPadding(20, 15, 20, 15)
            setBackgroundColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        // Teléfono del contacto
        val lblTelefonoContacto = TextView(this).apply {
            text = "📞 Teléfono del Contacto"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 20, 0, 8)
            typeface = Typeface.DEFAULT_BOLD
        }

        etTelefonoContacto = EditText(this).apply {
            hint = "Ej: +51 987 654 321"
            textSize = 16f
            setPadding(20, 15, 20, 15)
            setBackgroundColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_PHONE
        }

        // Información
        val infoText = TextView(this).apply {
            text = "ℹ️ Este contacto será notificado si no respondes a los recordatorios de medicamentos después de 45 minutos."
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#E3F2FD"))
        }

        // Botones
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
        }

        btnProbar = Button(this).apply {
            text = "🧪 PROBAR"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#FF9800"))
            setTextColor(Color.WHITE)
            setPadding(30, 20, 30, 20)
            setOnClickListener { probarConfiguracion() }
        }

        btnGuardar = Button(this).apply {
            text = "💾 GUARDAR"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            setPadding(30, 20, 30, 20)
            setOnClickListener { guardarConfiguracion() }
        }

        val buttonParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            setMargins(10, 0, 10, 0)
        }

        buttonLayout.addView(btnProbar, buttonParams)
        buttonLayout.addView(btnGuardar, buttonParams)

        // Agregar todos los elementos
        layout.addView(titulo)
        layout.addView(lblNombrePaciente)
        layout.addView(etNombrePaciente)
        layout.addView(lblNombreContacto)
        layout.addView(etNombreContacto)
        layout.addView(lblTelefonoContacto)
        layout.addView(etTelefonoContacto)
        layout.addView(infoText)
        layout.addView(buttonLayout)

        setContentView(layout)
    }

    private fun cargarConfiguracionExistente() {
        val sharedPrefs = getSharedPreferences("medicare_config", Context.MODE_PRIVATE)

        etNombrePaciente.setText(sharedPrefs.getString("nombre_paciente", ""))
        etNombreContacto.setText(sharedPrefs.getString("contacto_nombre", ""))
        etTelefonoContacto.setText(sharedPrefs.getString("contacto_telefono", ""))
    }

    private fun guardarConfiguracion() {
        val nombrePaciente = etNombrePaciente.text.toString().trim()
        val nombreContacto = etNombreContacto.text.toString().trim()
        val telefonoContacto = etTelefonoContacto.text.toString().trim()

        if (nombrePaciente.isEmpty()) {
            mostrarError("Por favor ingrese el nombre del paciente")
            return
        }

        if (nombreContacto.isEmpty()) {
            mostrarError("Por favor ingrese el nombre del contacto")
            return
        }

        if (telefonoContacto.isEmpty()) {
            mostrarError("Por favor ingrese el teléfono del contacto")
            return
        }

        if (!validarTelefono(telefonoContacto)) {
            mostrarError("Por favor ingrese un teléfono válido")
            return
        }

        // Guardar configuración
        dbHelper.guardarNombrePaciente(nombrePaciente)
        dbHelper.guardarContactoEmergencia(nombreContacto, telefonoContacto)

        Toast.makeText(this, "✅ Configuración guardada correctamente", Toast.LENGTH_LONG).show()

        // Regresar a la actividad principal
        finish()
    }

    private fun probarConfiguracion() {
        val nombreContacto = etNombreContacto.text.toString().trim()
        val telefonoContacto = etTelefonoContacto.text.toString().trim()

        if (nombreContacto.isEmpty() || telefonoContacto.isEmpty()) {
            mostrarError("Complete los datos del contacto para probar")
            return
        }

        if (!validarTelefono(telefonoContacto)) {
            mostrarError("Ingrese un teléfono válido para probar")
            return
        }

        // Mostrar diálogo de prueba
        val builder = AlertDialog.Builder(this)
        builder.setTitle("🧪 Probar Configuración")
        builder.setMessage("¿Qué desea probar?\n\n📱 Mensaje: Enviará un SMS de prueba\n📞 Llamada: Iniciará una llamada de prueba")

        builder.setPositiveButton("📱 Mensaje") { _, _ ->
            probarMensaje(nombreContacto, telefonoContacto)
        }

        builder.setNegativeButton("📞 Llamada") { _, _ ->
            probarLlamada(telefonoContacto)
        }

        builder.setNeutralButton("Cancelar", null)
        builder.show()
    }

    private fun probarMensaje(nombreContacto: String, telefono: String) {
        try {
            val mensaje = "🧪 PRUEBA de Medicare: Esta es una prueba del sistema de alertas médicas. Si recibe este mensaje, la configuración funciona correctamente."

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$telefono")
                putExtra("sms_body", mensaje)
            }
            startActivity(intent)

            Toast.makeText(this, "📱 Abriendo aplicación de mensajes...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            mostrarError("Error al abrir mensajes: ${e.message}")
        }
    }

    private fun probarLlamada(telefono: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$telefono")
            }
            startActivity(intent)

            Toast.makeText(this, "📞 Abriendo marcador...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            mostrarError("Error al abrir marcador: ${e.message}")
        }
    }

    private fun validarTelefono(telefono: String): Boolean {
        // Validación básica de teléfono
        val telefonoLimpio = telefono.replace(Regex("[^\\d+]"), "")
        return telefonoLimpio.length >= 9 && (telefonoLimpio.startsWith("+") || telefonoLimpio.matches(Regex("\\d+")))
    }

    private fun mostrarError(mensaje: String) {
        Toast.makeText(this, "❌ $mensaje", Toast.LENGTH_LONG).show()
    }
}