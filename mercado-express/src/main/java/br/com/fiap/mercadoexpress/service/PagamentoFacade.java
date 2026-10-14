package br.com.fiap.mercadoexpress.service;

import br.com.fiap.mercadoexpress.exception.RecursoNaoEncontradoException;
import br.com.fiap.mercadoexpress.exception.RegraNegocioException;
import br.com.fiap.mercadoexpress.mensageria.dto.EmailRequest;
import br.com.fiap.mercadoexpress.mensageria.dto.EmailStatusMensagem;
import br.com.fiap.mercadoexpress.mensageria.dto.PagamentoRequest;
import br.com.fiap.mercadoexpress.mensageria.dto.PagamentoResposta;
import br.com.fiap.mercadoexpress.mensageria.producer.EmailRequestProducer;
import br.com.fiap.mercadoexpress.mensageria.producer.PagamentoRequestProducer;
import br.com.fiap.mercadoexpress.model.ItemPedido;
import br.com.fiap.mercadoexpress.model.Pedido;
import br.com.fiap.mercadoexpress.model.StatusEmail;
import br.com.fiap.mercadoexpress.model.StatusPedido;
import br.com.fiap.mercadoexpress.repository.PedidoRepository;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import br.com.fiap.mercadoexpress.service.evento.EmailSolicitadoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Fachada da integração de pagamentos (mesmo papel da PagamentoFacade da aula):
 * envia o pedido ao Banco Tranquilo, trata as respostas de sucesso/erro e controla o e-mail de confirmação.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PagamentoFacade {

    public static final String OWNER_REF = "Mercado Express";
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final PagamentoRequestProducer pagamentoProducer;
    private final EmailRequestProducer emailProducer;
    private final ApplicationEventPublisher eventos;

    // ------------------------------------------------------------------ pedido -> banco

    /**
     * Publica o pedido de pagamento. Se o broker estiver fora do ar, o pedido continua
     * "aguardando" e o {@link ReenvioMensagensJob} tenta de novo depois.
     *
     * @return true se a mensagem foi publicada
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean solicitarPagamento(Long pedidoId) {
        Pedido pedido = pedidoRepository.buscarComItens(pedidoId).orElse(null);
        if (pedido == null || !pedido.isAguardandoPagamento() || pedido.getPagamentoEnviadoEm() != null) {
            return false;
        }
        PagamentoRequest request = new PagamentoRequest(
                pedido.getTransacaoId(),
                pedido.getId(),
                pedido.getValorTotal(),
                pedido.getFormaPagamento().name(),
                pedido.getClienteNome(),
                pedido.getClienteEmail(),
                "Mercado Express - pedido #" + pedido.getId() + " (" + pedido.getQuantidadeItens() + " itens)",
                LocalDateTime.now());
        try {
            pagamentoProducer.integrar(request);
        } catch (AmqpException e) {
            log.warn("RabbitMQ indisponível: pagamento do pedido {} será reenviado depois ({})", pedidoId, e.getMessage());
            return false;
        }
        pedidoRepository.marcarPagamentoPublicado(pedidoId, LocalDateTime.now());
        return true;
    }

    // ------------------------------------------------------------------ banco -> pedido

    @Transactional
    public void sucessoPagamento(PagamentoResposta resposta) {
        Pedido pedido = pedidoParaResposta(resposta);
        if (pedido == null) {
            return;
        }
        pedido.setStatus(StatusPedido.PAGO);
        pedido.setCodigoAutorizacao(resposta.codigoAutorizacao());
        pedido.setMotivoRecusa(null);
        pedido.setRespondidoEm(LocalDateTime.now());
        prepararNovoEmail(pedido);
        log.info("==== PAGAMENTO APROVADO ==== pedido {} | autorização {}", pedido.getId(), resposta.codigoAutorizacao());
        eventos.publishEvent(new EmailSolicitadoEvent(pedido.getId()));
    }

    @Transactional
    public void erroPagamento(PagamentoResposta resposta) {
        Pedido pedido = pedidoParaResposta(resposta);
        if (pedido == null) {
            return;
        }
        pedido.setStatus(StatusPedido.RECUSADO);
        pedido.setMotivoRecusa(resposta.motivo() == null ? "Pagamento não autorizado" : resposta.motivo());
        pedido.setRespondidoEm(LocalDateTime.now());
        devolverEstoque(pedido);
        log.warn("==== PAGAMENTO RECUSADO ==== pedido {} | motivo: {}", pedido.getId(), pedido.getMotivoRecusa());
    }

    /**
     * Valida a resposta do banco. Respostas repetidas, de pedidos desconhecidos ou com transação
     * diferente são ignoradas (o processamento é idempotente).
     */
    private Pedido pedidoParaResposta(PagamentoResposta resposta) {
        if (resposta == null || resposta.numeroPedido() == null) {
            log.warn("Resposta do banco sem número de pedido ignorada: {}", resposta);
            return null;
        }
        Pedido pedido = pedidoRepository.buscarParaAtualizar(resposta.numeroPedido()).orElse(null);
        if (pedido == null) {
            log.warn("Resposta do banco para pedido inexistente ({}) ignorada", resposta.numeroPedido());
            return null;
        }
        if (resposta.transacaoId() != null && !resposta.transacaoId().equals(pedido.getTransacaoId())) {
            log.warn("Resposta do banco com transação {} diferente da do pedido {} ignorada",
                    resposta.transacaoId(), pedido.getId());
            return null;
        }
        if (!pedido.isAguardandoPagamento()) {
            log.info("Pedido {} já está {}: resposta repetida ignorada", pedido.getId(), pedido.getStatus());
            return null;
        }
        return pedido;
    }

    private void devolverEstoque(Pedido pedido) {
        for (ItemPedido item : pedido.getItens()) {
            if (item.getProdutoId() == null) {
                continue; // produto excluído do catálogo
            }
            produtoRepository.buscarParaAtualizarEstoque(item.getProdutoId()).ifPresent(produto ->
                    produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() + item.getQuantidade()));
        }
    }

    // ------------------------------------------------------------------ pedido -> e-mail

    private void prepararNovoEmail(Pedido pedido) {
        pedido.setEmailStatus(StatusEmail.PENDENTE);
        pedido.setEmailSolicitacaoId(UUID.randomUUID());
        pedido.setEmailAtualizadoEm(LocalDateTime.now());
        pedido.setEmailErro(null);
    }

    /** Publica o pedido de e-mail de confirmação de pagamento para o email-service. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean solicitarEmail(Long pedidoId) {
        Pedido pedido = pedidoRepository.buscarComItens(pedidoId).orElse(null);
        if (pedido == null || !pedido.isPago() || pedido.getEmailStatus() != StatusEmail.PENDENTE) {
            return false;
        }
        EmailRequest request = montarEmail(pedido);
        try {
            emailProducer.solicitar(request);
        } catch (AmqpException e) {
            log.warn("RabbitMQ indisponível: e-mail do pedido {} será reenviado depois ({})", pedidoId, e.getMessage());
            return false;
        }
        pedidoRepository.marcarEmailPublicado(pedidoId, pedido.getEmailSolicitacaoId(), LocalDateTime.now());
        return true;
    }

    /** Reenvio manual pelo gerente (ex.: depois de um erro de SMTP). */
    @Transactional
    public void reenviarEmail(Long pedidoId) {
        Pedido pedido = pedidoRepository.buscarParaAtualizar(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido " + pedidoId + " não encontrado."));
        if (!pedido.isPago()) {
            throw new RegraNegocioException("Só é possível enviar o e-mail de pedidos pagos.");
        }
        prepararNovoEmail(pedido);
        eventos.publishEvent(new EmailSolicitadoEvent(pedido.getId()));
    }

    EmailRequest montarEmail(Pedido pedido) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(PT_BR);
        StringBuilder texto = new StringBuilder()
                .append("Olá, ").append(pedido.getClienteNome()).append("!\n\n")
                .append("O Banco Tranquilo confirmou o pagamento do seu pedido #").append(pedido.getId())
                .append(" no Mercado Express.\n\n");
        pedido.getItens().forEach(item -> texto
                .append("- ").append(item.getQuantidade()).append("x ").append(item.getProdutoNome())
                .append(" = ").append(moeda.format(item.getSubtotal())).append('\n'));
        texto.append("\nTotal pago: ").append(moeda.format(pedido.getValorTotal()))
                .append("\nForma de pagamento: ").append(pedido.getFormaPagamento().getDescricao())
                .append("\nCódigo de autorização: ").append(pedido.getCodigoAutorizacao())
                .append("\nData: ").append(pedido.getRespondidoEm().format(DATA_HORA))
                .append("\n\nObrigado pela preferência!\nMercado Express");

        var itens = pedido.getItens().stream()
                .map(item -> new EmailRequest.Item(item.getProdutoNome(), item.getQuantidade(),
                        item.getPrecoUnitario(), item.getSubtotal()))
                .toList();
        var detalhes = new EmailRequest.DetalhesPedido(itens, pedido.getValorTotal().setScale(2, RoundingMode.HALF_UP),
                pedido.getFormaPagamento().getDescricao(), pedido.getCodigoAutorizacao(), pedido.getTransacaoId(),
                pedido.getRespondidoEm());

        return new EmailRequest(
                pedido.getEmailSolicitacaoId(),
                OWNER_REF,
                null, // remetente definido no email-service (conta SMTP configurada)
                pedido.getClienteEmail(),
                pedido.getClienteNome(),
                "Pagamento confirmado - pedido #" + pedido.getId() + " | Mercado Express",
                texto.toString(),
                pedido.getId(),
                detalhes);
    }

    // ------------------------------------------------------------------ e-mail -> pedido

    @Transactional
    public void atualizarStatusEmail(EmailStatusMensagem status) {
        if (status == null || status.numeroPedido() == null) {
            return; // e-mail avulso (ex.: teste publicado pelo painel do RabbitMQ)
        }
        Pedido pedido = pedidoRepository.buscarParaAtualizar(status.numeroPedido()).orElse(null);
        if (pedido == null) {
            log.warn("Status de e-mail para pedido inexistente ({}) ignorado", status.numeroPedido());
            return;
        }
        if (status.solicitacaoId() != null && !status.solicitacaoId().equals(pedido.getEmailSolicitacaoId())) {
            log.info("Status de e-mail antigo do pedido {} ignorado", pedido.getId());
            return;
        }
        boolean enviado = "SENT".equalsIgnoreCase(status.statusEmail());
        pedido.setEmailStatus(enviado ? StatusEmail.ENVIADO : StatusEmail.ERRO);
        pedido.setEmailId(status.emailId());
        pedido.setEmailErro(enviado ? null : limitar(status.erro(), 500));
        pedido.setEmailAtualizadoEm(LocalDateTime.now());
        log.info("E-mail do pedido {}: {}", pedido.getId(), pedido.getEmailStatus());
    }

    private static String limitar(String texto, int tamanho) {
        if (texto == null || texto.length() <= tamanho) {
            return texto;
        }
        return texto.substring(0, tamanho);
    }
}
