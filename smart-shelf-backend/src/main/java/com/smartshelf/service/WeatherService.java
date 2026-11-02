package com.smartshelf.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final long CACHE_TTL_MS = 15 * 60 * 1000; // 15 minutes cache

    @Value("${weather.shop.latitude:13.038190}")
    private double shopLatitude;

    @Value("${weather.shop.longitude:80.156548}")
    private double shopLongitude;

    @Value("${weather.api.key:cb89d2ef8f4d13b16447bc50cdf5405b}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private String cachedCondition = "Clear";
    private long lastFetchTimestamp = 0;

    public WeatherService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    @SuppressWarnings("unchecked")
    public synchronized String fetchWeatherCondition() {
        long now = System.currentTimeMillis();
        if (cachedCondition != null && (now - lastFetchTimestamp) < CACHE_TTL_MS) {
            return cachedCondition;
        }

        try {
            String url = String.format(
                    "https://api.openweathermap.org/data/2.5/weather?lat=%.6f&lon=%.6f&appid=%s&units=metric",
                    shopLatitude, shopLongitude, apiKey);

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("weather")) {
                var weatherList = (java.util.List<Map<String, Object>>) response.get("weather");
                if (!weatherList.isEmpty()) {
                    cachedCondition = (String) weatherList.get(0).get("main");
                    lastFetchTimestamp = now;
                    log.info("Refreshed weather telemetry: {}", cachedCondition);
                    return cachedCondition;
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch precise local weather, using cached/default value: {}. Error: {}", 
                     cachedCondition, e.getMessage());
        }

        // Return previously cached condition or default
        return cachedCondition != null ? cachedCondition : "Clear";
    }

    public double fetchWeatherPenalty() {
        String condition = fetchWeatherCondition().toLowerCase();
        return getWeatherPenalty(condition);
    }

    public double getWeatherPenalty(String condition) {
        String normalizedCondition = condition == null ? "" : condition.toLowerCase();
        if (normalizedCondition.contains("thunder") || normalizedCondition.contains("storm")
                || normalizedCondition.contains("squall") || normalizedCondition.contains("tornado")) {
            return 1.8;
        } else if (normalizedCondition.contains("rain")) {
            return 1.4;
        } else if (normalizedCondition.contains("drizzle")) {
            return 1.25;
        } else if (normalizedCondition.contains("mist") || normalizedCondition.contains("fog")
                || normalizedCondition.contains("haze")) {
            return 1.15;
        }
        return 1.0;
    }
}
