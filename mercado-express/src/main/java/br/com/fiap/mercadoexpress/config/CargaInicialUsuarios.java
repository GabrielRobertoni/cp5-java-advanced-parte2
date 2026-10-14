package br.com.fiap.mercadoexpress.config;

import br.com.fiap.mercadoexpress.model.Perfil;
import br.com.fiap.mercadoexpress.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Na inicialização, garante que exista um administrador (e, opcionalmente, um cliente de demonstração).
 * Credenciais configuráveis por variáveis de ambiente (ver application.yml).
 * A senha é gravada como hash BCrypt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CargaInicialUsuarios implements ApplicationRunner {

    private final UsuarioService usuarioService;

    @Value("${app.admin.nome}")
    private String adminNome;
    @Value("${app.admin.email}")
    private String adminEmail;
    @Value("${app.admin.senha}")
    private String adminSenha;

    @Value("${app.demo-cliente.criar:true}")
    private boolean criarCliente;
    @Value("${app.demo-cliente.nome}")
    private String clienteNome;
    @Value("${app.demo-cliente.email}")
    private String clienteEmail;
    @Value("${app.demo-cliente.senha}")
    private String clienteSenha;

    @Value("${server.port:8080}")
    private String porta;

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioService.criarSeNaoExistir(adminNome, adminEmail, adminSenha, Perfil.ADMIN)) {
            log.info("Administrador inicial criado: {}", adminEmail);
        }
        if (criarCliente && usuarioService.criarSeNaoExistir(clienteNome, clienteEmail, clienteSenha, Perfil.CLIENTE)) {
            log.info("Cliente de demonstração criado: {}", clienteEmail);
        }
        log.info(">>> Mercado Express pronto: http://localhost:{}", porta);
    }
}
