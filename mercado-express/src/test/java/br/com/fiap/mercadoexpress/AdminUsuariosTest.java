package br.com.fiap.mercadoexpress;

import br.com.fiap.mercadoexpress.model.Perfil;
import br.com.fiap.mercadoexpress.model.Usuario;
import br.com.fiap.mercadoexpress.repository.UsuarioRepository;
import br.com.fiap.mercadoexpress.security.UsuarioAutenticado;
import br.com.fiap.mercadoexpress.support.TesteIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@WithUserDetails(TesteIntegracao.ADMIN_EMAIL)
@DisplayName("Gestão de usuários (ADMIN)")
class AdminUsuariosTest extends TesteIntegracao {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioRepository repository;

    private Usuario criarCliente(String email) {
        return repository.save(new Usuario("Fulano de Tal", email, "$2a$10$hashqualquer", Perfil.CLIENTE));
    }

    private Usuario admin() {
        return repository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
    }

    @Test
    @DisplayName("Lista os usuários da tabela TDS_Users_Mercado")
    void listar() throws Exception {
        mvc.perform(get("/admin/usuarios"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(ADMIN_EMAIL)))
                .andExpect(content().string(containsString(CLIENTE_EMAIL)));
    }

    @Test
    @DisplayName("Promove um cliente a administrador e depois rebaixa")
    void promoverERebaixar() throws Exception {
        Usuario u = criarCliente("promover@teste.com");

        mvc.perform(post("/admin/usuarios/{id}/perfil", u.getId()).with(csrf())
                        .param("_method", "put").param("perfil", "ADMIN"))
                .andExpect(redirectedUrl("/admin/usuarios"))
                .andExpect(flash().attribute("sucesso", containsString("Administrador")));
        assertThat(repository.findById(u.getId()).orElseThrow().getPerfil()).isEqualTo(Perfil.ADMIN);

        mvc.perform(post("/admin/usuarios/{id}/perfil", u.getId()).with(csrf())
                        .param("_method", "put").param("perfil", "CLIENTE"))
                .andExpect(redirectedUrl("/admin/usuarios"));
        assertThat(repository.findById(u.getId()).orElseThrow().getPerfil()).isEqualTo(Perfil.CLIENTE);
    }

    @Test
    @DisplayName("Um admin não pode remover o próprio acesso de administrador")
    void naoRebaixaASiMesmo() throws Exception {
        Usuario outroAdmin = repository.save(new Usuario("Outro Admin", "outro.admin@teste.com", "$2a$10$hash", Perfil.ADMIN));
        mvc.perform(post("/admin/usuarios/{id}/perfil", outroAdmin.getId()).with(csrf())
                        .with(user(new UsuarioAutenticado(outroAdmin)))
                        .param("_method", "put").param("perfil", "CLIENTE"))
                .andExpect(redirectedUrl("/admin/usuarios"))
                .andExpect(flash().attribute("erro", containsString("próprio acesso")));
        assertThat(repository.findById(outroAdmin.getId()).orElseThrow().getPerfil()).isEqualTo(Perfil.ADMIN);
    }

    @Test
    @DisplayName("As contas de avaliação (admin e cliente) não podem ser excluídas nem ter o perfil alterado")
    void contasDeAvaliacaoProtegidas() throws Exception {
        Usuario cliente = repository.findByEmailIgnoreCase(CLIENTE_EMAIL).orElseThrow();

        mvc.perform(post("/admin/usuarios/{id}", cliente.getId()).with(csrf()).param("_method", "delete"))
                .andExpect(redirectedUrl("/admin/usuarios"))
                .andExpect(flash().attribute("erro", containsString("conta de acesso para avaliação")));
        mvc.perform(post("/admin/usuarios/{id}/perfil", cliente.getId()).with(csrf())
                        .param("_method", "put").param("perfil", "ADMIN"))
                .andExpect(flash().attribute("erro", containsString("conta de acesso para avaliação")));

        // mesmo outro administrador não consegue mexer na conta admin de avaliação
        Usuario outroAdmin = repository.save(new Usuario("Outro Admin", "outro2.admin@teste.com", "$2a$10$hash", Perfil.ADMIN));
        mvc.perform(post("/admin/usuarios/{id}", admin().getId()).with(csrf())
                        .with(user(new UsuarioAutenticado(outroAdmin))).param("_method", "delete"))
                .andExpect(flash().attribute("erro", containsString("conta de acesso para avaliação")));
        mvc.perform(post("/admin/usuarios/{id}/perfil", admin().getId()).with(csrf())
                        .with(user(new UsuarioAutenticado(outroAdmin)))
                        .param("_method", "put").param("perfil", "CLIENTE"))
                .andExpect(flash().attribute("erro", containsString("conta de acesso para avaliação")));

        assertThat(repository.findById(cliente.getId()).orElseThrow().getPerfil()).isEqualTo(Perfil.CLIENTE);
        assertThat(admin().getPerfil()).isEqualTo(Perfil.ADMIN);
        assertThat(repository.existsById(admin().getId())).isTrue();

        mvc.perform(get("/admin/usuarios"))
                .andExpect(content().string(containsString("conta de avaliação")));
    }

    @Test
    @DisplayName("Admin exclui outro usuário, mas não a si mesmo")
    void excluir() throws Exception {
        Usuario u = criarCliente("excluir@teste.com");
        mvc.perform(post("/admin/usuarios/{id}", u.getId()).with(csrf()).param("_method", "delete"))
                .andExpect(redirectedUrl("/admin/usuarios"))
                .andExpect(flash().attribute("sucesso", containsString("excluído")));
        assertThat(repository.existsById(u.getId())).isFalse();

        Usuario outroAdmin = repository.save(new Usuario("Outro Admin", "outro3.admin@teste.com", "$2a$10$hash", Perfil.ADMIN));
        mvc.perform(post("/admin/usuarios/{id}", outroAdmin.getId()).with(csrf())
                        .with(user(new UsuarioAutenticado(outroAdmin))).param("_method", "delete"))
                .andExpect(flash().attribute("erro", containsString("própria conta")));
        assertThat(repository.existsById(outroAdmin.getId())).isTrue();
    }

    @Test
    @DisplayName("Perfil inválido ou usuário inexistente não causam erro 500")
    void entradasInvalidas() throws Exception {
        mvc.perform(post("/admin/usuarios/{id}/perfil", admin().getId()).with(csrf())
                        .param("_method", "put").param("perfil", "SUPERUSUARIO"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/admin/usuarios/{id}", 999_999).with(csrf()).param("_method", "delete"))
                .andExpect(status().isNotFound());
    }
}
