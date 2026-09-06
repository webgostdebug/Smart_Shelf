package com.smartshelf.controller;

import com.smartshelf.service.StoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/store")
@CrossOrigin(origins = "*")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @PostMapping("/extend-time")
    public ResponseEntity<Map<String, Object>> extendClosingTime(@RequestParam int minutesToAdd) {
        try {
            storeService.extendClosingTime(minutesToAdd);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("status", "Failure", "message", exception.getMessage()));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "Success");
        response.put("newClosingTime", storeService.getClosingTime().toString());
        response.put("isStoreClosed", storeService.isStoreClosed());
        return ResponseEntity.ok(response);
    }
}