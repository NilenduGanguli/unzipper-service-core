package com.unzipper.repository;

import com.unzipper.entity.KycDocumentUnzip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KycDocumentUnzipRepository extends JpaRepository<KycDocumentUnzip, Integer> {
    Optional<KycDocumentUnzip> findByClientIdAndDocumentLinkId(String clientId, String documentLinkId);
}
