package br.com.fiap.emailservice.service;

import br.com.fiap.emailservice.mensageria.dto.EmailRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

/** Monta o corpo HTML do e-mail de confirmação a partir do template Thymeleaf. */
@Component
@RequiredArgsConstructor
public class ModeloEmail {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final ITemplateEngine templateEngine;

    /** @return HTML do e-mail, ou null quando a mensagem não traz os dados do pedido (e-mail só texto) */
    public String html(EmailRequest request) {
        if (request.pedido() == null) {
            return null;
        }
        Context contexto = new Context(PT_BR);
        contexto.setVariable("email", request);
        contexto.setVariable("pedido", request.pedido());
        return templateEngine.process("email/confirmacao-pagamento", contexto);
    }
}
