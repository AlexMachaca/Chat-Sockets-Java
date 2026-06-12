# Socket Chat con MongoDB Atlas

Chat en tiempo real usando Java Sockets, JavaFX y MongoDB Atlas como base de datos.

## Requisitos

- Java 17
- Maven (incluido en NetBeans 17 o instalar por separado)
- Cuenta en [MongoDB Atlas](https://www.mongodb.com/atlas)

## Configuracion de MongoDB Atlas

### 1. Whitelist de IP

Cada vez que cambies de red debes verificar esto:

1. Entra a **MongoDB Atlas → Network Access**
2. Haz clic en **"+ Add IP Address"**
3. Selecciona **"Allow Access from Anywhere"** (`0.0.0.0/0`) para desarrollo
4. Guarda y espera ~1 minuto

### 2. Archivo de configuracion

Copia el archivo de ejemplo y completa tus credenciales:

```
src/main/resources/config.properties.example  →  src/main/resources/config.properties
```

Edita `config.properties`:

```properties
mongodb.connection_string=mongodb+srv://<usuario>:<password>@<cluster>.mongodb.net/?appName=<AppName>
mongodb.database_name=chat_app
```

> `config.properties` esta en `.gitignore` y nunca se sube al repositorio.

### 3. Ver las colecciones

Las colecciones `messages` y `users` se crean automaticamente la primera vez que alguien se conecta al chat.

Para verlas: **Atlas → Cluster0 → Browse Collections → chat_app**

---

## Correr el proyecto

Abre **dos terminales** en la raiz del proyecto.

### Variable de Maven (pegar en cada terminal)

```powershell
$mvn = "C:\Program Files\NetBeans-17\netbeans\java\maven\bin\mvn.cmd"
```

> Si tienes Maven instalado globalmente puedes usar `mvn` directamente.

---

### Terminal 1 — Servidor

```powershell
& $mvn compile exec:exec
```

El servidor esta listo cuando ves:

```
Conexion exitosa a MongoDB Atlas.
Estado de todos los usuarios reseteado a offline.
Servidor escuchando en el puerto 5000. Esperando conexiones...
```

---

### Terminal 2 — Cliente (GUI)

```powershell
& $mvn javafx:run
```

Se abrira una ventana pidiendo tu nombre de usuario.

Para conectar **multiples clientes**, abre terminales adicionales y repite el mismo comando.

---

## Uso del chat

| Accion | Comando |
|--------|---------|
| Mensaje publico | Escribe y presiona Enter |
| Mensaje privado | `@usuario mensaje` |
| Ver usuarios online | Panel lateral derecho |

### Reglas de username

- Solo letras, numeros y guion bajo `_`
- Maximo 20 caracteres
- No se permite el mismo username dos veces simultaneamente

---

## Estructura del proyecto

```
src/
  main/
    java/
      ChatServer.java       # Servidor de sockets
      ChatClientGUI.java    # Cliente con interfaz JavaFX
      ChatClient.java       # Cliente consola (para pruebas)
      DatabaseManager.java  # Conexion y operaciones MongoDB
    resources/
      config.properties         # Credenciales (NO subir al repo)
      config.properties.example # Plantilla publica
      style.css                 # Estilos del cliente GUI
```

## Base de datos (MongoDB Atlas)

### Coleccion `messages`

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| sender | String | Usuario que envia |
| receiver | String | `"ALL"` para publico o username para privado |
| content | String | Contenido del mensaje |
| type | String | `"PUBLIC"` o `"PRIVATE"` |
| timestamp | Date | Fecha y hora del mensaje |

### Coleccion `users`

| Campo | Tipo | Descripcion |
|-------|------|-------------|
| username | String | Nombre unico del usuario |
| status | String | `"online"` u `"offline"` |
| first_connected | Date | Primera vez que se conecto |
| last_seen | Date | Ultima conexion |

### Indices creados automaticamente

- `messages.timestamp` — acelera carga del historial
- `messages.{sender, receiver}` — acelera historial privado
- `users.username` — indice unico
