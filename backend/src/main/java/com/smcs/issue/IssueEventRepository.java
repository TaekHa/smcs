package com.smcs.issue;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueEventRepository extends JpaRepository<IssueEvent, Long> {

	List<IssueEvent> findByIssueIdOrderByCreatedAtAsc(Long issueId);
}
