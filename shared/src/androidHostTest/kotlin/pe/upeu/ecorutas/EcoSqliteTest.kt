package pe.upeu.ecorutas

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import pe.upeu.ecorutas.db.EcoDb
import kotlin.test.Test
import kotlin.test.assertEquals

class EcoSqliteTest {
    @Test fun pesajePendienteSeSubeAntesDeDescargar() = runBlocking {
        val eventos = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/api/v1/pesajes" -> {
                    eventos += "subir"
                    respond("""{"id":31,"rutaId":1,"puntoId":2,"operario":"operario1","material":"PAPEL","pesoBruto":10.0,"tara":2.0,"pesoNeto":8.0,"observaciones":null,"fechaHora":"2026-10-28T10:00:00","estado":"ACTIVO"}""", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                path == "/api/v1/puntos" -> {
                    eventos += "puntos"
                    respond("""{"contenido":[{"id":2,"nombre":"Norte","direccion":"Av. 1","zona":"NORTE"}],"totalElementos":1,"totalPaginas":1,"pagina":0,"tamano":100}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                path == "/api/v1/rutas/hoy" -> {
                    eventos += "ruta"
                    respond("""{"id":1,"fecha":"2026-10-28","estado":"ABIERTA","operario":"operario1","puntos":[{"puntoId":2,"nombre":"Norte","direccion":"Av. 1","zona":"NORTE","ordenVisita":1,"motivoOmision":null}]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> {
                    eventos += "perfil"
                    respond("""{"id":1,"usuario":"operario1","roles":["OPERARIO"],"privilegios":["PESAJE_CREAR"]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EcoDb.Schema.create(driver)
        val tokens = object : TokenStore {
            override fun guardar(token: String, usuario: String) {}
            override fun token() = "token-de-prueba"
            override fun usuario() = "operario1"
            override fun limpiar() {}
        }
        val servicios = EcoServices(client, driver, tokens) { "http://eco.test" }
        val id = servicios.registrarLocal(1, 2, "PAPEL", 10.0, 2.0, null)
        assertEquals(-1, id)
        assertEquals(1, EcoDb(driver).ecoQueries.pendientes().executeAsList().size)
        servicios.sincronizar()
        assertEquals(listOf("subir", "puntos", "ruta", "perfil"), eventos)
        assertEquals(0, EcoDb(driver).ecoQueries.pendientes().executeAsList().size)
        assertEquals(31, EcoDb(driver).ecoQueries.listarPesajes().executeAsOne().id)
        client.close(); driver.close()
    }
}
