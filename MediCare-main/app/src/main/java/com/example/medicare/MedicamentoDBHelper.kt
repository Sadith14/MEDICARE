package com.example.medicare

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.medicare.Medicamento

class MedicamentosDBHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "medicamentos.db"
        private const val DATABASE_VERSION = 3 // Increm
        // Tabla medicamentos
        private const val TABLE_MEDICAMENTOS = "medicamentos"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NOMBRE = "nombre"
        private const val COLUMN_CANTIDAD = "cantidad"
        private const val COLUMN_HORARIO_HORAS = "horario_horas"
        private const val COLUMN_FECHA_CREACION = "fecha_creacion"
        private const val COLUMN_HORA_INICIO = "hora_inicio"
        private const val COLUMN_ACTIVO = "activo"

        // Tabla recordatorios
        private const val TABLE_RECORDATORIOS = "recordatorios"
        private const val COLUMN_REC_ID = "id"
        private const val COLUMN_REC_MEDICAMENTO_ID = "medicamento_id"
        private const val COLUMN_REC_FECHA_HORA = "fecha_hora"
        private const val COLUMN_REC_COMPLETADO = "completado"
        private const val COLUMN_REC_POSTERGADO = "postergado"
        private const val COLUMN_REC_ALARM_ID = "alarm_id"
        private const val COLUMN_REC_NUMERO_POSTERGACIONES = "numero_postergaciones"
        private const val COLUMN_REC_FECHA_ORIGINAL = "fecha_original"
        private const val COLUMN_REC_NOTIFICACION_ENVIADA = "notificacion_enviada"


        // Tabla historial tomas
        private const val TABLE_HISTORIAL = "historial_tomas"
        private const val COLUMN_HIST_ID = "id"
        private const val COLUMN_HIST_MEDICAMENTO_ID = "medicamento_id"
        private const val COLUMN_HIST_FECHA_HORA_PROGRAMADA = "fecha_hora_programada"
        private const val COLUMN_HIST_FECHA_HORA_TOMADA = "fecha_hora_tomada"
        private const val COLUMN_HIST_ESTADO = "estado"
        private const val COLUMN_HIST_CANTIDAD_TOMADA = "cantidad_tomada"

        // Tabla escalamientos
        private const val TABLE_ESCALAMIENTOS = "escalamientos"
        private const val COLUMN_ESC_ID = "id"
        private const val COLUMN_ESC_MEDICAMENTO_ID = "medicamento_id"
        private const val COLUMN_ESC_RECORDATORIO_ID = "recordatorio_id"
        private const val COLUMN_ESC_NIVEL = "nivel"
        private const val COLUMN_ESC_FECHA_CREACION = "fecha_creacion"
        private const val COLUMN_ESC_COMPLETADO = "completado"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        // Tabla medicamentos
        val createMedicamentosTable = """
            CREATE TABLE $TABLE_MEDICAMENTOS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NOMBRE TEXT NOT NULL,
                $COLUMN_CANTIDAD INTEGER NOT NULL,
                $COLUMN_HORARIO_HORAS INTEGER NOT NULL,
                $COLUMN_FECHA_CREACION INTEGER NOT NULL,
                $COLUMN_HORA_INICIO INTEGER,
                $COLUMN_ACTIVO INTEGER DEFAULT 1
            )
        """.trimIndent()

        // Tabla recordatorios
        val createRecordatoriosTable = """
            CREATE TABLE $TABLE_RECORDATORIOS (
                $COLUMN_REC_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_REC_MEDICAMENTO_ID INTEGER NOT NULL,
                $COLUMN_REC_FECHA_HORA INTEGER NOT NULL,
                $COLUMN_REC_COMPLETADO INTEGER DEFAULT 0,
                $COLUMN_REC_POSTERGADO INTEGER DEFAULT 0,
                $COLUMN_REC_ALARM_ID INTEGER NOT NULL,
                $COLUMN_REC_NUMERO_POSTERGACIONES INTEGER DEFAULT 0,
                $COLUMN_REC_FECHA_ORIGINAL INTEGER,
                $COLUMN_REC_NOTIFICACION_ENVIADA INTEGER DEFAULT 0,
                FOREIGN KEY($COLUMN_REC_MEDICAMENTO_ID) REFERENCES $TABLE_MEDICAMENTOS($COLUMN_ID)
            )
        """.trimIndent()

        // Tabla historial
        val createHistorialTable = """
            CREATE TABLE $TABLE_HISTORIAL (
                $COLUMN_HIST_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_HIST_MEDICAMENTO_ID INTEGER NOT NULL,
                $COLUMN_HIST_FECHA_HORA_PROGRAMADA INTEGER NOT NULL,
                $COLUMN_HIST_FECHA_HORA_TOMADA INTEGER,
                $COLUMN_HIST_ESTADO TEXT NOT NULL,
                $COLUMN_HIST_CANTIDAD_TOMADA INTEGER DEFAULT 1,
                FOREIGN KEY($COLUMN_HIST_MEDICAMENTO_ID) REFERENCES $TABLE_MEDICAMENTOS($COLUMN_ID)
            )
        """.trimIndent()

        // Tabla escalamientos
        val createEscalamientosTable = """
            CREATE TABLE $TABLE_ESCALAMIENTOS (
                $COLUMN_ESC_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_ESC_MEDICAMENTO_ID INTEGER NOT NULL,
                $COLUMN_ESC_RECORDATORIO_ID INTEGER NOT NULL,
                $COLUMN_ESC_NIVEL INTEGER DEFAULT 1,
                $COLUMN_ESC_FECHA_CREACION INTEGER NOT NULL,
                $COLUMN_ESC_COMPLETADO INTEGER DEFAULT 0,
                FOREIGN KEY($COLUMN_ESC_MEDICAMENTO_ID) REFERENCES $TABLE_MEDICAMENTOS($COLUMN_ID),
                FOREIGN KEY($COLUMN_ESC_RECORDATORIO_ID) REFERENCES $TABLE_RECORDATORIOS($COLUMN_REC_ID)
            )
        """.trimIndent()

        db?.execSQL(createMedicamentosTable)
        db?.execSQL(createRecordatoriosTable)
        db?.execSQL(createHistorialTable)
        db?.execSQL(createEscalamientosTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Agregar nuevas columnas a la tabla existente
            db?.execSQL("ALTER TABLE $TABLE_MEDICAMENTOS ADD COLUMN $COLUMN_HORA_INICIO INTEGER")
            db?.execSQL("ALTER TABLE $TABLE_MEDICAMENTOS ADD COLUMN $COLUMN_ACTIVO INTEGER DEFAULT 1")

            // Crear nuevas tablas
            val createRecordatoriosTable = """
                CREATE TABLE $TABLE_RECORDATORIOS (
                    $COLUMN_REC_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_REC_MEDICAMENTO_ID INTEGER NOT NULL,
                    $COLUMN_REC_FECHA_HORA INTEGER NOT NULL,
                    $COLUMN_REC_COMPLETADO INTEGER DEFAULT 0,
                    $COLUMN_REC_POSTERGADO INTEGER DEFAULT 0,
                    $COLUMN_REC_ALARM_ID INTEGER NOT NULL,
                    FOREIGN KEY($COLUMN_REC_MEDICAMENTO_ID) REFERENCES $TABLE_MEDICAMENTOS($COLUMN_ID)
                )
            """.trimIndent()

            val createHistorialTable = """
                CREATE TABLE $TABLE_HISTORIAL (
                    $COLUMN_HIST_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_HIST_MEDICAMENTO_ID INTEGER NOT NULL,
                    $COLUMN_HIST_FECHA_HORA_PROGRAMADA INTEGER NOT NULL,
                    $COLUMN_HIST_FECHA_HORA_TOMADA INTEGER,
                    $COLUMN_HIST_ESTADO TEXT NOT NULL,
                    $COLUMN_HIST_CANTIDAD_TOMADA INTEGER DEFAULT 1,
                    FOREIGN KEY($COLUMN_HIST_MEDICAMENTO_ID) REFERENCES $TABLE_MEDICAMENTOS($COLUMN_ID)
                )
            """.trimIndent()

            db?.execSQL(createRecordatoriosTable)
            db?.execSQL(createHistorialTable)
        }
        if (oldVersion < 3) {
            // Agregar nuevas columnas para manejo de postergaciones
            db?.execSQL("ALTER TABLE $TABLE_RECORDATORIOS ADD COLUMN $COLUMN_REC_NUMERO_POSTERGACIONES INTEGER DEFAULT 0")
            db?.execSQL("ALTER TABLE $TABLE_RECORDATORIOS ADD COLUMN $COLUMN_REC_FECHA_ORIGINAL INTEGER")
            db?.execSQL("ALTER TABLE $TABLE_RECORDATORIOS ADD COLUMN $COLUMN_REC_NOTIFICACION_ENVIADA INTEGER DEFAULT 0")

            // Actualizar registros existentes
            db?.execSQL("UPDATE $TABLE_RECORDATORIOS SET $COLUMN_REC_FECHA_ORIGINAL = $COLUMN_REC_FECHA_HORA WHERE $COLUMN_REC_FECHA_ORIGINAL IS NULL")
        }
    }

    // === MÉTODOS PARA ESCALAMIENTOS ===

    fun crearEscalamiento(medicamentoId: Long, recordatorioId: Long): Long {
        val db = writableDatabase
        return try {
            val values = ContentValues().apply {
                put(COLUMN_ESC_MEDICAMENTO_ID, medicamentoId)
                put(COLUMN_ESC_RECORDATORIO_ID, recordatorioId)
                put(COLUMN_ESC_NIVEL, 1)
                put(COLUMN_ESC_FECHA_CREACION, System.currentTimeMillis())
                put(COLUMN_ESC_COMPLETADO, 0)
            }

            val id = db.insert(TABLE_ESCALAMIENTOS, null, values)
            Log.d("MedicamentosDBHelper", "Escalamiento creado con ID: $id")
            id
        } catch (e: Exception) {
            Log.e("MedicamentosDBHelper", "Error creando escalamiento: ${e.message}")
            -1
        } finally {
            db.close()
        }
    }

    fun marcarEscalamientoCompletado(escalamientoId: Long): Boolean {
        val db = writableDatabase
        return try {
            val values = ContentValues().apply {
                put(COLUMN_ESC_COMPLETADO, 1)
            }

            val rowsUpdated = db.update(
                TABLE_ESCALAMIENTOS,
                values,
                "$COLUMN_ESC_ID = ?",
                arrayOf(escalamientoId.toString())
            )

            Log.d("MedicamentosDBHelper", "Escalamiento $escalamientoId marcado como completado")
            rowsUpdated > 0
        } catch (e: Exception) {
            Log.e("MedicamentosDBHelper", "Error marcando escalamiento completado: ${e.message}")
            false
        } finally {
            db.close()
        }
    }

    fun obtenerEscalamientosActivos(): List<EscalamientoAlarma> {
        val escalamientos = mutableListOf<EscalamientoAlarma>()
        val db = readableDatabase

        try {
            val cursor = db.query(
                TABLE_ESCALAMIENTOS,
                null,
                "$COLUMN_ESC_COMPLETADO = 0",
                null,
                null,
                null,
                "$COLUMN_ESC_FECHA_CREACION DESC"
            )

            while (cursor.moveToNext()) {
                escalamientos.add(
                    EscalamientoAlarma(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ESC_ID)),
                        medicamentoId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ESC_MEDICAMENTO_ID)),
                        recordatorioId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ESC_RECORDATORIO_ID)),
                        nivel = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ESC_NIVEL)),
                        fechaCreacion = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ESC_FECHA_CREACION)),
                        completado = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ESC_COMPLETADO)) == 1
                    )
                )
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("MedicamentosDBHelper", "Error obteniendo escalamientos: ${e.message}")
        } finally {
            db.close()
        }

        return escalamientos
    }

    // === MÉTODOS PARA CONFIGURACIÓN ===

    fun guardarContactoEmergencia(nombre: String, telefono: String) {
        val sharedPrefs = context.getSharedPreferences("medicare_config", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString("contacto_nombre", nombre)
            putString("contacto_telefono", telefono)
            apply()
        }
        Log.d("MedicamentosDBHelper", "Contacto de emergencia guardado: $nombre")
    }

    fun obtenerContactoEmergencia(): ContactoEmergencia? {
        val sharedPrefs = context.getSharedPreferences("medicare_config", Context.MODE_PRIVATE)
        val nombre = sharedPrefs.getString("contacto_nombre", null)
        val telefono = sharedPrefs.getString("contacto_telefono", null)

        return if (nombre != null && telefono != null) {
            ContactoEmergencia(nombre, telefono)
        } else null
    }

    fun guardarNombrePaciente(nombre: String) {
        val sharedPrefs = context.getSharedPreferences("medicare_config", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString("nombre_paciente", nombre)
            apply()
        }
        Log.d("MedicamentosDBHelper", "Nombre del paciente guardado: $nombre")
    }

    fun obtenerNombrePaciente(): String? {
        val sharedPrefs = context.getSharedPreferences("medicare_config", Context.MODE_PRIVATE)
        return sharedPrefs.getString("nombre_paciente", null)
    }

    // === MÉTODOS PARA MEDICAMENTOS ===

    fun insertarMedicamento(medicamento: Medicamento): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NOMBRE, medicamento.nombre)
            put(COLUMN_CANTIDAD, medicamento.cantidad)
            put(COLUMN_HORARIO_HORAS, medicamento.horarioHoras)
            put(COLUMN_FECHA_CREACION, medicamento.fechaCreacion)
            put(COLUMN_HORA_INICIO, medicamento.horaInicio ?: medicamento.fechaCreacion)
            put(COLUMN_ACTIVO, 1)
        }

        val id = db.insert(TABLE_MEDICAMENTOS, null, values)
        db.close()
        return id
    }

    fun obtenerTodosMedicamentos(): List<Medicamento> {
        val medicamentos = mutableListOf<Medicamento>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_MEDICAMENTOS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_FECHA_CREACION DESC"
        )

        with(cursor) {
            while (moveToNext()) {
                val medicamento = Medicamento(
                    id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
                    nombre = getString(getColumnIndexOrThrow(COLUMN_NOMBRE)),
                    cantidad = getInt(getColumnIndexOrThrow(COLUMN_CANTIDAD)),
                    horarioHoras = getInt(getColumnIndexOrThrow(COLUMN_HORARIO_HORAS)),
                    fechaCreacion = getLong(getColumnIndexOrThrow(COLUMN_FECHA_CREACION)),
                    horaInicio = if (isNull(getColumnIndexOrThrow(COLUMN_HORA_INICIO))) null else getLong(getColumnIndexOrThrow(COLUMN_HORA_INICIO)),
                    activo = getInt(getColumnIndexOrThrow(COLUMN_ACTIVO)) == 1
                )
                medicamentos.add(medicamento)
            }
        }

        cursor.close()
        db.close()
        return medicamentos
    }

    fun obtenerMedicamentoPorNombre(nombre: String): Medicamento? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MEDICAMENTOS,
            null,
            "$COLUMN_NOMBRE = ?",
            arrayOf(nombre),
            null,
            null,
            null
        )

        var medicamento: Medicamento? = null
        if (cursor.moveToFirst()) {
            medicamento = Medicamento(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                nombre = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOMBRE)),
                cantidad = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CANTIDAD)),
                horarioHoras = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_HORARIO_HORAS)),
                fechaCreacion = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_FECHA_CREACION)),
                horaInicio = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_HORA_INICIO))) null else cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_HORA_INICIO)),
                activo = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ACTIVO)) == 1
            )
        }

        cursor.close()
        db.close()
        return medicamento
    }

    fun eliminarMedicamento(id: Long): Boolean {
        val db = writableDatabase
        // Eliminar escalamientos asociados
        db.delete(TABLE_ESCALAMIENTOS, "$COLUMN_ESC_MEDICAMENTO_ID = ?", arrayOf(id.toString()))
        // Eliminar recordatorios asociados
        db.delete(TABLE_RECORDATORIOS, "$COLUMN_REC_MEDICAMENTO_ID = ?", arrayOf(id.toString()))
        // Eliminar historial asociado
        db.delete(TABLE_HISTORIAL, "$COLUMN_HIST_MEDICAMENTO_ID = ?", arrayOf(id.toString()))
        // Eliminar medicamento
        val rowsDeleted = db.delete(TABLE_MEDICAMENTOS, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        return rowsDeleted > 0
    }

    fun actualizarEstadoMedicamento(medicamentoId: Long, activo: Boolean): Boolean {
        val db = writableDatabase
        return try {
            val values = ContentValues().apply {
                put(COLUMN_ACTIVO, if (activo) 1 else 0)
            }

            val rowsUpdated = db.update(
                TABLE_MEDICAMENTOS,
                values,
                "$COLUMN_ID = ?",
                arrayOf(medicamentoId.toString())
            )

            rowsUpdated > 0
        } catch (e: Exception) {
            Log.e("MedicamentosDBHelper", "Error actualizando estado del medicamento: ${e.message}")
            false
        } finally {
            db.close()
        }
    }

    fun actualizarCantidadMedicamento(id: Long, nuevaCantidad: Int): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_CANTIDAD, nuevaCantidad)
        }

        val rowsUpdated = db.update(TABLE_MEDICAMENTOS, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        return rowsUpdated > 0
    }

    fun obtenerMedicamentosConBajoStock(umbral: Int = 3): List<Medicamento> {
        val medicamentosBajoStock = mutableListOf<Medicamento>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_MEDICAMENTOS,
            null,
            "$COLUMN_CANTIDAD <= ? AND $COLUMN_ACTIVO = 1",
            arrayOf(umbral.toString()),
            null,
            null,
            null
        )

        with(cursor) {
            while (moveToNext()) {
                val medicamento = Medicamento(
                    id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
                    nombre = getString(getColumnIndexOrThrow(COLUMN_NOMBRE)),
                    cantidad = getInt(getColumnIndexOrThrow(COLUMN_CANTIDAD)),
                    horarioHoras = getInt(getColumnIndexOrThrow(COLUMN_HORARIO_HORAS)),
                    fechaCreacion = getLong(getColumnIndexOrThrow(COLUMN_FECHA_CREACION)),
                    horaInicio = if (isNull(getColumnIndexOrThrow(COLUMN_HORA_INICIO))) null else getLong(getColumnIndexOrThrow(COLUMN_HORA_INICIO)),
                    activo = getInt(getColumnIndexOrThrow(COLUMN_ACTIVO)) == 1
                )
                medicamentosBajoStock.add(medicamento)
            }
        }

        cursor.close()
        db.close()
        return medicamentosBajoStock
    }

    fun activarDesactivarMedicamento(medicamentoId: Long, activo: Boolean): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ACTIVO, if (activo) 1 else 0)
        }

        val rowsUpdated = db.update(TABLE_MEDICAMENTOS, values, "$COLUMN_ID = ?", arrayOf(medicamentoId.toString()))
        db.close()
        return rowsUpdated > 0
    }

    // === FUNCIONES FALTANTES PARA MedicamentosDBHelper ===

    // 1. Función para obtener número de postergaciones actual
    fun obtenerNumeroPostergaciones(recordatorioId: Long): Int {
        val db = readableDatabase
        var numeroPostergaciones = 0

        try {
            val cursor = db.query(
                TABLE_RECORDATORIOS,
                arrayOf(COLUMN_REC_NUMERO_POSTERGACIONES),
                "$COLUMN_REC_ID = ?",
                arrayOf(recordatorioId.toString()),
                null,
                null,
                null
            )

            if (cursor.moveToFirst()) {
                numeroPostergaciones = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_REC_NUMERO_POSTERGACIONES))
            }
            cursor.close()

            Log.d("MedicamentosDBHelper", "Postergaciones para recordatorio $recordatorioId: $numeroPostergaciones")

        } catch (e: Exception) {
            Log.e("MedicamentosDBHelper", "Error obteniendo número de postergaciones: ${e.message}")
        } finally {
            db.close()
        }

        return numeroPostergaciones
    }

    // 2. Función para actualizar número de postergaciones
    fun actualizarNumeroPostergaciones(recordatorioId: Long, numero: Int): Boolean {
        val db = writableDatabase
        return try {
            val values = ContentValues().apply {
                put(COLUMN_REC_NUMERO_POSTERGACIONES, numero)
            }

            val rowsUpdated = db.update(
                TABLE_RECORDATORIOS,
                values,
                "$COLUMN_REC_ID = ?",
                arrayOf(recordatorioId.toString())
            )

            Log.d("MedicamentosDBHelper", "Postergaciones actualizadas para recordatorio $recordatorioId: $numero")
            rowsUpdated > 0

        } catch (e: Exception) {
            Log.e("MedicamentosDBHelper", "Error actualizando postergaciones: ${e.message}")
            false
        } finally {
            db.close()
        }
    }

    // 3. Función para obtener fecha original del recordatorio
    fun obtenerFechaOriginalRecordatorio(recordatorioId: Long): Long {
        val db = readableDatabase
        var fechaOriginal = System.currentTimeMillis()

        try {
            val cursor = db.query(
                TABLE_RECORDATORIOS,
                arrayOf(COLUMN_REC_FECHA_ORIGINAL, COLUMN_REC_FECHA_HORA),
                "$COLUMN_REC_ID = ?",
                arrayOf(recordatorioId.toString()),
                null,
                null,
                null
            )

            if (cursor.moveToFirst()) {
                // Si hay fecha original, usarla, sino usar fecha_hora
                fechaOriginal = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_REC_FECHA_ORIGINAL))) {
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_REC_FECHA_HORA))
                } else {
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_REC_FECHA_ORIGINAL))
                }
            }
            cursor.close()

            Log.d("MedicamentosDBHelper", "Fecha original para recordatorio $recordatorioId: $fechaOriginal")

        } catch (e: Exception) {
            Log.e("MedicamentosDBHelper", "Error obteniendo fecha original: ${e.message}")
        } finally {
            db.close()
        }

        return fechaOriginal
    }

    // 4. Función para inicializar fecha original en recordatorios existentes (llamar al insertar)
    fun inicializarFechaOriginal(recordatorioId: Long, fechaOriginal: Long): Boolean {
        val db = writableDatabase
        return try {
            val values = ContentValues().apply {
                put(COLUMN_REC_FECHA_ORIGINAL, fechaOriginal)
            }

            val rowsUpdated = db.update(
                TABLE_RECORDATORIOS,
                values,
                "$COLUMN_REC_ID = ? AND $COLUMN_REC_FECHA_ORIGINAL IS NULL",
                arrayOf(recordatorioId.toString())
            )

            Log.d("MedicamentosDBHelper", "Fecha original inicializada para recordatorio $recordatorioId")
            rowsUpdated > 0

        } catch (e: Exception) {
            Log.e("MedicamentosDBHelper", "Error inicializando fecha original: ${e.message}")
            false
        } finally {
            db.close()
        }
    }

    // 5. Función mejorada para insertar recordatorio (con fecha original)
    fun insertarRecordatorioConFechaOriginal(medicamentoId: Long, fechaHora: Long, alarmId: Int): Long {
        val db = writableDatabase
        return try {
            val values = ContentValues().apply {
                put(COLUMN_REC_MEDICAMENTO_ID, medicamentoId)
                put(COLUMN_REC_FECHA_HORA, fechaHora)
                put(COLUMN_REC_FECHA_ORIGINAL, fechaHora) // Inicializar fecha original
                put(COLUMN_REC_ALARM_ID, alarmId)
                put(COLUMN_REC_NUMERO_POSTERGACIONES, 0) // Inicializar en 0
                put(COLUMN_REC_NOTIFICACION_ENVIADA, 0) // Inicializar en 0
            }

            val id = db.insert(TABLE_RECORDATORIOS, null, values)
            Log.d("MedicamentosDBHelper", "Recordatorio insertado con ID: $id, fecha original: $fechaHora")
            id
        } catch (e: Exception) {
            Log.e("MedicamentosDBHelper", "Error insertando recordatorio: ${e.message}")
            -1
        } finally {
            db.close()
        }
    }

    // 6. Función para obtener recordatorio por ID (útil para debugging)
    fun obtenerRecordatorioPorId(recordatorioId: Long): RecordatorioDataExtendido? {
        val db = readableDatabase
        var recordatorio: RecordatorioDataExtendido? = null

        try {
            val query = """
            SELECT r.*, m.$COLUMN_NOMBRE as nombre_medicamento, m.$COLUMN_CANTIDAD as cantidad_medicamento
            FROM $TABLE_RECORDATORIOS r
            JOIN $TABLE_MEDICAMENTOS m ON r.$COLUMN_REC_MEDICAMENTO_ID = m.$COLUMN_ID
            WHERE r.$COLUMN_REC_ID = ?
        """.trimIndent()

            val cursor = db.rawQuery(query, arrayOf(recordatorioId.toString()))

            if (cursor.moveToFirst()) {
                recordatorio = RecordatorioDataExtendido(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_REC_ID)),
                    medicamentoId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_REC_MEDICAMENTO_ID)),
                    fechaHora = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_REC_FECHA_HORA)),
                    fechaOriginal = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_REC_FECHA_ORIGINAL)))
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_REC_FECHA_HORA))
                    else cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_REC_FECHA_ORIGINAL)),
                    completado = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_REC_COMPLETADO)) == 1,
                    postergado = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_REC_POSTERGADO)),
                    numeroPostergaciones = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_REC_NUMERO_POSTERGACIONES)),
                    notificacionEnviada = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_REC_NOTIFICACION_ENVIADA)) == 1,
                    alarmId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_REC_ALARM_ID)),
                    nombreMedicamento = cursor.getString(cursor.getColumnIndexOrThrow("nombre_medicamento")),
                    cantidadMedicamento = cursor.getInt(cursor.getColumnIndexOrThrow("cantidad_medicamento"))
                )
            }
            cursor.close()

        } catch (e: Exception) {
            Log.e("MedicamentosDBHelper", "Error obteniendo recordatorio por ID: ${e.message}")
        } finally {
            db.close()
        }

        return recordatorio
    }

    // 7. Función para limpiar notificaciones enviadas (útil para testing)
    fun limpiarNotificacionesEnviadas(): Boolean {
        val db = writableDatabase
        return try {
            val values = ContentValues().apply {
                put(COLUMN_REC_NOTIFICACION_ENVIADA, 0)
            }

            val rowsUpdated = db.update(
                TABLE_RECORDATORIOS,
                values,
                "$COLUMN_REC_COMPLETADO = 0", // Solo recordatorios no completados
                null
            )

            Log.d("MedicamentosDBHelper", "Notificaciones limpiadas: $rowsUpdated recordatorios")
            rowsUpdated > 0

        } catch (e: Exception) {
            Log.e("MedicamentosDBHelper", "Error limpiando notificaciones: ${e.message}")
            false
        } finally {
            db.close()
        }
    }

    fun postergarRecordatorio(recordatorioId: Long, nuevaFechaHora: Long): Boolean {
        val db = writableDatabase

        // Primero obtener el número actual de postergaciones
        val cursor = db.query(
            TABLE_RECORDATORIOS,
            arrayOf(COLUMN_REC_NUMERO_POSTERGACIONES),
            "$COLUMN_REC_ID = ?",
            arrayOf(recordatorioId.toString()),
            null,
            null,
            null
        )

        var numeroPostergaciones = 0
        if (cursor.moveToFirst()) {
            numeroPostergaciones = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_REC_NUMERO_POSTERGACIONES))
        }
        cursor.close()

        // Incrementar contador y actualizar
        val values = ContentValues().apply {
            put(COLUMN_REC_FECHA_HORA, nuevaFechaHora)
            put(COLUMN_REC_POSTERGADO, 1)
            put(COLUMN_REC_NUMERO_POSTERGACIONES, numeroPostergaciones + 1)
        }

        val rowsUpdated = db.update(TABLE_RECORDATORIOS, values, "$COLUMN_REC_ID = ?", arrayOf(recordatorioId.toString()))
        db.close()
        return rowsUpdated > 0
    }
    // === MÉTODOS PARA RECORDATORIOS ===

    fun insertarRecordatorio(medicamentoId: Long, fechaHora: Long, alarmId: Int): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_REC_MEDICAMENTO_ID, medicamentoId)
            put(COLUMN_REC_FECHA_HORA, fechaHora)
            put(COLUMN_REC_ALARM_ID, alarmId)
        }

        val id = db.insert(TABLE_RECORDATORIOS, null, values)
        db.close()
        return id
    }

    fun obtenerRecordatoriosPendientes(): List<RecordatorioData> {
        val recordatorios = mutableListOf<RecordatorioData>()
        val db = readableDatabase

        val query = """
            SELECT r.*, m.$COLUMN_NOMBRE as nombre_medicamento, m.$COLUMN_CANTIDAD as cantidad_medicamento
            FROM $TABLE_RECORDATORIOS r
            JOIN $TABLE_MEDICAMENTOS m ON r.$COLUMN_REC_MEDICAMENTO_ID = m.$COLUMN_ID
            WHERE r.$COLUMN_REC_COMPLETADO = 0 AND m.$COLUMN_ACTIVO = 1
            ORDER BY r.$COLUMN_REC_FECHA_HORA ASC
        """.trimIndent()

        val cursor = db.rawQuery(query, null)

        with(cursor) {
            while (moveToNext()) {
                val recordatorio = RecordatorioData(
                    id = getLong(getColumnIndexOrThrow(COLUMN_REC_ID)),
                    medicamentoId = getLong(getColumnIndexOrThrow(COLUMN_REC_MEDICAMENTO_ID)),
                    fechaHora = getLong(getColumnIndexOrThrow(COLUMN_REC_FECHA_HORA)),
                    completado = getInt(getColumnIndexOrThrow(COLUMN_REC_COMPLETADO)) == 1,
                    postergado = getInt(getColumnIndexOrThrow(COLUMN_REC_POSTERGADO)),
                    alarmId = getInt(getColumnIndexOrThrow(COLUMN_REC_ALARM_ID)),
                    nombreMedicamento = getString(getColumnIndexOrThrow("nombre_medicamento")),
                    cantidadMedicamento = getInt(getColumnIndexOrThrow("cantidad_medicamento"))
                )
                recordatorios.add(recordatorio)
            }
        }

        cursor.close()
        db.close()
        return recordatorios
    }

    fun marcarRecordatorioCompletado(recordatorioId: Long): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_REC_COMPLETADO, 1)
        }

        val rowsUpdated = db.update(TABLE_RECORDATORIOS, values, "$COLUMN_REC_ID = ?", arrayOf(recordatorioId.toString()))
        db.close()
        return rowsUpdated > 0
    }

    fun marcarNotificacionEnviada(recordatorioId: Long): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_REC_NOTIFICACION_ENVIADA, 1)
        }

        val rowsUpdated = db.update(TABLE_RECORDATORIOS, values, "${COLUMN_REC_ID} = ?", arrayOf(recordatorioId.toString()))
        db.close()
        return rowsUpdated > 0
    }
    fun eliminarRecordatoriosPorMedicamento(medicamentoId: Long): Boolean {
        val db = writableDatabase
        val rowsDeleted = db.delete(TABLE_RECORDATORIOS, "$COLUMN_REC_MEDICAMENTO_ID = ?", arrayOf(medicamentoId.toString()))
        db.close()
        return rowsDeleted > 0
    }

    // === MÉTODOS PARA HISTORIAL ===

    fun insertarHistorialToma(medicamentoId: Long, fechaHoraProgramada: Long, fechaHoraTomada: Long?, estado: String, cantidadTomada: Int = 1): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_HIST_MEDICAMENTO_ID, medicamentoId)
            put(COLUMN_HIST_FECHA_HORA_PROGRAMADA, fechaHoraProgramada)
            put(COLUMN_HIST_FECHA_HORA_TOMADA, fechaHoraTomada)
            put(COLUMN_HIST_ESTADO, estado)
            put(COLUMN_HIST_CANTIDAD_TOMADA, cantidadTomada)
        }

        val id = db.insert(TABLE_HISTORIAL, null, values)
        db.close()
        return id
    }

    fun obtenerHistorialMedicamento(medicamentoId: Long): List<HistorialTomaData> {
        val historial = mutableListOf<HistorialTomaData>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_HISTORIAL,
            null,
            "$COLUMN_HIST_MEDICAMENTO_ID = ?",
            arrayOf(medicamentoId.toString()),
            null,
            null,
            "$COLUMN_HIST_FECHA_HORA_PROGRAMADA DESC"
        )

        with(cursor) {
            while (moveToNext()) {
                val toma = HistorialTomaData(
                    id = getLong(getColumnIndexOrThrow(COLUMN_HIST_ID)),
                    medicamentoId = getLong(getColumnIndexOrThrow(COLUMN_HIST_MEDICAMENTO_ID)),
                    fechaHoraProgramada = getLong(getColumnIndexOrThrow(COLUMN_HIST_FECHA_HORA_PROGRAMADA)),
                    fechaHoraTomada = if (isNull(getColumnIndexOrThrow(COLUMN_HIST_FECHA_HORA_TOMADA))) null else getLong(getColumnIndexOrThrow(COLUMN_HIST_FECHA_HORA_TOMADA)),
                    estado = getString(getColumnIndexOrThrow(COLUMN_HIST_ESTADO)),
                    cantidadTomada = getInt(getColumnIndexOrThrow(COLUMN_HIST_CANTIDAD_TOMADA))
                )
                historial.add(toma)
            }
        }

        cursor.close()
        db.close()
        return historial
    }
    /*
    fun obtenerRecordatoriosPendientes(): List<RecordatorioDataExtendido> {
        val recordatorios = mutableListOf<RecordatorioDataExtendido>()
        val db = readableDatabase

        val query = """
            SELECT r.*, m.$COLUMN_NOMBRE as nombre_medicamento, m.$COLUMN_CANTIDAD as cantidad_medicamento
            FROM $TABLE_RECORDATORIOS r
            JOIN $TABLE_MEDICAMENTOS m ON r.$COLUMN_REC_MEDICAMENTO_ID = m.$COLUMN_ID
            WHERE r.$COLUMN_REC_COMPLETADO = 0 AND m.$COLUMN_ACTIVO = 1
            ORDER BY r.$COLUMN_REC_FECHA_HORA ASC
        """.trimIndent()

        val cursor = db.rawQuery(query, null)

        with(cursor) {
            while (moveToNext()) {
                val recordatorio = RecordatorioDataExtendido(
                    id = getLong(getColumnIndexOrThrow(COLUMN_REC_ID)),
                    medicamentoId = getLong(getColumnIndexOrThrow(COLUMN_REC_MEDICAMENTO_ID)),
                    fechaHora = getLong(getColumnIndexOrThrow(COLUMN_REC_FECHA_HORA)),
                    fechaOriginal = if (isNull(getColumnIndexOrThrow(COLUMN_REC_FECHA_ORIGINAL)))
                        getLong(getColumnIndexOrThrow(COLUMN_REC_FECHA_HORA))
                    else getLong(getColumnIndexOrThrow(COLUMN_REC_FECHA_ORIGINAL)),
                    completado = getInt(getColumnIndexOrThrow(COLUMN_REC_COMPLETADO)) == 1,
                    postergado = getInt(getColumnIndexOrThrow(COLUMN_REC_POSTERGADO)),
                    numeroPostergaciones = getInt(getColumnIndexOrThrow(COLUMN_REC_NUMERO_POSTERGACIONES)),
                    notificacionEnviada = getInt(getColumnIndexOrThrow(COLUMN_REC_NOTIFICACION_ENVIADA)) == 1,
                    alarmId = getInt(getColumnIndexOrThrow(COLUMN_REC_ALARM_ID)),
                    nombreMedicamento = getString(getColumnIndexOrThrow("nombre_medicamento")),
                    cantidadMedicamento = getInt(getColumnIndexOrThrow("cantidad_medicamento"))
                )
                recordatorios.add(recordatorio)
            }
        }

        cursor.close()
        db.close()
        return recordatorios
    }*/

    fun obtenerRecordatoriosParaEmergencia(): List<RecordatorioDataExtendido> {
        val recordatorios = mutableListOf<RecordatorioDataExtendido>()
        val db = readableDatabase
        val tiempoLimite = System.currentTimeMillis() - (45 * 60 * 1000) // 45 minutos atrás

        val query = """
            SELECT r.*, m.$COLUMN_NOMBRE as nombre_medicamento, m.$COLUMN_CANTIDAD as cantidad_medicamento
            FROM $TABLE_RECORDATORIOS r
            JOIN $TABLE_MEDICAMENTOS m ON r.$COLUMN_REC_MEDICAMENTO_ID = m.$COLUMN_ID
            WHERE r.$COLUMN_REC_COMPLETADO = 0 
            AND m.$COLUMN_ACTIVO = 1 
            AND r.$COLUMN_REC_FECHA_ORIGINAL <= ?
            AND r.$COLUMN_REC_NOTIFICACION_ENVIADA = 0
            ORDER BY r.$COLUMN_REC_FECHA_ORIGINAL ASC
        """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(tiempoLimite.toString()))

        with(cursor) {
            while (moveToNext()) {
                val recordatorio = RecordatorioDataExtendido(
                    id = getLong(getColumnIndexOrThrow(COLUMN_REC_ID)),
                    medicamentoId = getLong(getColumnIndexOrThrow(COLUMN_REC_MEDICAMENTO_ID)),
                    fechaHora = getLong(getColumnIndexOrThrow(COLUMN_REC_FECHA_HORA)),
                    fechaOriginal = getLong(getColumnIndexOrThrow(COLUMN_REC_FECHA_ORIGINAL)),
                    completado = getInt(getColumnIndexOrThrow(COLUMN_REC_COMPLETADO)) == 1,
                    postergado = getInt(getColumnIndexOrThrow(COLUMN_REC_POSTERGADO)),
                    numeroPostergaciones = getInt(getColumnIndexOrThrow(COLUMN_REC_NUMERO_POSTERGACIONES)),
                    notificacionEnviada = getInt(getColumnIndexOrThrow(COLUMN_REC_NOTIFICACION_ENVIADA)) == 1,
                    alarmId = getInt(getColumnIndexOrThrow(COLUMN_REC_ALARM_ID)),
                    nombreMedicamento = getString(getColumnIndexOrThrow("nombre_medicamento")),
                    cantidadMedicamento = getInt(getColumnIndexOrThrow("cantidad_medicamento"))
                )
                recordatorios.add(recordatorio)
            }
        }

        cursor.close()
        db.close()
        return recordatorios
    }

}

// === CLASE DE DATOS EXTENDIDA ===
data class RecordatorioDataExtendido(
    val id: Long,
    val medicamentoId: Long,
    val fechaHora: Long,
    val fechaOriginal: Long, // Nueva: fecha original sin postergaciones
    val completado: Boolean,
    val postergado: Int,
    val numeroPostergaciones: Int, // Nueva: contador de postergaciones
    val notificacionEnviada: Boolean, // Nueva: si ya se envió notificación
    val alarmId: Int,
    val nombreMedicamento: String,
    val cantidadMedicamento: Int
)
// Clases de datos para los recordatorios
data class RecordatorioData(
    val id: Long,
    val medicamentoId: Long,
    val fechaHora: Long,
    val completado: Boolean,
    val postergado: Int,
    val alarmId: Int,
    val nombreMedicamento: String,
    val cantidadMedicamento: Int
)

data class HistorialTomaData(
    val id: Long,
    val medicamentoId: Long,
    val fechaHoraProgramada: Long,
    val fechaHoraTomada: Long?,
    val estado: String, // "tomado", "no_tomado", "postergado"
    val cantidadTomada: Int
)
data class MedicamentoPostergadoEvent(
    val medicamentoId: Long,
    val nombreMedicamento: String,
    val tiempoPostergado: Int, // en minutos
    val fechaHoraOriginal: Long,
    val numeroPostergacion: Int
)

data class MedicamentoNoTomadoEvent(
    val medicamentoId: Long,
    val nombreMedicamento: String,
    val fechaHoraOriginal: Long,
    val tiempoTranscurrido: Int // en minutos
)

data class ContactoEmergenciaEvent(
    val contactoNombre: String,
    val contactoTelefono: String,
    val tipoAccion: String // "MENSAJE" o "LLAMADA"
)


data class ConfiguracionTelegram(
    val botToken: String,
    val chatId: String
)
