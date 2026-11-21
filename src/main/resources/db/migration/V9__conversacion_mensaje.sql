-- Chat privado entre un cliente y el ofertador de un servicio (CHAT-USR-H01).
-- Una conversación es única por (servicio, cliente); el ofertador sale del servicio.
CREATE TABLE conversacion (
    id INTEGER PRIMARY KEY,
    servicio_id INTEGER NOT NULL REFERENCES servicio (id),
    cliente_id INTEGER NOT NULL REFERENCES usuario (id),
    fecha_creacion TIMESTAMP NOT NULL,
    UNIQUE (servicio_id, cliente_id)
);

-- Para listar las conversaciones en las que participa un usuario como cliente.
CREATE INDEX conversacion_cliente ON conversacion (cliente_id);

CREATE TABLE mensaje (
    id INTEGER PRIMARY KEY,
    conversacion_id INTEGER NOT NULL REFERENCES conversacion (id),
    autor_id INTEGER NOT NULL REFERENCES usuario (id),
    -- trim() de SQLite solo quita espacios por defecto: se le pasan también tab y saltos de línea.
    contenido TEXT NOT NULL
        CHECK (length(trim(contenido, ' ' || char(9) || char(10) || char(13))) > 0
               AND length(contenido) <= 1000),
    fecha_envio TIMESTAMP NOT NULL
);

-- Leer los mensajes de una conversación en orden.
CREATE INDEX mensaje_conversacion ON mensaje (conversacion_id, id);