package com.smartshelf.repository;

import com.smartshelf.model.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<SalesOrder, Long> {
    List<SalesOrder> findByCustomerId(Long customerId);

    @Modifying
    @Transactional
    @Query("UPDATE SalesOrder s SET s.customer = null WHERE s.customer.id = :customerId")
    void detachCustomer(@Param("customerId") Long customerId);
}
