package com.example.appbootstrap.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.client-ip")
public record ClientIpProperties(List<String> trustedProxies) {}
