package com.ncba.countryintegration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    /** RestClient with hard connect/read timeouts so a slow SOAP service can never hold threads indefinitely. */
    @Bean
    public RestClient soapRestClient(SoapProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeoutMs());
        factory.setReadTimeout(props.readTimeoutMs());
        return RestClient.builder().requestFactory(factory).build();
    }
}
