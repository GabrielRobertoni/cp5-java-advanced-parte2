package br.com.fiap.emailservice.service;

import br.com.fiap.emailservice.mensageria.dto.EmailRequest;
import br.com.fiap.emailservice.model.EmailModel;
import br.com.fiap.emailservice.model.StatusEmail;
import br.com.fiap.emailservice.repository.EmailRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Envia o e-mail e registra o resultado na tabela TDS_Emails_Mercado (PROCESSING -> SENT ou ERROR),
 * como o EmailService visto em aula.
 */
@Slf4j
@Service
public class EmailService {

    private final EmailRepository repository;
    private final JavaMailSender mailSender;
    private final ModeloEmail modeloEmail;
    private final Validator validator;
    private final String remetentePadrao;
    private final String nomeRemetente;

    public EmailService(EmailRepository repository, JavaMailSender mailSender, ModeloEmail modeloEmail,
                        Validator validator,
                        @Value("${app.email.remetente}") String remetentePadrao,
                        @Value("${app.email.nome-remetente:Mercado Express}") String nomeRemetente) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.modeloEmail = modeloEmail;
        this.validator = validator;
        this.remetentePadrao = remetentePadrao;
        this.nomeRemetente = nomeRemetente;
    }

    /**
     * Processa uma solicitação de e-mail. Uma solicitação repetida (mesmo solicitacaoId) que já foi
     * concluída não é enviada de novo: devolve o registro existente.
     */
    public EmailModel processar(EmailRequest request) {
        validar(request);
        UUID solicitacaoId = request.solicitacaoId() != null ? request.solicitacaoId() : UUID.randomUUID();

        Optional<EmailModel> existente = repository.findBySolicitacaoId(solicitacaoId);
        if (existente.isPresent() && existente.get().getStatusEmail() != StatusEmail.PROCESSING) {
            log.info("Solicitação {} já processada ({}): e-mail não reenviado", solicitacaoId,
                    existente.get().getStatusEmail());
            return existente.get();
        }
        EmailModel email = existente.orElseGet(() -> repository.save(novoRegistro(request, solicitacaoId)));
        enviar(email, modeloEmail.html(request));
        return repository.save(email);
    }

    private void validar(EmailRequest request) {
        if (request == null) {
            throw new EmailInvalidoException("mensagem vazia");
        }
        Set<ConstraintViolation<EmailRequest>> erros = validator.validate(request);
        if (!erros.isEmpty()) {
            throw new EmailInvalidoException(erros.stream()
                    .map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining("; ")));
        }
    }

    private EmailModel novoRegistro(EmailRequest request, UUID solicitacaoId) {
        EmailModel email = new EmailModel();
        email.setSolicitacaoId(solicitacaoId);
        email.setOwnerRef(temTexto(request.ownerRef()) ? request.ownerRef() : nomeRemetente);
        email.setNumeroPedido(request.numeroPedido());
        email.setEmailFrom(temTexto(request.emailFrom()) ? request.emailFrom().trim() : remetentePadrao);
        email.setEmailTo(request.emailTo().trim());
        email.setNomeDestinatario(request.nomeDestinatario());
        email.setSubject(request.subject());
        email.setText(temTexto(request.text()) ? request.text()
                : "Pagamento do pedido #" + request.numeroPedido() + " confirmado.");
        email.setStatusEmail(StatusEmail.PROCESSING);
        return email;
    }

    /** Envia (texto + HTML quando houver) e marca SENT ou ERROR. */
    private void enviar(EmailModel email, String html) {
        email.setSendDateEmail(LocalDateTime.now());
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, html != null, "UTF-8");
            helper.setFrom(email.getEmailFrom(), nomeRemetente);
            helper.setTo(email.getEmailTo());
            helper.setSubject(email.getSubject());
            if (html != null) {
                helper.setText(email.getText(), html);
            } else {
                helper.setText(email.getText());
            }
            enviarComNovaTentativa(mensagem);
            email.setStatusEmail(StatusEmail.SENT);
            email.setErro(null);
            log.info("E-mail ENVIADO para {} (pedido {})", email.getEmailTo(), email.getNumeroPedido());
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            email.setStatusEmail(StatusEmail.ERROR);
            Throwable causa = NestedExceptionUtils.getMostSpecificCause(e);
            email.setErro(limitar("Falha no envio (" + causa.getClass().getSimpleName() + "): " + causa.getMessage(), 500));
            log.error("ERRO ao enviar e-mail para {} (pedido {}): {}", email.getEmailTo(), email.getNumeroPedido(),
                    e.getMessage());
        }
    }

    /** Falhas de rede/SMTP costumam ser passageiras: tenta mais uma vez antes de registrar ERROR. */
    private void enviarComNovaTentativa(MimeMessage mensagem) {
        try {
            mailSender.send(mensagem);
        } catch (MailException primeiraFalha) {
            log.warn("Falha ao enviar e-mail ({}): tentando novamente", primeiraFalha.getMessage());
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw primeiraFalha;
            }
            mailSender.send(mensagem);
        }
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String limitar(String texto, int tamanho) {
        return texto == null || texto.length() <= tamanho ? texto : texto.substring(0, tamanho);
    }
}
