package br.com.fiap.mercadoexpress.service;

import br.com.fiap.mercadoexpress.model.StatusEmail;
import br.com.fiap.mercadoexpress.model.StatusPedido;
import br.com.fiap.mercadoexpress.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Se o RabbitMQ estava fora do ar quando a compra foi feita, a mensagem não foi publicada.
 * Este job procura essas mensagens pendentes e publica de novo quando o broker voltar.
 * (O Banco Tranquilo e o email-service ignoram mensagens repetidas.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReenvioMensagensJob {

    private final PedidoRepository pedidoRepository;
    private final PagamentoFacade pagamentoFacade;

    @Value("${app.pagamentos.reenvio-apos:20s}")
    private Duration reenvioApos;

    @Scheduled(fixedDelayString = "${app.pagamentos.reenvio-intervalo:30s}",
            initialDelayString = "${app.pagamentos.reenvio-intervalo:30s}")
    public void reenviarPendentes() {
        LocalDateTime limite = LocalDateTime.now().minus(reenvioApos);

        List<Long> pagamentos = pedidoRepository.pagamentosNaoPublicados(StatusPedido.AGUARDANDO_PAGAMENTO, limite);
        long pagamentosEnviados = pagamentos.stream().filter(pagamentoFacade::solicitarPagamento).count();

        List<Long> emails = pedidoRepository.emailsNaoPublicados(StatusEmail.PENDENTE, limite);
        long emailsEnviados = emails.stream().filter(pagamentoFacade::solicitarEmail).count();

        if (pagamentosEnviados + emailsEnviados > 0) {
            log.info("Reenvio: {} pagamento(s) e {} e-mail(s) publicados no RabbitMQ", pagamentosEnviados, emailsEnviados);
        }
    }
}
