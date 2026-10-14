package br.com.fiap.bancotranquilo.service;

import br.com.fiap.bancotranquilo.mensageria.dto.PagamentoRequest;
import br.com.fiap.bancotranquilo.mensageria.dto.PagamentoResposta;
import br.com.fiap.bancotranquilo.model.StatusTransacao;
import br.com.fiap.bancotranquilo.model.TransacaoBancaria;
import br.com.fiap.bancotranquilo.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Processa o pedido de pagamento e registra a transação (uma única vez por transacaoId). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PagamentoService {

    private final TransacaoRepository repository;
    private final AnalisePagamento analise;

    /**
     * Mensagem repetida (mesma transação) devolve a mesma resposta já registrada,
     * sem cobrar de novo.
     */
    @Transactional
    public PagamentoResposta processar(PagamentoRequest request) {
        UUID transacaoId = request.transacaoId() != null ? request.transacaoId() : UUID.randomUUID();
        return repository.findById(transacaoId)
                .map(existente -> {
                    log.info("Transação {} já processada ({}): reenviando a mesma resposta", transacaoId, existente.getStatus());
                    return resposta(existente);
                })
                .orElseGet(() -> resposta(registrar(transacaoId, request)));
    }

    private TransacaoBancaria registrar(UUID transacaoId, PagamentoRequest request) {
        LocalDateTime recebidaEm = request.solicitadoEm() != null ? request.solicitadoEm() : LocalDateTime.now();
        AnalisePagamento.Decisao decisao = analise.analisar(request);

        TransacaoBancaria transacao = new TransacaoBancaria();
        transacao.setTransacaoId(transacaoId);
        transacao.setNumeroPedido(request.numeroPedido());
        transacao.setValor(request.valor());
        transacao.setFormaPagamento(limitar(request.formaPagamento(), 30));
        transacao.setClienteNome(limitar(request.clienteNome(), 100));
        transacao.setClienteEmail(limitar(request.clienteEmail(), 150));
        transacao.setDescricao(limitar(request.descricao(), 255));
        transacao.setRecebidaEm(recebidaEm);
        transacao.setProcessadaEm(LocalDateTime.now());
        if (decisao.aprovado()) {
            transacao.setStatus(StatusTransacao.APROVADO);
            transacao.setCodigoAutorizacao(novoCodigoAutorizacao());
        } else {
            transacao.setStatus(StatusTransacao.RECUSADO);
            transacao.setMotivo(decisao.motivo());
        }
        TransacaoBancaria salva = repository.save(transacao);
        log.info("Pedido {} | R$ {} | {} {}", salva.getNumeroPedido(), salva.getValor(), salva.getStatus(),
                salva.isAprovada() ? salva.getCodigoAutorizacao() : "(" + salva.getMotivo() + ")");
        return salva;
    }

    private static PagamentoResposta resposta(TransacaoBancaria transacao) {
        return new PagamentoResposta(transacao.getTransacaoId(), transacao.getNumeroPedido(),
                transacao.getStatus().name(), transacao.getValor(), transacao.getCodigoAutorizacao(),
                transacao.getMotivo(), transacao.getProcessadaEm());
    }

    /** Código de autorização no formato TQ-123456. */
    static String novoCodigoAutorizacao() {
        return "TQ-" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
    }

    private static String limitar(String texto, int tamanho) {
        return texto == null || texto.length() <= tamanho ? texto : texto.substring(0, tamanho);
    }
}
