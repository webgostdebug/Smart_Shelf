package com.smartshelf.service;

import com.smartshelf.model.InventoryItem;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class SmartShelfEngine {

    // Define standard closing time: 9:00 PM (21:00)
    private static final LocalTime CLOSING_TIME = LocalTime.of(21, 0);

            public Map<String, Object> evaluateItem(InventoryItem item, double weatherPenalty,
                String weatherCondition, boolean rainDiscountApproved, double dayWeight) {
        LocalDateTime now = LocalDateTime.now();
        
        // Target today's 9:00 PM closing time
        LocalDateTime closingDateTime = LocalDateTime.of(now.toLocalDate(), CLOSING_TIME);
        
        // If it's already past 9 PM, target 9 PM tomorrow
        if (now.isAfter(closingDateTime)) {
            closingDateTime = closingDateTime.plusDays(1);
        }

        // Hours remaining until closing time
        double hoursRemaining = Duration.between(now, closingDateTime).toMinutes() / 60.0;
        
        if (hoursRemaining <= 0.1) {
            hoursRemaining = 0.1; // Safety guard against division by zero
        }

        // Risk Score Formula: (Items Left / Hours Remaining) * Weather Penalty * Day Weight
        double rawScore = ((double) item.getCurrentQuantity() / hoursRemaining) * weatherPenalty * dayWeight;
        
        // Normalize and cap the score out of 5.0 so thresholds like 3.5 work accurately
        double riskScore = Math.min(5.0, Math.round((rawScore / 15.0) * 100.0) / 100.0);

        // Decision Logic Matrix
        String action;
        String channel;
        int discountPercentage = 0;

        if (weatherCondition != null && weatherCondition.toLowerCase().contains("rain")
                && rainDiscountApproved) {
            discountPercentage = 50;
            action = "Approved Rain Emergency Sale (50% Off)";
            channel = "Rainy Day App Push & In-store Banner";
        } else {
            if (riskScore <= 1.50) {
                action = "Maintain Regular Price";
                channel = "Digital Menu (Standard)";
            } else if (riskScore <= 3.00) {
                action = "Soft Bundle (Coffee + Item)";
                channel = "POS Prompt & In-store Screen";
                discountPercentage = 10;
            } else if (riskScore <= 4.00) {
                action = "Zero-Waste Flash Sale (15-30% Off)";
                channel = "Local SMS / Web Push";
                discountPercentage = 25;
            } else {
                action = "Surplus Mystery Bag / BOGO";
                channel = "Aggressive Social / App Alert";
                discountPercentage = 50;
            }
        }

        // Safe extraction of name from the Product catalog relation
        String itemName = (item.getProduct() != null) ? item.getProduct().getName() : "Unknown Item";

        Map<String, Object> result = new HashMap<>();
        result.put("itemId", item.getId());
        result.put("itemName", itemName);
        double basePrice = (item.getProduct() != null) ? item.getProduct().getBasePrice() : 0.0;
        double adjustedPrice = Math.round((basePrice * (1.0 - (discountPercentage / 100.0))) * 100.0) / 100.0;
        result.put("riskScore", riskScore);
        result.put("currentQuantity", item.getCurrentQuantity());
        result.put("basePrice", basePrice);
        result.put("adjustedPrice", adjustedPrice);
        result.put("discountPercentage", discountPercentage);
        result.put("hoursUntilClosing", Math.round(hoursRemaining * 10.0) / 10.0);
        result.put("action", action);
        result.put("channel", channel);

        return result;
    }
}