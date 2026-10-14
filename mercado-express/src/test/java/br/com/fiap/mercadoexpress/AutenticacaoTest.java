package br.com.fiap.mercadoexpress;

import br.com.fiap.mercadoexpress.model.Perfil;
import br.com.fiap.mercadoexpress.model.Usuario;
import br.com.fiap.mercadoexpress.repository.UsuarioRepository;
import br.com.fiap.mercadoexpress.support.TesteIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.FormLoginRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@Transactional
@DisplayName("Login, logout e Sign Up")
class AutenticacaoTest extends TesteIntegracao {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static FormLoginRequestBuilder login(String email, String senha) {
        return formLogin("/login").userParameter("email").user(email).passwordParam("senha").password(senha);
    }

    // ------------------------------------------------------------------ Login

    @Test
    @DisplayName("ADMIN é redirecionado para o painel restrito após o login")
    void loginAdminVaiParaPainel() throws Exception {
        mvc.perform(login(ADMIN_EMAIL, ADMIN_SENHA))
                .andExpect(redirectedUrl("/admin/painel"))
                .andExpect(authenticated().withUsername(ADMIN_EMAIL).withRoles("ADMIN"));
    }

    @Test
    @DisplayName("CLIENTE é redirecionado para o catálogo restrito após o login")
    void loginClienteVaiParaProdutos() throws Exception {
        mvc.perform(login(CLIENTE_EMAIL, CLIENTE_SENHA))
                .andExpect(redirectedUrl("/produtos"))
                .andExpect(authenticated().withUsername(CLIENTE_EMAIL).withRoles("CLIENTE"));
    }

    @Test
    @DisplayName("Login aceita e-mail com maiúsculas e espaços")
    void loginEmailNormalizado() throws Exception {
        mvc.perform(login("  ADMIN@MercadoExpress.com ", ADMIN_SENHA))
                .andExpect(redirectedUrl("/admin/painel"))
                .andExpect(authenticated().withRoles("ADMIN"));
    }

    @Test
    @DisplayName("Senha errada volta ao login com mensagem de erro")
    void senhaErrada() throws Exception {
        mvc.perform(login(ADMIN_EMAIL, "senha-errada"))
                .andExpect(redirectedUrl("/login?erro"))
                .andExpect(unauthenticated());

        mvc.perform(get("/login").param("erro", ""))
                .andExpect(content().string(containsString("E-mail ou senha incorretos")));
    }

    @Test
    @DisplayName("Usuário sem login é redirecionado para o Sign Up com o e-mail preenchido")
    void semContaVaiParaCadastro() throws Exception {
        mvc.perform(login("novo.cliente@teste.com", "qualquer123"))
                .andExpect(redirectedUrl("/cadastro?semConta&email=novo.cliente@teste.com"))
                .andExpect(unauthenticated());

        mvc.perform(get("/cadastro").param("semConta", "").param("email", "novo.cliente@teste.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Não encontramos uma conta com este e-mail")))
                .andExpect(content().string(containsString("value=\"novo.cliente@teste.com\"")));
    }

    @Test
    @DisplayName("Senha acima do limite do BCrypt no login não causa erro 500")
    void senhaGiganteNoLogin() throws Exception {
        mvc.perform(login(ADMIN_EMAIL, "ç".repeat(80)))
                .andExpect(redirectedUrl("/login?erro"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("Login exige token CSRF")
    void loginSemCsrfBloqueado() throws Exception {
        mvc.perform(post("/login").param("email", ADMIN_EMAIL).param("senha", ADMIN_SENHA))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    @DisplayName("Usuário já logado que abre /login ou /cadastro vai para a sua área")
    void logadoNaoVeLogin() throws Exception {
        mvc.perform(get("/login")).andExpect(redirectedUrl("/admin/painel"));
        mvc.perform(get("/cadastro")).andExpect(redirectedUrl("/admin/painel"));
    }

    @Test
    @WithUserDetails(CLIENTE_EMAIL)
    @DisplayName("Logout encerra a sessão e volta à página inicial")
    void logoutEncerraSessao() throws Exception {
        mvc.perform(logout("/logout"))
                .andExpect(redirectedUrl("/?saiu"))
                .andExpect(unauthenticated());
    }

    // ---------------------------------------------------------------- Sign Up

    @Test
    @DisplayName("Sign Up grava o usuário como CLIENTE com senha em BCrypt e permite login")
    void cadastroComSucesso() throws Exception {
        mvc.perform(post("/cadastro").with(csrf())
                        .param("nome", "Maria Souza")
                        .param("email", "Maria.Souza@Teste.com ")
                        .param("senha", "segredo123")
                        .param("confirmacaoSenha", "segredo123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?email=maria.souza%40teste.com"))
                .andExpect(flash().attribute("sucesso", containsString("Cadastro realizado")));

        // a tela de login já vem com o e-mail preenchido
        mvc.perform(get("/login").param("email", "maria.souza@teste.com"))
                .andExpect(content().string(containsString("value=\"maria.souza@teste.com\"")));

        Usuario salvo = usuarioRepository.findByEmailIgnoreCase("maria.souza@teste.com").orElseThrow();
        assertThat(salvo.getNome()).isEqualTo("Maria Souza");
        assertThat(salvo.getPerfil()).isEqualTo(Perfil.CLIENTE);
        assertThat(salvo.getSenha()).isNotEqualTo("segredo123").startsWith("$2");
        assertThat(passwordEncoder.matches("segredo123", salvo.getSenha())).isTrue();
        assertThat(salvo.getCriadoEm()).isNotNull();

        mvc.perform(login("maria.souza@teste.com", "segredo123"))
                .andExpect(redirectedUrl("/produtos"))
                .andExpect(authenticated().withRoles("CLIENTE"));
    }

    @Test
    @DisplayName("Sign Up não permite escolher o perfil ADMIN")
    void cadastroNaoEscalaPrivilegio() throws Exception {
        mvc.perform(post("/cadastro").with(csrf())
                        .param("nome", "Espertinho")
                        .param("email", "hacker@teste.com")
                        .param("senha", "segredo123")
                        .param("confirmacaoSenha", "segredo123")
                        .param("perfil", "ADMIN"))
                .andExpect(status().is3xxRedirection());

        assertThat(usuarioRepository.findByEmailIgnoreCase("hacker@teste.com").orElseThrow().getPerfil())
                .isEqualTo(Perfil.CLIENTE);
    }

    @Test
    @DisplayName("Sign Up valida campos e não salva dados inválidos")
    void cadastroInvalido() throws Exception {
        long antes = usuarioRepository.count();
        mvc.perform(post("/cadastro").with(csrf())
                        .param("nome", "A")
                        .param("email", "email-invalido")
                        .param("senha", "123")
                        .param("confirmacaoSenha", "999"))
                .andExpect(status().isOk())
                .andExpect(view().name("cadastro"))
                .andExpect(model().attributeHasFieldErrors("cadastroForm", "nome", "email", "senha", "senhasConferem"))
                // a senha digitada não é devolvida no HTML
                .andExpect(content().string(not(containsString("value=\"123\""))));
        assertThat(usuarioRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Sign Up rejeita senha acima de 72 bytes (limite do BCrypt) sem erro 500")
    void cadastroSenhaLongaDemais() throws Exception {
        String senha = "ç".repeat(40); // 40 caracteres, 80 bytes em UTF-8
        mvc.perform(post("/cadastro").with(csrf())
                        .param("nome", "Senha Longa")
                        .param("email", "longa@teste.com")
                        .param("senha", senha)
                        .param("confirmacaoSenha", senha))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("cadastroForm", "senhaDentroDoLimiteBcrypt"));
    }

    @Test
    @DisplayName("Sign Up recusa e-mail já cadastrado")
    void cadastroEmailDuplicado() throws Exception {
        mvc.perform(post("/cadastro").with(csrf())
                        .param("nome", "Outro Admin")
                        .param("email", "ADMIN@mercadoexpress.com")
                        .param("senha", "segredo123")
                        .param("confirmacaoSenha", "segredo123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("cadastroForm", "email", "email.duplicado"))
                .andExpect(content().string(containsString("já está cadastrado")));
    }

    @Test
    @DisplayName("Sign Up exige token CSRF")
    void cadastroSemCsrf() throws Exception {
        mvc.perform(post("/cadastro")
                        .param("nome", "Sem Token")
                        .param("email", "semtoken@teste.com")
                        .param("senha", "segredo123")
                        .param("confirmacaoSenha", "segredo123"))
                .andExpect(status().isForbidden());
        assertThat(usuarioRepository.existsByEmailIgnoreCase("semtoken@teste.com")).isFalse();
    }
}
