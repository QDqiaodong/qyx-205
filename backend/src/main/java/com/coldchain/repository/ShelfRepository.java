package com.coldchain.repository;

import com.coldchain.entity.Shelf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShelfRepository extends JpaRepository<Shelf, Long> {
    
    Optional<Shelf> findByShelfNo(String shelfNo);
    
    List<Shelf> findByZone(String zone);
    
    List<Shelf> findByStatus(Integer status);
    
    List<Shelf> findByZoneAndStatus(String zone, Integer status);
    
    boolean existsByShelfNo(String shelfNo);
    
    List<Shelf> findAllByOrderByZoneAscShelfNoAsc();
}
