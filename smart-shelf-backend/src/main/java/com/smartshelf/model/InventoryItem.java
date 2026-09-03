package com.smartshelf.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "current_quantity", nullable = false)
    private Integer currentQuantity;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private LocalDate bakedDate = LocalDate.now();

    private String status = "ACTIVE";

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Integer getCurrentQuantity() { return currentQuantity; }
    public void setCurrentQuantity(Integer currentQuantity) { this.currentQuantity = currentQuantity; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDate getBakedDate() { return bakedDate; }
    public void setBakedDate(LocalDate bakedDate) { this.bakedDate = bakedDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getShelfLifeDays() {
        return (product != null) ? product.getShelfLifeDays() : 1;
    }
}