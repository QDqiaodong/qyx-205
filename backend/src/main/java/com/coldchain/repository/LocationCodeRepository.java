package com.coldchain.repository;

import com.coldchain.entity.LocationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationCodeRepository extends JpaRepository<LocationCode, Long> {
    
    Optional<LocationCode> findByCode(String code);
    
    Optional<LocationCode> findByShelfId(Long shelfId);
    
    List<LocationCode> findByStatus(Integer status);
    
    List<LocationCode> findByShelfIdIsNull();
    
    boolean existsByCode(String code);
    
    boolean existsByShelfId(Long shelfId);
}
