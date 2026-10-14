package br.com.fiap.mercadoexpress.model;

/**
 * Tipos de usuário do sistema.
 * ADMIN   - gerente do mercado: CRUD de produtos e gestão de usuários.
 * CLIENTE - consulta o catálogo de produtos.
 */
public enum Perfil {
    ADMIN("Administrador"),
    CLIENTE("Cliente");

    private final String descricao;

    Perfil(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Nome da autoridade usada pelo Spring Security (hasRole('ADMIN') -> ROLE_ADMIN). */
    public String getAuthority() {
        return "ROLE_" + name();
    }
}
