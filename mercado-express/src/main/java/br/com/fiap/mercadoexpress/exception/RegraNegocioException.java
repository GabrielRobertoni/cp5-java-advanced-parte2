package br.com.fiap.mercadoexpress.exception;

/** Violação de uma regra de negócio (ex.: e-mail já cadastrado). */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
