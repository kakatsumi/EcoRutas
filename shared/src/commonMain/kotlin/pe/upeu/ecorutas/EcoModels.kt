package pe.upeu.ecorutas

import kotlinx.serialization.Serializable

@Serializable data class LoginRequest(val usuario: String, val contrasena: String)
@Serializable data class LoginResponse(val token: String, val tipo: String, val expiraEn: String)
@Serializable data class PerfilDto(val id: Long, val usuario: String, val roles: Set<String>, val privilegios: Set<String>)
@Serializable data class PuntoDto(val id: Long, val nombre: String, val direccion: String, val zona: String)
@Serializable data class PaginaPuntosDto(val contenido: List<PuntoDto>, val totalElementos: Long, val totalPaginas: Int, val pagina: Int, val tamano: Int)
@Serializable data class RutaPuntoDto(val puntoId: Long, val nombre: String, val direccion: String, val zona: String, val ordenVisita: Int, val motivoOmision: String? = null)
@Serializable data class RutaDto(val id: Long, val fecha: String, val estado: String, val operario: String, val puntos: List<RutaPuntoDto>)
@Serializable data class PesajeRequest(val rutaId: Long, val puntoId: Long, val material: String, val pesoBruto: Double, val tara: Double, val observaciones: String? = null)
@Serializable data class PesajeDto(val id: Long, val rutaId: Long, val puntoId: Long, val operario: String, val material: String, val pesoBruto: Double, val tara: Double, val pesoNeto: Double, val observaciones: String? = null, val fechaHora: String, val estado: String)
class ApiError(val codigo: Int, detalle: String) : Exception("HTTP $codigo: $detalle")
interface TokenStore { fun guardar(token: String, usuario: String); fun token(): String?; fun usuario(): String?; fun limpiar() }
