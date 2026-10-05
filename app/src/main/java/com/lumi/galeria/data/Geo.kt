package com.lumi.galeria.data

import android.content.Context
import java.io.BufferedInputStream
import java.io.DataInputStream

class City(val name: String, val lat: Double, val lon: Double, val population: Int)

/**
 * Geografía incluida en la app (Natural Earth, dominio público): 1.251 ciudades con su
 * posición. Con ella se nombran los lugares de las fotos sin ninguna conexión.
 */
object Geo {
    /** De más a menos habitantes. */
    var cities: List<City> = emptyList()
        private set

    fun load(context: Context) {
        if (cities.isNotEmpty()) return
        cities = runCatching {
            DataInputStream(BufferedInputStream(context.assets.open("places.bin"))).use { input ->
                List(input.readInt()) {
                    val name = input.readUTF()
                    val lon = input.readFloat().toDouble()
                    val lat = input.readFloat().toDouble()
                    City(name, lat, lon, input.readInt())
                }
            }
        }.getOrDefault(emptyList())
    }
}
