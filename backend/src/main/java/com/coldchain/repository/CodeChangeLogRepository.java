package com.coldchain.repository;

import com.coldchain.entity.CodeChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodeChangeLogRepository extends JpaRepository<CodeChangeLog, Long> {
    
    List<CodeChangeLog> findByShelfId(Long shelfId);
    
    List<CodeChangeLog> findByOperationType(Integer operationType);
    
    List<CodeChangeLog> findByShelfIdOrderByCreatedAtDesc(Long shelfId);
    
    List<CodeChangeLog> findAllByOrderByCreatedAtDesc();
}
