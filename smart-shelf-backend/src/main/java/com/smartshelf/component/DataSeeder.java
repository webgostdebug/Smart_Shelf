package com.smartshelf.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartshelf.model.InventoryItem;
import com.smartshelf.model.Product;
import com.smartshelf.model.StoreSettings;
import com.smartshelf.repository.InventoryRepository;
import com.smartshelf.repository.ProductRepository;
import com.smartshelf.repository.StoreSettingsRepository;
import com.smartshelf.service.InventoryEvaluationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryEvaluationService inventoryEvaluationService;
    private final com.smartshelf.repository.CustomerRepository customerRepository;
    private final StoreSettingsRepository storeSettingsRepository;

    public DataSeeder(ProductRepository productRepository,
                      InventoryRepository inventoryRepository,
                      InventoryEvaluationService inventoryEvaluationService,
                      com.smartshelf.repository.CustomerRepository customerRepository,
                      StoreSettingsRepository storeSettingsRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryEvaluationService = inventoryEvaluationService;
        this.customerRepository = customerRepository;
        this.storeSettingsRepository = storeSettingsRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (productRepository.count() < 50) {
            System.out.println("Seeding full 50-item bakery catalog and inventory into database...");
            inventoryRepository.deleteAll();
            productRepository.deleteAll();

            ClassPathResource resource = new ClassPathResource("initial_catalog_50.json");
            if (resource.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                try (InputStream is = resource.getInputStream()) {
                    List<Map<String, Object>> items = mapper.readValue(is, new TypeReference<>() {});
                    for (Map<String, Object> itemData : items) {
                        String name = (String) itemData.get("name");
                        String category = (String) itemData.get("category");
                        Double basePrice = ((Number) itemData.get("basePrice")).doubleValue();
                        int shelfLifeDays = ((Number) itemData.get("shelfLifeDays")).intValue();
                        String imageUrl = (String) itemData.get("imageUrl");
                        int quantity = ((Number) itemData.get("quantity")).intValue();
                        String status = (String) itemData.getOrDefault("status", quantity > 0 ? "ACTIVE" : "EXPIRED");

                        Product product = new Product();
                        product.setName(name);
                        product.setCategory(category);
                        product.setBasePrice(basePrice);
                        product.setShelfLifeDays(shelfLifeDays);
                        product.setImageUrl(imageUrl);
                        Product savedProduct = productRepository.save(product);

                        InventoryItem item = new InventoryItem();
                        item.setStoreId(1L);
                        item.setProduct(savedProduct);
                        item.setCurrentQuantity(quantity);
                        item.setStatus(status);
                        item.setBakedDate(LocalDate.now());
                        item.setExpiresAt(LocalDateTime.now().plusDays(shelfLifeDays));
                        inventoryRepository.save(item);
                    }
                }
                inventoryEvaluationService.evaluateInventory();
                System.out.println("Data seeding complete! Successfully seeded all " + productRepository.count() + " products and inventory items.");
            }
        }

        StoreSettings settings = storeSettingsRepository.findById(1L).orElse(null);
        if (settings == null) {
            settings = new StoreSettings();
            settings.setId(1L);
            settings = storeSettingsRepository.save(settings);
        }

        // Only seed initial regular customers if the database has NEVER seeded them before
        if (!settings.isInitialCustomersSeeded() && customerRepository.count() == 0) {
            com.smartshelf.model.Customer c1 = new com.smartshelf.model.Customer();
            c1.setName("Sandheep R S");
            c1.setPhone("9876543210");
            customerRepository.save(c1);

            com.smartshelf.model.Customer c2 = new com.smartshelf.model.Customer();
            c2.setName("Raghul S");
            c2.setPhone("9876543211");
            customerRepository.save(c2);

            com.smartshelf.model.Customer c3 = new com.smartshelf.model.Customer();
            c3.setName("Priya Sharma");
            c3.setPhone("9876543212");
            customerRepository.save(c3);

            com.smartshelf.model.Customer c4 = new com.smartshelf.model.Customer();
            c4.setName("Ananya Iyer");
            c4.setPhone("9876543213");
            customerRepository.save(c4);

            settings.setInitialCustomersSeeded(true);
            storeSettingsRepository.save(settings);
            System.out.println("Seeded initial regular customer phone directory (4 regular customers).");
        }
    }
}
