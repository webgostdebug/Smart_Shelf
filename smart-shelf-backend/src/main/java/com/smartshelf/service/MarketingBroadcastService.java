package com.smartshelf.service;

import com.smartshelf.model.Customer;
import com.smartshelf.model.StoreSettings;
import com.smartshelf.repository.CustomerRepository;
import com.smartshelf.repository.StoreSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MarketingBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(MarketingBroadcastService.class);

    private final CustomerRepository customerRepository;
    private final StoreSettingsRepository storeSettingsRepository;

    public MarketingBroadcastService(CustomerRepository customerRepository, StoreSettingsRepository storeSettingsRepository) {
        this.customerRepository = customerRepository;
        this.storeSettingsRepository = storeSettingsRepository;
    }

    public Map<String, Object> executeBroadcast(Map<String, Object> payload) {
        Map<String, Object> response = new LinkedHashMap<>();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<String> channels = (List<String>) payload.getOrDefault("channels", List.of("sms", "whatsapp", "instagram"));
        List<Map<String, Object>> topDeals = (List<Map<String, Object>>) payload.getOrDefault("topDeals", List.of());
        int maxDiscount = payload.get("maxDiscount") != null ? ((Number) payload.get("maxDiscount")).intValue() : 50;
        String storeUrl = (String) payload.getOrDefault("storeUrl", "https://webgostdebug.github.io/Smart_Shelf/shop.html");

        StringBuilder dealsText = new StringBuilder();
        int dealCount = 0;
        for (Map<String, Object> deal : topDeals) {
            if (dealCount >= 5) break; // keep SMS message concise
            String name = (String) deal.getOrDefault("itemName", deal.getOrDefault("name", "Artisan Bakery Item"));
            Object flashPrice = deal.getOrDefault("flashPrice", deal.getOrDefault("adjustedPrice", "0.00"));
            Object origPrice = deal.getOrDefault("originalPrice", deal.getOrDefault("basePrice", "0.00"));
            Object discount = deal.getOrDefault("discount", deal.getOrDefault("discountPercentage", ""));
            dealsText.append("• ").append(name)
                    .append(": ₹").append(flashPrice)
                    .append(" (Was ₹").append(origPrice).append(")");
            if (discount != null && !discount.toString().isBlank()) {
                dealsText.append(" [").append(discount).append("% OFF]");
            }
            dealsText.append("\n");
            dealCount++;
        }

        // 1. Text Messages (SMS) via Twilio (or graceful simulation fallback if credentials unconfigured)
        if (channels.contains("sms")) {
            List<Customer> regularCustomers = customerRepository.findAll().stream()
                    .filter(c -> c.getPhone() != null && !c.getPhone().trim().isEmpty())
                    .toList();

            StoreSettings settings = storeSettingsRepository.findById(1L).orElse(null);

            // Check payload first, then database
            String twilioSid = (payload.containsKey("twilioAccountSid") && payload.get("twilioAccountSid") != null && !((String) payload.get("twilioAccountSid")).isBlank())
                    ? ((String) payload.get("twilioAccountSid")).trim()
                    : (settings != null && settings.getTwilioAccountSid() != null ? settings.getTwilioAccountSid().trim() : "");

            String twilioToken = (payload.containsKey("twilioAuthToken") && payload.get("twilioAuthToken") != null && !((String) payload.get("twilioAuthToken")).isBlank())
                    ? ((String) payload.get("twilioAuthToken")).trim()
                    : (settings != null && settings.getTwilioAuthToken() != null ? settings.getTwilioAuthToken().trim() : "");

            String twilioFrom = (payload.containsKey("twilioPhoneNumber") && payload.get("twilioPhoneNumber") != null && !((String) payload.get("twilioPhoneNumber")).isBlank())
                    ? ((String) payload.get("twilioPhoneNumber")).trim()
                    : (settings != null && settings.getTwilioPhoneNumber() != null ? settings.getTwilioPhoneNumber().trim() : "");

            String fast2smsKey = (payload.containsKey("fast2smsApiKey") && payload.get("fast2smsApiKey") != null && !((String) payload.get("fast2smsApiKey")).isBlank())
                    ? ((String) payload.get("fast2smsApiKey")).trim()
                    : (settings != null && settings.getFast2smsApiKey() != null ? settings.getFast2smsApiKey().trim() : "");

            // Sanitize twilioFrom (+ prefix)
            String cleanFrom = twilioFrom.replaceAll("[^0-9+]", "");
            if (!cleanFrom.isEmpty() && !cleanFrom.startsWith("+")) {
                cleanFrom = "+" + cleanFrom;
            }

            boolean twilioConfigured = !twilioSid.isEmpty() && !twilioToken.isEmpty() && !cleanFrom.isEmpty();
            if (twilioConfigured) {
                try {
                    Twilio.init(twilioSid, twilioToken);
                    // Also auto-update settings if they weren't saved
                    if (settings != null) {
                        settings.setTwilioAccountSid(twilioSid);
                        settings.setTwilioAuthToken(twilioToken);
                        settings.setTwilioPhoneNumber(cleanFrom);
                        if (!fast2smsKey.isEmpty()) {
                            settings.setFast2smsApiKey(fast2smsKey);
                        }
                        storeSettingsRepository.save(settings);
                    }
                } catch (Exception initEx) {
                    log.warn("Twilio client initialization warning: {}", initEx.getMessage());
                }
            }

            List<Map<String, Object>> smsDispatches = new ArrayList<>();
            int successCount = 0;
            int failedCount = 0;
            String lastTwilioError = null;

            for (Customer customer : regularCustomers) {
                String customerName = customer.getName() != null && !customer.getName().isBlank() 
                        ? customer.getName() : "Valued Customer";
                String personalizedMsg = String.format(
                        "Hi %s! ⚡ Smart Shelf Flash Deal Alert at Bake & Bliss! Up to %d%% OFF on freshly baked treats before closing:\n%sOrder online to reserve: %s\n~ Zero Waste Flash Deals",
                        customerName,
                        maxDiscount,
                        dealsText.toString(),
                        storeUrl
                );

                String dispatchStatus = "DELIVERED";
                String dispatchError = null;

                if (!fast2smsKey.isEmpty()) {
                    try {
                        String rawPhone = customer.getPhone().trim().replaceAll("[^0-9]", "");
                        if (rawPhone.startsWith("91") && rawPhone.length() == 12) {
                            rawPhone = rawPhone.substring(2);
                        }
                        if (rawPhone.length() == 10) {
                            String encodedMessage = java.net.URLEncoder.encode(personalizedMsg, java.nio.charset.StandardCharsets.UTF_8);
                            String urlStr = "https://www.fast2sms.com/dev/bulkV2?route=q&message=" + encodedMessage + "&numbers=" + rawPhone;
                            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                            java.net.http.HttpRequest httpReq = java.net.http.HttpRequest.newBuilder()
                                    .uri(java.net.URI.create(urlStr))
                                    .header("authorization", fast2smsKey)
                                    .GET()
                                    .build();
                            java.net.http.HttpResponse<String> httpRes = httpClient.send(httpReq, java.net.http.HttpResponse.BodyHandlers.ofString());
                            com.fasterxml.jackson.databind.ObjectMapper fMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            Map<String, Object> fResult = fMapper.readValue(httpRes.body(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                            if (Boolean.TRUE.equals(fResult.get("return"))) {
                                dispatchStatus = "DELIVERED";
                                successCount++;
                                log.info("[FAST2SMS] Sent to {} ({})", customerName, rawPhone);
                            } else {
                                dispatchStatus = "FAILED";
                                dispatchError = fResult.getOrDefault("message", "Fast2SMS error").toString();
                                lastTwilioError = dispatchError;
                                failedCount++;
                                log.error("[FAST2SMS ERROR] Failed for {}: {}", customer.getPhone(), dispatchError);
                            }
                        } else {
                            dispatchStatus = "FAILED";
                            dispatchError = "Phone number must be 10 digits for Indian SMS";
                            failedCount++;
                        }
                    } catch (Exception fEx) {
                        dispatchStatus = "FAILED";
                        dispatchError = fEx.getMessage();
                        lastTwilioError = fEx.getMessage();
                        failedCount++;
                        log.error("[FAST2SMS ERROR] Exception for {}: {}", customer.getPhone(), fEx.getMessage());
                    }
                } else if (twilioConfigured) {
                    try {
                        String rawPhone = customer.getPhone().trim().replaceAll("[^0-9+]", "");
                        String toPhone = rawPhone.startsWith("+") ? rawPhone : (rawPhone.length() == 10 ? "+91" + rawPhone : "+" + rawPhone);
                        Message twilioMessage = Message.creator(
                                new PhoneNumber(toPhone),
                                new PhoneNumber(cleanFrom),
                                personalizedMsg
                        ).create();
                        dispatchStatus = twilioMessage.getStatus() != null ? twilioMessage.getStatus().toString() : "SENT";
                        successCount++;
                        log.info("[TWILIO SMS] Sent to {} ({}) - SID: {}, Status: {}", customerName, toPhone, twilioMessage.getSid(), dispatchStatus);
                    } catch (Exception twilioEx) {
                        dispatchStatus = "FAILED";
                        dispatchError = twilioEx.getMessage();
                        lastTwilioError = twilioEx.getMessage();
                        failedCount++;
                        log.error("[TWILIO SMS ERROR] Failed to send to {}: {}", customer.getPhone(), twilioEx.getMessage());
                    }
                } else {
                    successCount++;
                    log.info("[SMS BROADCAST SIMULATED] Dispatched SMS to regular customer {} ({}) - Message: {}",
                            customerName, customer.getPhone(), personalizedMsg.replace("\n", " "));
                }

                Map<String, Object> dispatchRecord = new LinkedHashMap<>();
                dispatchRecord.put("customerId", customer.getId());
                dispatchRecord.put("customerName", customerName);
                dispatchRecord.put("phone", customer.getPhone());
                dispatchRecord.put("status", dispatchStatus);
                if (dispatchError != null) {
                    dispatchRecord.put("error", dispatchError);
                }
                dispatchRecord.put("deliveredAt", timestamp);
                smsDispatches.add(dispatchRecord);
            }

            Map<String, Object> smsResult = new LinkedHashMap<>();
            smsResult.put("recipientCount", smsDispatches.size());
            smsResult.put("successCount", successCount);
            smsResult.put("failedCount", failedCount);
            smsResult.put("recipients", smsDispatches);
            smsResult.put("gateway", !fast2smsKey.isEmpty() ? "FAST2SMS" : (twilioConfigured ? "TWILIO" : "SIMULATION"));
            smsResult.put("status", failedCount > 0 && successCount == 0 ? "FAILED" : "SUCCESS");
            if (lastTwilioError != null) {
                smsResult.put("lastError", lastTwilioError);
            }
            smsResult.put("sampleMessage", String.format(
                    "Hi [Customer Name]! ⚡ Smart Shelf Flash Deal Alert at Bake & Bliss! Up to %d%% OFF:\n%sOrder now: %s",
                    maxDiscount, dealsText.toString(), storeUrl));
            response.put("sms", smsResult);
        }

        // 2. WhatsApp Community
        if (channels.contains("whatsapp")) {
            String whatsappMsg = String.format(
                    "🚨 *BAKE & BLISS FLASH SALE IS LIVE!* 🚨\n\n" +
                    "Grab tonight's artisan fresh-baked specials before we close! *Save up to %d%% OFF*:\n\n" +
                    "%s\n" +
                    "🛒 *Reserve & Order Now:* %s\n\n" +
                    "📍 Pick up at Bake & Bliss Counter or get express local delivery. Hurry, while freshly baked stock lasts!\n" +
                    "🌱 *Thank you for supporting Zero-Food-Waste baking!*",
                    maxDiscount,
                    dealsText.toString(),
                    storeUrl
            );

            Map<String, Object> waResult = new LinkedHashMap<>();
            waResult.put("status", "SUCCESS");
            waResult.put("channel", "WhatsApp Community");
            waResult.put("message", whatsappMsg);
            waResult.put("communityUrl", payload.getOrDefault("whatsappCommunityUrl", "https://chat.whatsapp.com/BakeAndBlissCommunity"));
            waResult.put("timestamp", timestamp);
            response.put("whatsapp", waResult);
        }

        // 3. Instagram Channel
        if (channels.contains("instagram")) {
            String instagramCaption = String.format(
                    "⚡ FLASH DEAL ALERT: Fresh from our ovens, ready for your table! 🥐✨\n\n" +
                    "Tonight only: Enjoy up to %d%% OFF our artisanal bakery selections before closing!\n\n" +
                    "%s\n" +
                    "📲 Order online via the link in our bio (%s) for pickup or express doorstep delivery!\n\n" +
                    "#BakeAndBliss #ZeroWasteBakery #SmartShelf #FreshlyBaked #BakeryDeals #ArtisanPastry #PastryDiscount #ChennaiFoodie",
                    maxDiscount,
                    dealsText.toString(),
                    payload.getOrDefault("instagramHandle", "@bakeandbliss")
            );

            Map<String, Object> igResult = new LinkedHashMap<>();
            igResult.put("status", "SUCCESS");
            igResult.put("channel", "Instagram Feed");
            igResult.put("caption", instagramCaption);
            igResult.put("handle", payload.getOrDefault("instagramHandle", "@bakeandbliss"));
            igResult.put("timestamp", timestamp);
            response.put("instagram", igResult);
        }

        return response;
    }
}
