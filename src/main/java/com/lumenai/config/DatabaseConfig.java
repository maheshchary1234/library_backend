package com.lumenai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.jdbc.DataSourceBuilder;
import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
@Profile("prod")
public class DatabaseConfig {

    @Value("${DATABASE_URL}")
    private String databaseUrl;

    @Value("${DB_USERNAME:}")
    private String defaultUsername;

    @Value("${DB_PASSWORD:}")
    private String defaultPassword;

    @Bean
    public DataSource dataSource() {
        try {
            // Render / Cloud database url format: postgresql://username:password@host:port/database?sslmode=require
            String cleanUrl = databaseUrl;
            if (cleanUrl.startsWith("jdbc:")) {
                cleanUrl = cleanUrl.substring(5);
            }
            
            URI dbUri = new URI(cleanUrl);
            
            String username = null;
            String password = null;
            
            if (dbUri.getUserInfo() != null) {
                String[] userInfo = dbUri.getUserInfo().split(":", 2);
                if (userInfo.length >= 1 && !userInfo[0].isEmpty()) {
                    username = URLDecoder.decode(userInfo[0], StandardCharsets.UTF_8);
                }
                if (userInfo.length >= 2 && !userInfo[1].isEmpty()) {
                    password = URLDecoder.decode(userInfo[1], StandardCharsets.UTF_8);
                }
            }
            
            // Fallback to environment variables if not present in the URL
            if (username == null || username.isEmpty()) {
                username = defaultUsername;
            }
            if (password == null || password.isEmpty()) {
                password = defaultPassword;
            }
            
            String host = dbUri.getHost();
            int port = dbUri.getPort();
            String path = dbUri.getPath();
            String query = dbUri.getQuery();
            
            StringBuilder jdbcUrlBuilder = new StringBuilder("jdbc:postgresql://")
                    .append(host)
                    .append(port == -1 ? "" : ":" + port)
                    .append(path != null ? path : "");
                    
            if (query != null && !query.trim().isEmpty()) {
                jdbcUrlBuilder.append("?").append(query);
            } else {
                // Default to sslmode=require for cloud PostgreSQL hosts (e.g. Render, Neon, Supabase)
                jdbcUrlBuilder.append("?sslmode=require");
            }
            
            String jdbcUrl = jdbcUrlBuilder.toString();
            
            return DataSourceBuilder.create()
                    .driverClassName("org.postgresql.Driver")
                    .url(jdbcUrl)
                    .username(username)
                    .password(password)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure DataSource from DATABASE_URL: " + databaseUrl, e);
        }
    }
}

