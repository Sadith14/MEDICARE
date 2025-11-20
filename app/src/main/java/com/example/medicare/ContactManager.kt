package com.example.medicare

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import java.util.Locale

class ContactManager(private val context: Context) {

    fun buscarContactoPorNombre(nombreBuscado: String): ContactoInfo? {
        return try {
            val resolver = context.contentResolver
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI

            val proyeccion = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            )

            val cursor = resolver.query(uri, proyeccion, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")

            var mejorCoincidencia: ContactoInfo? = null
            var mejorPuntuacion = 0

            cursor?.use {
                while (it.moveToNext()) {
                    val nombre = it.getString(0) ?: continue
                    val numero = it.getString(1) ?: continue
                    val tipo = it.getInt(2)

                    val puntuacion = calcularSimilitud(nombreBuscado, nombre)

                    if (puntuacion > mejorPuntuacion && puntuacion >= 60) { // 60% mínimo de similitud
                        mejorPuntuacion = puntuacion
                        mejorCoincidencia = ContactoInfo(
                            nombre = nombre,
                            numero = limpiarNumero(numero),
                            tipo = obtenerTipoTelefono(tipo),
                            similitud = puntuacion
                        )
                    }
                }
            }

            mejorCoincidencia
        } catch (e: Exception) {
            Log.e("ContactManager", "Error buscando contacto: ${e.message}")
            null
        }
    }

    fun obtenerContactosEmergencia(): List<ContactoInfo> {
        val contactosEmergencia = mutableListOf<ContactoInfo>()

        // Números de emergencia predefinidos
        contactosEmergencia.add(ContactoInfo("Emergencias", "911", "Emergencia", 100))
        contactosEmergencia.add(ContactoInfo("Bomberos", "116", "Emergencia", 100))
        contactosEmergencia.add(ContactoInfo("Policía", "105", "Emergencia", 100))

        // Buscar contactos marcados como favoritos o emergencia
        try {
            val resolver = context.contentResolver
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val proyeccion = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.STARRED
            )

            val seleccion = "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1"
            val cursor = resolver.query(uri, proyeccion, seleccion, null, null)

            cursor?.use {
                while (it.moveToNext()) {
                    val nombre = it.getString(0)
                    val numero = it.getString(1)

                    if (!nombre.isNullOrEmpty() && !numero.isNullOrEmpty()) {
                        contactosEmergencia.add(
                            ContactoInfo(nombre, limpiarNumero(numero), "Favorito", 90)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ContactManager", "Error obteniendo contactos de emergencia: ${e.message}")
        }

        return contactosEmergencia
    }

    private fun calcularSimilitud(texto1: String, texto2: String): Int {
        val t1 = normalizarTexto(texto1)
        val t2 = normalizarTexto(texto2)

        // Coincidencia exacta
        if (t1 == t2) return 100

        // Contiene la palabra completa
        if (t2.contains(t1) || t1.contains(t2)) return 85

        // Similitud por palabras
        val palabras1 = t1.split("\\s+".toRegex())
        val palabras2 = t2.split("\\s+".toRegex())

        var coincidencias = 0
        for (palabra1 in palabras1) {
            for (palabra2 in palabras2) {
                if (palabra1.length >= 3 && palabra2.contains(palabra1)) {
                    coincidencias++
                    break
                }
            }
        }

        return (coincidencias * 100) / maxOf(palabras1.size, palabras2.size)
    }

    private fun normalizarTexto(texto: String): String {
        return texto.lowercase(locale = Locale.ROOT)
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
            .trim()
    }

    private fun limpiarNumero(numero: String): String {
        return numero.replace(Regex("[^0-9+]"), "")
    }

    private fun obtenerTipoTelefono(tipo: Int): String {
        return when (tipo) {
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Casa"
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Móvil"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Trabajo"
            ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Otro"
            else -> "Desconocido"
        }
    }
}