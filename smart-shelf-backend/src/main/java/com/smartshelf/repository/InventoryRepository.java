package com.smartshelf.repository;

import com.smartshelf.model.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, Long> {
    List<InventoryItem> findByStoreId(Long storeId);

    @Query("SELECT i FROM InventoryItem i JOIN FETCH i.product")
    List<InventoryItem> findAllWithProducts();
}