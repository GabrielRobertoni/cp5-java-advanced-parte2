package br.com.fiap.emailservice.repository;

import br.com.fiap.emailservice.model.EmailModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailRepository extends JpaRepository<EmailModel, UUID> {

    Optional<EmailModel> findBySolicitacaoId(UUID solicitacaoId);
}
