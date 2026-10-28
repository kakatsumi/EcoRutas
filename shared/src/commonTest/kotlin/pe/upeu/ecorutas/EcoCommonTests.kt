package pe.upeu.ecorutas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EcoCommonTests {
    @Test fun mapeoDtoReal() = runBlocking {
        val client = HttpClient(MockEngine { respond("""{"id":7,"fecha":"2026-10-28","estado":"ABIERTA","operario":"operario1","puntos":[{"puntoId":2,"nombre":"Norte","direccion":"Av. 1","zona":"NORTE","ordenVisita":1,"motivoOmision":null}]}""", HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json")) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val ruta = client.get("http://eco.test/api/v1/rutas/hoy").body<RutaDto>()
        assertEquals(7, ruta.id)
        assertEquals(2, ruta.puntos.single().puntoId)
        client.close()
    }
    @Test fun errorHttpConMockEngine() = runBlocking {
        val client = HttpClient(MockEngine { respond("ruta cerrada", HttpStatusCode.Conflict) })
        val response = client.get("http://eco.test/api/v1/rutas/hoy")
        val ex = assertFailsWith<ApiError> { verificarHttp(response.status.value, "ruta cerrada") }
        assertEquals(409, ex.codigo)
        client.close()
    }
    @Test fun creacionOfflineUsaIdsNegativosYValidaPesos() {
        assertEquals(-1, siguienteIdLocal(emptyList()))
        assertEquals(-3, siguienteIdLocal(listOf(-1, -2, 5)))
        validarPesajeLocal("PAPEL", 10.0, 2.0)
        assertFailsWith<IllegalArgumentException> { validarPesajeLocal("PAPEL", 1.0, 2.0) }
    }
    @Test fun sincronizacionSubeAntesDeDescargar() = runBlocking {
        val acciones = mutableListOf<String>()
        val resultado = sincronizarEnOrden(subir = { acciones += "subir"; 1 }, descargar = { acciones += "descargar"; it })
        assertEquals(1, resultado)
        assertEquals(listOf("subir", "descargar"), acciones)
    }
}
