package com.smartshelf.controller;

import com.smartshelf.model.Customer;
import com.smartshelf.model.InventoryItem;
import com.smartshelf.model.OnlineOrder;
import com.smartshelf.model.SalesOrder;
import com.smartshelf.repository.CustomerRepository;
import com.smartshelf.repository.InventoryRepository;
import com.smartshelf.repository.OnlineOrderRepository;
import com.smartshelf.repository.OrderRepository;
import com.smartshelf.service.SmartShelfEngine;
import com.smartshelf.service.StoreService;
import com.smartshelf.service.WeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/shop")
@CrossOrigin(origins = "*")
public class ShopController {

    private final InventoryRepository inventoryRepository;
    private final OnlineOrderRepository orderRepository;
    private final OrderRepository salesOrderRepository;
    private final SmartShelfEngine shelfEngine;
    private final WeatherService weatherService;
    private final StoreService storeService;
    private final CustomerRepository customerRepository;
    private final com.smartshelf.service.InventoryEvaluationService inventoryEvaluationService;

    public ShopController(InventoryRepository inventoryRepository,
            OnlineOrderRepository orderRepository,
            OrderRepository salesOrderRepository,
            SmartShelfEngine shelfEngine,
            WeatherService weatherService,
            StoreService storeService,
            CustomerRepository customerRepository,
            com.smartshelf.service.InventoryEvaluationService inventoryEvaluationService) {
        this.inventoryRepository = inventoryRepository;
        this.orderRepository = orderRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.shelfEngine = shelfEngine;
        this.weatherService = weatherService;
        this.storeService = storeService;
        this.customerRepository = customerRepository;
        this.inventoryEvaluationService = inventoryEvaluationService;
    }

    @GetMapping("/menu")
    public ResponseEntity<List<Map<String, Object>>> getCustomerMenu(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean rainDiscountApproved) {
        double weatherPenalty = weatherService.fetchWeatherPenalty();
        String weatherCondition = weatherService.fetchWeatherCondition();
        boolean rainApproved = (rainDiscountApproved != null) ? rainDiscountApproved : storeService.isRainDiscountApproved();
        List<Map<String, Object>> menuItems = new ArrayList<>();

        for (InventoryItem item : inventoryRepository.findAllWithProducts()) {
            if (item.getExpiresAt() != null && LocalDateTime.now().isAfter(item.getExpiresAt())) {
                if (item.getCurrentQuantity() > 0 || !"EXPIRED".equals(item.getStatus())) {
                    item.setCurrentQuantity(0);
                    item.setStatus("EXPIRED");
                    inventoryRepository.save(item);
                }
            }

            if (item.getProduct() == null || item.getCurrentQuantity() == null
                    || item.getCurrentQuantity() <= 0 || !"ACTIVE".equalsIgnoreCase(item.getStatus())) {
                continue;
            }

            Map<String, Object> evaluation = shelfEngine.evaluateItem(
                    item, weatherPenalty, weatherCondition, rainApproved, 1.0);
            Map<String, Object> menuItem = new HashMap<>();
            menuItem.put("id", item.getId());
            menuItem.put("itemName", item.getProduct().getName());
            menuItem.put("price", evaluation.get("adjustedPrice"));
            menuItem.put("adjustedPrice", evaluation.get("adjustedPrice"));
            menuItem.put("originalPrice", item.getProduct().getBasePrice());
            menuItem.put("basePrice", item.getProduct().getBasePrice());
            menuItem.put("discountPercentage", evaluation.get("discountPercentage"));
            menuItem.put("isFlashDeal", ((Number) evaluation.get("discountPercentage")).doubleValue() > 0);
            menuItem.put("currentQuantity", item.getCurrentQuantity());
            menuItem.put("imageUrl", item.getProduct().getImageUrl());
            menuItems.add(menuItem);
        }

        return ResponseEntity.ok(menuItems);
    }

    @PostMapping("/checkout")
    @Transactional
    public ResponseEntity<Map<String, Object>> placeCustomerOrder(
            @RequestBody OnlineOrder order) {

        List<Map<String, Object>> items = order.getItems();
        double baseValue = 0.0;
        int totalItemCount = 0;

        // Deduct inventory stock if items list is provided
        if (items != null && !items.isEmpty()) {
            Map<String, Integer> counts = new HashMap<>();
            for (Map<String, Object> cartItem : items) {
                String name = (String) cartItem.get("name");
                if (name == null || name.isBlank()) {
                    name = (String) cartItem.get("itemName");
                }
                if (name != null && !name.isBlank()) {
                    String trimmedName = name.trim();
                    int qty = 1;
                    if (cartItem.containsKey("quantity") && cartItem.get("quantity") != null) {
                        try {
                            qty = ((Number) cartItem.get("quantity")).intValue();
                        } catch (Exception ignored) {}
                    }
                    counts.put(trimmedName, counts.getOrDefault(trimmedName, 0) + qty);
                }
            }

            // First pass: Verify all items have enough stock
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                String itemName = entry.getKey();
                int requestedQty = entry.getValue();
                InventoryItem invItem = inventoryRepository.findFirstByProductNameIgnoreCase(itemName).orElse(null);
                if (invItem == null || invItem.getCurrentQuantity() < requestedQty) {
                    int available = invItem != null ? invItem.getCurrentQuantity() : 0;
                    return ResponseEntity.badRequest().body(Map.of(
                            "status", "Error",
                            "message", "Insufficient stock for " + itemName + " (Available: " + available + ", Requested: " + requestedQty + ")"
                    ));
                }
            }

            // Second pass: Deduct stock and accumulate base value
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                String itemName = entry.getKey();
                int requestedQty = entry.getValue();
                InventoryItem invItem = inventoryRepository.findFirstByProductNameIgnoreCase(itemName).orElse(null);
                if (invItem != null) {
                    invItem.setCurrentQuantity(Math.max(0, invItem.getCurrentQuantity() - requestedQty));
                    inventoryRepository.save(invItem);
                    if (invItem.getProduct() != null && invItem.getProduct().getBasePrice() != null) {
                        baseValue += invItem.getProduct().getBasePrice() * requestedQty;
                    }
                    totalItemCount += requestedQty;
                }
            }
            try {
                inventoryEvaluationService.evaluateInventory();
            } catch (Exception e) {
                // ignore evaluation error during checkout
            }
            try {
                order.setItemsJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(items));
            } catch (Exception ignored) {}
            order.setStockDeducted(true);
        }

        order.setOrderId("ORD-" + System.currentTimeMillis());
        order.setStatus("Pending");
        order.setTimestamp(new Date().toString());
        orderRepository.save(order);

        Customer customer = null;
        if (order.getPhone() != null && !order.getPhone().trim().isEmpty()) {
            String phone = order.getPhone().trim();
            String name = (order.getCustomerName() != null && !order.getCustomerName().isBlank())
                    ? order.getCustomerName().trim() : "Online Customer";
            customer = customerRepository.findByPhone(phone).orElse(null);
            if (customer != null) {
                if (order.getCustomerName() != null && !order.getCustomerName().isBlank()) {
                    customer.setName(name);
                    customer = customerRepository.save(customer);
                }
            } else {
                Customer newCust = new Customer();
                newCust.setName(name);
                newCust.setPhone(phone);
                customer = customerRepository.save(newCust);
            }
        }

        // Record into sales analytics so online sales appear in Revenue & Sales metrics
        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setSummaryText("[Online] " + (order.getItemsSummary() != null ? order.getItemsSummary() : "Customer Order"));
        salesOrder.setItemCount(totalItemCount > 0 ? totalItemCount : 1);
        salesOrder.setTotalAmount(order.getTotalAmount());
        salesOrder.setBaseValue(baseValue > 0 ? baseValue : order.getTotalAmount());
        salesOrder.setTotalDiscountSaved(Math.max(0, salesOrder.getBaseValue() - salesOrder.getTotalAmount()));
        salesOrder.setCustomer(customer);
        salesOrderRepository.save(salesOrder);

        return ResponseEntity.ok(Map.of("status", "Success", "orderId", order.getOrderId()));
    }
}