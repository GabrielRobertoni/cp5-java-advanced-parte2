package br.com.fiap.bancotranquilo.repository;

import br.com.fiap.bancotranquilo.model.TransacaoBancaria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransacaoRepository extends JpaRepository<TransacaoBancaria, UUID> {
}
