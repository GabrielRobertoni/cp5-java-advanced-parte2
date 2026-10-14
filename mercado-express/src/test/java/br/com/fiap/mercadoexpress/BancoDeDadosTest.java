package br.com.fiap.mercadoexpress;

import br.com.fiap.mercadoexpress.support.TesteIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Confere a estrutura criada pelo Flyway no PostgreSQL e as restrições do banco. */
@Transactional
@DisplayName("Estrutura do banco PostgreSQL (Flyway)")
class BancoDeDadosTest extends TesteIntegracao {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("O banco de testes é PostgreSQL de verdade")
    void ehPostgres() {
        String versao = jdbc.queryForObject("SELECT version()", String.class);
        assertThat(versao).startsWith("PostgreSQL 16");
    }

    @Test
    @DisplayName("Migrations V1, V2 e V3 aplicadas com sucesso")
    void migrationsAplicadas() {
        List<String> versoes = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(versoes).containsExactly("1", "2", "3");
    }

    @Test
    @DisplayName("Tabelas de produtos, usuários, pedidos e itens existem")
    void tabelasExistem() {
        List<String> tabelas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tabelas).contains("tds_sec_mvc_tb_mercado", "tds_users_mercado",
                "tds_pedidos_mercado", "tds_itens_pedido_mercado");
    }

    @Test
    @DisplayName("Carga inicial: 12 produtos e usuários admin/cliente com senha em hash")
    void cargaInicial() {
        Integer produtos = jdbc.queryForObject("SELECT COUNT(*) FROM TDS_Sec_MVC_TB_Mercado", Integer.class);
        assertThat(produtos).isEqualTo(12);

        List<String> perfis = jdbc.queryForList(
                "SELECT perfil FROM TDS_Users_Mercado WHERE email IN (?, ?) ORDER BY perfil",
                String.class, ADMIN_EMAIL, CLIENTE_EMAIL);
        assertThat(perfis).containsExactly("ADMIN", "CLIENTE");

        Integer senhasPuras = jdbc.queryForObject(
                "SELECT COUNT(*) FROM TDS_Users_Mercado WHERE senha NOT LIKE '$2%'", Integer.class);
        assertThat(senhasPuras).isZero();
    }

    @Test
    @DisplayName("Restrição: e-mail duplicado é recusado")
    void restricaoEmailUnico() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO TDS_Users_Mercado (nome, email, senha, perfil) VALUES ('X', ?, 'h', 'CLIENTE')",
                ADMIN_EMAIL))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Restrição: perfil fora de ADMIN/CLIENTE é recusado")
    void restricaoPerfil() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO TDS_Users_Mercado (nome, email, senha, perfil) VALUES ('X', 'x@x.com', 'h', 'ROOT')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Restrição: preço zero é recusado")
    void restricaoPreco() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO TDS_Sec_MVC_TB_Mercado (nome, tipo, setor, preco) VALUES ('X', 'X', 'X', 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
