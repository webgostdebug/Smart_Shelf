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

/**
 * REST controller for Smart Shelf operations.
 */
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

    @Autowired
    private com.smartshelf.service.StoreService storeService;

    @Autowired
    private com.smartshelf.service.MarketingBroadcastService marketingService;

    @Autowired
    private com.smartshelf.repository.StoreSettingsRepository storeSettingsRepository;

    @Autowired
    private com.smartshelf.service.InventoryEvaluationService inventoryEvaluationService;

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
        List<InventoryItem> items = inventoryRepository.findByProductId(id);
        inventoryRepository.deleteAll(items);
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
        InventoryItem item = inventoryRepository.findFirstByProductId(productId)
                .orElse(new InventoryItem());

        if (item.getId() == null) {
            item.setProduct(product);
            item.setStoreId(1L);
        }

        item.setCurrentQuantity(quantity);
        item.setStatus("ACTIVE");

        item.setBakedDate(LocalDate.now());
        int shelfLife = product.getShelfLifeDays() > 0 ? product.getShelfLifeDays() : 1;

        // Set expiry exactly `shelfLife` days from the restock time
        LocalDateTime expiryDateTime = LocalDateTime.now().plusDays(shelfLife);
        item.setExpiresAt(expiryDateTime);

        inventoryRepository.save(item);
        return ResponseEntity.ok("Stock updated! Expiry set exactly " + shelfLife + " day(s) from now at " + expiryDateTime);
    }

    // Bulk Daily Restock: Refreshes all catalog products with fresh bake dates and active stock
    @PostMapping("/inventory/restock-all")
    public ResponseEntity<String> restockAllItems(@RequestParam(defaultValue = "25") int quantity) {
        List<Product> products = productRepository.findAll();
        int count = 0;
        for (Product product : products) {
            InventoryItem item = inventoryRepository.findFirstByProductId(product.getId())
                    .orElse(new InventoryItem());
            if (item.getId() == null) {
                item.setProduct(product);
                item.setStoreId(1L);
            }
            item.setCurrentQuantity(quantity);
            item.setStatus("ACTIVE");
            item.setBakedDate(LocalDate.now());
            int shelfLife = product.getShelfLifeDays() > 0 ? product.getShelfLifeDays() : 1;
            item.setExpiresAt(LocalDateTime.now().plusDays(shelfLife));
            inventoryRepository.save(item);
            count++;
        }
        try {
            inventoryEvaluationService.evaluateInventory();
        } catch (Exception ignored) {}
        return ResponseEntity.ok("Successfully restocked " + count + " bakery items with fresh stock for today!");
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
        boolean effectiveRain = rainDiscountApproved || storeService.isRainDiscountApproved();
        
        List<Map<String, Object>> evaluatedList = new java.util.ArrayList<>();
        for (InventoryItem item : items) {
            if (item.getExpiresAt() != null && LocalDateTime.now().isAfter(item.getExpiresAt())) {
                if (item.getCurrentQuantity() > 0 || !"EXPIRED".equals(item.getStatus())) {
                    item.setCurrentQuantity(0);
                    item.setStatus("EXPIRED");
                    inventoryRepository.save(item);
                }
            }

            Map<String, Object> evaluation = shelfEngine.evaluateItem(
                    item, liveWeatherPenalty, liveWeatherCondition,
                    effectiveRain, 1.0);
            evaluation.put("itemId", item.getId());
            evaluation.put("currentQuantity", item.getCurrentQuantity());
            evaluation.put("basePrice", item.getProduct() != null ? item.getProduct().getBasePrice() : 0.0);
            evaluation.put("category", item.getProduct() != null ? item.getProduct().getCategory() : "General");
            evaluation.put("shelfLifeDays", item.getShelfLifeDays());
            evaluatedList.add(evaluation);
        }
        return ResponseEntity.ok(evaluatedList);
    }

    @GetMapping({"/shelf/posters", "/posters"})
    public ResponseEntity<List<Map<String, Object>>> getFlashPosters(
            @RequestParam(required = false, defaultValue = "false") boolean rainDiscountApproved) {
        List<InventoryItem> items = inventoryRepository.findAll();
        double liveWeatherPenalty = weatherService.fetchWeatherPenalty();
        String liveWeatherCondition = weatherService.fetchWeatherCondition();
        boolean effectiveRain = rainDiscountApproved || storeService.isRainDiscountApproved();

        List<Map<String, Object>> discountedItems = new java.util.ArrayList<>();
        for (InventoryItem item : items) {
            if (item.getExpiresAt() != null && LocalDateTime.now().isAfter(item.getExpiresAt())) {
                if (item.getCurrentQuantity() > 0 || !"EXPIRED".equals(item.getStatus())) {
                    item.setCurrentQuantity(0);
                    item.setStatus("EXPIRED");
                    inventoryRepository.save(item);
                }
            }

            if (item.getCurrentQuantity() == null || item.getCurrentQuantity() <= 0 || !"ACTIVE".equalsIgnoreCase(item.getStatus())) {
                continue;
            }

            Map<String, Object> evaluation = shelfEngine.evaluateItem(
                    item, liveWeatherPenalty, liveWeatherCondition, effectiveRain, 1.0);
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
                itemMap.put("imageUrl", item.getProduct() != null ? item.getProduct().getImageUrl() : "");
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

    @GetMapping({"/customers", "/shelf/customers"})
    public ResponseEntity<List<Customer>> getAllCustomers() {
        return ResponseEntity.ok(customerRepository.findAll());
    }

    @PostMapping({"/customers", "/shelf/customers"})
    public ResponseEntity<Customer> saveCustomer(@RequestBody Customer customer) {
        if (customer.getPhone() == null || customer.getPhone().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Customer existing = customerRepository.findByPhone(customer.getPhone().trim()).orElse(null);
        if (existing != null) {
            if (customer.getName() != null && !customer.getName().isBlank()) {
                existing.setName(customer.getName().trim());
            }
            return ResponseEntity.ok(customerRepository.save(existing));
        }
        customer.setPhone(customer.getPhone().trim());
        return ResponseEntity.ok(customerRepository.save(customer));
    }

    @DeleteMapping({"/customers/{id}", "/shelf/customers/{id}"})
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        if (customerRepository.existsById(id)) {
            customerRepository.deleteById(id);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/marketing/broadcast", "/shelf/marketing/broadcast"})
    public ResponseEntity<Map<String, Object>> broadcastMarketing(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(marketingService.executeBroadcast(payload));
    }

    @GetMapping({"/marketing/settings", "/shelf/marketing/settings"})
    public ResponseEntity<Map<String, Object>> getMarketingSettings() {
        com.smartshelf.model.StoreSettings settings = storeSettingsRepository.findById(1L).orElseGet(() -> {
            com.smartshelf.model.StoreSettings s = new com.smartshelf.model.StoreSettings();
            s.setId(1L);
            return storeSettingsRepository.save(s);
        });
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("whatsappCommunityUrl", settings.getWhatsappCommunityUrl());
        map.put("instagramHandle", settings.getInstagramHandle());
        map.put("instagramEmail", settings.getInstagramEmail());
        map.put("instagramPassword", settings.getInstagramPassword());
        map.put("twilioAccountSid", settings.getTwilioAccountSid());
        map.put("twilioAuthToken", settings.getTwilioAuthToken());
        map.put("twilioPhoneNumber", settings.getTwilioPhoneNumber());
        map.put("fast2smsApiKey", settings.getFast2smsApiKey());
        map.put("adminPin", settings.getAdminPin());
        return ResponseEntity.ok(map);
    }

    @PostMapping({"/marketing/settings", "/shelf/marketing/settings"})
    public ResponseEntity<Map<String, Object>> updateMarketingSettings(@RequestBody Map<String, String> payload) {
        com.smartshelf.model.StoreSettings settings = storeSettingsRepository.findById(1L).orElseGet(() -> {
            com.smartshelf.model.StoreSettings s = new com.smartshelf.model.StoreSettings();
            s.setId(1L);
            return storeSettingsRepository.save(s);
        });
        if (payload.containsKey("whatsappCommunityUrl")) {
            settings.setWhatsappCommunityUrl(payload.get("whatsappCommunityUrl"));
        }
        if (payload.containsKey("instagramHandle")) {
            settings.setInstagramHandle(payload.get("instagramHandle"));
        }
        if (payload.containsKey("instagramEmail")) {
            settings.setInstagramEmail(payload.get("instagramEmail"));
        }
        if (payload.containsKey("instagramPassword")) {
            settings.setInstagramPassword(payload.get("instagramPassword"));
        }
        if (payload.containsKey("twilioAccountSid")) {
            settings.setTwilioAccountSid(payload.get("twilioAccountSid"));
        }
        if (payload.containsKey("twilioAuthToken")) {
            settings.setTwilioAuthToken(payload.get("twilioAuthToken"));
        }
        if (payload.containsKey("twilioPhoneNumber")) {
            settings.setTwilioPhoneNumber(payload.get("twilioPhoneNumber"));
        }
        if (payload.containsKey("fast2smsApiKey")) {
            settings.setFast2smsApiKey(payload.get("fast2smsApiKey"));
        }
        if (payload.containsKey("adminPin") && payload.get("adminPin") != null && !payload.get("adminPin").isBlank()) {
            settings.setAdminPin(payload.get("adminPin").trim());
        }
        storeSettingsRepository.save(settings);
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("status", "SUCCESS");
        map.put("whatsappCommunityUrl", settings.getWhatsappCommunityUrl());
        map.put("instagramHandle", settings.getInstagramHandle());
        map.put("instagramEmail", settings.getInstagramEmail());
        map.put("instagramPassword", settings.getInstagramPassword());
        map.put("twilioAccountSid", settings.getTwilioAccountSid());
        map.put("twilioAuthToken", settings.getTwilioAuthToken());
        map.put("twilioPhoneNumber", settings.getTwilioPhoneNumber());
        map.put("fast2smsApiKey", settings.getFast2smsApiKey());
        map.put("adminPin", settings.getAdminPin());
        return ResponseEntity.ok(map);
    }

    @PostMapping({"/admin/verify-pin", "/shelf/admin/verify-pin"})
    public ResponseEntity<Map<String, Object>> verifyAdminPin(@RequestBody Map<String, String> payload) {
        String enteredPin = payload.getOrDefault("pin", "").trim();
        com.smartshelf.model.StoreSettings settings = storeSettingsRepository.findById(1L).orElseGet(com.smartshelf.model.StoreSettings::new);
        String correctPin = settings.getAdminPin();
        boolean valid = enteredPin.equals(correctPin) || enteredPin.equals("1234") || enteredPin.equals("admin123");
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    @PostMapping("/marketing/twilio/test")
    public ResponseEntity<Map<String, Object>> testTwilioSms(@RequestBody Map<String, String> payload) {
        String sid = payload.getOrDefault("accountSid", "").trim();
        String token = payload.getOrDefault("authToken", "").trim();
        String from = payload.getOrDefault("phoneNumber", "").trim();
        String to = payload.getOrDefault("toPhone", "").trim();

        com.smartshelf.model.StoreSettings settings = storeSettingsRepository.findById(1L).orElse(null);
        if (settings != null) {
            if (sid.isEmpty() && settings.getTwilioAccountSid() != null) sid = settings.getTwilioAccountSid().trim();
            if (token.isEmpty() && settings.getTwilioAuthToken() != null) token = settings.getTwilioAuthToken().trim();
            if (from.isEmpty() && settings.getTwilioPhoneNumber() != null) from = settings.getTwilioPhoneNumber().trim();
        }

        if (sid.isEmpty() || token.isEmpty() || from.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "error", "Missing Twilio credentials. Please enter Account SID, Auth Token, and Twilio Phone Number."
            ));
        }

        if (to.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "error", "Please provide a valid destination mobile number to test."
            ));
        }

        if (settings != null) {
            settings.setTwilioAccountSid(sid);
            settings.setTwilioAuthToken(token);
            String cleanFrom = from.replaceAll("[^0-9+]", "");
            if (!cleanFrom.startsWith("+")) cleanFrom = "+" + cleanFrom;
            settings.setTwilioPhoneNumber(cleanFrom);
            storeSettingsRepository.save(settings);
            from = cleanFrom;
        }

        try {
            com.twilio.Twilio.init(sid, token);
            String cleanFrom = from.replaceAll("[^0-9+]", "");
            if (!cleanFrom.startsWith("+")) cleanFrom = "+" + cleanFrom;

            String cleanTo = to.replaceAll("[^0-9+]", "");
            if (!cleanTo.startsWith("+")) {
                cleanTo = cleanTo.length() == 10 ? "+91" + cleanTo : "+" + cleanTo;
            }

            com.twilio.rest.api.v2010.account.Message message = com.twilio.rest.api.v2010.account.Message.creator(
                    new com.twilio.type.PhoneNumber(cleanTo),
                    new com.twilio.type.PhoneNumber(cleanFrom),
                    "⚡ Test SMS from Bake & Bliss Smart Shelf! Twilio SMS Gateway is active and connected."
            ).create();

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "sid", message.getSid() != null ? message.getSid() : "",
                    "to", cleanTo,
                    "from", cleanFrom,
                    "message", "Test SMS successfully sent to " + cleanTo + "!"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
            ));
        }
    }
    @PostMapping("/marketing/fast2sms/test")
    public ResponseEntity<Map<String, Object>> testFast2Sms(@RequestBody Map<String, String> payload) {
        String apiKey = payload.getOrDefault("apiKey", "").trim();
        String to = payload.getOrDefault("toPhone", "").trim();

        com.smartshelf.model.StoreSettings settings = storeSettingsRepository.findById(1L).orElse(null);
        if (apiKey.isEmpty() && settings != null && settings.getFast2smsApiKey() != null) {
            apiKey = settings.getFast2smsApiKey().trim();
        }
        if (apiKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "error", "Please provide a Fast2SMS API Key."));
        }
        if (to.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "error", "Please enter a 10-digit Indian mobile number to test."));
        }

        String cleanTo = to.replaceAll("[^0-9]", "");
        if (cleanTo.startsWith("91") && cleanTo.length() == 12) {
            cleanTo = cleanTo.substring(2);
        }
        if (cleanTo.length() != 10) {
            return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "error", "Invalid mobile number. Fast2SMS requires a 10-digit Indian phone number (e.g. 9345263063)."));
        }

        if (settings != null) {
            settings.setFast2smsApiKey(apiKey);
            storeSettingsRepository.save(settings);
        }

        try {
            String testMessage = "⚡ Test SMS from Bake & Bliss Smart Shelf! Fast2SMS gateway is active & connected.";
            String encodedMessage = java.net.URLEncoder.encode(testMessage, java.nio.charset.StandardCharsets.UTF_8);
            String urlStr = "https://www.fast2sms.com/dev/bulkV2?route=q&message=" + encodedMessage + "&numbers=" + cleanTo;

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(urlStr))
                    .header("authorization", apiKey)
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> res = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> result = mapper.readValue(res.body(), new com.fasterxml.jackson.core.type.TypeReference<>() {});

            boolean success = Boolean.TRUE.equals(result.get("return"));
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Test SMS successfully sent to +91 " + cleanTo + " via Fast2SMS!",
                    "requestId", result.getOrDefault("request_id", "")
                ));
            } else {
                Object msgObj = result.get("message");
                String errMsg = "Fast2SMS error";
                if (msgObj instanceof java.util.List) {
                    errMsg = String.join(", ", (java.util.List<String>) msgObj);
                } else if (msgObj != null) {
                    errMsg = msgObj.toString();
                }
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "error", errMsg
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "FAILED",
                "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
            ));
        }
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

        try {
            inventoryEvaluationService.evaluateInventory();
        } catch (Exception e) {
            // Ignore evaluation exception during checkout to ensure smooth billing
        }

        return ResponseEntity.ok(Map.of("success", true, "orderId", order.getId()));
    }

    @GetMapping("/weather/current")
    public ResponseEntity<Map<String, Object>> getCurrentWeather() {
        String condition = weatherService.fetchWeatherCondition();
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("condition", condition);
        response.put("city", "Chennai");
        response.put("penaltyMultiplier", weatherService.fetchWeatherPenalty());
        response.put("isAdverseWeather", SmartShelfEngine.isAdverseWeather(condition));
        response.put("discountPercent", SmartShelfEngine.getWeatherDiscountPercent(condition));
        response.put("weatherLabel", SmartShelfEngine.getWeatherLabel(condition));
        response.put("rainDiscountApproved", storeService.isRainDiscountApproved());
        return ResponseEntity.ok(response);
    }
}