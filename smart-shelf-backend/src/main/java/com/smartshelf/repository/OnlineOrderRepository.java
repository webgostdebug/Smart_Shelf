package com.smartshelf.repository;

import com.smartshelf.model.OnlineOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OnlineOrderRepository extends JpaRepository<OnlineOrder, Long> {
    Optional<OnlineOrder> findByOrderId(String orderId);
}