package com.atenea.unimarket.negocio;

/** La conversación no existe o el usuario no participa en ella: en ambos casos se responde igual. */
public class ConversacionNoEncontradaException extends RuntimeException {

    public ConversacionNoEncontradaException() {
        super("La conversación no existe.");
    }
}