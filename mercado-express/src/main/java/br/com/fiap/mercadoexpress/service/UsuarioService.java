package br.com.fiap.mercadoexpress.service;

import br.com.fiap.mercadoexpress.config.ContasDemonstracao;
import br.com.fiap.mercadoexpress.dto.CadastroForm;
import br.com.fiap.mercadoexpress.exception.RecursoNaoEncontradoException;
import br.com.fiap.mercadoexpress.exception.RegraNegocioException;
import br.com.fiap.mercadoexpress.model.Perfil;
import br.com.fiap.mercadoexpress.model.Usuario;
import br.com.fiap.mercadoexpress.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ContasDemonstracao contasDemonstracao;

    /**
     * Sign Up: todo usuário que se cadastra pela tela pública recebe o perfil CLIENTE.
     * Somente um ADMIN pode promover alguém a ADMIN.
     */
    @Transactional
    public Usuario cadastrarCliente(CadastroForm form) {
        String email = normalizarEmail(form.getEmail());
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new RegraNegocioException("Este e-mail já está cadastrado. Faça login.");
        }
        Usuario usuario = new Usuario(form.getNome().trim(), email,
                passwordEncoder.encode(form.getSenha()), Perfil.CLIENTE);
        return repository.save(usuario);
    }

    /** Cria um usuário caso ainda não exista (usado na carga inicial). */
    @Transactional
    public boolean criarSeNaoExistir(String nome, String email, String senhaPura, Perfil perfil) {
        String emailNormalizado = normalizarEmail(email);
        if (repository.existsByEmailIgnoreCase(emailNormalizado)) {
            return false;
        }
        repository.save(new Usuario(nome, emailNormalizado, passwordEncoder.encode(senhaPura), perfil));
        return true;
    }

    @Transactional(readOnly = true)
    public boolean emailCadastrado(String email) {
        return email != null && repository.existsByEmailIgnoreCase(normalizarEmail(email));
    }

    @Transactional(readOnly = true)
    public List<Usuario> listarTodos() {
        return repository.findAllByOrderByNomeAsc();
    }

    @Transactional(readOnly = true)
    public long contarPorPerfil(Perfil perfil) {
        return repository.countByPerfil(perfil);
    }

    @Transactional
    public Usuario alterarPerfil(Long id, Perfil novoPerfil, String emailDoAdminLogado) {
        Usuario usuario = buscarPorId(id);
        if (contasDemonstracao.isProtegida(usuario.getEmail())) {
            throw new RegraNegocioException("Esta é uma conta de acesso para avaliação e não pode ter o perfil alterado.");
        }
        if (usuario.getEmail().equalsIgnoreCase(emailDoAdminLogado) && novoPerfil != Perfil.ADMIN) {
            throw new RegraNegocioException("Você não pode remover o seu próprio acesso de administrador.");
        }
        usuario.setPerfil(novoPerfil);
        return repository.save(usuario);
    }

    @Transactional
    public Usuario excluir(Long id, String emailDoAdminLogado) {
        Usuario usuario = buscarPorId(id);
        if (contasDemonstracao.isProtegida(usuario.getEmail())) {
            throw new RegraNegocioException("Esta é uma conta de acesso para avaliação e não pode ser excluída.");
        }
        if (usuario.getEmail().equalsIgnoreCase(emailDoAdminLogado)) {
            throw new RegraNegocioException("Você não pode excluir a sua própria conta.");
        }
        repository.delete(usuario);
        return usuario;
    }

    private Usuario buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário " + id + " não encontrado."));
    }

    public static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
