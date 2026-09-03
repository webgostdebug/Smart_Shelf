package com.smartshelf.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_orders")
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp = LocalDateTime.now();
    private String summaryText;
    private int itemCount;
    private double baseValue;
    private double totalDiscountSaved;
    private double totalAmount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }
    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }
    public double getBaseValue() { return baseValue; }
    public void setBaseValue(double baseValue) { this.baseValue = baseValue; }
    public double getTotalDiscountSaved() { return totalDiscountSaved; }
    public void setTotalDiscountSaved(double totalDiscountSaved) { this.totalDiscountSaved = totalDiscountSaved; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
}
