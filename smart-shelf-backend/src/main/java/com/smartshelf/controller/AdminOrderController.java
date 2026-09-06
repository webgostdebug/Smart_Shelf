package com.smartshelf.controller;

import com.smartshelf.model.OnlineOrder;
import com.smartshelf.repository.OnlineOrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/orders")
@CrossOrigin(origins = "*")
public class AdminOrderController {

    private final OnlineOrderRepository orderRepository;

    public AdminOrderController(OnlineOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping
    public ResponseEntity<List<OnlineOrder>> getOnlineOrders() {
        return ResponseEntity.ok(orderRepository.findAll());
    }

    @PostMapping("/{orderId}/status")
    public ResponseEntity<OnlineOrder> updateOrderStatus(
            @PathVariable String orderId, @RequestParam("status") String status) {
        Optional<OnlineOrder> optionalOrder = orderRepository.findByOrderId(orderId);
        if (optionalOrder.isPresent()) {
            OnlineOrder order = optionalOrder.get();
            order.setStatus(status);
            return ResponseEntity.ok(orderRepository.save(order));
        }
        return ResponseEntity.notFound().build();
    }
}