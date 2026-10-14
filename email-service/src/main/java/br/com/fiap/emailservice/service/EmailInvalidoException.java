package br.com.fiap.emailservice.service;

/** A mensagem não tem os dados mínimos para um e-mail (vai para a dead letter queue). */
public class EmailInvalidoException extends RuntimeException {

    public EmailInvalidoException(String mensagem) {
        super(mensagem);
    }
}
