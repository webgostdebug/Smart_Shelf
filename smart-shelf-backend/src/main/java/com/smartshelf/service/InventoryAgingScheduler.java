package com.smartshelf.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class InventoryAgingScheduler {

    private final InventoryEvaluationService inventoryEvaluationService;

    public InventoryAgingScheduler(InventoryEvaluationService inventoryEvaluationService) {
        this.inventoryEvaluationService = inventoryEvaluationService;
    }

    @Scheduled(fixedRate = 60000)
    public void runContinuousEvaluation() {
        inventoryEvaluationService.evaluateInventory();
    }
}