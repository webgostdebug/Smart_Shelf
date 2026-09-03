package com.smartshelf.component;

import com.smartshelf.model.InventoryItem;
import com.smartshelf.repository.InventoryRepository;
import com.smartshelf.service.SmartShelfEngine;
import com.smartshelf.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InventoryStartupInitializer implements CommandLineRunner {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private SmartShelfEngine shelfEngine;

    @Autowired
    private WeatherService weatherService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Smart Shelf Server Started: Evaluating live inventory aging and risk scores...");

        List<InventoryItem> items = inventoryRepository.findAllWithProducts();
        double weatherPenalty = weatherService.fetchWeatherPenalty();
        String weatherCondition = weatherService.fetchWeatherCondition();

        for (InventoryItem item : items) {
            shelfEngine.evaluateItem(item, weatherPenalty, weatherCondition, false, 1.0);
        }

        System.out.println("Inventory successfully re-evaluated for current server time!");
    }
}
