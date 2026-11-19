[README.md](https://github.com/user-attachments/files/35065460/README.md)
# Amp39

**Amp39** es un reproductor de música local para Android, desarrollado en Java, enfocado en ofrecer una experiencia de reproducción intuitiva, personalizable y completa. Permite gestionar bibliotecas musicales, crear listas de reproducción, personalizar metadatos, visualizar letras sincronizadas y controlar la reproducción desde diferentes interfaces del dispositivo.

El proyecto busca combinar funcionalidad, personalización y estabilidad en una aplicación que permita disfrutar de la música almacenada localmente.

## Características principales

### Reproducción de música
- Reproducción de archivos de audio almacenados en el dispositivo.
- Controles de reproducción: reproducir, pausar, anterior y siguiente.
- Modos de reproducción aleatoria y repetición.
- Cola de reproducción interactiva.
- Recuperación de la última sesión de reproducción.
- Controles desde la notificación del sistema.

### Biblioteca musical
- Exploración de canciones y álbumes.
- Gestión de canciones favoritas.
- Creación y administración de listas de reproducción.
- Organización y búsqueda de canciones.
- Selección de carpetas musicales mediante el selector del sistema.

### Personalización
- Seis temas visuales:
  - Turquesa.
  - Negro AMOLED.
  - Rosa.
  - Violeta.
  - Azul.
  - Dinámico (colores del sistema).
- Ecualizador de audio.
- Refuerzo de graves.
- Preajustes de ecualización disponibles en el dispositivo.

### Letras y metadatos
- Compatibilidad con archivos de letras `.lrc`.
- Sincronización de letras con la reproducción.
- Resaltado de la línea actual.
- Edición y almacenamiento de letras.
- Edición de títulos y carátulas de canciones.
- Gestión de metadatos de archivos de audio.

### Herramientas adicionales
- Temporizador de apagado de música.
- Fundido de salida de audio.
- Widget de reproducción para la pantalla de inicio.
- Respaldo y restauración de datos.
- Administración de canciones y listas de reproducción.

## Tecnologías utilizadas

| Tecnología | Descripción |
|---|---|
| Java | Lenguaje de programación |
| Android SDK | Plataforma de desarrollo |
| AndroidX | Componentes de compatibilidad |
| Material Design 3 | Diseño de interfaz |
| Media3 / ExoPlayer | Motor de reproducción de audio |
| Room | Persistencia de datos local |
| Hilt | Inyección de dependencias |
| Glide | Carga y visualización de imágenes |
| Jaudiotagger | Lectura y edición de metadatos |
| Gradle | Sistema de construcción |

## Requisitos

- Android Studio.
- JDK 17.
- Android SDK.
- Dispositivo o emulador con Android 8.0 (API 26) o superior.

## Instalación y ejecución

1. Clona este repositorio:

   ```bash
   git clone https://github.com/TU-USUARIO/Amp39.git
   ```

2. Abre el proyecto en Android Studio.
3. Espera a que Gradle sincronice las dependencias.
4. Conecta un dispositivo Android o inicia un emulador.
5. Ejecuta el módulo `app` desde Android Studio.

También puedes generar un APK desde:

**Build → Build Bundle(s) / APK(s) → Build APK(s)**

## Compatibilidad

| Propiedad | Valor |
|---|---|
| Plataforma | Android |
| Versión mínima | Android 8.0 (Oreo) |
| API mínima | 26 |
| Compile SDK | 34 |
| Target SDK | 36 |
| Lenguaje | Java |
| Versión del proyecto | 1.1.0 |

## Estructura del proyecto

```text
Amp39/
├── app/
│   └── src/main/
│       ├── java/com/amp39/
│       │   ├── data/
│       │   │   ├── model/
│       │   │   ├── repository/
│       │   │   └── service/
│       │   ├── ui/
│       │   │   ├── backup/
│       │   │   ├── common/
│       │   │   ├── equalizer/
│       │   │   ├── favorites/
│       │   │   ├── lyrics/
│       │   │   ├── main/
│       │   │   ├── player/
│       │   │   ├── playlists/
│       │   │   ├── queue/
│       │   │   ├── settings/
│       │   │   ├── songs/
│       │   │   └── widget/
│       │   ├── utils/
│       │   └── res/
│       └── AndroidManifest.xml
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Privacidad

Amp39 está diseñado para reproducir música almacenada localmente en el dispositivo. El acceso a los archivos musicales se gestiona mediante los mecanismos de permisos y selección de carpetas de Android.

## Estado del proyecto

El proyecto cuenta con funcionalidades de reproducción, biblioteca musical, listas de reproducción, ecualizador, temas, letras, widget y respaldo de datos.

Algunas áreas todavía requieren pruebas adicionales en dispositivos físicos y distintas versiones de Android, especialmente las operaciones de edición y eliminación de archivos y la compatibilidad con las versiones más recientes del sistema.

## Próximas mejoras

- Mejorar la compatibilidad con las versiones recientes de Android.
- Ampliar las pruebas de estabilidad y recuperación de datos.
- Optimizar la experiencia de usuario.
- Revisar y mejorar el proceso de distribución.

## Licencias

El proyecto utiliza dependencias de terceros con sus respectivas licencias. Consulta las licencias de cada dependencia antes de redistribuir una versión de la aplicación.

---

**Desarrollado para los amantes de la música.**
