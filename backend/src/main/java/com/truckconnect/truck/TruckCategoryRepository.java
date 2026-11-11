package com.truckconnect.truck;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TruckCategoryRepository extends JpaRepository<TruckCategory, Long> {
    boolean existsByName(String name);
    Optional<TruckCategory> findByName(String name);
    List<TruckCategory> findByActiveTrueOrderByTyreCountAsc();
}
