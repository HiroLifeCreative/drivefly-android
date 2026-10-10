# DriveFly · código fuente versión 93

Proyecto Android nativo Kotlin. Abrir la carpeta DriveFly en Android Studio.
Incluye código, recursos, pruebas y Gradle Wrapper. Sin APK, AAB, carpetas build,
cachés, local.properties ni claves de firma. Los modelos GLB y el JAR del
Gradle Wrapper son recursos necesarios, no salidas de compilación de la app.

Estado: integración inicial Android Auto en el mismo proceso :map que la
navegación del teléfono. Versión 93, SDK compile/target 34, Maps SDK 11.30.1,
Car App Library 1.4.0. 228 pruebas aprobadas en la última compilación.
Android Auto todavía no se probó en DHU ni BMW físico.

Pendiente para Google Play: comprobar requisitos actuales de SDK, configurar
release y firma de subida, generar AAB y gestionar prueba interna privada.
No publicar en producción: el usuario quiere uso personal.
La instalación actual usa firma debug; Google puede cambiar la firma y requerir
reinstalación. Preparar respaldo/restauración de configuración antes de ello.
Las huellas de certificados no sustituyen una clave privada.

Configurar el SDK local creando local.properties o usando Android Studio.
Usar JDK 17 y Gradle 8.7 (incluido el wrapper).
El token de Mapbox se introduce desde los ajustes de la app en el teléfono.
Consultar README.md para funciones y cambios previos.
