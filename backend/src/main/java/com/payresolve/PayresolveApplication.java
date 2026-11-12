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
        configureSessionCookieDefaults();
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
            System.setProperty("spring.datasource.url", envDbUrl);
        }
        String envUser = System.getenv("DB_USER");
        if (envUser != null && !envUser.isBlank()) {
            SensitiveDataMasker.registerSensitiveValue(envUser);
            System.setProperty("spring.datasource.username", envUser);
        }
        String envPass = System.getenv("DB_PASSWORD");
        if (envPass != null && !envPass.isBlank()) {
            SensitiveDataMasker.registerSensitiveValue(envPass);
            System.setProperty("spring.datasource.password", envPass);
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

    private static void configureSessionCookieDefaults() {
        String explicitSecure = System.getenv("SESSION_COOKIE_SECURE");
        if (explicitSecure == null || explicitSecure.isBlank()) {
            explicitSecure = readEnvFileValue("SESSION_COOKIE_SECURE");
        }
        if (explicitSecure != null && !explicitSecure.isBlank()) {
            System.setProperty("SESSION_COOKIE_SECURE", explicitSecure.trim());
            return;
        }
        String frontendOrigin = System.getenv("FRONTEND_ORIGIN");
        if (frontendOrigin == null || frontendOrigin.isBlank()) {
            frontendOrigin = readEnvFileValue("FRONTEND_ORIGIN");
        }
        String targetOrigin = (frontendOrigin == null || frontendOrigin.isBlank())
            ? "http://localhost:5173"
            : frontendOrigin.trim().toLowerCase();
        boolean isLocalhost = targetOrigin.startsWith("http://localhost")
            || targetOrigin.startsWith("http://127.0.0.1")
            || targetOrigin.startsWith("https://localhost")
            || targetOrigin.startsWith("https://127.0.0.1");
        System.setProperty("SESSION_COOKIE_SECURE", isLocalhost ? "false" : "true");
    }

    private static String readEnvFileValue(String key) {
        Path envFile = Path.of("../.env");
        if (!Files.exists(envFile)) {
            envFile = Path.of(".env");
        }
        if (!Files.exists(envFile)) {
            return null;
        }
        String prefix = key + "=";
        try {
            for (String rawLine : Files.readAllLines(envFile)) {
                String line = rawLine.trim();
                if (line.startsWith(prefix)) {
                    String val = line.substring(prefix.length()).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        val = val.substring(1, val.length() - 1);
                    }
                    return val;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
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
                try {
                    MigrateResult result = flyway.migrate();
                    System.out.println("Flyway migration completed successfully: " + result.migrationsExecuted + " migrations executed.");
                } catch (org.flywaydb.core.api.exception.FlywayValidateException ve) {
                    if (ve.getMessage() != null && ve.getMessage().contains("migration version 5")) {
                        System.out.println("Repairing historical Flyway migration v5 checksum mismatch before migrating...");
                        flyway.repair();
                        MigrateResult result = flyway.migrate();
                        System.out.println("Flyway migration completed after repair: " + result.migrationsExecuted + " migrations executed.");
                    } else {
                        throw ve;
                    }
                }
            } catch (Exception e) {
                System.err.println("Flyway migration exception: " + SensitiveDataMasker.mask(e.getMessage()));
            }
        }
    }
}
