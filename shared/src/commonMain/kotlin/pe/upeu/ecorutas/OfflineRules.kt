package pe.upeu.ecorutas

fun siguienteIdLocal(ids: List<Long>): Long = minOf(-1L, (ids.filter { it < 0 }.minOrNull() ?: 0L) - 1)
fun validarPesajeLocal(material: String, bruto: Double, tara: Double) {
    require(material.isNotBlank() && bruto.isFinite() && tara.isFinite() && bruto > tara && tara >= 0) {
        "El material y los pesos deben ser válidos"
    }
}
fun verificarHttp(codigo: Int, texto: String) {
    if (codigo !in 200..299) throw ApiError(codigo, texto.take(300))
}
suspend fun <T> sincronizarEnOrden(subir: suspend () -> Int, descargar: suspend (Int) -> T): T {
    val enviados = subir()
    return descargar(enviados)
}
