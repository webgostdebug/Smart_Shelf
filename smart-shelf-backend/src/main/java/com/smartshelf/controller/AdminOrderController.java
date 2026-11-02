package com.smartshelf.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartshelf.model.InventoryItem;
import com.smartshelf.model.OnlineOrder;
import com.smartshelf.repository.InventoryRepository;
import com.smartshelf.repository.OnlineOrderRepository;
import com.smartshelf.service.InventoryEvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/orders")
@CrossOrigin(origins = "*")
public class AdminOrderController {

    private final OnlineOrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryEvaluationService inventoryEvaluationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminOrderController(OnlineOrderRepository orderRepository,
                                InventoryRepository inventoryRepository,
                                InventoryEvaluationService inventoryEvaluationService) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryEvaluationService = inventoryEvaluationService;
    }

    @GetMapping
    public ResponseEntity<List<OnlineOrder>> getOnlineOrders() {
        return ResponseEntity.ok(orderRepository.findAll());
    }

    @PostMapping("/{orderId}/status")
    @Transactional
    public ResponseEntity<OnlineOrder> updateOrderStatus(
            @PathVariable String orderId, @RequestParam("status") String status) {
        Optional<OnlineOrder> optionalOrder = orderRepository.findByOrderId(orderId);
        if (optionalOrder.isPresent()) {
            OnlineOrder order = optionalOrder.get();
            order.setStatus(status);

            // Deduct inventory when order is accepted/packing/completed if not already deducted
            if (("Completed".equalsIgnoreCase(status) || "Packing".equalsIgnoreCase(status) || "Ready for Pickup".equalsIgnoreCase(status))
                    && !order.isStockDeducted()) {
                deductOrderStock(order);
                order.setStockDeducted(true);
            }

            try {
                inventoryEvaluationService.evaluateInventory();
            } catch (Exception ignored) {}

            return ResponseEntity.ok(orderRepository.save(order));
        }
        return ResponseEntity.notFound().build();
    }

    private void deductOrderStock(OnlineOrder order) {
        Map<String, Integer> itemQuantities = new HashMap<>();

        // 1. Try parsing from order items list if present
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            for (Map<String, Object> item : order.getItems()) {
                String name = (String) item.get("name");
                if (name == null || name.isBlank()) {
                    name = (String) item.get("itemName");
                }
                if (name != null && !name.isBlank()) {
                    int qty = 1;
                    if (item.containsKey("quantity") && item.get("quantity") != null) {
                        try {
                            qty = ((Number) item.get("quantity")).intValue();
                        } catch (Exception ignored) {}
                    }
                    itemQuantities.put(name.trim(), itemQuantities.getOrDefault(name.trim(), 0) + qty);
                }
            }
        }

        // 2. Try parsing from itemsJson if present
        if (itemQuantities.isEmpty() && order.getItemsJson() != null && !order.getItemsJson().isBlank()) {
            try {
                List<Map<String, Object>> parsedList = objectMapper.readValue(order.getItemsJson(), new TypeReference<>() {});
                for (Map<String, Object> item : parsedList) {
                    String name = (String) item.get("name");
                    if (name == null || name.isBlank()) {
                        name = (String) item.get("itemName");
                    }
                    if (name != null && !name.isBlank()) {
                        int qty = 1;
                        if (item.containsKey("quantity") && item.get("quantity") != null) {
                            try {
                                qty = ((Number) item.get("quantity")).intValue();
                            } catch (Exception ignored) {}
                        }
                        itemQuantities.put(name.trim(), itemQuantities.getOrDefault(name.trim(), 0) + qty);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3. Fallback: Parse from itemsSummary text (e.g. "Red Velvet Cake Slice (₹14.28), Chocolate Chip Muffin (₹12.75)")
        if (itemQuantities.isEmpty() && order.getItemsSummary() != null && !order.getItemsSummary().isBlank()) {
            String[] tokens = order.getItemsSummary().split(",");
            for (String token : tokens) {
                String clean = token.replaceAll("\\s*\\([^)]*\\).*", "").replaceAll("^[•\\s]+", "").trim();
                if (!clean.isBlank()) {
                    itemQuantities.put(clean, itemQuantities.getOrDefault(clean, 0) + 1);
                }
            }
        }

        // Apply deduction to inventory items
        for (Map.Entry<String, Integer> entry : itemQuantities.entrySet()) {
            String itemName = entry.getKey();
            int qtyToDeduct = entry.getValue();

            InventoryItem invItem = inventoryRepository.findFirstByProductNameIgnoreCase(itemName).orElse(null);
            if (invItem != null) {
                int newQty = Math.max(0, invItem.getCurrentQuantity() - qtyToDeduct);
                invItem.setCurrentQuantity(newQty);
                inventoryRepository.save(invItem);
            }
        }
    }
}