package br.com.fiap.mercadoexpress.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;

/** Dados do formulário de Sign Up (cadastro de novo usuário). */
@Data
@NoArgsConstructor
public class CadastroForm {

    @NotBlank(message = "Informe seu nome.")
    @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres.")
    private String nome;

    @NotBlank(message = "Informe seu e-mail.")
    @Email(message = "Informe um e-mail válido.")
    @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres.")
    private String email;

    @NotBlank(message = "Informe uma senha.")
    @Size(min = 6, max = 72, message = "A senha deve ter entre 6 e 72 caracteres.")
    private String senha;

    @NotBlank(message = "Confirme a senha.")
    private String confirmacaoSenha;

    /** Remove espaços acidentais (comuns ao copiar e colar). A senha não é alterada. */
    public void setNome(String nome) {
        this.nome = nome == null ? null : nome.strip();
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip();
    }

    @AssertTrue(message = "As senhas não conferem.")
    public boolean isSenhasConferem() {
        // Se algum campo estiver vazio, a validação @NotBlank já acusa o erro
        return senha == null || confirmacaoSenha == null || senha.equals(confirmacaoSenha);
    }

    /** O BCrypt aceita no máximo 72 bytes (caracteres acentuados ocupam mais de 1 byte). */
    @AssertTrue(message = "A senha é longa demais.")
    public boolean isSenhaDentroDoLimiteBcrypt() {
        return senha == null || senha.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
