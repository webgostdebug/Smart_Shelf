package com.smartshelf.controller;

import com.smartshelf.model.InventoryItem;
import com.smartshelf.model.Product;
import com.smartshelf.model.SalesOrder;
import com.smartshelf.model.Customer;
import com.smartshelf.repository.InventoryRepository;
import com.smartshelf.repository.CustomerRepository;
import com.smartshelf.repository.OrderRepository;
import com.smartshelf.repository.ProductRepository;
import com.smartshelf.service.SmartShelfEngine;
import com.smartshelf.service.WeatherService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/shelf")
@CrossOrigin(origins = "*")
public class ShelfController {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SmartShelfEngine shelfEngine;

    // --- PRODUCT CATALOG MANAGEMENT ---

    @GetMapping("/products")
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(productRepository.findAll());
    }

    @PostMapping("/products")
    public ResponseEntity<Product> addProduct(@RequestBody Product product) {
        return ResponseEntity.ok(productRepository.save(product));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // --- DAILY INVENTORY MANAGEMENT ---

    @GetMapping("/inventory/all")
    public ResponseEntity<List<InventoryItem>> getAllInventory() {
        return ResponseEntity.ok(inventoryRepository.findAll());
    }

    // Daily Morning Restock: Sets or resets daily stock for a permanent product
    @PostMapping("/inventory/restock")
    public ResponseEntity<String> restockItem(@RequestParam Long productId, @RequestParam int quantity) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body("Product not found");
        }

        // Find the existing inventory item for this product or create one for today.
        InventoryItem item = inventoryRepository.findAll().stream()
                .filter(i -> i.getProduct() != null && i.getProduct().getId().equals(productId))
                .findFirst()
                .orElse(new InventoryItem());

        if (item.getId() == null) {
            item.setProduct(product);
            item.setStoreId(1L);
        }

        item.setCurrentQuantity(quantity);
        item.setStatus("ACTIVE");

        item.setBakedDate(LocalDate.now());
        int shelfLife = product.getShelfLifeDays() > 0 ? product.getShelfLifeDays() : 1;

        LocalDateTime expiryDateTime = LocalDateTime.now()
                .plusDays(shelfLife)
                .withHour(21)
                .withMinute(0)
                .withSecond(0);
        item.setExpiresAt(expiryDateTime);

        inventoryRepository.save(item);
    return ResponseEntity.ok("Stock updated with product-specific shelf life ("
        + shelfLife + " days)!");
    }

    @PostMapping("/inventory/rollover")
    public ResponseEntity<String> performEodRollover() {
        List<InventoryItem> items = inventoryRepository.findAll();
        LocalDate today = LocalDate.now();

        int expiredCount = 0;
        int carriedOverCount = 0;

        for (InventoryItem item : items) {
            LocalDate bakedDate = item.getBakedDate() != null ? item.getBakedDate() : today;
            int shelfLife = item.getShelfLifeDays() > 0 ? item.getShelfLifeDays() : 1;
            LocalDate expiryDate = bakedDate.plusDays(shelfLife);

            if (!today.isBefore(expiryDate)) {
                item.setCurrentQuantity(0);
                item.setStatus("EXPIRED");
                expiredCount++;
            } else {
                carriedOverCount++;
            }
            inventoryRepository.save(item);
        }

        return ResponseEntity.ok("EOD Rollover complete. Expired items zeroed: "
                + expiredCount + ", Carried over: " + carriedOverCount);
    }

    @PostMapping("/sell/{id}")
    public ResponseEntity<InventoryItem> sellItem(@PathVariable Long id, @RequestParam int quantitySold) {
        Optional<InventoryItem> optionalItem = inventoryRepository.findById(id);
        if (optionalItem.isEmpty()) return ResponseEntity.notFound().build();

        InventoryItem item = optionalItem.get();
        int newQty = Math.max(item.getCurrentQuantity() - quantitySold, 0);
        item.setCurrentQuantity(newQty);
        if (newQty == 0) item.setStatus("SOLD_OUT");

        return ResponseEntity.ok(inventoryRepository.save(item));
    }

    @Autowired
    private WeatherService weatherService;

    @GetMapping("/evaluate/{id}")
    public ResponseEntity<Map<String, Object>> evaluateStoredItem(
            @PathVariable Long id, @RequestParam double dayWeight,
            @RequestParam(defaultValue = "false") boolean rainDiscountApproved) {
        Optional<InventoryItem> optionalItem = inventoryRepository.findById(id);
        if (optionalItem.isEmpty()) return ResponseEntity.notFound().build();

        // Automatically fetch live weather penalty instead of manual parameter
        double liveWeatherPenalty = weatherService.fetchWeatherPenalty();
        String liveWeatherCondition = weatherService.fetchWeatherCondition();

        Map<String, Object> evaluation = shelfEngine.evaluateItem(
                optionalItem.get(), liveWeatherPenalty, liveWeatherCondition,
                rainDiscountApproved, dayWeight);
        return ResponseEntity.ok(evaluation);
    }

    @GetMapping("/inventory/evaluated")
        public ResponseEntity<List<Map<String, Object>>> getAllEvaluatedInventory(
            @RequestParam(defaultValue = "false") boolean rainDiscountApproved) {
        List<InventoryItem> items = inventoryRepository.findAllWithProducts();
        double liveWeatherPenalty = weatherService.fetchWeatherPenalty();
        String liveWeatherCondition = weatherService.fetchWeatherCondition();
        
        List<Map<String, Object>> evaluatedList = new java.util.ArrayList<>();
        for (InventoryItem item : items) {
            Map<String, Object> evaluation = shelfEngine.evaluateItem(
                    item, liveWeatherPenalty, liveWeatherCondition,
                    rainDiscountApproved, 1.0);
            evaluation.put("itemId", item.getId());
            evaluation.put("currentQuantity", item.getCurrentQuantity());
            evaluation.put("basePrice", item.getProduct() != null ? item.getProduct().getBasePrice() : 0.0);
            evaluation.put("category", item.getProduct() != null ? item.getProduct().getCategory() : "General");
            evaluation.put("shelfLifeDays", item.getShelfLifeDays());
            evaluatedList.add(evaluation);
        }
        return ResponseEntity.ok(evaluatedList);
    }

    @GetMapping("/shelf/posters")
    public ResponseEntity<List<Map<String, Object>>> getFlashPosters(
            @RequestParam(required = false, defaultValue = "false") boolean rainDiscountApproved) {
        List<InventoryItem> items = inventoryRepository.findAll();
        double liveWeatherPenalty = weatherService.fetchWeatherPenalty();
        String liveWeatherCondition = weatherService.fetchWeatherCondition();

        List<Map<String, Object>> discountedItems = new java.util.ArrayList<>();
        for (InventoryItem item : items) {
            Map<String, Object> evaluation = shelfEngine.evaluateItem(
                    item, liveWeatherPenalty, liveWeatherCondition, rainDiscountApproved, 1.0);
            double discount = 0.0;
            if (evaluation.containsKey("discountPercentage")) {
                discount = Double.parseDouble(evaluation.get("discountPercentage").toString());
            } else if (evaluation.containsKey("discount")) {
                discount = Double.parseDouble(evaluation.get("discount").toString());
            }

            if (discount > 0) {
                Map<String, Object> itemMap = new java.util.HashMap<>();
                itemMap.put("itemId", item.getId());
                itemMap.put("itemName", item.getProduct() != null ? item.getProduct().getName() : "Unknown Item");
                itemMap.put("category", item.getProduct() != null ? item.getProduct().getCategory() : "Bakery");
                itemMap.put("basePrice", item.getProduct() != null ? item.getProduct().getBasePrice() : 0.0);
                itemMap.put("adjustedPrice", evaluation.get("adjustedPrice"));
                itemMap.put("discountPercentage", discount);
                itemMap.put("riskScore", evaluation.get("riskScore"));
                discountedItems.add(itemMap);
            }
        }

        List<Map<String, Object>> megaPosters = new java.util.ArrayList<>();
        int batchSize = 10;
        int batchNumber = 1;
        for (int i = 0; i < discountedItems.size(); i += batchSize) {
            int end = Math.min(i + batchSize, discountedItems.size());
            Map<String, Object> megaPoster = new java.util.HashMap<>();
            megaPoster.put("batchId", batchNumber++);
            megaPoster.put("slogan", "MEGA FLASH SALE: ACCUMULATED CLOSEOUT BATCH");
            megaPoster.put("itemCount", end - i);
            megaPoster.put("items", discountedItems.subList(i, end));
            megaPosters.add(megaPoster);
        }
        return ResponseEntity.ok(megaPosters);
    }

    @GetMapping({"/orders", "/shelf/orders"})
    public ResponseEntity<List<SalesOrder>> getAllOrders() {
        return ResponseEntity.ok(orderRepository.findAll());
    }

    @GetMapping("/customers/lookup")
    public ResponseEntity<Customer> lookupCustomer(@RequestParam String phone) {
        return customerRepository.findByPhone(phone)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping({"/checkout", "/shelf/checkout"})
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> processCheckout(@RequestBody Map<String, Object> checkoutPayload) {
        List<Map<String, Object>> cartItems = (List<Map<String, Object>>) checkoutPayload.get("items");
        if (cartItems == null || cartItems.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Cart is empty"));
        }

        String customerName = (String) checkoutPayload.getOrDefault("customerName", "Walk-in Customer");
        String customerPhone = (String) checkoutPayload.getOrDefault("customerPhone", "");
        Customer customer = null;
        if (customerPhone != null && !customerPhone.isBlank()) {
            customer = customerRepository.findByPhone(customerPhone)
                    .orElseGet(() -> {
                        Customer newCustomer = new Customer();
                        newCustomer.setName(customerName);
                        newCustomer.setPhone(customerPhone);
                        return customerRepository.save(newCustomer);
                    });
        }

        double totalAmount = 0.0;
        double baseValue = 0.0;
        int totalItems = 0;
        StringBuilder summary = new StringBuilder();

        for (Map<String, Object> cartItem : cartItems) {
            Long itemId = Long.valueOf(cartItem.get("inventoryId").toString());
            int qtyToBuy = Integer.parseInt(cartItem.get("quantity").toString());
            double price = Double.parseDouble(cartItem.get("price").toString());
            double originalPrice = cartItem.get("originalPrice") != null
                    ? Double.parseDouble(cartItem.get("originalPrice").toString()) : price;

            totalItems += qtyToBuy;
            totalAmount += price * qtyToBuy;
            baseValue += originalPrice * qtyToBuy;

            InventoryItem inventoryItem = inventoryRepository.findById(itemId).orElse(null);
            if (inventoryItem == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Inventory item not found"));
            }
            inventoryItem.setCurrentQuantity(Math.max(0, inventoryItem.getCurrentQuantity() - qtyToBuy));
            inventoryRepository.save(inventoryItem);
            summary.append(inventoryItem.getProduct() != null ? inventoryItem.getProduct().getName() : "Item")
                    .append(" x").append(qtyToBuy).append(", ");
        }

        SalesOrder order = new SalesOrder();
        order.setSummaryText(summary.toString());
        order.setItemCount(totalItems);
        order.setTotalAmount(totalAmount);
        order.setBaseValue(baseValue);
        order.setTotalDiscountSaved(Math.max(0, baseValue - totalAmount));
        order.setCustomer(customer);
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of("success", true, "orderId", order.getId()));
    }

    @GetMapping("/weather/current")
    public ResponseEntity<Map<String, Object>> getCurrentWeather() {
        String condition = weatherService.fetchWeatherCondition();
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("condition", condition);
        response.put("city", "Chennai");
        response.put("penaltyMultiplier", weatherService.fetchWeatherPenalty());
        return ResponseEntity.ok(response);
    }
}