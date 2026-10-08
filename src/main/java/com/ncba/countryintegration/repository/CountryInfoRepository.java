package com.ncba.countryintegration.repository;

import com.ncba.countryintegration.entity.CountryInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryInfoRepository extends JpaRepository<CountryInfo, Long> {
    Optional<CountryInfo> findByIsoCode(String isoCode);
    Optional<CountryInfo> findByNameIgnoreCase(String name);
}
