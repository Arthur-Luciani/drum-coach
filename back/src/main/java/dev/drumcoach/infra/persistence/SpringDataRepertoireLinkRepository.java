package dev.drumcoach.infra.persistence;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

/** Repositorio tecnico Spring Data JDBC - detalhe de implementacao de {@code infra}. */
interface SpringDataRepertoireLinkRepository extends CrudRepository<RepertoireLinkEntity, Long> {

	List<RepertoireLinkEntity> findByRepertoireItemId(long repertoireItemId);
}
