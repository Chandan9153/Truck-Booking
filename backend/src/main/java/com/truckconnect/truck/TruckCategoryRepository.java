package com.truckconnect.truck;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TruckCategoryRepository extends JpaRepository<TruckCategory, Long> {
    boolean existsByName(String name);
    List<TruckCategory> findByActiveTrueOrderByTyreCountAsc();
}
