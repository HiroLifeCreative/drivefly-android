# DriveFly 93 · Android Auto para uso personal

Servicio proyectado de navegación Android for Cars App Library 1.4.0, categoría NAVIGATION, API de carro mínima 3 y permisos ACCESS_SURFACE / NAVIGATION_TEMPLATES. Vive en :map, comparte NavigationSession con teléfono/flotante: destino de Dasher, geometría restante, avance, recálculo, voz y fin de ruta. La pantalla del carro no crea otra sesión ni otro motor de voz; desconectarla no termina la ruta del teléfono.

Mapbox en la superficie del host mediante una pantalla virtual privada y TextureView, con ciclo de vida y liberación de Surface/Display/Presentation. Mismo token, color de ruta, modo claro/oscuro, inclinación/zoom configurado y zoom por velocidad; el encuadre se adapta al área visible del host. Controles de centrar, +/−, desplazar y terminar. Indicaciones oficiales de NavigationTemplate con distancia por avance sobre ruta y estimación de llegada. El pin mantiene sus colores y visibilidad por proximidad. GPS del teléfono, sin prometer acceso a sensores del BMW.

Instalación del APK actualiza el teléfono con la firma existente. IMPORTANTE: según https://developer.android.com/training/cars/testing#real-vehicles las apps de Car App Library requieren instalación desde fuente confiable (Google Play, Internal App Sharing o Internal Test Track) para pruebas en carro real. El ajuste Orígenes desconocidos no aplica a estas apps. No se publicó ni distribuyó en Google Play; el usuario quiere uso personal. Una prueba privada puede limitarse a la propia cuenta, pero requiere Play Console. No se garantiza aparición en el BMW al instalar este APK directamente. Validación: 228 pruebas sin fallos, lint 0 errores (76 advertencias), manifiesto de APK verificado, firma de actualización habitual. Pendiente de probar superficie/host/GPS en DHU y carro físico.

# DriveFly 92 · hoteles como destino de Dasher

Los nombres con comas se separan de la ciudad donde comienza una localidad válida, no en la primera coma. Así se detecta «Sebastian Hotel, A Member Of Radisson Individuals, Saint Augustine, FL 32084». Se unen encabezados de hotel divididos en dos líneas por la afiliación. La búsqueda permite el nombre abreviado Sebastian Hotel sin confundirlo con Radisson Hotel ni otro resort, y mantiene comprobación de ciudad, código postal y ambigüedad.

«Dejar en el vestíbulo / leave in lobby» identifica entrega cuando el encabezado Entregar a ya no está visible. El nombre repetido en Nombre del edificio no crea otro destino. Pruebas con ambas capturas y encabezado dividido. Pendiente de consulta real con el token del teléfono y prueba física.

# DriveFly 91 · voz según distancia al giro

Antes se anunciaba cada maneuver al cambiar de tramo, aunque estuviera lejos. Ahora se mide la distancia restante por el avance sobre la ruta compartida: aviso inicial «Continúa durante … Después …», aproximación a 500/200 metros y aviso directo solo cerca del giro (25–80 metros según velocidad fiable, cuatro segundos de margen). Avisos lejanos usan millas, cercanos metros. No se repite la calle si ya está en la instrucción.

Cada etapa se anuncia una sola vez, tolera fluctuaciones GPS, suprime giros ya pasados y espera ocho segundos entre avisos de aproximación; el giro cercano tiene prioridad. Recalcular/nueva sesión reinicia las etapas. Pruebas de distancia, velocidad, próximo giro lejano, deduplicación, datos inválidos y reinicio. Pendiente de prueba física.

# DriveFly 90 · nombre correcto en anuncios de recogida

Se reconoce «Recoger desde», «Pick up from» y encabezados separados o combinados con el nombre. El comercio explícito tiene prioridad sobre heurísticas. Postre/Postres/Dessert/Desserts y bebidas son etiquetas del contenido del pedido, nunca comercios. En pantallas con «La orden incluye» no se usa el texto anterior a la dirección como nombre del local; si el encabezado falta se anuncia de forma genérica.

Pruebas de regresión con los textos de la captura de McDonald's, variantes en inglés y ausencia del comercio con distintos contenidos del pedido. Validación: 217 pruebas sin fallos y lint sin errores. Pendiente de prueba física con Dasher.

# DriveFly 89 · visibilidad y minimapa automático

Semáforos 50% mayores en ambos mapas. Pasos peatonales geográficos visibles desde zoom 14, sobre la ruta, pintura blanca emisiva y un borde de 0.6 píxeles para mejorar la lectura de cruces lejanos (objetivo: aproximadamente 0.5 millas). Su presencia depende de geometría marcada disponible en OSM; no se inventan cruces. La distancia legible depende del zoom, inclinación y tamaño de pantalla.

Una recogida de Dasher resuelta abre el minimapa oculto, incluso si se estaba minimizando. Si la lectura inicia el servicio cerrado, se muestra inicialmente la burbuja, esperando una coordenada válida de recogida para expandir. Mantener pantalla encendida cuando el minimapa está abierto o existe destino activo; minimizado sin destino permite suspensión normal. Validación: 214 pruebas unitarias sin fallos; lint sin errores; APK firmado y descarga verificada. Pendiente de validación física.

# DriveFly 88 · voz para pedidos y llegada

Anuncios breves de ofertas de Dasher y recogida aceptada con el nombre visible del comercio. Se leen etiquetas en español/inglés, notificaciones «Nuevo pedido/New order» y encabezados de recogida. Las lecturas repetidas y la transición oferta→recogida no vuelven a hablar; ofertas posteriores pueden anunciarse. Si no hay un nombre único se usa una frase genérica, sin inventar una tienda.

Al confirmar la llegada con los criterios GPS existentes, se anuncia una sola vez el número de casa y apartamento disponibles. El lado izquierda/derecha solo proviene del último maneuver tipo arrive de Mapbox. La locución se produce al comenzar la cuenta de llegada, sin repetirla veinte segundos después. Datos y marca de llegada hablada se conservan en la sesión compartida; no se cambian las coordenadas de navegación.

Configuración → Voz de navegación incorpora «Anunciar nuevos pedidos» y «Detalles al llegar». Se utiliza el motor y velocidad de voz ya elegidos; Sin voz apaga todo. Los anuncios de pedido pasan por el proceso del mapa, se encolan detrás de giros hablados y caducan tras treinta segundos para evitar avisos viejos. Validación: 214 pruebas unitarias sin fallos, lint sin errores (71 advertencias), APK ARM64 con firma habitual y descarga pública verificada por SHA-256. Pendiente de prueba física con Dasher y el motor de voz instalado.

# DriveFly 87 · eliminar destino y alerta de velocidad

La X del minimapa detiene la sesión compartida en vez de borrar únicamente las capas del mapa. Se cancelan las búsquedas de Dasher pendientes/en curso y se bloquea la misma dirección visible para que no reaparezca. Se limpia ruta, alternativas, pin, ETA y navegación en ambos mapas, manteniendo abierto el flotante.

La velocidad actual pasa a rojo brillante con un pulso suave de sombra al superar el límite del tramo. El límite de la señal conserva su aspecto normal. La animación sigue funcionando aunque la cámara no se mueva, vuelve al aspecto normal al bajar de velocidad y no se activa con límite desconocido, inválido o GPS antiguo. Compartido entre pantalla principal y flotante. Validación: 201 pruebas sin fallos, lint sin errores, firma habitual y descarga pública verificada por SHA-256. Pendiente de prueba física.

# DriveFly 86 · detección de Dasher y consulta de direcciones

Se distinguen fallos HTTP de Mapbox (token/restricciones, límite de consultas, servicio), problemas de conexión y ausencia de coincidencia única. Antes todos podían devolver el mensaje genérico «dirección segura». Una consulta en curso no se repite para el mismo destino; tras fallo se espera un minuto en vez de reiniciar cada lectura. Los estados de búsqueda se guardan en diagnóstico sin cubrir los giros de una ruta activa.

Para direcciones numéricas sin resultado aceptable en Geocoding v5, se añade Geocoding v6 siguiendo la documentación pública https://docs.mapbox.com/api/search/geocoding/ . Se valida coincidencia del número y calle, confianza alta/exacta, código postal indicado, contexto y resultados no ambiguos. Se conserva el acceso para vehículos separado del punto del edificio.

La lectura reconoce la fase «Para llevar antes» y «Pick up before/by», reconstruye número/calle divididos entre nodos y excluye direcciones en campos Apt./Suite o código de acceso. No se almacena el contenido de las pantallas ni el token en diagnóstico. La última respuesta de búsqueda se guarda en un archivo atómico leído entre procesos desde Configuración → Diagnóstico, con botón para copiar y borrar. No se pierde al cambiar un mensaje de accesibilidad. Validación: 198 pruebas sin fallos y lint sin errores. La captura no deja leer la dirección completa detrás del flotante; pendiente de validación física y respuesta real de Mapbox con el token del teléfono.

# DriveFly 85 · puck más abajo y giro suave

El puck fijo pasa del 66% al 70% de la altura del mapa, centrado horizontalmente. El padding de cámara comparte la misma constante para que la ruta y la ubicación permanezcan alineadas. Al cambiar realmente el rumbo en movimiento, la flecha responde con un giro máximo de 14 grados y vuelve suavemente al frente mientras la cámara completa el giro. El halo conserva su orientación; se ignoran pequeñas oscilaciones y movimiento inferior a 3 mph. Misma política en mapa principal y flotante. Validación: 191 pruebas sin fallos, lint sin errores, firma habitual y descarga pública verificada por SHA-256. Pendiente de prueba física.

# DriveFly 84 · flechas de cruce compactas

La flecha sobre la ruta usa la misma condición de cercanía que el aviso visual (aproximadamente cinco metros del giro con GPS válido). Antes se dibujaba siempre que el cruce estaba visible, aunque estuviera lejos. Se acorta de 16/22 metros a 7/9 metros antes/después del giro y se limita cada brazo en pantalla a 22/28 dp. Punta triangular más corta, proporcional al brazo, trazo blanco más fino y contorno azul. Cambio compartido entre mapa principal y flotante. Validación: 188 pruebas sin fallos, lint sin errores (71 advertencias), firma habitual y descarga pública verificada por SHA-256. Pendiente de verificación visual en el teléfono.

# DriveFly 83 · destinos por nombre de instituciones y edificios

La búsqueda de Dasher admite puntos de interés de cualquier categoría: bancos, escuelas, hospitales, parques, bibliotecas y residencias. Se elimina la igualdad literal del nombre; se normalizan acentos, puntuación, St./Saint y variantes Dorms/Dormitory/Residence Hall/Student Housing. Se consultan nombres preferidos y alternativos de Search Box.

En destinos sin dirección numérica, se incorpora el campo visible «Nombre del edificio»/«Building name». Para la captura Flagler College Fec Dorms, el destino conserva FEC Tower C y no acepta Tower A ni el campus genérico como sustitutos. Se buscan hasta tres variantes del nombre, reuniendo resultados antes de decidir para no elegir arbitrariamente entre sucursales. Se mantienen ciudad, código postal, coordenadas válidas y rechazo de resultados ambiguos.

La cobertura depende de que Mapbox tenga indexado el lugar o edificio. No se pudo verificar el resultado real de ese campus sin el token configurado en el teléfono. Validación: 188 pruebas unitarias sin fallos; lint sin errores (70 advertencias existentes). APK ARM64 firmado con el certificado habitual y descarga pública verificada por SHA-256. Pendiente de prueba física con Dasher; no se garantiza que todos los edificios estén disponibles.

# DriveFly 82 · avisos visuales cerca del cruce

Los avisos de giro proyectados y los recuadros de alternativas se limitan a unos cinco metros del giro seleccionado. Se compara la distancia recorrida y el punto de maniobra sobre la ruta, usando GPS reciente, precisión válida y proximidad a la carretera. La ventana incluye los primeros cinco metros después del giro para tolerar el muestreo del GPS; luego el aviso se retira.

Se elimina la pastilla de velocidad de arriba a la izquierda en el mapa flotante. Se conserva el panel de velocidad y límite de la derecha.

Las pruebas incluyen cruces visibles lejanos, retirada tras pasar, ubicación imprecisa/antigua y rutas en curva donde la distancia en línea recta sería engañosa. Pendiente de prueba física.

# DriveFly 81 · orientación del puck en vista aérea

La vista 2D deja el puck fijo de navegación plano, visto desde arriba, aunque el usuario haya guardado una inclinación propia. Al volver a 3D, el ángulo se recupera proporcionalmente a la inclinación real de la cámara, durante la transición animada.

En vista 2D el halo es circular y se retiran la sombra y el relieve de perspectiva. En 3D reaparecen progresivamente con el ángulo real de la cámara.

Se corrige el signo de la rotación X del dibujo y del modelo nativo para compartir el sentido de inclinación. Se mantienen el tamaño dinámico con zoom, los colores, la posición fija del puck y la sesión compartida entre mapas.

Pruebas específicas: vista 2D con inclinación guardada, transición por el ángulo real de cámara, conservación del ángulo elegido y límites/valores inválidos. Pendiente de comprobación visual en TCL T807W.

# DriveFly 80 · tamaños según zoom

El puck, los pins de destino y el grosor de las rutas aumentan al acercar y disminuyen al alejar. Se utiliza una curva continua compartida, limitada entre 40% y 170% del tamaño base. El zoom de referencia 17.145 conserva el tamaño anterior y el porcentaje de puck elegido por el usuario.

- Líneas y alternativas: expresiones de estilo evaluadas por Mapbox, con contorno y desplazamiento proporcional.
- Puck nativo 2D: expresión de escala; modelo 3D: expresión vectorial para sus tres ejes, conservando el modo viewport. No se reconstruye el modelo al cambiar el zoom.
- Marcador fijo: escala alrededor de su centro, manteniendo el anclaje de navegación.
- Pins de destino: escala desde su punta, manteniendo la coordenada geográfica y su visibilidad por distancia.

Pendiente de prueba visual en el dispositivo; las expresiones y la escala matemática tienen pruebas de continuidad, límites, monotonía y proporción entre ejes.

# DriveFly 79 · puck azul y recuperación del modelo 3D

- Puck azul primario #007AFF en el marcador fijo y los pucks nativos, en ambos temas.
- Modelo GLB convencional con índices, normales y material opaco azul, sin atributo COLOR_0. 224 triángulos; validador Khronos glTF con cero errores y advertencias.
- Las actualizaciones de tamaño/inclinación esperan 350 ms de estabilidad; una actualización GPS con los mismos ajustes no reinicia la espera. Se conserva el proveedor de ubicación y la visibilidad de la capa al modificar el puck.
- Se captura el fallo Java de configuración y se recupera el puck seguro. Para errores nativos que evaden Kotlin, se registra la sesión 3D; en la próxima apertura se consultan las salidas históricas del proceso :map y se desactiva solo el modelo 3D si hubo un cierre registrado desde esa sesión. No se desactiva por una salida normal.

165 pruebas aprobadas en la revisión inicial. Los cambios de estabilidad no permiten afirmar la causa del cierre sin el diagnóstico del teléfono; se necesita prueba física en TCL T807W / Android 16.

# DriveFly 78 · color de ruta independiente de la iluminación

Se aplica lineEmissiveStrength(1.0) a la línea seleccionada, su contorno y el resaltado en ambos mapas. Evita que la iluminación nocturna de Mapbox Standard oscurezca el color personalizado. Conserva el color guardado, la vista previa y la sesión de navegación.

La API se verificó en Mapbox Maps SDK 11.30.1. Pendiente de comprobación visual en el dispositivo del usuario.

# DriveFly 77 · colores personalizables

Configuración → Colores de ruta y pins. Permite personalizar la línea de ruta y los pins de restaurante, cliente y otros destinos mediante paleta o código HEX RGB de seis dígitos. Incluye vista previa y restauración de los colores originales.

Se guardan en un archivo atómico compartido entre procesos. El mapa flotante recibe una actualización de estilo; el principal recarga al volver de configuración. Se conserva la sesión de navegación, la geometría y la visibilidad del pin por distancia. El color elegido no cambia con el tema. El contorno se deriva de la misma tonalidad, sin borde blanco.

# DriveFly 76 · ruta azul

La ruta seleccionada usa el mismo azul (#007AFF) en modo claro y oscuro, tanto en la vista previa como en la navegación principal y flotante. Contorno azul oscuro y brillo del mismo color. Se conserva la lógica de navegación de la versión 75.

# DriveFly 75 · precisión de navegación

- Una ubicación fuera de la ruta ya no consume tramos ni adelanta giros.
- Los desvíos deben durar al menos cuatro segundos y tener tres posiciones válidas antes de recalcular; se conserva el intervalo de reintento existente.
- Las indicaciones permanecen hasta superar el cruce por 12 metros.
- Se descartan posiciones sin precisión o antiguas usando el reloj monotónico. La orientación necesita velocidad fiable y precisión angular de 30 grados o mejor cuando el proveedor la informa.
- Lógica compartida en el mapa principal y flotante; se conservan ajustes de cámara y puck.

Validación: 160 pruebas aprobadas, compilación y lint correctos. Descarga externa verificada por SHA-256. Pendiente de validación física durante una ruta en el TCL T807W.

APK TCL ARM64: https://files.catbox.moe/3s6zih.apk

# DriveFly 74

SDK Mapbox Maps actualizado de 11.18.1 a 11.30.1. Standard 3D pasa a ser el estilo inicial para esta actualización, con edificios, árboles, monumentos, fachadas, marcas y superficies de carretera detalladas y estructuras viales 3D donde Mapbox ofrece cobertura. El modo compatible sigue disponible como recuperación. Se elimina la referencia obsoleta al servicio LifecycleService que ya no existe en el SDK nuevo. Iniciar con Dasher ya no fuerza el estilo compatible.

Configuración → Mapa 3D y navegación: interruptores independientes de elementos del entorno, detalle de carreteras, guía de carriles, entradas y clima. Iluminación según tema, franjas horarias del teléfono o un preset manual de amanecer/día/atardecer/noche. Se guarda en un archivo atómico compartido entre procesos, se aplica al flotante por broadcast y se comprueba al volver al mapa principal. Los presets por hora se actualizan sin reiniciar la cámara ni la ruta; no representan la posición solar exacta.

Llegadas: las consultas Geocoding v5 solicitan entrances=true y routing=true. Se acepta una puerta de precisión high con coordenadas válidas, cercana al edificio y sin múltiples entradas separadas; si falta, se conserva el punto habitual. El pin señala la puerta y la ruta/recalculo/finalización utilizan el acceso vehicular default cuando existe, evitando dirigir el coche al acceso peatonal. Ambos puntos se conservan al compartir la sesión. Los negocios detectados por nombre y ciudad/código postal se enriquecen con su dirección completa cuando se puede validar la sucursal. Las selecciones manuales con dirección numerada se enriquecen sin bloquear la vista ni reemplazar una ruta que ya se inició.

Guía de carriles: interpreta las intersecciones de Directions correspondientes al próximo giro, respeta su orden de izquierda a derecha y muestra flechas con carriles permitidos/recomendados. No inventa carriles sin datos ni dibuja una trayectoria a nivel de carril. Se oculta lejos del giro y con GPS insuficiente. La geometría pública de Standard (showHdRoads) y la guía privada de 3D Lanes son capacidades diferentes.

Clima: consulta observaciones de NOAA/NWS de la estación más cercana, máximo 35 km. Solicita la ubicación redondeada a 0.01 grados, sin token ni dirección del pedido. Consulta cada 15 minutos o al cambiar de zona, descarta observaciones de más de 90 minutos y muestra partículas suaves solo ante lluvia/nieve informadas. Faltan datos, fuera de cobertura, pérdida de vigencia o interruptor apagado: sin efectos. Son condiciones regionales, no mediciones de cada calle. Datos abiertos NWS: https://www.weather.gov/documentation/services-web-api .

Fuentes de integración: https://docs.mapbox.com/map-styles/reference/standard/ ; https://docs.mapbox.com/api/search/geocoding-v5/ ; https://docs.mapbox.com/api/navigation/directions/ . 3D Lanes avanzado continúa sujeto al acceso privado y a su integración específica: https://www.mapbox.com/3d-lanes . No se implementan detecciones ADAS de vehículos, pagos/disponibilidad de cargadores ni condiciones reales de calzada sin sensores o fuentes de datos correspondientes. Un token con todos los scopes no demuestra por sí solo la habilitación de un producto privado; la key de la cuenta permanece en el teléfono y no está disponible en este entorno para verificarlo.

# DriveFly 73

Pasos peatonales dibujados como polígonos geográficos sobre la calle, compartidos entre mapa principal y flotante. Se eliminan los iconos azules de peatón. Las franjas siguen el giro, la inclinación y el zoom del mapa y usan dimensiones en metros, no un tamaño fijo en pantalla. Se muestran desde zoom 17 para no saturar la vista lejana. Una base de asfalto discreta dentro del área del cruce aporta contraste en ambos temas.

Fuente: OpenStreetMap/Overpass. Se utilizan caminos footway=crossing con marcas confirmadas y geometría de 3–45 m. También se admiten nodos marcados asociados a una única carretera con ancho explícito, orientación local definida y sin un giro ambiguo. Cruces sin marcas, sin geometría suficiente o en intersecciones ambiguas no generan pintura. El ancho peatonal se toma de width cuando existe; en caminos de cruce confirmados sin ese dato, la representación usa 3 m de ancho, por lo que no equivale a una medición del pavimento. Las marcas no garantizan el estado actual de la vía.

Consulta real en la zona de la captura (29.86767, -81.32166, radio 1800 m): respuesta con 215 nodos de cruce y 91 caminos de cruce; 31 caminos tenían marcas confirmadas y una longitud apta. La disponibilidad puede variar. Las señales de STOP, semáforos y cámaras conservan su representación independiente.

Validación v73: APK ensamblado; 134 pruebas unitarias aprobadas; Android Lint sin errores. La apariencia en el TCL y el renderizado con Mapbox requieren prueba física.

# DriveFly 72

Navegación adaptada a las referencias del usuario: ruta violeta de día y cian de noche, borde oscuro y relieve, puck redondeado con volumen y halo translúcido, flecha blanca sobre el giro y globo de indicación anclado a la maniobra real. La calle actual aparece debajo del puck; la barra inferior muestra hora estimada de llegada, minutos en verde y millas. El mapa compatible nocturno utiliza una paleta azul. El mapa principal y el flotante comparten el seguimiento y las indicaciones; se conservan preferencias de cámara/tamaño, rerouting, finalización al llegar y filtros de Dasher.

Los globos de alternativas usan calles reales, diferencia de llegada y «Menos tráfico» únicamente cuando existen anotaciones de congestión comparables, ponderadas por distancia y con cobertura suficiente. La velocidad actual usa el filtro existente. El límite vial solo se muestra con anotaciones maxspeed válidas, alineadas con los segmentos y una ubicación reciente con precisión suficiente cerca de la ruta; datos desconocidos no se sustituyen por límites inventados. Anotaciones: https://docs.mapbox.com/api/navigation/directions/ .

Incluye dos modelos GLB propios de flecha redondeada, diurno y nocturno, para la opción de puck nativo 3D. El indicador fijo durante seguimiento dibuja relieve con Canvas y conserva el anclaje de cámara. Ambos modelos pasan glTF Validator sin errores ni advertencias. Los edificios, objetos 3D y árboles dependen del estilo seleccionado y de la cobertura de Mapbox; no se reproduce una escena geográfica ficticia. Los semáforos no indican fase en tiempo real.

Validación: APK ensamblado, 128 pruebas unitarias aprobadas y Android Lint sin errores.

La verificación visual, GPS y rendimiento en Android 16 necesita prueba física en el TCL; este entorno no dispone del teléfono ni de un token Mapbox para ejecutar el mapa.

# DriveFly 71

Configuración → Dasher → Filtros de pedidos. Pantalla agrupada coherente con los ajustes, con Guardar en la cabecera. Filtros por pago mínimo USD, distancia máxima en millas y pago mínimo USD/mi, con interruptor individual y control general. Valores iniciales editables: $6, 10 mi y $1.50/mi. Los límites activos se combinan con AND y son inclusivos. La aceptación automática mantiene su interruptor independiente. Desactivar Aplicar filtros conserva el modo aceptar todas las ofertas de la versión 70.

El lector extrae únicamente métricas visibles de la oferta, elimina duplicados de Accesibilidad, reconoce formatos decimales en español/inglés y convierte kilómetros a millas. Usa pago garantizado y distancia total si están explícitamente etiquetados. No suma pagos extra ni presume propinas futuras; si hay varios pagos/distancias y no se identifica un total único, no pulsa Aceptar cuando el dato es necesario. Si una oferta falla algún límite, queda en Dasher para revisión manual; no pulsa Rechazar. $4 por 10 mi equivale a $0.40/mi y falla los valores iniciales.

Los cambios se guardan juntos en preferencias persistentes; el lector vuelve a evaluar las ofertas al cambiar filtros. El borrador conserva valores y toggles al rotar. No se almacenan importes ni texto de ofertas en los registros; el estado explica el motivo sin copiar datos de pantalla. Se mantienen la detección posterior de la parada activa y el marcado del destino.

Validación: 121 pruebas unitarias aprobadas; Android Lint: 0 errors, 76 warnings; APK ensamblado. Las métricas y el clic de Accesibilidad requieren comprobación con una oferta real en el TCL; no hay dispositivo físico disponible en este entorno.

# DriveFly 70

Configuración → Dasher → Aceptar pedidos automáticamente. Interruptor persistente, apagado por defecto, independiente de Destino automático. Cuando se activa, el lector pulsa una única acción Aceptar en una oferta visible de los paquetes Dasher autorizados; reconoce español/inglés y contadores cortos. Identifica el contexto de oferta por Rechazar o por pago y distancia visibles, sin aplicar mínimos. Solo usa ACTION_CLICK en un botón/ancestro visible y habilitado; no usa coordenadas, capturas, gestos ni confirma recogida o entrega.

Un clic aceptado por accesibilidad no garantiza que Dasher acepte el pedido: se espera el cambio de pantalla y una dirección legible en una pantalla de recogida o entrega, evitando usar direcciones de la transición. No se genera una ruta desde la oferta. Después, el lector y geocodificador existentes resuelven la parada visible y la muestran en el flotante si Destino automático está activo. Tres reintentos máximos de clic fallido, separados tres segundos; un clic exitoso no se repite hasta que la oferta lleva cuatro segundos ausente. Desactivar la opción cancela reintentos posteriores. Cambiar el interruptor despierta el lector sin reiniciar la app.

No se almacena ni registra el texto de la oferta. Depende de la información que Dasher expone a Accesibilidad; requiere prueba física con una oferta real. Validación: 109 pruebas unitarias aprobadas; Android Lint: 0 errors, 70 warnings; APK ensamblado.

# DriveFly 69

Principal basada en la opción 1 elegida: https://dribbble.com/shots/16786064-Taxi-Driver-App . El lanzador abre NavigationActivity directamente en el proceso aislado :map. La barra compacta incluye menú y búsqueda; una tarjeta inferior redondeada muestra el estado del lector de Dasher, un acceso a Configuración y el botón Iniciar turno. Se oculta durante búsquedas, selección de destino y navegación; vuelve al explorar sin destino. Los controles de cámara, las atribuciones y la leyenda de negocios se desplazan sobre la tarjeta. Se mantienen estilo claro/oscuro, rutas, cámara y preferencias. El diagnóstico se accede desde el menú, separado del inicio.

Iniciar turno utiliza la coordinación existente: permisos y lector de accesibilidad, arranque del flotante, confirmación y apertura de Dasher. La pantalla ligera permanece como recuperación si el mapa no completó el inicio anterior; permite abrir el mapa compatible o entrar a Configuración sin cargar Mapbox en el proceso principal.

Protección del receptor de preferencias de cámara: las excepciones de un observador se registran con su causa original y no interrumpen los demás ni escapan del broadcast. La captura aportada contiene únicamente la envoltura de Android; no permite atribuir una causa precisa al fallo anterior. No se afirma resolución de un fallo nativo con esta protección.

Validación: APK ensamblado, 101 pruebas unitarias aprobadas; Android Lint: 0 errors, 70 warnings. Prueba visual e interacción con un pedido real pendiente en el TCL físico.

# DriveFly 68

Configuración rediseñada según la opción 1 elegida: https://dribbble.com/shots/22910953-Mobile-Settings-Page . Cabecera compacta con regreso, grupos redondeados, iconos de contorno, etiquetas alineadas a la izquierda, valores e interruptores a la derecha. Paleta propia clara y oscura. Personalización, Dasher, acceso y ayuda enlazan pantallas separadas; el token y los registros siguen accesibles sin cargar el SDK de mapas.

Cámara y puck usan controles compactos y Guardar en la cabecera. El zoom automático despliega sus umbrales solamente al activarlo. Se conservan vista previa compartida, borrador durante rotación y retorno a los valores guardados al salir sin guardar. Distancia 50–1000 %, inclinación 0–75°, tamaño del puck 50–250 %. Voz: selección por filas, instalación de Claude High, control de velocidad 80–120 % y prueba de voz. Tema: Claro/Oscuro/Sistema, guardado inmediato. Las preferencias y funciones existentes se conservan. El estado del flotante se consulta entre procesos y su activación no se cancela por comprobar prematuramente el arranque del servicio.

Validación: APK ensamblado correctamente; 101 pruebas unitarias aprobadas, sin fallos; Android Lint: 0 errores y 68 advertencias. La apariencia final y la interacción en Android 16 requieren comprobarse en el TCL físico.

# DriveFly 67

Cámara personalizable: Configuración → Cámara y navegación → Ajustar cámara. Inclinación de 0° a 75° y distancia de 50 % a 250 %, con referencia ilustrativa y vista previa en los mapas abiertos. Guardar y aplicar conserva los valores en almacenamiento atómico compartido por ambos procesos. Salir sin guardar recupera los valores guardados. Restablecer recupera 60° y zoom 17.145 (100 % de distancia), que muestra aproximadamente 50 % más alcance que la versión 51. El modo 2D conserva 0°.

Suavidad 51: ruta azul de 6 a 11 píxeles de estilo y borde blanco de 10 a 16; alternativas grises de 4.5 a 7. Seguimiento continuo sincronizado con Choreographer, con un filtro críticamente amortiguado para avance y rumbo que conserva posición y velocidad al recibir un nuevo GPS. Movimiento sobre los segmentos de la ruta y recorte azul a 20 Hz. Cambio 2D/3D amortiguado; selección de destino y encuadre de ruta con easeTo de 1.2 segundos; seguimiento sin ruta en ambos mapas con easeTo de 1.1 segundos. Ubicación solicitada cada segundo/metro y prioridad a un GPS reciente de buena precisión sobre una posición de red; se rechazan posiciones más antiguas. La cámara conserva zoom 17.73 y el marcador de pantalla fijo. Pruebas del filtro: convergencia, cambios de objetivo sin reiniciar velocidad, equivalencia 30/60 Hz, cruce del norte incluso tras varias vueltas completas, frenado al invertir rumbo y entradas inválidas. La fluidez física en TCL todavía requiere prueba en el dispositivo.

Cámara 50: zoom activo de 18.5 a 17.73, equivalente a aproximadamente 70 % más alcance visible (escala logarítmica Mapbox). Se mantiene inclinación de 70° y el marcador fijo al 66 % de altura, en ambos mapas.

Seguimiento 49: el marcador durante seguimiento se dibuja en una View personalizada, fija al 66 % de altura del mapa, que no recibe coordenadas GPS ni transformaciones de cámara. El puck nativo Mapbox se desactiva durante este modo y se reactiva al explorar o terminar. La posición visual se proyecta sobre la ruta si el GPS está a menos de 45 metros y tiene precisión de 35 metros o mejor. En paradas se estabilizan posición y rumbo; pequeñas oscilaciones hacia atrás no hacen regresar el tramo recorrido. GPS real se conserva separado para detección de desvíos, rumbo contrario y nuevas rutas; fuera del umbral no se fuerza una falsa posición sobre la ruta. La cámara interpola sobre los segmentos de la ruta, sin cortar diagonalmente curvas. Ambos mapas usan el mismo controlador. Pruebas de ruido lateral, detención, precisión mala, desviación real, reversa, esquinas y retrocesos pequeños. No hay validación visual física en el TCL.

Navegación 48: cámara vinculada a la posición y rumbo que dibuja el indicador nativo de Mapbox (onIndicatorPositionChanged/onIndicatorBearingChanged), también durante la animación. Puck anclado en el centro horizontal y al 66 % de altura durante seguimiento, con el mismo perfil en ambas vistas. El puck se sitúa por encima de la capa de ruta. La geometría azul se recorta interpolando dentro del segmento recorrido, actualizada hasta 10 veces por segundo durante la animación; el historial de geometría original se conserva para progreso y recálculo, y las alternativas siguen grises. El gesto de explorar pausa la cámara pero sigue actualizando la ruta. Falta validación visual en el TCL físico.

Corrección Dasher 47: reconoce calles con prefijo Cll/Calle/Camino y conserva ciudad/ZIP en direcciones de US Highway. Deduplica tarjeta y etiqueta de accesibilidad; examina las ventanas de Dasher incluso con el flotante encima, recorre contenedores con hijos visibles y revisa cada dos segundos mientras Dasher permanece visible. La geocodificación se ejecuta en el servicio flotante con su GPS y token; no intenta obtener GPS desde el lector en segundo plano. El flotante muestra espera GPS, búsqueda o falta de coincidencia. Pruebas de regresión basadas en las dos direcciones de las capturas; falta validar lectura y resultado Mapbox en el dispositivo con un pedido real.

Una sesión en el proceso :map mantiene destino, ruta elegida, alternativas, progreso y solicitudes de recálculo para NavigationActivity y FloatingMapService. La ruta se conserva al cambiar de vista y al reiniciar (máximo seis horas); finalizar en una vista finaliza en las dos. Cámara compartida: zoom 18.5, inclinación activa 70°, margen proporcional al viewport, rumbo suavizado; modo 2D conserva inclinación cero. Puck nativo 2D escala 1.35 y modelo opcional 3D escala 24 (antes 18).

Monitor de ruta de DriveFly: distancia a segmentos completos, retroceso con rumbo contrario y tres fixes consecutivos de precisión <=35m; no reacciona a rumbo en parado ni a fixes viejos. Una sola petición de recálculo en curso, mínimo ocho segundos entre reintentos, envío de rumbo real a Directions, conservación de ruta anterior si falla la red. Llegada confirmada con tres fixes cerca del destino y velocidad baja. Es un controlador propio sobre Maps SDK + Directions API, no la lógica nativa OffRouteObserver del Navigation SDK.

Documentación consultada:
- https://docs.mapbox.com/android/navigation/guides/turn-by-turn-navigation/rerouting-and-refresh/
- https://docs.mapbox.com/android/navigation/guides/ui-components/camera/
- https://docs.mapbox.com/android/maps/guides/user-location/location-on-map/
- https://docs.mapbox.com/android/search/examples/

Se requiere prueba física de recálculo y sincronización en el TCL con un trayecto real; pruebas automatizadas cubren segmentos largos, desvíos sostenidos, sentido contrario, precisión mala y recuperación.

# DriveFly 45

Modo Dasher: Iniciar con Dasher verifica token, permiso de superposición, ubicación precisa y accesibilidad; inicia primero el servicio flotante y espera confirmación antes de abrir Dasher. El lector procesa texto visible de los paquetes Dasher, no la app de consumidor. Detecta recogida/entrega con contexto, rechaza pantallas ambiguas y ofertas pendientes, conserva ciudad/código postal/unidad. Comparte coordinación con las notificaciones de Dasher. Mapbox geocodifica usando una ubicación reciente; valida número de casa, relevancia y ambigüedad antes de enviar destino y ruta al flotante. No registra el texto completo de pantalla ni acepta pedidos. Configuración permite desactivar la automatización. Depende de que Dasher exponga la dirección en accesibilidad o notificaciones; requiere validación con un pedido real en el dispositivo.

# DriveFly 44

Navegación: oculta leyenda y marcadores de exploración, mueve controles sobre la barra inferior, zoom 18.5 con ubicación más baja en pantalla. Exploración: cinco consultas en paralelo (centro y cuatro cuadrantes), deduplicación por mapbox_id, hasta 125 resultados según cobertura. Horarios: conserva metadatos de búsqueda, lotes de 100, consultas individuales acotadas cuando faltan detalles y mensaje distinto para horario publicado sin estado confirmable. No inventa horarios ni zona horaria. Diagnóstico: extracto legible acotado de trazas nativas binarias, explícitamente no presentado como pila completa.

# DriveFly 43

Inicio seguro independiente del mapa. MainActivity es una Activity ligera sin referencias al SDK; NavigationActivity, FloatingMapService y el proveedor AndroidX Startup (incluidos los inicializadores de Mapbox) se ejecutan en :map. El diagnóstico recupera registros Java y ApplicationExitInfo en Android 11 o superior, con lectura acotada de trazas nativas y botón para copiar. No abre el mapa automáticamente durante este diagnóstico.

# DriveFly 42

Corrección de compatibilidad del arranque: por defecto usa Mapbox Light y puck direccional 2D nativo, sin carro. Conserva fichas, colores de negocios, rutas y cámara inclinada con edificios extruidos. Standard y LocationPuck3D disponibles en Configuración > Personaliza tu mapa > Mapa 3D avanzado. Un inicio incompleto activa pantalla de recuperación en la siguiente apertura, con acceso al error Java guardado. Los fallos nativos de GPU pueden no generar registro Java. Causa del cierre en el teléfono aún no confirmada sin diagnóstico del dispositivo.

# DriveFly 41

Restaurantes: verde abierto, amarillo a 30 minutos o menos del cierre, gris cerrado; punto hueco sin horario válido. Estado según horario publicado y zona horaria del negocio; no representa disponibilidad de pedidos. Ficha personalizada dentro del mapa con horario de hoy y semana desplegable. Caché de horarios en memoria por cinco minutos.

Ruta previa turquesa, activa azul, alternativas reales grises cuando el proveedor las devuelve. Mapbox Standard con detalle de vías y números de casa a partir del zoom 18 según cobertura. Puck con LocationPuck3D del SDK y modelo propio de flecha GLB, sin carro; el SDK requiere un modelo, no incluye una flecha 3D predeterminada.

# Historial y configuración

Aplicación nativa de Android para repartir con un mapa flotante y navegación. El rediseño utiliza tarjetas blancas, fondos claros, azul como color de acción, controles redondeados y jerarquía visual inspirada en iOS.

## Empezar

1. Instala el APK de desarrollo en Android 8.0 o superior.
2. La app abre directamente en el mapa. Si falta el token, pulsa **Configurar Mapbox** y guarda un token **público** de Mapbox (`pk.…`). El botón Abrir Mapbox lleva a la página de tokens. No se incluye ningún token en el proyecto; la disponibilidad y los costes del servicio dependen de tu cuenta.
3. Abre el menú **☰** y pulsa el interruptor **Mapa flotante**. Si necesitas configurar permisos, entra en **Estado y permisos**. La app explica y solicita ubicación precisa y permiso para mostrarse sobre otras apps. Tras conceder el permiso de superposición, continúa al volver a DriveFly.
4. Usa la barra **Buscar dirección o lugar**, selecciona un resultado y elige **Iniciar ruta** o **Flotante**. Desde **Guardar destino** puedes guardar Casa y Trabajo. Toca el mapa para marcar un punto; arrastra el asa de la tarjeta para dejar más espacio al mapa. Usa **2D/3D** para cambiar la perspectiva. Estos destinos se conservan al cerrar la app.
5. Las conexiones con notificaciones y el lector de DoorDash son opcionales y se activan manualmente desde **☰ → Configuración**. El lector está limitado a los paquetes de DoorDash configurados.

Para seguir la ubicación con otras aplicaciones al frente, consulta **Permitir ubicación siempre**. En Android 11 y posteriores se abre la ficha de permisos de la aplicación. El sistema puede requerir activar antes la ubicación precisa.

## Referencias visuales

- Apple Maps: https://www.apple.com/maps/
- Diseño de iOS: https://www.apple.com/newsroom/2025/06/apple-introduces-a-delightful-and-elegant-new-software-design/
- Menú de Uber Driver: https://www.uber.com/us/en/blog/planner-offline-zones-heatmap/
- Soy Rappi: https://play.google.com/store/apps/details?id=com.rappi.storekeeper&hl=es

Se adaptaron la búsqueda tipo cápsula, controles circulares, tarjetas compactas y filas de menú con iconos. No se incorporan imágenes ni marcas de estas apps.

## Cambios

- Versión 40: botón Lugares con restaurantes, tiendas y cafeterías de Mapbox Search Box alrededor del centro del mapa, hasta 25 resultados en aproximadamente cuatro kilómetros. Ver en el mapa encuadra los resultados.
- Puntos con nombres y selección de negocios ya etiquetados en el mapa. Las fichas solicitan los detalles del negocio identificado por su Mapbox ID; una búsqueda de identidad para las etiquetas del mapa exige una única coincidencia de nombre a menos de 50 metros.
- Horarios mediante Mapbox Places API (public preview). Abierto/cerrado se calcula según el horario publicado y la zona horaria del negocio, cuando ambos son interpretables. No se usa la zona horaria del teléfono como sustituto. Horarios especiales, reglas no compatibles o datos ausentes devuelven estado desconocido; un cierre permanente es un campo separado.
- Tocar el estado abre un panel propio con horarios y procedencia. Los horarios publicados pueden diferir por festivos y cambios del negocio; no representan una confirmación en vivo.

## Negocios y horarios

Desde **Lugares**, elige una categoría. Mueve el mapa antes de buscar para explorar otra zona. Toca un resultado o un marcador, o pulsa **Ver en el mapa** para encuadrar los negocios.

La app reutiliza tu token público de Mapbox. Tu cuenta debe permitir Search Box y Places, y se aplican la cobertura, límites y tarifas de estos servicios. No se añadió una clave alternativa ni se comprobó el acceso de tu cuenta desde este entorno. Cuando no hay horario o acceso a detalles se muestra **Horario no disponible**.

Fuentes técnicas:
- https://docs.mapbox.com/api/search/search-box/
- https://docs.mapbox.com/api/search/places/
- https://docs.mapbox.com/android/navigation/v2/examples/navigation-view-custom-location-puck/
- https://docs.mapbox.com/android/navigation/v2/examples/show-camera-transitions/

Los ejemplos de Navigation SDK v2 son referencias; el proyecto usa Maps SDK 11.18.1 y no mezcla sus dependencias con las del SDK Navigation v2.

## Cambios anteriores

- Versión 39: estilo vectorial claro, edificios con volumen a partir del zoom 14, cámara inclinada y control 2D/3D. La disponibilidad de edificios depende de los datos de Mapbox; no hay modelos de vehículos o edificios de Uber incorporados.
- Puck personalizado de vehículo conectado al GPS de Android y marcador visible del destino.
- Tocar o mantener pulsado el mapa selecciona un punto para ir. Rotación, zoom e inclinación con gestos habilitados; explorar libera el seguimiento y el botón de ubicación lo recupera.
- Tarjeta inferior propia: arrastra el asa o tócala para alternar entre el estado compacto y expandido. Favoritos, guardado, ayuda y diagnóstico utilizan paneles propios; interruptores dibujados a medida. Los permisos siguen siendo pantallas del sistema Android.
- Ruta dibujada en la vista previa con tiempo y distancia del proveedor. La navegación reutiliza esa respuesta. Mapbox Directions es el primer proveedor y OSRM la alternativa; ya no se oculta la causa tras «Ruta no disponible».
- No se utiliza una ubicación antigua de más de dos minutos para iniciar la vista previa; la ruta se vuelve a solicitar cuando llega la primera ubicación. Peticiones obsoletas no sustituyen la selección actual.
- Mapas flotantes usan el mismo cliente de rutas y actualizan el mapa en el hilo de interfaz.

- Versión 38: búsqueda unificada con menú integrado, tarjeta de destino compacta, acción Guardar destino, iconos de línea y drawer organizado con interruptor de mapa flotante.
- Texto oscuro en las barras del sistema y botón de ubicación por encima de la tarjeta de destino.

- Inicio directo en el mapa con drawer para Configuración, Estado y permisos, mapa flotante, Dasher y ayuda. Preparación del mapa integrada cuando falta el token.
- Componentes compartidos en `DriveFlyUi.kt`; tarjetas, colores y controles coherentes en el mapa, Ajustes, Estado y permisos y mapa flotante.
- Ajustes organizados con configuración de Mapbox al comienzo, validación del token, navegación de vuelta y permisos solicitados al usar las funciones.
- Búsqueda de Nominatim mediante JSON estructurado: independiente del orden de campos, reconoce caracteres escapados y descarta coordenadas inválidas.
- Rutas OSRM mediante JSON estructurado: geometría completa, coordenadas de cada maniobra y renovación de los pasos al recalcular.
- Destinos enviados al servicio con la acción correcta y arranque en primer plano.
- Favoritos Casa/Trabajo funcionales y persistentes.
- Ubicación reactivada al conceder permiso y liberada cuando se abandona la pantalla de navegación.
- Resultados de búsqueda/rutas obsoletos descartados cuando otra petición los reemplaza.
- Apertura y detección de Dasher, filtros de paquetes del lector y notificaciones corregidos.
- Repositorios públicos y Gradle Wrapper para abrir el proyecto fuera del equipo original; eliminadas rutas locales de repositorios y firma.

## Compilar

Abre esta carpeta en Android Studio, configura Android SDK 34 y usa JDK 17 o 21. Android Studio crea `local.properties` con la ubicación de tu SDK.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

APK generado: `app/build/outputs/apk/debug/app-debug.apk`.

`SearchParser.kt` y `RouteParser.kt` contienen la lectura de datos separada de la interfaz, con pruebas unitarias para formatos de respuesta de ambos servicios. Las pantallas mantienen las actividades nativas y la integración Mapbox del proyecto original.

## Validación realizada

Compilación del APK de desarrollo completada, doce pruebas unitarias aprobadas y Android Lint sin errores. Permanecen advertencias de mantenimiento del proyecto original, principalmente cadenas en la interfaz y APIs obsoletas.

La comprobación visual en emulador no se pudo completar: este entorno no dispone de aceleración KVM y Android no terminó su primer arranque dentro del plazo de prueba. Por tanto, la validación realizada es de compilación, lógica de lectura de datos y análisis estático; queda pendiente la revisión de las pantallas en un dispositivo.

## Límites de validación

La app necesita Internet y un token válido de Mapbox para mostrar el mapa. La búsqueda usa Nominatim; las rutas usan Mapbox Directions con alternativa OSRM. La disponibilidad y los límites de cada proveedor afectan a búsquedas y rutas. Directions puede tener costes o requerir permisos en tu cuenta Mapbox. El tiempo de la barra de navegación es aproximado y no incorpora tráfico en vivo.

Las integraciones de DoorDash y Waze dependen del contenido que sus apps comparten o muestran. No hay una conexión oficial a sus cuentas. Deben probarse con las aplicaciones reales, sus permisos y tu dispositivo. También queda pendiente validar seguimiento GPS, voz, batería y superposición durante un recorrido real.

El APK entregado lleva una firma de desarrollo nueva porque el ZIP original no incluía su almacén de claves. Para actualizar una instalación anterior manteniendo la firma, recompila con la clave original. Una firma distinta puede impedir instalarlo como actualización. La versión release no contiene una clave de producción y requiere configurar la firma antes de publicarla.

Versión 54: el mapa flotante usa TextureView para que el mapa, las esquinas y el marco participen juntos en la animación de cierre. Botones de mapa plegado con +/− y descripciones accesibles. Se ignoran pulsaciones durante la transición para evitar animaciones superpuestas.

Versión 55: reconoce destinos por nombre y localidad en pedidos activos de Dasher (por ejemplo Chase Bank, Saint Augustine, FL 32084). Consulta Search Box POI y exige nombre, ciudad y código postal coincidentes. Si hay varias sucursales distintas compatibles no escoge una automáticamente.

Versión 56: Configuración → Apariencia permite Claro / Oscuro / Sistema. Preferencia atómica compartida entre procesos. Mapbox Dark con edificios 3D y números de casa adaptados; Standard usa lightPreset night. Tarjetas antracita, controles, textos y barras del sistema con paleta semántica.

Señales: dataset compartido de OpenStreetMap, consulta Overpass en segundo plano a 1.8 km alrededor del GPS, renovación tras 800 m y límite mínimo de 60 s, con servidor alternativo. Ambos mapas muestran semáforos (símbolo rojo/amarillo/verde, sin estado en vivo), STOP octagonal, ceda el paso, pasos peatonales y cámaras de velocidad registrados. No garantiza cobertura completa ni proporciona señales en tiempo real. Mantiene el último dataset si falla la consulta. © OpenStreetMap contributors: https://www.openstreetmap.org/copyright

Referencias visuales consultadas:
- Uber: https://www.uber.com/ug/en/blog/from-light-to-dark-the-story-behind-dark-mode/
- Waze: https://support.google.com/waze/answer/13772684?hl=es-419
- Semáforos OSM: https://wiki.openstreetmap.org/wiki/Tag:highway=traffic_signals

Validación en entorno de desarrollo: pruebas JVM y lint. No hay dispositivo físico ni token Mapbox aquí. La consulta en vivo a Overpass devolvió timeout/504 durante la verificación; el mapa y las señales requieren validación en el teléfono.

Versión 57: control de distancia de 50 % a 1000 %, valor predeterminado 100 % sin cambios. Mapbox Traffic v1 muestra closed=yes como líneas rojas discontinuas y etiqueta CALLE CERRADA en ambos mapas. Directions usa driving-traffic y annotations=closure al calcular/recalcular. Si falla la conexión y se usa OSRM, se muestra que no hay tráfico/cierres en vivo; un NoRoute de Mapbox no se elude mediante OSRM. La cobertura y actualidad dependen de Mapbox.
Documentación de cierres: https://docs.mapbox.com/data/tilesets/reference/mapbox-traffic-v1/
Rutas: https://docs.mapbox.com/api/navigation/directions/

Versión 58: tras crear o recalcular las rutas, se reordenan explícitamente las capas de señales, etiquetas, cierres y puntos encima de la ruta, también en Light/Dark sin slots. El puck queda encima de esos detalles. En modo oscuro la capa de borde blanco de rutas principales/alternativas tiene opacidad cero y el puck usa flecha azul sin disco/halo blancos, tanto fijo como nativo 2D en exploración.

Versión 59: corrige el contraste en la pantalla de inicio/recuperación y usa la versión real en el diagnóstico. Tras tres posiciones GPS elegibles (menos de 25 m del destino, menos de 60 m de ruta restantes, velocidad menor a 3 m/s, precisión de 25 m o mejor), muestra una cuenta de 20 segundos y termina la ruta en ambos mapas. Alejarse más de 50 m con un GPS preciso cancela la cuenta. Terminar manualmente, cambiar de destino o recalcular anula el temporizador anterior. No reabre una ruta Dasher terminada mientras siga visible la misma dirección/etapa.

Versión 60: Configuración → Cámara y navegación → Ajustar cámara ofrece zoom según velocidad (desactivado inicialmente para conservar ajustes). Hasta 15 mph: distancia +20 %; entre 15 y 60 mph: distancia guardada; más de 60 mph: distancia −20 %. Velocidades y porcentajes editables, con signo positivo para alejar y negativo para acercar. La velocidad GPS se suaviza en un único animador compartido y el zoom usa un muelle amortiguado; lecturas sin velocidad fiable o ausentes 15 s recuperan el zoom base. Velocímetros animados en ambos mapas. Preferencias y borrador conservan todos los controles.

Inclinación propia del puck 0°–60°, independiente de la cámara. El seguimiento usa la flecha fija con perspectiva ajustable; al explorar se puede activar LocationPuck3D de Mapbox con modelRotation configurado. No se afirma que la flecha fija sea un modelo 3D nativo. Abrir mapa compatible desactiva el modelo nativo.

## Versión 61 · señales y destino

Semáforos y señales aumentan exactamente un 10% en ambos mapas. El destino tiene un marcador de tamaño constante en pantalla: fuera de la zona visible se mantiene en el borde con una flecha hacia su ubicación; dentro se coloca en la coordenada proyectada. Se actualiza con cada cambio de cámara, por encima de edificios y ruta, y se elimina al terminar o quitar el destino. La flecha del borde indica un destino fuera de pantalla, no su posición geográfica dentro del mapa. Requiere comprobar la proyección 3D en el teléfono.

## Versión 62 · pantalla durante navegación

Una sesión de ruta activa mantiene la pantalla encendida mediante FLAG_KEEP_SCREEN_ON en la actividad y keepScreenOn en las ventanas flotantes (mapa y botón de restaurar). La finalización manual o automática elimina el indicador; sin ruta se conserva la suspensión normal de Android. No cambia el tiempo global de suspensión ni utiliza wake locks. Comprobar en el dispositivo con ruta activa, flotante visible/oculto y tras finalizar.

## Versión 63 · destino por proximidad

Reemplaza el indicador de borde de la versión 61: nunca se fija ni desplaza el destino al borde del mapa. Durante una ruta se oculta más allá de una milla (1609,344 m), aparece progresivamente hasta 500 m y desde ahí conserva opacidad completa. Distancia geográfica desde la ubicación compartida, sin depender del zoom. Si la coordenada queda fuera del mapa no se muestra ninguna flecha. El marcador en la coordenada real sigue por encima de edificios y líneas. En la vista previa se muestra la selección sin filtro de distancia. Aplicado a ambos mapas; tres pruebas cubren distancia, transición y ausencia de ubicación.

El marcador usa verde #16A765 para recogidas/restaurantes y rojo #FF3B30 para entregas a clientes. La etapa de Dasher determina el color (un negocio también puede ser una entrega). Los restaurantes seleccionados en el mapa conservan su tipo en la sesión, al recalcular y reiniciar. Destinos genéricos sin tipo mantienen azul oscuro. Dos pruebas adicionales cubren clasificación y restauración.

## Versión 64 · finalizar sin cerrar el mapa

Al finalizar manualmente o tras la cuenta de llegada se limpia destino, ruta, alternativas y controles de navegación; el mapa permanece abierto con cámara libre y puck de exploración. El flotante conserva su visibilidad y sigue atendiendo nuevos pedidos de Dasher. Enviar una ruta al flotante tampoco cierra la actividad del mapa principal. La suspensión normal de pantalla vuelve al terminar, como en la versión 62. Comprobar llegada automática, terminación manual y siguiente pedido en el dispositivo.

## Versión 65 · tamaño del puck

Configuración → Cámara → Tu puck añade tamaño 50–250%, por defecto 100% conserva el tamaño anterior. Vista previa y Guardar y aplicar comparten el valor entre ambos mapas, navegación fija y puck nativo de exploración (2D o 3D). El tamaño del destino no cambia. Al ampliar el puck fijo se recalcula su marco alrededor del mismo centro, sin desplazar el anclaje de navegación. Restaurar valores recupera 100%; preferencias antiguas también usan 100%.

## Versión 66 · pin de destino

Sustituye el círculo con bandera por un pin de ubicación con punta y centro blanco sencillo. La punta, no el centro del dibujo, se ancla a la coordenada. Conserva los colores por etapa y la visibilidad por proximidad en ambos mapas.

Los pines aprobados tienen acabado con relieve: canto extruido dibujado, degradado de iluminación y centro blanco hundido. Es un efecto visual sobre una vista superpuesta, no un modelo GLB nativo: conserva tamaño legible y evita ocultarse detrás de edificios. Referencias consultadas: https://docs.mapbox.com/android/maps/examples/android-view/3D-model-layer/ y https://docs.mapbox.com/android/maps/guides/add-your-data/view-annotations/ .

## Versión 67 · Claude High / sherpa-onnx

Instalar el APK oficial del motor Android sherpa-onnx 1.13.8 para `vits-piper-es_MX-claude-high` (arm64-v8a, modelo completo), luego actualizar DriveFly y entrar en Configuración → Voz de navegación → Claude High → Probar voz. La descarga también está en esa pantalla. No hace falta cambiar la voz predeterminada global de Android. DriveFly selecciona explícitamente `com.k2fsa.sherpa.onnx.tts.engine`.

El motor se distribuye como aplicación separada y mantiene la inferencia ONNX y sus dependencias nativas fuera del proceso del mapa. DriveFly no incluye el AAR ni carga sherpa JNI. La voz funciona sin conexión una vez instalada; esto no hace que el mapa o el cálculo de rutas funcionen sin red. Si no está instalado el motor, se usa la voz Android disponible y la pantalla lo indica. Otros paquetes de modelos de sherpa pueden compartir el mismo nombre de aplicación: instalar específicamente Claude High. Las opciones Android y Sin voz y la velocidad 80–120% se guardan en un archivo atómico compartido entre procesos.

Las maniobras, recálculos y finalización se anuncian una sola vez desde NavigationSession, incluyendo el flotante sobre Dasher. Android TTS reproduce el audio con uso de navegación; se pide foco transitorio, se detiene al perderlo y se descartan indicaciones antiguas durante la carga. El cliente se libera tras terminar o después de cinco minutos inactivo.

Proveniencia: https://k2-fsa.github.io/sherpa/onnx/tts/all/Spanish/vits-piper-es_MX-claude-high.html ; motor y código: https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8/android/SherpaOnnxTtsEngine . Modelo original y ficha: https://huggingface.co/rhasspy/piper-voices/tree/main/es/es_MX/claude/high . La ficha identifica idioma es_MX, un hablante, calidad high, 22050 Hz y dataset Apache-2.0. El APK de motor se entrega sin modificar, con su firma original k2-fsa. El modelo ONNX dentro de ese APK coincide por SHA256 con el paquete oficial de modelo completo.

Validación de síntesis en Linux con sherpa-onnx 1.13.8, modelo completo CPU: audio español de 4,34 s a 22050 Hz, pico 0,694; carga 1,15 s y generación 0,60 s en este entorno (no son mediciones del TCL). Muestra: DriveFly-Claude-muestra.wav. La conexión Android al motor, reproducción y latencia sobre el mapa requieren prueba en el teléfono. Cinco pruebas nuevas cubren persistencia de opciones, límites y deduplicación de maniobras.

Validación de la versión 74: compilación correcta, 154 pruebas unitarias aprobadas y lint con 0 errores (70 advertencias). Pendiente de prueba física en TCL T807W.

Validación v77: 165 pruebas aprobadas; compilación y lint correctos. Descarga externa verificada por SHA-256: 13f08d126b52d68264a3f30f4617d16bd3cadba5cbc1274326ec5315d73c03eb. Prueba visual en dispositivo pendiente. APK TCL: https://files.catbox.moe/fl02gp.apk

Entrega v79: compilación y lint finales correctos; descarga SHA-256 verificada: e58b5eb57c535594dee20af32bea01ee7652172e032191d19fb63c7390734bab. APK TCL: https://files.catbox.moe/jpdc87.apk

Validación v80: compilación correcta, 171 pruebas aprobadas y lint sin errores (71 advertencias). Descarga verificada por SHA-256: 5a06ad9e24445924df901cd5437567fec5e5b1870b67794f4b58c352917ba265. APK TCL: https://files.catbox.moe/oxpdbb.apk

Validación v81: compilación y lint correctos; 175 pruebas aprobadas. Descarga completa verificada SHA-256: e1befe552a96cd0be58eec12812ad6760b2d231466ada550b6a3812e3e435ec3. APK TCL: https://files.catbox.moe/jz6weo.apk

Validación v82: compilación y lint correctos; 179 pruebas aprobadas. Descarga SHA-256 verificada: 73c0eca918a4925ea65683f51e587c19000dac1d7bfa35a6f1caa464e371621d. APK TCL: https://files.catbox.moe/6sc1f6.apk
