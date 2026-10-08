package com.ncba.countryintegration.service;

import com.ncba.countryintegration.dto.CountryResponse;
import com.ncba.countryintegration.dto.LanguageDto;
import com.ncba.countryintegration.dto.PagedResponse;
import com.ncba.countryintegration.dto.UpdateCountryRequest;
import com.ncba.countryintegration.entity.CountryInfo;
import com.ncba.countryintegration.entity.Language;
import com.ncba.countryintegration.exception.ExternalServiceException;
import com.ncba.countryintegration.exception.ResourceNotFoundException;
import com.ncba.countryintegration.repository.CountryInfoRepository;
import com.ncba.countryintegration.soap.CountryInfoSoapClient;
import com.ncba.countryintegration.soap.FullCountryInfoData;
import com.ncba.countryintegration.util.TextUtils;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CountryService {

    private static final Logger log = LoggerFactory.getLogger(CountryService.class);

    public record LookupResult(CountryResponse country, boolean created, boolean servedFromStore) { }

    private final CountryInfoRepository repository;
    private final CountryInfoSoapClient soapClient;
    private final MeterRegistry meters;
    private final TransactionTemplate writeTx;
    private final TransactionTemplate readTx;

    public CountryService(CountryInfoRepository repository, CountryInfoSoapClient soapClient,
                          MeterRegistry meters, PlatformTransactionManager tm) {
        this.repository = repository;
        this.soapClient = soapClient;
        this.meters = meters;
        this.writeTx = new TransactionTemplate(tm);
        this.readTx = new TransactionTemplate(tm);
        this.readTx.setReadOnly(true);
    }

    /**
     * Steps 3-6: sentence-case the name, resolve ISO code (SOAP), fetch full info (SOAP), persist.
     * SOAP calls run OUTSIDE any DB transaction so a slow upstream never pins a DB connection.
     * If the upstream is down but we already stored this country, serve the stored copy (graceful degradation).
     */
    public LookupResult lookupAndStore(String rawName) {
        String name = TextUtils.toSentenceCase(rawName);
        log.info("Country lookup started name={}", name);

        FullCountryInfoData data;
        try {
            String iso = soapClient.getCountryIsoCode(name);
            data = soapClient.getFullCountryInfo(iso);
        } catch (ExternalServiceException ex) {
            CountryResponse stored = readTx.execute(s ->
                    repository.findByNameIgnoreCase(name).map(CountryResponse::from).orElse(null));
            if (stored == null) {
                meters.counter("country.lookup", "outcome", "failure").increment();
                throw ex;
            }
            log.warn("Upstream unavailable, serving stored record name={} id={}", name, stored.id());
            meters.counter("country.lookup", "outcome", "fallback").increment();
            return new LookupResult(stored, false, true);
        }

        LookupResult result = writeTx.execute(s -> persist(data));
        meters.counter("country.lookup", "outcome", "success").increment();
        log.info("Country lookup stored iso={} id={} created={}", data.isoCode(), result.country().id(), result.created());
        return result;
    }

    private LookupResult persist(FullCountryInfoData data) {
        var existing = repository.findByIsoCode(data.isoCode());
        boolean created = existing.isEmpty();
        CountryInfo c = existing.orElseGet(CountryInfo::new);
        c.setIsoCode(data.isoCode());
        c.setName(data.name());
        c.setCapitalCity(data.capitalCity());
        c.setPhoneCode(data.phoneCode());
        c.setContinentCode(data.continentCode());
        c.setCurrencyIsoCode(data.currencyIsoCode());
        c.setCountryFlag(data.countryFlag());
        c.replaceLanguages(data.languages().stream().map(l -> new Language(l.isoCode(), l.name())).toList());
        CountryInfo saved = repository.save(c);
        return new LookupResult(CountryResponse.from(saved), created, false);
    }

    public PagedResponse<CountryResponse> findAll(Pageable pageable) {
        return readTx.execute(s -> PagedResponse.of(repository.findAll(pageable).map(CountryResponse::from)));
    }

    public CountryResponse findById(Long id) {
        return readTx.execute(s -> repository.findById(id).map(CountryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Country with id " + id + " not found")));
    }

    public CountryResponse update(Long id, UpdateCountryRequest req) {
        return writeTx.execute(s -> {
            CountryInfo c = repository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Country with id " + id + " not found"));
            if (req.name() != null) c.setName(req.name());
            if (req.capitalCity() != null) c.setCapitalCity(req.capitalCity());
            if (req.phoneCode() != null) c.setPhoneCode(req.phoneCode());
            if (req.continentCode() != null) c.setContinentCode(req.continentCode());
            if (req.currencyIsoCode() != null) c.setCurrencyIsoCode(req.currencyIsoCode());
            if (req.countryFlag() != null) c.setCountryFlag(req.countryFlag());
            if (req.languages() != null) {
                List<Language> langs = req.languages().stream()
                        .map((LanguageDto l) -> new Language(l.isoCode(), l.name())).toList();
                c.replaceLanguages(langs);
            }
            log.info("Country updated id={}", id);
            return CountryResponse.from(repository.save(c));
        });
    }

    public void delete(Long id) {
        writeTx.executeWithoutResult(s -> {
            if (!repository.existsById(id)) {
                throw new ResourceNotFoundException("Country with id " + id + " not found");
            }
            repository.deleteById(id);
            log.info("Country deleted id={}", id);
        });
    }
}
