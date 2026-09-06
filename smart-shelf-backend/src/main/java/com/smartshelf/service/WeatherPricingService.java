package com.smartshelf.service;

import org.springframework.stereotype.Service;

@Service
public class WeatherPricingService {

    private boolean rainEffectAlreadyTriggered;

    public synchronized boolean evaluateWeatherCondition(String currentWeather) {
        boolean rainLikeCondition = currentWeather != null
                && (currentWeather.toLowerCase().contains("rain")
                || currentWeather.toLowerCase().contains("drizzle")
                || currentWeather.toLowerCase().contains("storm"));

        if (rainLikeCondition && !rainEffectAlreadyTriggered) {
            rainEffectAlreadyTriggered = true;
            System.out.println("Rain detected for the first time. Weather markdown effect applied.");
            return true;
        }

        if (!rainLikeCondition) {
            rainEffectAlreadyTriggered = false;
        }

        return false;
    }
}