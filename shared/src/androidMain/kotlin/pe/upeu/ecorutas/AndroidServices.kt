package pe.upeu.ecorutas

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun crearCliente(): HttpClient = HttpClient(OkHttp) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    expectSuccess = false
}
fun crearServicios(context: Context, client: HttpClient, url: () -> String): EcoServices =
    EcoServices(client, AndroidSqliteDriver(pe.upeu.ecorutas.db.EcoDb.Schema, context, "ecorutas.db"),
        AndroidTokenStore(context), url)

private class AndroidTokenStore(context: Context) : TokenStore {
    private val prefs = context.getSharedPreferences("sesion_cifrada", Context.MODE_PRIVATE)
    private val alias = "ecorutas_jwt_aes"
    private val key: SecretKey
        get() {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(alias, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
            }.generateKey()
        }
    override fun guardar(token: String, usuario: String) {
        check(prefs.edit().putString("jwt", cifrar(token)).putString("usuario", cifrar(usuario)).commit())
    }
    override fun token(): String? = prefs.getString("jwt", null)?.let(::descifrar)
    override fun usuario(): String? = prefs.getString("usuario", null)?.let(::descifrar)
    override fun limpiar() { prefs.edit().clear().commit() }
    private fun cifrar(valor: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(valor.toByteArray()), Base64.NO_WRAP)
    }
    private fun descifrar(valor: String): String {
        val partes = valor.split(':', limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(partes[0], Base64.NO_WRAP)))
        }
        return cipher.doFinal(Base64.decode(partes[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }
}
