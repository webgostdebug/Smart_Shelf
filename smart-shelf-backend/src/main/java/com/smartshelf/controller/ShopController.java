package com.smartshelf.controller;

import com.smartshelf.model.InventoryItem;
import com.smartshelf.model.OnlineOrder;
import com.smartshelf.repository.InventoryRepository;
import com.smartshelf.repository.OnlineOrderRepository;
import com.smartshelf.service.SmartShelfEngine;
import com.smartshelf.service.WeatherService;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api/shop")
@CrossOrigin(origins = "*")
public class ShopController {

    private final InventoryRepository inventoryRepository;
    private final OnlineOrderRepository orderRepository;
    private final SmartShelfEngine shelfEngine;
    private final WeatherService weatherService;

    public ShopController(InventoryRepository inventoryRepository,
            OnlineOrderRepository orderRepository, SmartShelfEngine shelfEngine,
            WeatherService weatherService) {
        this.inventoryRepository = inventoryRepository;
        this.orderRepository = orderRepository;
        this.shelfEngine = shelfEngine;
        this.weatherService = weatherService;
    }

    @GetMapping("/menu")
    public ResponseEntity<List<Map<String, Object>>> getCustomerMenu() {
        double weatherPenalty = weatherService.fetchWeatherPenalty();
        String weatherCondition = weatherService.fetchWeatherCondition();
        List<Map<String, Object>> menuItems = new ArrayList<>();

        for (InventoryItem item : inventoryRepository.findAllWithProducts()) {
            if (item.getProduct() == null || item.getCurrentQuantity() == null
                    || item.getCurrentQuantity() <= 0 || !"ACTIVE".equalsIgnoreCase(item.getStatus())) {
                continue;
            }

            Map<String, Object> evaluation = shelfEngine.evaluateItem(
                    item, weatherPenalty, weatherCondition, false, 1.0);
            Map<String, Object> menuItem = new HashMap<>();
            menuItem.put("id", item.getId());
            menuItem.put("itemName", item.getProduct().getName());
            menuItem.put("price", evaluation.get("adjustedPrice"));
            menuItem.put("originalPrice", item.getProduct().getBasePrice());
            menuItem.put("adjustedPrice", evaluation.get("adjustedPrice"));
            menuItem.put("basePrice", item.getProduct().getBasePrice());
            menuItem.put("isFlashDeal", ((Number) evaluation.get("discountPercentage")).doubleValue() > 0);
            menuItem.put("currentQuantity", item.getCurrentQuantity());
            menuItems.add(menuItem);
        }

        return ResponseEntity.ok(menuItems);
    }

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, Object>> placeCustomerOrder(
            @RequestBody OnlineOrder order) {
        order.setOrderId("ORD-" + System.currentTimeMillis());
        order.setStatus("Pending");
        order.setTimestamp(new Date().toString());

        orderRepository.save(order);
        return ResponseEntity.ok(Map.of("status", "Success", "orderId", order.getOrderId()));
    }
}