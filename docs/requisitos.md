# Requisitos del producto

Base funcional de UniMarket. Cada caso reúne sus reglas, excepciones e historias de usuario.

## Propósito y actores

UniMarket es una plataforma de compra y venta de servicios de la comunidad universitaria. Incluye tutorías, clases privadas, asesorías de tesis y trabajos, y servicios o entregas de proyectos de diseño y escritura. E3 p. 2.

Cliente y ofertador son roles del mismo usuario. Un usuario puede buscar y contratar servicios, publicar ofertas y valorar o denunciar a su contraparte. Denunciante también es un rol, no una cuenta distinta. El perfil contiene los datos públicos. E2 pp. 1-2, 24; E3 pp. 6, 8-9.

CU-01 y CU-02 son los casos de mayor prioridad en E3 p. 3. Los otros cinco casos siguen dentro del alcance documental.

## CU-01: comprar / negociar servicio

Actor principal: cliente. Requiere perfiles de cliente y ofertador y un servicio publicado. Fuentes: E2 pp. 3-4, 25-26; E3 pp. 2, 5-6, 10.

1. El cliente consulta la tarifa por hora y la intensidad horaria de la oferta.
2. Inicia una solicitud aceptando las condiciones publicadas o proponiendo otra tarifa. El cliente no modifica la intensidad horaria.
3. El sistema registra la solicitud en `NEGOCIANDO` y notifica al ofertador.
4. El ofertador acepta, rechaza o contrapropone tarifa e intensidad. El cliente puede aceptar o volver a proponer solo tarifa.
5. Se conservan todas las propuestas, sus autores, fechas y resultado. No hay límite fijo de rondas.
6. La aceptación de la propuesta vigente por la contraparte registra el acuerdo y activa la solicitud. No basta la aceptación unilateral de las condiciones originales por el cliente.
7. Cuando el cliente acepta, se cancelan sus otras solicitudes abiertas para el mismo servicio buscado. Solo la primera aceptación concurrente debe producir el acuerdo. La agrupación de solicitudes competidoras se detalla en las preguntas abiertas de este caso.
8. Se notifica el acuerdo a ambas partes y las cancelaciones a los ofertadores no seleccionados.
9. El cliente confirma que recibió el servicio y se cierra la solicitud, conservando el historial y el acuerdo.

Excepciones:

- Rechazo sin contrapropuesta: cancelar la solicitud, notificar al cliente y mantener el servicio disponible.
- Retiro del cliente o negociación sin acuerdo: terminar sin compra. La solicitud queda `CANCELADA`.
- Una semana sin respuesta del ofertador desde la última propuesta del cliente: finalizar por inactividad y notificar al cliente.
- Falta de confirmación de finalización: el servicio se cierra al cumplirse el plazo o entregarse lo acordado. El plazo de confirmación automática es de una semana; falta definir desde qué evento se cuenta y su relación con el plazo de entrega.
- Si el ofertador no confirma disponibilidad después del acuerdo, cancelar el servicio activo y notificar al cliente. Falta definir el flujo y plazo de esa confirmación.

Por definir en compra y negociación:

- Cómo identificar las solicitudes del mismo servicio buscado entre ofertas de distintos ofertadores. La cancelación se limita a ese grupo, no a todas las solicitudes del cliente.
- Si la cancelación de competidoras también se dispara cuando acepta el ofertador y cómo garantizar que solo el primer acuerdo concurrente se registra.
- Desde cuándo corre el plazo de entrega y cómo se refleja su vencimiento en la solicitud. El [ciclo de estados](diseno.md#estados) distingue el cierre del servicio del cumplimiento de la solicitud.

Historias de E2 pp. 25-26: `COMPRA-CLI-H01` contratar condiciones publicadas; `H02` negociar; `H03` terminar sin compra; `H04` evitar contrataciones simultáneas para la misma búsqueda; `H05` confirmar finalización. Los cinco identificadores comparten el prefijo `COMPRA-CLI-`.

## CU-02: ofertar y editar servicio

Actor principal: ofertador. E2 pp. 6-7, 26-27; E3 pp. 3, 7-8, 11.

- Campos obligatorios: categoría, título, descripción, tarifa por hora e intensidad horaria.
- Campos opcionales: experiencia, especialidad y horarios de disponibilidad.
- Validar antes de publicar. La tarifa por hora es un valor entero en pesos colombianos y debe ser mayor que cero. La intensidad horaria son horas por semana, entre 1 y 168.
- Las categorías son una lista fija: Tutorías, Clases particulares, Asesoría de tesis y trabajos, Diseño y Escritura. Un futuro rol de administrador las gestionará.
- Crear el servicio en `DISPONIBLE`, vincularlo al ofertador y a su categoría, mostrarlo en catálogo y confirmar la publicación.
- Para editar un servicio propio, cargar sus valores actuales y aplicar las validaciones de publicación.
- Si hay una negociación o transacción en curso, bloquear cambios a tarifa e intensidad; permitir modificar los demás campos. Esto no impide contrapropuestas dentro de la negociación.
- Si una nueva publicación es muy similar a otra del mismo ofertador, advertir y permitir continuar o editar la existente. La advertencia no prohíbe duplicados. Falta definir el criterio de similitud.

Historias: `OFERTA-OFE-H01` publicar; `OFERTA-OFE-H02` actualizar; `OFERTA-OFE-H03` revisar posible duplicado.

## CU-03: verificar perfil

Actor: persona de la comunidad universitaria. E2 pp. 9-10, 27-28; E3 pp. 3-4.

El inicio de sesión con Google es el único medio de autenticación y acredita la pertenencia institucional. Solo se admiten cuentas con correo verificado del dominio `unal.edu.co`. Toda cuenta registrada queda verificada, por lo que no hay códigos por correo o SMS, contraseñas ni intentos de verificación que registrar. Iniciar sesión no crea la cuenta. Mientras la identidad de Google no tenga perfil, toda página redirige al formulario de [CU-04](#cu-04-crear-y-actualizar-perfil); al guardarlo se crean juntos el usuario y su perfil. La cuenta se identifica por el identificador de cuenta de Google, no por el correo.

Esta decisión reemplaza el flujo de códigos de E2 y E3 y sus historias `VERIFICACION-USR-H01` y `VERIFICACION-USR-H02`.

## CU-04: crear y actualizar perfil

Actor: persona autenticada con Google. E2 pp. 12-13, 28-29.

El formulario muestra el correo institucional de Google sin permitir editarlo y el nombre de Google como valor inicial editable. Incluye además carrera, semestre, avance, intereses, experiencia, especialidad, teléfono de contacto y horarios de disponibilidad. Completar el perfil es obligatorio para usar la plataforma y crea la cuenta. Al editar un perfil existente, precargar los datos actuales. Validar los datos, impedir campos obligatorios vacíos y rechazar formatos inválidos, incluido el teléfono. Confirmar el guardado, registrar su fecha y reflejar los cambios donde se muestra el perfil.

Si el usuario cambia disponibilidad con un servicio activo cuyo horario ya fue acordado, advertir antes de actualizar. Aplicar el cambio a futuras negociaciones y conservar los horarios ya pactados.

Los horarios de disponibilidad son franjas semanales de día, hora de inicio y hora de fin; su formato de almacenamiento está en [persistencia](diseno.md#persistencia). El teléfono de contacto es obligatorio y debe ser un celular colombiano de 10 dígitos que empiece por 3, con prefijo `+57` opcional; se guarda sin prefijo. Los horarios son opcionales. Falta definir cuáles de los campos académicos y de servicios serán obligatorios. El teléfono solo lo ve el dueño del perfil; los demás usuarios contactan al ofertador por el chat de UniMarket.

Para crear la cuenta hay que marcar una casilla obligatoria que acepta la política de tratamiento de datos personales (Ley 1581 de 2012); se guarda la fecha de aceptación. La política se publica en `/politica-datos`, enlazada desde el formulario y desde el encabezado, y se puede leer antes de tener perfil. Las cuentas creadas antes de esta regla quedan sin fecha de aceptación y no se les pide aceptarla. Historia: `DATOS-USR-H01`.

Historias: `PERFIL-USR-H01` completar por primera vez; `PERFIL-USR-H02` mantener actualizado.

## CU-05: navegar servicios disponibles

Actor: cliente. E2 pp. 15-16, 29-30.

- Listar solo servicios `DISPONIBLE` de perfiles no bloqueados. Excluir activos, cerrados y cancelados.
- Ordenar según la prioridad elegida por el cliente. Por defecto, fecha de publicación ascendente.
- Permitir uno o más filtros por categoría y búsqueda por palabra clave o nombre de persona o negocio.
- Sin criterios, conservar el catálogo completo de servicios disponibles. Sin coincidencias, informar y sugerir ajustar criterios.
- Mostrar detalle con descripción, categoría, tarifa, intensidad, perfil e información del ofertador y calificaciones. El detalle no muestra el teléfono del ofertador; el cliente lo contacta por el chat de UniMarket.
- Si el servicio deja de estar disponible durante la consulta, informar y regresar al listado.
- Desde el detalle, permitir guardarlo en la colección del cliente y confirmar la acción.

La búsqueda por palabra clave o nombre cubre título, descripción, especialidad, experiencia y nombre del ofertador. No existe entidad de negocio. La comparación ignora mayúsculas solo en letras sin tilde, por una limitación de SQLite.

Historias: `BUSQUEDA-CLI-H01` consultar catálogo y detalle; `BUSQUEDA-CLI-H02` buscar; `BUSQUEDA-CLI-H03` guardar.

## CU-06: dejar reseña

Actor: cualquiera de las dos partes de un servicio finalizado. E2 pp. 18-19, 30-31; E3 p. 2.

Exigir una solicitud cerrada entre autor y contraparte. Bloquear reseñas de solicitudes activas o canceladas. La calificación numérica es obligatoria y el comentario opcional. Asociar la reseña a la experiencia cerrada y al perfil reseñado, publicarla y actualizar el promedio del perfil.

Solo se admite una reseña por autor y experiencia. Ante otro intento, bloquear el segundo envío y ofrecer editar la existente. Una denuncia activa sobre la misma experiencia no impide reseñar; publicar con marca "en revisión" hasta resolverla. Falta definir la escala de calificación y la política de edición. La resolución de denuncias está por definir en [CU-07](#cu-07-dejar-denuncia).

Historias: `RESENA-USR-H01` calificar; `RESENA-USR-H02` acceder a edición; `RESENA-USR-H03` publicar con denuncia activa.

## CU-07: dejar denuncia

Actor: participante que considera que la contraparte incumplió. E2 pp. 21-22, 31-32; E3 p. 2.

Exigir una solicitud `CERRADA` entre denunciante y contraparte. Bloquear denuncias de solicitudes `NEGOCIANDO`, `ACTIVA` o `CANCELADA`.

Seleccionar la experiencia del historial, indicar motivo y aportar al menos una evidencia textual o imagen. Validar que existe entre ambas partes y que el mismo denunciante no la haya denunciado antes. Registrar la denuncia contra la contraparte y confirmar el registro. Rechazar denuncias ajenas o duplicadas sin incrementar conteos.

Contar denuncias válidas de servicios distintos. Al alcanzar el umbral, aplicar automáticamente la sanción predefinida y notificar al denunciado. Por debajo, conservar la denuncia sin sanción inmediata. Las sanciones posibles son suspensión de publicaciones, marca "en observación" o bloqueo. Falta seleccionar la sanción, fijar el umbral y definir cómo se determina la validez y se resuelve una denuncia. El conteo usa denuncias válidas, no todas las registradas. Las facultades de un administrador o moderador aún no están definidas.

Historias: `DENUNCIA-USR-H01` registrar con evidencia; `DENUNCIA-USR-H02` evitar duplicados o denuncias ajenas; `DENUNCIA-USR-H03` aplicar consecuencias acumuladas.

## Metas sin caso de uso independiente

E2 p. 2 menciona reprogramar o cancelar un servicio acordado, consultar agenda, comprobar confiabilidad, guardar servicios, responder solicitudes, retirar o pausar ofertas y consultar reputación. Algunas están desarrolladas dentro de los siete casos, como guardar o responder. Agenda, reprogramación y pausa no tienen un flujo completo aprobado en estas fuentes. La compensación se menciona como interés del ofertador, pero no se especifican pasarela de pagos, cobros ni reembolsos.
