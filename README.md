# EcoRutas

Aplicación Android Kotlin Multiplatform (destino Android) y Compose Multiplatform para EcoRecicla.

## Arquitectura

`androidApp` contiene la actividad y la interfaz Compose. `shared` contiene DTO, cliente Ktor, sincronización y SQLDelight. Koin crea un único `HttpClient`. La pantalla de ruta y pesajes observa exclusivamente consultas SQLDelight mediante `Flow`.

El JWT se cifra con una clave AES-GCM de Android Keystore y se guarda cifrado en preferencias. La base SQLite guarda ruta, puntos y pesajes. Un pesaje nuevo recibe un ID negativo y estado `PENDIENTE`; al sincronizar, primero se suben todos los pendientes en orden de creación y solo después se descargan puntos, ruta y perfil. Ante un error HTTP, el pendiente permanece local y se muestra el error.

## Requisitos

Java 17, Android SDK con plataforma 36 y Gradle Wrapper incluido. El backend EcoAPI debe estar disponible con PostgreSQL. Para el emulador, la URL inicial es `http://10.0.2.2:8080`. En un teléfono físico, escribe en la pantalla de login `http://IP_LOCAL_DEL_EQUIPO:8080` (misma red Wi-Fi; el backend debe escuchar en la interfaz local). HTTP local se permite para esta APK debug.

## Compilación

```bash
printf 'sdk.dir=%s/Android/Sdk\n' "$HOME" > local.properties
./gradlew :androidApp:assembleDebug
```

APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. Instalar con `adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Uso y pruebas

Inicia EcoAPI; entra con `operario1` y la contraseña de demostración. La app sincroniza la ruta y los puntos. Desconecta internet, registra un pesaje en un punto y comprueba su ID negativo en **Sync**. Restaura la conexión y pulsa **Sincronizar ahora**; el ID se reemplaza por el del servidor y aparece el peso neto calculado por EcoAPI. **Perfil** muestra el usuario y los privilegios descargados. **Cerrar sesión** elimina el token cifrado.

Ejecutar pruebas de la lógica compartida: `./gradlew :shared:testAndroidHostTest`.

## API

Se usan los contratos reales de EcoAPI: `/api/v1/auth/login`, `/api/v1/usuarios/me`, `/api/v1/puntos`, `/api/v1/rutas/hoy` y `/api/v1/pesajes`. Las correcciones, anulaciones, omisiones y cierre de ruta permanecen en el backend para su siguiente integración en la interfaz móvil.
