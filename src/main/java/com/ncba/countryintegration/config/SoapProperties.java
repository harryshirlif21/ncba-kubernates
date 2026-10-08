package com.ncba.countryintegration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "soap.country-info")
public record SoapProperties(String url, int connectTimeoutMs, int readTimeoutMs) { }
