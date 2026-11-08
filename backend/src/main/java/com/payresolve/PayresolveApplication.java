package com.payresolve;

import com.payresolve.common.DatasourceValidator;
import com.payresolve.common.SensitiveDataMasker;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class PayresolveApplication {

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        validateAndConfigureDatasourceFromEnv();
        runFlywayMigrationIfConfigured();
        for (String arg : args) {
            if ("--migrate-only".equals(arg)) {
                System.out.println("Flyway migration completed (--migrate-only requested, exiting).");
                return;
            }
        }
        SpringApplication.run(PayresolveApplication.class, args);
    }

    private static void validateAndConfigureDatasourceFromEnv() {
        // 1. Check system environment variable DATABASE_URL first
        String envDbUrl = System.getenv("DATABASE_URL");
        if (envDbUrl != null && !envDbUrl.isBlank()) {
            DatasourceValidator.validate(envDbUrl);
            SensitiveDataMasker.registerSensitiveValue(envDbUrl);
        }

        // 2. Check local .env file if running in local development
        Path envFile = Path.of("../.env");
        if (!Files.exists(envFile)) {
            envFile = Path.of(".env");
        }
        if (Files.exists(envFile)) {
            try {
                List<String> lines = Files.readAllLines(envFile);
                for (String line : lines) {
                    line = line.trim();
                    if (line.startsWith("DATABASE_URL=")) {
                        String val = line.substring("DATABASE_URL=".length()).trim();
                        if (val.startsWith("\"") && val.endsWith("\"")) {
                            val = val.substring(1, val.length() - 1);
                        }
                        if (val.startsWith("'") && val.endsWith("'")) {
                            val = val.substring(1, val.length() - 1);
                        }
                        DatasourceValidator.validate(val);
                        SensitiveDataMasker.registerSensitiveValue(val);
                        System.setProperty("spring.datasource.url", val);
                    }
                    if (line.startsWith("DB_USER=")) {
                        String user = line.substring("DB_USER=".length()).trim().replace("\"", "").replace("'", "");
                        SensitiveDataMasker.registerSensitiveValue(user);
                        System.setProperty("spring.datasource.username", user);
                    }
                    if (line.startsWith("DB_PASSWORD=")) {
                        String pass = line.substring("DB_PASSWORD=".length()).trim().replace("\"", "").replace("'", "");
                        SensitiveDataMasker.registerSensitiveValue(pass);
                        System.setProperty("spring.datasource.password", pass);
                    }
                }
            } catch (IllegalStateException e) {
                // Re-throw validation errors immediately to fail fast
                throw e;
            } catch (Exception ignored) {}
        }
    }

    private static void runFlywayMigrationIfConfigured() {
        String url = System.getProperty("spring.datasource.url");
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            return;
        }
        String user = System.getenv("DB_USER");
        String pass = System.getenv("DB_PASSWORD");
        Path envFile = Path.of("../.env");
        if (!Files.exists(envFile)) {
            envFile = Path.of(".env");
        }
        if (Files.exists(envFile)) {
            try {
                for (String line : Files.readAllLines(envFile)) {
                    line = line.trim();
                    if (user == null && line.startsWith("DB_USER=")) {
                        user = line.substring("DB_USER=".length()).trim().replace("\"", "").replace("'", "");
                    }
                    if (pass == null && line.startsWith("DB_PASSWORD=")) {
                        pass = line.substring("DB_PASSWORD=".length()).trim().replace("\"", "").replace("'", "");
                    }
                }
            } catch (Exception ignored) {}
        }
        if (user != null && pass != null) {
            try {
                System.out.println("Running Flyway migrations against PostgreSQL...");
                Flyway flyway = Flyway.configure()
                    .dataSource(url, user, pass)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .load();
                MigrateResult result = flyway.migrate();
                System.out.println("Flyway migration completed successfully: " + result.migrationsExecuted + " migrations executed.");
            } catch (Exception e) {
                System.err.println("Flyway migration exception: " + SensitiveDataMasker.mask(e.getMessage()));
            }
        }
    }
}
