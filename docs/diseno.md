# Diseño del proyecto

Base de arquitectura, dominio y persistencia. Para comportamiento y validaciones, consultar el [caso de uso](requisitos.md) correspondiente. Los diagramas de referencia están enlazados en cada sección.

## Arquitectura

El sistema es un monolito con base de datos conectada y arquitectura por capas.

| Capa | Tipo | Responsabilidad |
| --- | --- | --- |
| Presentación | Cerrada | Interfaz y recepción de peticiones |
| Negocio | Cerrada | Reglas del marketplace y validaciones |
| Servicios compartidos | Abierta | Historial, fechas, auditoría y utilidades de validación |
| Datos | Cerrada | Repositorios y almacenamiento relacional en archivo |
| Externos | Cerrada | Comunicación con proveedores, por ejemplo correo o SMS |

Las capas cerradas protegen las reglas mediante el paso por el nivel correspondiente. La capa abierta permite omitir utilidades que no se necesitan. La diapositiva no selecciona motor de base de datos. En p. 12, el diseño separa además las entidades de dominio y muestra dependencias de los servicios de negocio hacia repositorios y notificación.

## Entidades y asociaciones

Transcripción del [modelo de dominio, E3 p. 9](diagramas/modelo-dominio.png).

| Entidad | Atributos mostrados |
| --- | --- |
| Usuario | `nombre: String`, `correoInstitucional: String` |
| Perfil | `carrera: String`, `semestre: Integer`, `avance`, `intereses`, `experiencia`, `especialidad`, `telefonoContacto`, `horariosDisponibilidad`: String |
| Categoria | `nombre: String` |
| Servicio | `titulo: String`, `descripcion: String`, `tarifaPorHora: Integer`, `intensidadHoraria: Integer`, `experiencia`, `especialidad`, `horariosDisponibilidad`: String; `fechaPublicacion: DateTime`, `estado: EstadoServicio` |
| SolicitudCompra | `fechaCreacion: DateTime`, `fechaUltimaActividad: DateTime`, `estado: EstadoSolicitud` |
| Propuesta | `tarifaPorHora: Integer`, `intensidadHoraria: Integer`, `numeroRonda: Integer`, `fechaEnvio: DateTime` |
| Notificador | `mensaje: String`, `leida: Boolean`, `fechaCreacion: DateTime`, `tipo: TipoNotificacion` |

Asociaciones visibles:

- Usuario tiene un Perfil, relación 1 a 1.
- Un usuario publica cero o más servicios como ofertador e inicia cero o más solicitudes como cliente.
- Cada servicio pertenece a un ofertador y a una categoría. Una categoría clasifica cero o más servicios.
- Un servicio recibe cero o más solicitudes. Cada solicitud corresponde a un servicio y un cliente.
- Una solicitud agrega una o más propuestas. Cada propuesta tiene un autor Usuario; un usuario puede escribir cero o más propuestas.
- El diagrama asocia los registros llamados `Notificador` a un usuario y una solicitud; cada uno puede tener cero o más registros.

Las páginas 6 y 8 descartan entidades separadas para cliente, ofertador, negociación, intercambio, formulario y buscador. Negociación se representa mediante SolicitudCompra y el intercambio mediante sus propuestas. Categoría sí es entidad. `numeroRonda` ordena propuestas.

`Notificador` en el modelo tiene datos de una notificación persistida; `INotificador` y `NotificadorCorreo` en diseño son el mecanismo de envío. Son responsabilidades distintas. Las notificaciones de la aplicación se persisten en la transacción de la solicitud y se anuncian por SSE después del commit. Los tipos son `SOLICITUD_RECIBIDA`, `CONTRAPROPUESTA_RECIBIDA`, `ACUERDO_CONFIRMADO` y `SOLICITUD_RECHAZADA`. Leer y descartar son estados persistidos independientes; descartar retira el aviso del desplegable, pero lo conserva en el historial. Falta definir el envío externo y sus reintentos y fallos. Reseñas y denuncias no aparecen modeladas aquí; eso no las retira del alcance.

## Estados

E3 p. 2 define dos ciclos distintos, confirmados en p. 9.

| EstadoServicio | Significado |
| --- | --- |
| `DISPONIBLE` | Publicado y visible, sin solicitud activa; puede tener solicitudes negociando |
| `ACTIVO` | Existe una solicitud con acuerdo y aún no se ha cumplido el plazo |
| `CERRADO` | Se cumplió el plazo o se entregó lo acordado |
| `CANCELADO` | No se cumplió lo acordado y no se continuará la entrega |

| EstadoSolicitud | Significado |
| --- | --- |
| `NEGOCIANDO` | El cliente mostró interés e inició negociación |
| `ACTIVA` | Se aceptó una propuesta y comenzó el plazo de entrega |
| `CERRADA` | Se cumplió la entrega; habilita reseñas y denuncias |
| `CANCELADA` | No se continuará la solicitud; no habilita reseña ni denuncia |

El acuerdo lleva la solicitud de `NEGOCIANDO` a `ACTIVA`; la confirmación del cliente la lleva a `CERRADA`. Negociaciones fallidas terminan en `CANCELADA`. Falta definir la sincronización entre ambos ciclos, el efecto sobre solicitudes de otros clientes y si una oferta puede volver a estar disponible después de un cierre. También falta precisar cómo representar el incumplimiento posterior al acuerdo frente a una negociación fallida.

## Secuencia de aceptación de propuesta

[Diagrama original, E3 p. 10](diagramas/secuencia-compra.png).

1. Cliente llama `aceptarPropuesta(solicitudId)` en `SolicitudCompraController`.
2. El controlador pasa `solicitudId` y `usuarioId` a `SolicitudCompraService`.
3. El servicio consulta `ISolicitudCompraRepository.buscarPorId`.
4. Invoca `solicitud.aceptarPropuesta(usuarioId)`, que valida y cambia el estado. La nota dice `ACTIVO`; el enum correcto de solicitud usa `ACTIVA`.
5. Consulta `buscarAbiertasPorCliente(clienteId)` y cancela las demás del mismo servicio buscado.
6. Guarda cada solicitud modificada, incluida la aceptada.
7. Notifica a cliente y ofertador mediante `INotificador` y retorna acuerdo confirmado.

Al implementar esta secuencia, completar la transacción, el manejo de errores y la actualización del estado de Servicio. Aplicar el alcance de cancelación definido en [CU-01](requisitos.md#cu-01-comprar--negociar-servicio).

## Secuencia de publicación

[Diagrama original, E3 p. 11](diagramas/secuencia-oferta.png).

1. Ofertador envía datos a `ServicioController.publicarServicio`.
2. El controlador llama `ServicioService.publicar(ofertadorId, datos)`.
3. El servicio carga usuario con `IUsuarioRepository`. El diagrama comprueba perfil verificado; con [CU-03](requisitos.md#cu-03-verificar-perfil) todo usuario autenticado lo está.
4. Carga categoría con `ICategoriaRepository`.
5. Crea Servicio con ofertador, categoría y datos; llama `validar()` y lo deja `DISPONIBLE`.
6. Guarda con `IServicioRepository` y confirma publicación.

Aunque se titula "Ofertar y Editar", esta secuencia representa publicación. Las restricciones de edición están en [CU-02](requisitos.md#cu-02-ofertar-y-editar-servicio).

## Interfaces de diseño

[Diagrama original, E3 p. 12](diagramas/clases-diseno.png).

- `ServicioController`: `publicarServicio(datos: ServicioDTO)`, `editarServicio(servicioId, datos: ServicioDTO)`.
- `SolicitudCompraController`: `crearSolicitud(servicioId, tarifa)`, `contraproponer(solicitudId, tarifa, intensidad)`, `aceptarPropuesta(solicitudId)`, `confirmarFinalizacion(solicitudId)`.
- `ServicioService`: `publicar(ofertadorId, datos)`, `editar(servicioId, ofertadorId, datos)` y operación privada `verificarOfertador(ofertadorId)`.
- `SolicitudCompraService`: `crear(clienteId, servicioId, tarifa)`, `contraproponer(solicitudId, usuarioId, tarifa, intensidad)`, `aceptarPropuesta(solicitudId, usuarioId)`, `confirmarFinalizacion(solicitudId, clienteId)` y operación privada `cancelarSolicitudesAbiertas(clienteId, solicitudId)`.
- `IUsuarioRepository` e `ICategoriaRepository`: `buscarPorId(id)`.
- `IServicioRepository`: `guardar(s)`, `buscarPorId(id)`.
- `ISolicitudCompraRepository`: `guardar(sc)`, `buscarPorId(id)`, `buscarAbiertasPorCliente(clienteId)`.
- Implementaciones: `UsuarioRepositorySQL`, `CategoriaRepositorySQL`, `ServicioRepositorySQL`, `SolicitudCompraRepositorySQL`.
- `INotificador.notificar(usuarioId, mensaje)`, implementado por `NotificadorCorreo`.

Para el parámetro intensidad, aplicar los permisos por rol de [CU-01](requisitos.md#cu-01-comprar--negociar-servicio). El diagrama no detalla ServicioDTO.

## Persistencia

Basado en el [esquema, E3 p. 13](diagramas/persistencia.png). Todas las tablas tienen `id integer` como clave primaria. `NN` significa NOT NULL. `usuario`, `perfil`, `categoria` y `servicio` reflejan las decisiones de implementación descritas abajo; las demás tablas transcriben la imagen sin inferir restricciones adicionales.

| Tabla | Columnas aparte de id |
| --- | --- |
| `usuario` | `google_sub varchar(255) NN UNIQUE`, `nombre varchar(100) NN`, `correo_institucional varchar(150) NN UNIQUE`, `fecha_creacion timestamp NN` |
| `perfil` | `usuario_id integer NN UNIQUE`, `telefono_contacto varchar(20)`, `horarios_disponibilidad text (JSON)`, `fecha_creacion timestamp NN`, `fecha_actualizacion timestamp NN` |
| `categoria` | `nombre varchar(100) NN UNIQUE` |
| `servicio` | `ofertador_id integer NN`, `categoria_id integer NN`, `titulo varchar(100) NN`, `descripcion text NN`, `tarifa_por_hora integer NN`, `intensidad_horaria integer NN`, `experiencia text`, `especialidad varchar(100)`, `horarios_disponibilidad text (JSON)`, `fecha_publicacion timestamp NN`, `estado varchar(20) NN` |
| `solicitud_compra` | `cliente_id integer NN`, `servicio_id integer NN`, `fecha_creacion timestamp NN`, `fecha_ultima_actividad timestamp`, `estado estado_solicitud NN` |
| `propuesta` | `solicitud_id integer NN`, `autor_id integer NN`, `tarifa_por_hora integer NN`, `intensidad_horaria integer`, `numero_ronda integer NN`, `fecha_envio timestamp NN` |
| `notificador` | `usuario_id integer NN`, `solicitud_id integer NN`, `mensaje text NN`, `leida boolean NN`, `fecha_creacion timestamp NN`, `tipo tipo_notificacion NN` |

Las claves foráneas enlazan perfil con usuario; servicio con usuario y categoría; solicitud con cliente y servicio; propuesta con solicitud y autor; notificador con usuario y solicitud.

Los campos académicos y de servicios del formulario de [CU-04](requisitos.md#cu-04-crear-y-actualizar-perfil) (carrera, semestre, avance, intereses, experiencia, especialidad) se agregarán a `perfil` en una migración posterior. `google_sub` guarda el claim `sub` de Google, identificador estable de la cuenta. El esquema original incluía `password_hash`, `telefono` en usuario y `verificado` en perfil; se retiraron porque no hay contraseñas, el teléfono de contacto vive en perfil y toda cuenta queda verificada al iniciar sesión. Usuario y perfil se crean en la misma transacción al guardar el formulario de [CU-04](requisitos.md#cu-04-crear-y-actualizar-perfil); no existe usuario sin perfil. `perfil.horarios_disponibilidad` guarda un arreglo JSON de franjas semanales, por ejemplo `[{"dia":"LUNES","inicio":"08:00","fin":"10:00"}]`, con `dia` de `LUNES` a `DOMINGO`, horas `HH:MM` e `inicio` anterior a `fin`. SQLite no tiene tipo JSON de columna, así que se guarda como texto con un `CHECK` de `json_valid`; la aplicación valida la forma de cada franja. Los valores de `tipo_notificacion` son los enumerados en entidades y asociaciones.

En `servicio`, la tarifa se guarda en pesos colombianos enteros, sin decimales, porque SQLite almacena `decimal` como número de punto flotante; `propuesta` usa la misma unidad. La intensidad horaria son horas por semana, entre 1 y 168. `titulo` se agregó al esquema original para listados y búsqueda. La imagen marcaba sin NN la descripción y la intensidad, obligatorias en CU-02. El esquema original llamaba `ofertante_id` a la columna del ofertador. SQLite no tiene tipos enumerados, así que `estado` es texto con un `CHECK` de los valores de `EstadoServicio`, y `horarios_disponibilidad` usa el mismo formato JSON que `perfil`. Las categorías son una lista fija creada por la migración hasta que exista un rol de administrador que las gestione.

El esquema cubre principalmente compra y publicación. Falta representar el grupo de solicitudes competidoras, fecha límite, horarios acordados y acuerdo aceptado explícito, además de persistencia para reseñas, denuncias y guardados. Completar esas estructuras para cumplir los casos de uso.
