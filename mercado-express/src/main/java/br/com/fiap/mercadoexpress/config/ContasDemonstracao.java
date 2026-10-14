package br.com.fiap.mercadoexpress.config;

import br.com.fiap.mercadoexpress.service.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Contas de acesso para avaliação (uma de cada perfil), definidas no application.yml.
 * São exibidas na página inicial e na tela de login e ficam protegidas contra exclusão
 * e troca de perfil, para o acesso continuar sempre disponível.
 */
@Component
public class ContasDemonstracao {

    public record Conta(String perfil, String email, String senha) {
    }

    private final Conta admin;
    private final Conta cliente;
    private final boolean clienteAtivo;

    public ContasDemonstracao(@Value("${app.admin.email}") String adminEmail,
                              @Value("${app.admin.senha}") String adminSenha,
                              @Value("${app.demo-cliente.criar:true}") boolean clienteAtivo,
                              @Value("${app.demo-cliente.email}") String clienteEmail,
                              @Value("${app.demo-cliente.senha}") String clienteSenha) {
        this.admin = new Conta("Administrador", UsuarioService.normalizarEmail(adminEmail), adminSenha);
        this.cliente = new Conta("Cliente", UsuarioService.normalizarEmail(clienteEmail), clienteSenha);
        this.clienteAtivo = clienteAtivo;
    }

    public Conta getAdmin() {
        return admin;
    }

    /** Retorna null quando o cliente de demonstração está desativado. */
    public Conta getCliente() {
        return clienteAtivo ? cliente : null;
    }

    public boolean isProtegida(String email) {
        String normalizado = UsuarioService.normalizarEmail(email);
        return admin.email().equals(normalizado) || (clienteAtivo && cliente.email().equals(normalizado));
    }
}
