package com.atenea.unimarket.negocio;

/** La acción rompe una regla del chat; el mensaje le explica cuál al usuario. */
public class ChatNoPermitidoException extends RuntimeException {

    public ChatNoPermitidoException(String mensaje) {
        super(mensaje);
    }
}