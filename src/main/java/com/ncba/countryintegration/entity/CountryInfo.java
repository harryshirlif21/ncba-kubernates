package com.ncba.countryintegration.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "country_info", uniqueConstraints = @UniqueConstraint(name = "uk_country_iso", columnNames = "iso_code"))
public class CountryInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "iso_code", nullable = false, length = 10)
    private String isoCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "capital_city", length = 150)
    private String capitalCity;

    @Column(name = "phone_code", length = 20)
    private String phoneCode;

    @Column(name = "continent_code", length = 10)
    private String continentCode;

    @Column(name = "currency_iso_code", length = 10)
    private String currencyIsoCode;

    @Column(name = "country_flag", length = 512)
    private String countryFlag;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "country", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Language> languages = new ArrayList<>();

    @PrePersist
    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public void replaceLanguages(List<Language> newLanguages) {
        languages.clear();
        newLanguages.forEach(l -> { l.setCountry(this); languages.add(l); });
    }

    public Long getId() { return id; }
    public String getIsoCode() { return isoCode; }
    public void setIsoCode(String isoCode) { this.isoCode = isoCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCapitalCity() { return capitalCity; }
    public void setCapitalCity(String capitalCity) { this.capitalCity = capitalCity; }
    public String getPhoneCode() { return phoneCode; }
    public void setPhoneCode(String phoneCode) { this.phoneCode = phoneCode; }
    public String getContinentCode() { return continentCode; }
    public void setContinentCode(String continentCode) { this.continentCode = continentCode; }
    public String getCurrencyIsoCode() { return currencyIsoCode; }
    public void setCurrencyIsoCode(String currencyIsoCode) { this.currencyIsoCode = currencyIsoCode; }
    public String getCountryFlag() { return countryFlag; }
    public void setCountryFlag(String countryFlag) { this.countryFlag = countryFlag; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<Language> getLanguages() { return languages; }
}
