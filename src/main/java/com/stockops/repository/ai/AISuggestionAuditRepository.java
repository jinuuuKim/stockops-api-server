package com.stockops.repository.ai;

import com.stockops.entity.ai.AISuggestionAudit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AISuggestionAuditRepository extends JpaRepository<AISuggestionAudit, Long> {

    List<AISuggestionAudit> findBySuggestionIdOrderByRecordedAtAsc(Long suggestionId);
}
