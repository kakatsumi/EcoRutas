package pe.upeu.ecorutas

import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainActivity : ComponentActivity(), KoinComponent {
    private val servicios: EcoServices by inject()
    private var callback: ConnectivityManager.NetworkCallback? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("backend", MODE_PRIVATE)
        if (GlobalContext.getOrNull() == null) startKoin {
            modules(module {
                single { crearCliente() } // Único HttpClient compartido
                single { crearServicios(applicationContext, get(), { prefs.getString("url", "http://10.0.2.2:8080")!! }) }
            })
        }
        setContent { EcoApp(servicios, prefs.getString("url", "http://10.0.2.2:8080")!!, { prefs.edit().putString("url", it).apply() }) }
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (servicios.sesionActiva()) kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    runCatching { servicios.sincronizar() }
                }
            }
        }.also { manager.registerDefaultNetworkCallback(it) }
    }
    override fun onDestroy() {
        callback?.let { (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) }
        super.onDestroy()
    }
}

@Composable
private fun EcoApp(servicios: EcoServices, urlInicial: String, guardarUrl: (String) -> Unit) {
    var sesion by remember { mutableStateOf(servicios.sesionActiva()) }
    var pantalla by remember { mutableStateOf("ruta") }
    var usuario by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(urlInicial) }
    var puntoSeleccionado by remember { mutableStateOf<pe.upeu.ecorutas.db.Punto_local?>(null) }
    var bruto by remember { mutableStateOf("") }
    var tara by remember { mutableStateOf("") }
    var material by remember { mutableStateOf("") }
    var observaciones by remember { mutableStateOf("") }
    val puntos by servicios.puntos.collectAsState(initial = emptyList())
    val rutas by servicios.rutas.collectAsState(initial = emptyList())
    val pesajes by servicios.pesajes.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("EcoRutas", style = MaterialTheme.typography.headlineMedium)
            if (!sesion) {
                Text("Servidor EcoAPI")
                OutlinedTextField(url, { url = it; guardarUrl(it.trimEnd('/')) }, label = { Text("URL: emulador 10.0.2.2; teléfono IP local") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(contrasena, { contrasena = it }, label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { scope.launch {
                    runCatching { servicios.login(usuario, contrasena); sesion = true; mensaje = servicios.sincronizar() }
                        .onFailure { mensaje = it.message ?: "Error de conexión" }
                } }) { Text("Ingresar") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pantalla = "ruta" }) { Text("Ruta") }
                    TextButton(onClick = { pantalla = "sync" }) { Text("Sync") }
                    TextButton(onClick = { pantalla = "perfil" }) { Text("Perfil") }
                }
                when (pantalla) {
                    "ruta" -> {
                        Text("Ruta de hoy: ${rutas.firstOrNull()?.fecha ?: "sin datos locales"} · ${rutas.firstOrNull()?.estado ?: ""}")
                        LazyColumn {
                            items(puntos.filter { it.orden_visita < 999 }) { punto ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { puntoSeleccionado = punto; pantalla = "detalle" }) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("${punto.orden_visita}. ${punto.nombre}")
                                        Text("${punto.direccion} · ${punto.zona}")
                                        punto.motivo_omision?.let { Text("Omitido: $it") }
                                    }
                                }
                            }
                        }
                    }
                    "detalle" -> {
                        val punto = puntoSeleccionado
                        Text(punto?.nombre ?: "Punto")
                        Text("${punto?.direccion.orEmpty()} · ${punto?.zona.orEmpty()}")
                        OutlinedTextField(material, { material = it }, label = { Text("Material") })
                        OutlinedTextField(bruto, { bruto = it }, label = { Text("Peso bruto") })
                        OutlinedTextField(tara, { tara = it }, label = { Text("Tara") })
                        OutlinedTextField(observaciones, { observaciones = it }, label = { Text("Observaciones") })
                        Text("El peso neto lo calcula EcoAPI al sincronizar")
                        Button(onClick = { scope.launch {
                            val ruta = rutas.firstOrNull()
                            val b = bruto.toDoubleOrNull(); val t = tara.toDoubleOrNull()
                            if (ruta == null || punto == null || b == null || t == null) mensaje = "Faltan ruta o pesos válidos"
                            else runCatching { servicios.registrarLocal(ruta.id, punto.id, material, b, t, observaciones) }
                                .onSuccess { mensaje = "Pesaje guardado localmente ($it)"; pantalla = "sync" }
                                .onFailure { mensaje = it.message ?: "Datos inválidos" }
                        } }) { Text("Guardar pesaje offline") }
                        TextButton(onClick = { pantalla = "ruta" }) { Text("Volver") }
                    }
                    "sync" -> {
                        Text("Pendientes: ${pesajes.count { it.pendiente == 1L }}")
                        Button(onClick = { scope.launch { runCatching { servicios.sincronizar() }
                            .onSuccess { mensaje = it }.onFailure { mensaje = it.message ?: "Sin conexión" } } }) { Text("Sincronizar ahora") }
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            pesajes.forEach { Text("#${it.id}: ${it.material} · ${it.estado} · neto ${it.peso_neto ?: "pendiente"}") }
                        }
                    }
                    "perfil" -> {
                        Text("Usuario: ${servicios.perfil?.usuario ?: servicios.usuario().orEmpty()}")
                        Text("Roles: ${servicios.perfil?.roles?.joinToString() ?: "pendiente de sincronizar"}")
                        Text("Privilegios: ${servicios.perfil?.privilegios?.joinToString() ?: "pendiente de sincronizar"}")
                        Button(onClick = { servicios.salir(); sesion = false; pantalla = "ruta" }) { Text("Cerrar sesión") }
                    }
                }
            }
            if (mensaje.isNotBlank()) Text(mensaje, color = MaterialTheme.colorScheme.primary)
        }
    }
}
