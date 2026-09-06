package com.smartshelf.component;

import com.smartshelf.service.InventoryEvaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class InventoryStartupInitializer implements CommandLineRunner {

    private final InventoryEvaluationService inventoryEvaluationService;

    @Autowired
    public InventoryStartupInitializer(InventoryEvaluationService inventoryEvaluationService) {
        this.inventoryEvaluationService = inventoryEvaluationService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Smart Shelf Server Started: Evaluating live inventory aging and risk scores...");

        inventoryEvaluationService.evaluateInventory();

        System.out.println("Inventory successfully re-evaluated for current server time!");
    }
}
