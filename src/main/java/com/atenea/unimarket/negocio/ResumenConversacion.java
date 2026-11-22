package com.atenea.unimarket.negocio;

import com.atenea.unimarket.datos.modelo.Conversacion;
import com.atenea.unimarket.datos.modelo.Mensaje;
import com.atenea.unimarket.datos.modelo.Usuario;

/**
 * CHAT-USR-H02: una conversación tal como aparece en la lista del usuario: con quién es, sobre qué
 * servicio y cuál fue su último mensaje.
 */
public record ResumenConversacion(Conversacion conversacion, Usuario otraPersona, Mensaje ultimoMensaje) {

    /** El resumen desde el punto de vista de {@code usuarioId}, que participa en la conversación. */
    static ResumenConversacion de(Mensaje ultimoMensaje, Integer usuarioId) {
        Conversacion conversacion = ultimoMensaje.getConversacion();
        Usuario otraPersona = conversacion.getCliente().getId().equals(usuarioId)
                ? conversacion.getOfertador()
                : conversacion.getCliente();
        return new ResumenConversacion(conversacion, otraPersona, ultimoMensaje);
    }

    /** Si el último mensaje lo escribió el propio usuario (para mostrarlo como "Tú: ..."). */
    public boolean ultimoEsDe(Integer usuarioId) {
        return ultimoMensaje.getAutor().getId().equals(usuarioId);
    }
}
