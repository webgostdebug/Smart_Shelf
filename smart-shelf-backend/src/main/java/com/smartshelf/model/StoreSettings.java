package com.smartshelf.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;

@Entity
@Table(name = "store_settings")
public class StoreSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private boolean isOpen;

    private LocalTime closingTime = LocalTime.of(21, 0);

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public boolean isIsOpen() { return isOpen; }
    public void setIsOpen(boolean open) { isOpen = open; }
    public LocalTime getClosingTime() { return closingTime; }
    public void setClosingTime(LocalTime closingTime) { this.closingTime = closingTime; }
}