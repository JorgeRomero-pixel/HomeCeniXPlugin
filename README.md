# HomeCeniXPlugin

Plugin para servidores Paper de Minecraft Java Edition que permite a cada jugador guardar casas personales y teletransportarse a ellas. Las casas se guardan en Firebase Firestore y se mantienen en memoria para no consultar la base de datos en cada comando.

## Comandos

| Comando | Descripción |
|---|---|
| `/sethome <nombre>` | Guarda tu posición actual como casa. Si el nombre ya existe, la reemplaza. |
| `/home <nombre>` | Te teletransporta a una de tus casas. Tab sugiere solo tus casas. |

Cualquier jugador puede usarlos: no requieren permisos.

## Reglas

- **Privacidad:** cada jugador solo accede a sus propias casas. Ningún comando permite elegir a otro jugador.
- **Límite:** 5 casas por jugador. Al llegar al límite, solo se puede reemplazar una casa existente.
- **Nombres:** de 1 a 32 caracteres entre letras sin acentos, números, `_` y `-`. No distinguen mayúsculas: `Base` y `base` son la misma casa.

## Requisitos

- Paper 26.3
- Java 25
- Un proyecto de Firebase con Firestore

## Instalación

1. En [Firebase Console](https://console.firebase.google.com), crea una base de datos Firestore `(default)` en modo producción.
2. Ve a `Configuración del proyecto > Cuentas de servicio` y genera una clave privada.
3. Compila el plugin (ver [Compilar](#compilar)) y copia `build/libs/HomeCeniXPlugin-1.0.0.jar` a la carpeta `plugins` del servidor.
4. Guarda la clave como `plugins/HomeCeniXPlugin/firebase-service-account.json`.
5. Arranca el servidor. Si falta la clave o no se puede usar, el plugin lo indica en consola y se desactiva.

## Seguridad

- **Nunca subas ni compartas la clave de la cuenta de servicio:** da acceso de administrador al proyecto de Firebase. El `.gitignore` excluye los archivos `*service-account*.json`. Si se filtra, revócala en Google Cloud y genera otra.
- **Usa `online-mode=true`:** en modo offline, el UUID de un jugador depende de su nombre, y cualquiera que entre con ese nombre podría usar sus casas.
- **Mantén las reglas de Firestore cerradas** (`allow read, write: if false;`). El plugin usa el SDK de administrador, al que no le afectan esas reglas.

## Cómo funciona

Las casas se guardan en `players/{uuid}/homes/{nombre}`.

| Situación | Comportamiento | Acceso a Firestore |
|---|---|---|
| El jugador entra sin datos en memoria | Carga todas sus casas | Lectura |
| `/home` o sugerencias de Tab | Usa la memoria | Ninguno |
| `/sethome` | Escribe en Firestore y, si tiene éxito, actualiza la memoria | Escritura |
| El jugador se desconecta | Inicia una cuenta atrás de 10 minutos | Ninguno |
| Vuelve antes de 10 minutos | Cancela la cuenta atrás y reutiliza la memoria | Ninguno |
| Vuelve después de 10 minutos | Carga de nuevo sus casas | Lectura |

### Limitaciones

- La caché asume que un solo servidor usa la base de datos. Con varios servidores, uno no verá las casas nuevas del otro hasta recargar sus datos.
- Los cambios hechos a mano en Firestore no se ven hasta que caduca la caché de ese jugador o se reinicia el servidor.
- No existe un comando para borrar casas.

## Compilar

```powershell
.\gradlew build
```

El resultado es `build/libs/HomeCeniXPlugin-1.0.0.jar`. Pesa unos 67 MB porque incluye el SDK de Firebase, con sus librerías reubicadas para evitar conflictos con las que trae Paper.

## Tests

`.\gradlew build` ejecuta los tests de caché y de nombres. Los de `HomeStoreEmulatorTest` se omiten salvo que exista `FIRESTORE_EMULATOR_HOST`, y nunca usan una base de datos real.

Para ejecutarlos, arranca el emulador en una terminal:

```powershell
firebase emulators:start --only firestore --project demo-homecenix
```

Y en otra terminal:

```powershell
$env:FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080"
.\gradlew test
```

## Estructura

| Archivo en `src/main/java/com/jorge/homecenix/` | Responsabilidad |
|---|---|
| `HomeCeniXPlugin.java` | Arranque del plugin y conexión de sus piezas |
| `HomeCommand.java` | `/home` y sus sugerencias |
| `SetHomeCommand.java` | `/sethome` |
| `HomeCache.java` | Caché, expiración y límite de casas |
| `HomeStore.java` | Lectura y escritura en Firestore |
| `HomeDatabase.java` | Interfaz de acceso a la base de datos |
| `ConnectionListener.java` | Entrada y salida de jugadores, y limpieza de sugerencias |
| `HomeName.java` | Validación de nombres |
| `Home.java` | Datos de una casa |
