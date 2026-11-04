package com.smartshelf.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component("databaseSchemaMigrator")
public class DatabaseSchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaMigrator.class);
    private final DataSource dataSource;

    public DatabaseSchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void migrateSchema() {
        log.info("Running automatic database schema column migration for all tables...");
        String[] alterStatements = new String[]{
            // store_settings columns
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS rain_discount_approved BOOLEAN DEFAULT FALSE",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS whatsapp_community_url VARCHAR(255) DEFAULT 'https://chat.whatsapp.com/BakeAndBlissCommunity'",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS instagram_handle VARCHAR(255) DEFAULT '@bakeandbliss'",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS instagram_email VARCHAR(255) DEFAULT ''",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS instagram_password VARCHAR(255) DEFAULT ''",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS twilio_account_sid VARCHAR(255) DEFAULT ''",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS twilio_auth_token VARCHAR(255) DEFAULT ''",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS twilio_phone_number VARCHAR(255) DEFAULT ''",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS fast2sms_api_key VARCHAR(255) DEFAULT ''",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS admin_pin VARCHAR(100) DEFAULT '1234'",

            // online_orders columns
            "ALTER TABLE online_orders ADD COLUMN IF NOT EXISTS items_json TEXT DEFAULT ''",
            "ALTER TABLE online_orders ADD COLUMN IF NOT EXISTS stock_deducted BOOLEAN DEFAULT FALSE",
            "ALTER TABLE online_orders ADD COLUMN IF NOT EXISTS items_summary VARCHAR(2000) DEFAULT ''",
            "ALTER TABLE online_orders ADD COLUMN IF NOT EXISTS address VARCHAR(1000) DEFAULT ''",

            // products columns
            "ALTER TABLE products ADD COLUMN IF NOT EXISTS shelf_life_days INTEGER DEFAULT 1",
            "ALTER TABLE products ADD COLUMN IF NOT EXISTS image_url VARCHAR(500) DEFAULT ''",

            // inventory_items columns
            "ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS baked_date DATE",
            "ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'ACTIVE'",

            // store_settings columns
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS initial_customers_seeded BOOLEAN DEFAULT FALSE",

            // customers columns
            "ALTER TABLE customers ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP",

            // sales_orders columns
            "ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS customer_id BIGINT",
            "ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS item_count INTEGER DEFAULT 1",
            "ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS base_value DOUBLE PRECISION DEFAULT 0.0",
            "ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS total_discount_saved DOUBLE PRECISION DEFAULT 0.0",
            "ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS summary_text VARCHAR(2000) DEFAULT ''"
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : alterStatements) {
                try {
                    stmt.execute(sql);
                } catch (Exception e) {
                    log.debug("Schema migration notice for [{}]: {}", sql, e.getMessage());
                }
            }
            log.info("Database schema migration check completed successfully.");
        } catch (Exception e) {
            log.warn("Database schema migration connection check: {}", e.getMessage());
        }
    }

    @Component
    public static class DatabaseSchemaMigratorDependsOnPostProcessor extends EntityManagerFactoryDependsOnPostProcessor {
        public DatabaseSchemaMigratorDependsOnPostProcessor() {
            super("databaseSchemaMigrator");
        }
    }
}
