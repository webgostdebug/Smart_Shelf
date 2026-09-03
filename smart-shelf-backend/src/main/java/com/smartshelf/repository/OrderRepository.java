package com.smartshelf.repository;

import com.smartshelf.model.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<SalesOrder, Long> {
}
