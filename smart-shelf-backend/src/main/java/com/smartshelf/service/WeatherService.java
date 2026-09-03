package com.smartshelf.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
public class WeatherService {

    private static final double SHOP_LATITUDE = 13.038190;
    private static final double SHOP_LONGITUDE = 80.156548;
    private static final String API_KEY = "cb89d2ef8f4d13b16447bc50cdf5405b";

    public String fetchWeatherCondition() {
        try {
            String url = String.format(
                    "https://api.openweathermap.org/data/2.5/weather?lat=%.6f&lon=%.6f&appid=%s&units=metric",
                    SHOP_LATITUDE, SHOP_LONGITUDE, API_KEY);

            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("weather")) {
                var weatherList = (java.util.List<Map<String, Object>>) response.get("weather");
                if (!weatherList.isEmpty()) {
                    return (String) weatherList.get(0).get("main");
                }
            }
        } catch (Exception e) {
            System.err.println("Could not fetch precise local weather, defaulting to Clear. Error: " + e.getMessage());
        }
        return "Clear";
    }

    public double fetchWeatherPenalty() {
        String condition = fetchWeatherCondition().toLowerCase();
        if (condition.contains("rain") || condition.contains("storm") || condition.contains("drizzle")) {
            return 1.5;
        }
        return 1.0;
    }
}                                                 