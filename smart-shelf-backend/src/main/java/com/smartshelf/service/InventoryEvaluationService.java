package com.smartshelf.service;

import com.smartshelf.model.InventoryItem;
import com.smartshelf.repository.InventoryRepository;
import com.smartshelf.repository.StoreSettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class InventoryEvaluationService {

    private final InventoryRepository inventoryRepository;
    private final SmartShelfEngine shelfEngine;
    private final WeatherService weatherService;
    private final StoreSettingsRepository storeSettingsRepository;
    private final StoreService storeService;
    private final WeatherPricingService weatherPricingService;

    public InventoryEvaluationService(InventoryRepository inventoryRepository,
            SmartShelfEngine shelfEngine, WeatherService weatherService,
            StoreSettingsRepository storeSettingsRepository, StoreService storeService,
            WeatherPricingService weatherPricingService) {
        this.inventoryRepository = inventoryRepository;
        this.shelfEngine = shelfEngine;
        this.weatherService = weatherService;
        this.storeSettingsRepository = storeSettingsRepository;
        this.storeService = storeService;
        this.weatherPricingService = weatherPricingService;
    }

    public void evaluateInventory() {
        storeSettingsRepository.findById(1L)
                .map(settings -> settings.getClosingTime())
                .ifPresent(storeService::setClosingTime);

        String weatherCondition = weatherService.fetchWeatherCondition();
        weatherPricingService.evaluateWeatherCondition(weatherCondition);
        double weatherPenalty = weatherService.getWeatherPenalty(weatherCondition);

        for (InventoryItem item : inventoryRepository.findAllWithProducts()) {
            shelfEngine.evaluateItem(item, weatherPenalty, weatherCondition, false, 1.0);
        }
    }
}