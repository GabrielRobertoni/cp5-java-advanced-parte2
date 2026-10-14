package br.com.fiap.mercadoexpress.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TDS_Sec_MVC_TB_Mercado")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Produto {

    /** Abaixo desta quantidade o produto é sinalizado como "estoque baixo". */
    public static final int LIMITE_ESTOQUE_BAIXO = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Informe o nome do produto.")
    @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
    @Column(nullable = false, length = 100)
    private String nome;

    @NotBlank(message = "Informe o tipo (ex.: Fruta, Limpeza).")
    @Size(max = 50, message = "O tipo deve ter no máximo 50 caracteres.")
    @Column(nullable = false, length = 50)
    private String tipo;

    @NotBlank(message = "Informe o setor do mercado.")
    @Size(max = 50, message = "O setor deve ter no máximo 50 caracteres.")
    @Column(nullable = false, length = 50)
    private String setor;

    @Size(max = 30, message = "O tamanho deve ter no máximo 30 caracteres.")
    @Column(length = 30)
    private String tamanho;

    @NotNull(message = "Informe o preço.")
    @DecimalMin(value = "0.01", message = "O preço deve ser maior que zero.")
    @DecimalMax(value = "99999999.99", message = "Preço acima do permitido.")
    @Digits(integer = 8, fraction = 2, message = "Use no máximo 2 casas decimais.")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    /** Quanto o mercado paga pelo produto. Usado no cálculo do lucro do dashboard. */
    @NotNull(message = "Informe o custo do produto.")
    @DecimalMin(value = "0.00", message = "O custo não pode ser negativo.")
    @DecimalMax(value = "99999999.99", message = "Custo acima do permitido.")
    @Digits(integer = 8, fraction = 2, message = "Use no máximo 2 casas decimais.")
    @Column(name = "preco_custo", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoCusto;

    @NotNull(message = "Informe a quantidade em estoque.")
    @Min(value = 0, message = "O estoque não pode ser negativo.")
    @Max(value = 1_000_000, message = "Quantidade acima do permitido.")
    @Column(name = "quantidade_estoque", nullable = false)
    private Integer quantidadeEstoque;

    @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
    @Column(length = 255)
    private String descricao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    public Produto(String nome, String tipo, String setor, String tamanho,
                   BigDecimal preco, BigDecimal precoCusto, Integer quantidadeEstoque, String descricao) {
        this.nome = nome;
        this.tipo = tipo;
        this.setor = setor;
        this.tamanho = tamanho;
        this.preco = preco;
        this.precoCusto = precoCusto;
        this.quantidadeEstoque = quantidadeEstoque;
        this.descricao = descricao;
    }

    public boolean isEstoqueBaixo() {
        return quantidadeEstoque != null && quantidadeEstoque < LIMITE_ESTOQUE_BAIXO;
    }

    public boolean isDisponivel() {
        return quantidadeEstoque != null && quantidadeEstoque > 0;
    }

    @AssertTrue(message = "O custo não pode ser maior que o preço de venda.")
    public boolean isCustoValido() {
        return preco == null || precoCusto == null || precoCusto.compareTo(preco) <= 0;
    }

    @PrePersist
    void aoCriar() {
        LocalDateTime agora = LocalDateTime.now();
        criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = LocalDateTime.now();
    }
}
