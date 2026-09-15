package com.coldchain.repository;

import com.coldchain.entity.PalletActive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PalletActiveRepository extends JpaRepository<PalletActive, String> {

    boolean existsByShelfId(Long shelfId);

    long countByShelfId(Long shelfId);
}
