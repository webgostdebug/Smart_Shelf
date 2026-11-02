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
        log.info("Running automatic database schema migration for store_settings...");
        String[] alterStatements = new String[]{
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS rain_discount_approved BOOLEAN DEFAULT FALSE",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS whatsapp_community_url VARCHAR(255) DEFAULT 'https://chat.whatsapp.com/BakeAndBlissCommunity'",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS instagram_handle VARCHAR(255) DEFAULT '@bakeandbliss'",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS instagram_email VARCHAR(255) DEFAULT ''",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS instagram_password VARCHAR(255) DEFAULT ''",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS twilio_account_sid VARCHAR(255) DEFAULT ''",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS twilio_auth_token VARCHAR(255) DEFAULT ''",
            "ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS twilio_phone_number VARCHAR(255) DEFAULT ''"
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
