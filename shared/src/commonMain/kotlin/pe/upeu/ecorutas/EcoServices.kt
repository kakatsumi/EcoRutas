package pe.upeu.ecorutas

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import pe.upeu.ecorutas.db.EcoDb
import pe.upeu.ecorutas.db.Pesaje_local
import pe.upeu.ecorutas.db.Punto_local
import pe.upeu.ecorutas.db.Ruta_local

class EcoServices(
    private val client: HttpClient,
    driver: SqlDriver,
    private val tokens: TokenStore,
    private val baseUrl: () -> String
) {
    private val db = EcoDb(driver)
    val puntos: Flow<List<Punto_local>> = db.ecoQueries.listarPuntos().asFlow().mapToList(Dispatchers.Default)
    val rutas: Flow<List<Ruta_local>> = db.ecoQueries.listarRutas().asFlow().mapToList(Dispatchers.Default)
    val pesajes: Flow<List<Pesaje_local>> = db.ecoQueries.listarPesajes().asFlow().mapToList(Dispatchers.Default)
    var perfil: PerfilDto? = null
        private set

    fun usuario() = tokens.usuario()
    fun sesionActiva() = tokens.token() != null
    fun salir() { tokens.limpiar(); perfil = null }

    suspend fun login(usuario: String, contrasena: String) {
        val response = client.post(url("/api/v1/auth/login")) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(usuario, contrasena))
        }
        verificar(response.status.value, response.bodyAsText())
        val dto = Json.decodeFromString<LoginResponse>(response.bodyAsText())
        tokens.guardar(dto.token, usuario)
    }

    suspend fun registrarLocal(rutaId: Long, puntoId: Long, material: String, bruto: Double, tara: Double, observaciones: String?) : Long {
        validarPesajeLocal(material, bruto, tara)
        val actual = db.ecoQueries.listarPesajes().executeAsList().filter { it.id < 0 }.minOfOrNull { it.id } ?: 0L
        val id = siguienteIdLocal(listOf(actual))
        db.ecoQueries.insertarPesaje(id, rutaId, puntoId, material.trim(), bruto.toString(), tara.toString(), null, observaciones, null, "PENDIENTE", 1L)
        return id
    }

    suspend fun sincronizar(): String {
        val token = tokens.token() ?: throw ApiError(401, "Inicia sesión")
        var enviados = 0
        // Primero se publican los registros locales. Si falla uno, no se descarga ni se pierde ese registro.
        return sincronizarEnOrden(subir = {
        for (pendiente in db.ecoQueries.pendientes().executeAsList()) {
            val response = client.post(url("/api/v1/pesajes")) {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(PesajeRequest(pendiente.ruta_id, pendiente.punto_id, pendiente.material,
                    pendiente.peso_bruto.toDouble(), pendiente.tara.toDouble(), pendiente.observaciones))
            }
            val texto = response.bodyAsText()
            verificar(response.status.value, texto)
            val guardado = Json.decodeFromString<PesajeDto>(texto)
            db.transaction {
                db.ecoQueries.borrarPesaje(pendiente.id)
                db.ecoQueries.insertarPesaje(guardado.id, guardado.rutaId, guardado.puntoId, guardado.material,
                    guardado.pesoBruto.toString(), guardado.tara.toString(), guardado.pesoNeto.toString(),
                    guardado.observaciones, guardado.fechaHora, guardado.estado, 0L)
            }
            enviados++
        }
        enviados
        }, descargar = { subidos ->
        val paginaResponse = client.get(url("/api/v1/puntos?pagina=0&tamano=100")) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val paginaTexto = paginaResponse.bodyAsText()
        verificar(paginaResponse.status.value, paginaTexto)
        val pagina = Json.decodeFromString<PaginaPuntosDto>(paginaTexto)
        val rutaResponse = client.get(url("/api/v1/rutas/hoy")) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val rutaTexto = rutaResponse.bodyAsText()
        verificar(rutaResponse.status.value, rutaTexto)
        val ruta = Json.decodeFromString<RutaDto>(rutaTexto)
        val perfilResponse = client.get(url("/api/v1/usuarios/me")) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val perfilTexto = perfilResponse.bodyAsText()
        verificar(perfilResponse.status.value, perfilTexto)
        perfil = Json.decodeFromString<PerfilDto>(perfilTexto)
        db.transaction {
            db.ecoQueries.limpiarPuntos()
            db.ecoQueries.limpiarRutas()
            pagina.contenido.forEach { db.ecoQueries.insertarPunto(it.id, it.nombre, it.direccion, it.zona, 999L, null) }
            ruta.puntos.forEach { db.ecoQueries.insertarPunto(it.puntoId, it.nombre, it.direccion, it.zona, it.ordenVisita.toLong(), it.motivoOmision) }
            db.ecoQueries.insertarRuta(ruta.id, ruta.fecha, ruta.estado, ruta.operario)
        }
        "Sincronizado: $subidos pesajes subidos; ruta y puntos descargados"
        })
    }

    private fun url(path: String): String = baseUrl().trimEnd('/') + path
    private fun verificar(codigo: Int, texto: String) {
        verificarHttp(codigo, texto)
    }
}
