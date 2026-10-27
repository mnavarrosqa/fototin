package app.fototin.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdaterTest {

    @Test
    fun `el numero de version sale del final de la etiqueta`() {
        assertEquals(43, Updater.versionCodeOf("v0.2.43"))
        assertEquals(7, Updater.versionCodeOf("v1.0.7"))
        assertNull(Updater.versionCodeOf("sin-numero"))
    }

    @Test
    fun `las novedades son los renglones con viñeta`() {
        val body = """
            ## Novedades

            - Guardar como álbum las fotos recién traídas
            * Fotos desde Google Fotos
            -

            Descargá `fototin.apk` desde el teléfono.
        """.trimIndent()
        assertEquals(
            listOf("Guardar como álbum las fotos recién traídas", "Fotos desde Google Fotos"),
            Updater.parseNotes(body),
        )
    }
}
