package com.smartshelf.service;

import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Service
public class StoreService {

    private boolean storeClosed = false;
    private LocalTime closingTime = LocalTime.of(21, 0);

    public synchronized LocalTime extendClosingTime(int minutesToAdd) {
        if (minutesToAdd <= 0) {
            throw new IllegalArgumentException("minutesToAdd must be greater than zero");
        }

        closingTime = closingTime.plusMinutes(minutesToAdd);
        storeClosed = false;
        return closingTime;
    }

    public synchronized boolean isStoreClosed() {
        return storeClosed;
    }

    public synchronized void setStoreClosed(boolean storeClosed) {
        this.storeClosed = storeClosed;
    }

    public synchronized LocalTime getClosingTime() {
        return closingTime;
    }

    public synchronized void setClosingTime(LocalTime closingTime) {
        this.closingTime = closingTime;
    }

    public synchronized boolean shouldRunCalculations() {
        return !storeClosed && LocalTime.now().isBefore(closingTime);
    }
}