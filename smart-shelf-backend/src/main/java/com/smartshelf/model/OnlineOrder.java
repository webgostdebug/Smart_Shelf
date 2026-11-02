package com.smartshelf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "online_orders")
@JsonIgnoreProperties(ignoreUnknown = true)
public class OnlineOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderId;

    private String customerName;
    private String phone;

    @Column(length = 1000)
    private String address;

    @Column(length = 2000)
    private String itemsSummary;

    @Column(length = 4000)
    private String itemsJson;

    private boolean stockDeducted = false;

    private double totalAmount;
    private String status;
    private String timestamp;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getItemsSummary() { return itemsSummary; }
    public void setItemsSummary(String itemsSummary) { this.itemsSummary = itemsSummary; }
    public String getItemsJson() { return itemsJson; }
    public void setItemsJson(String itemsJson) { this.itemsJson = itemsJson; }
    public boolean isStockDeducted() { return stockDeducted; }
    public void setStockDeducted(boolean stockDeducted) { this.stockDeducted = stockDeducted; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    @jakarta.persistence.Transient
    private java.util.List<java.util.Map<String, Object>> items;

    public java.util.List<java.util.Map<String, Object>> getItems() { return items; }
    public void setItems(java.util.List<java.util.Map<String, Object>> items) { this.items = items; }
}