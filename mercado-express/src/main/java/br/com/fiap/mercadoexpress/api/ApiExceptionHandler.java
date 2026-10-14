package br.com.fiap.mercadoexpress.api;

import br.com.fiap.mercadoexpress.exception.RecursoNaoEncontradoException;
import br.com.fiap.mercadoexpress.exception.RegraNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** Erros da API REST em JSON (RFC 9457 - Problem Details). */
@RestControllerAdvice(basePackageClasses = PagamentoApi.class)
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException e) {
        List<String> erros = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .sorted()
                .toList();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados da compra inválidos.");
        problema.setTitle("Requisição inválida");
        problema.setProperty("erros", erros);
        return problema;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail jsonInvalido(HttpMessageNotReadableException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "JSON inválido. Exemplo: {\"formaPagamento\":\"PIX\",\"itens\":[{\"produtoId\":1,\"quantidade\":2}]}"
                        + " - formas aceitas: PIX, CARTAO_CREDITO, CARTAO_DEBITO.");
        problema.setTitle("Requisição inválida");
        return problema;
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail tipoNaoSuportado(HttpMediaTypeNotSupportedException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Envie o corpo em JSON (Content-Type: application/json).");
        problema.setTitle("Tipo de conteúdo não suportado");
        return problema;
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ProblemDetail regraNegocio(RegraNegocioException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        problema.setTitle("Compra não realizada");
        return problema;
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problema.setTitle("Não encontrado");
        return problema;
    }
}
