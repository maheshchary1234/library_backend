package com.lumenai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.jdbc.DataSourceBuilder;
import javax.sql.DataSource;
import java.net.URI;

@Configuration
@Profile("prod")
public class DatabaseConfig {

    @Value("${DATABASE_URL}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() {
        try {
            // Render database url format: postgresql://username:password@host/database
            String cleanUrl = databaseUrl;
            if (cleanUrl.startsWith("jdbc:")) {
                cleanUrl = cleanUrl.substring(5);
            }
            
            URI dbUri = new URI(cleanUrl);
            
            String username = null;
            String password = null;
            
            if (dbUri.getUserInfo() != null) {
                String[] userInfo = dbUri.getUserInfo().split(":");
                if (userInfo.length >= 1) {
                    username = userInfo[0];
                }
                if (userInfo.length >= 2) {
                    password = userInfo[1];
                }
            }
            
            // Build jdbc url without credentials
            String host = dbUri.getHost();
            int port = dbUri.getPort();
            String path = dbUri.getPath();
            
            String jdbcUrl = "jdbc:postgresql://" + host + (port == -1 ? "" : ":" + port) + path;
            
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
