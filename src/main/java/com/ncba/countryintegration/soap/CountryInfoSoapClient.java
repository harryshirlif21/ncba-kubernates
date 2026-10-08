package com.ncba.countryintegration.soap;

import com.ncba.countryintegration.config.SoapProperties;
import com.ncba.countryintegration.exception.CountryNotFoundException;
import com.ncba.countryintegration.exception.ExternalServiceException;
import com.ncba.countryintegration.util.TextUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Integration adapter for the CountryInfoService SOAP API (SOAP 1.1 over HTTP).
 * Protection layers (outer to inner): cache, retry with exponential backoff, circuit breaker,
 * timeout-bound HTTP call.
 */
@Component
public class CountryInfoSoapClient {

    private static final Logger log = LoggerFactory.getLogger(CountryInfoSoapClient.class);
    private static final String NS = "http://www.oorsprong.org/websamples.countryinfo";
    private static final MediaType TEXT_XML_UTF8 = new MediaType("text", "xml", StandardCharsets.UTF_8);

    private final RestClient restClient;
    private final SoapProperties props;

    public CountryInfoSoapClient(RestClient soapRestClient, SoapProperties props) {
        this.restClient = soapRestClient;
        this.props = props;
    }

    @Cacheable(cacheNames = "isoCodes", key = "#countryName")
    @Retry(name = "soap")
    @CircuitBreaker(name = "soap", fallbackMethod = "isoFallback")
    public String getCountryIsoCode(String countryName) {
        log.info("SOAP call CountryISOCode sCountryName={}", countryName);
        String xml = post(envelope("CountryISOCode", "sCountryName", countryName));
        return SoapXmlParser.parseIsoCode(xml, countryName);
    }

    @Cacheable(cacheNames = "fullCountryInfo", key = "#isoCode")
    @Retry(name = "soap")
    @CircuitBreaker(name = "soap", fallbackMethod = "fullInfoFallback")
    public FullCountryInfoData getFullCountryInfo(String isoCode) {
        log.info("SOAP call FullCountryInfo sCountryISOCode={}", isoCode);
        String xml = post(envelope("FullCountryInfo", "sCountryISOCode", isoCode));
        return SoapXmlParser.parseFullCountryInfo(xml, isoCode);
    }

    // Fallbacks: business errors pass through; everything else becomes ExternalServiceException.
    public String isoFallback(String countryName, Throwable t) {
        throw translate("CountryISOCode", t);
    }

    public FullCountryInfoData fullInfoFallback(String isoCode, Throwable t) {
        throw translate("FullCountryInfo", t);
    }

    private RuntimeException translate(String operation, Throwable t) {
        if (t instanceof CountryNotFoundException e) {
            return e;
        }
        log.warn("SOAP {} unavailable: {} ({})", operation, t.getClass().getSimpleName(), t.getMessage());
        return t instanceof ExternalServiceException e ? e
                : new ExternalServiceException("SOAP operation " + operation + " unavailable: " + t.getMessage(), t);
    }

    private String post(String envelope) {
        try {
            return restClient.post().uri(props.url())
                    .contentType(TEXT_XML_UTF8)
                    .accept(MediaType.TEXT_XML, MediaType.APPLICATION_XML)
                    .header("SOAPAction", "")
                    .body(envelope)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new ExternalServiceException("HTTP failure calling SOAP endpoint: " + e.getMessage(), e);
        }
    }

    private static String envelope(String operation, String param, String value) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + "<" + operation + " xmlns=\"" + NS + "\"><" + param + ">" + TextUtils.escapeXml(value)
                + "</" + param + "></" + operation + ">"
                + "</soap:Body></soap:Envelope>";
    }
}
