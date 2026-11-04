package com.smartshelf.controller;

import com.smartshelf.model.Customer;
import com.smartshelf.model.SalesOrder;
import com.smartshelf.repository.CustomerRepository;
import com.smartshelf.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CustomerDeletionTest {

    @Autowired
    private ShelfController shelfController;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("Deleting customer unlinks sales orders without foreign key violation and removes customer")
    void testDeleteCustomerWithSalesOrders() {
        // 1. Create and save a customer
        Customer cust = new Customer();
        cust.setName("Deletion Test User");
        cust.setPhone("9988776655");
        cust = customerRepository.save(cust);
        Long custId = cust.getId();

        // 2. Create a SalesOrder linked to this customer (simulating checkout)
        SalesOrder order = new SalesOrder();
        order.setSummaryText("Test Sourdough x1");
        order.setTotalAmount(150.0);
        order.setBaseValue(150.0);
        order.setItemCount(1);
        order.setCustomer(cust);
        order = orderRepository.save(order);
        Long orderId = order.getId();

        // 3. Delete the customer via shelfController using ID
        ResponseEntity<Map<String, Object>> response = shelfController.deleteCustomer(String.valueOf(custId));
        assertEquals(200, response.getStatusCode().value());
        assertTrue((Boolean) response.getBody().get("success"));

        // 4. Verify customer is removed from database
        Optional<Customer> foundCust = customerRepository.findById(custId);
        assertTrue(foundCust.isEmpty(), "Customer should be deleted from repository");

        // 5. Verify SalesOrder remains intact for store accounting, with customer unlinked (null)
        SalesOrder retainedOrder = orderRepository.findById(orderId).orElse(null);
        assertNotNull(retainedOrder, "SalesOrder should still exist for revenue/sales history");
        assertNull(retainedOrder.getCustomer(), "SalesOrder.customer should be unlinked/null");

        // Cleanup test order
        orderRepository.deleteById(orderId);
    }

    @Test
    @DisplayName("Deleting customer by phone number succeeds")
    void testDeleteCustomerByPhone() {
        Customer cust = new Customer();
        cust.setName("Phone Deletion Test");
        cust.setPhone("9123456789");
        cust = customerRepository.save(cust);

        ResponseEntity<Map<String, Object>> response = shelfController.deleteCustomer("9123456789");
        assertEquals(200, response.getStatusCode().value());
        assertTrue((Boolean) response.getBody().get("success"));

        assertTrue(customerRepository.findByPhone("9123456789").isEmpty());
    }
}
