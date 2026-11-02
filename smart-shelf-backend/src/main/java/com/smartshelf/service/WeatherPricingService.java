package com.smartshelf.service;

import org.springframework.stereotype.Service;

@Service
public class WeatherPricingService {

    private boolean rainEffectAlreadyTriggered;

    public synchronized boolean evaluateWeatherCondition(String currentWeather) {
        boolean adverseCondition = currentWeather != null && SmartShelfEngine.isAdverseWeather(currentWeather);

        if (adverseCondition && !rainEffectAlreadyTriggered) {
            rainEffectAlreadyTriggered = true;
            System.out.println("Adverse weather (" + currentWeather + ") detected for the first time. Weather markdown effect ready for manager approval.");
            return true;
        }

        if (!adverseCondition) {
            rainEffectAlreadyTriggered = false;
        }

        return false;
    }
}
