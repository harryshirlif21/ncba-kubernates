package com.ncba.countryintegration.dto;

import com.ncba.countryintegration.entity.CountryInfo;
import java.util.List;

public record CountryResponse(Long id, String isoCode, String name, String capitalCity, String phoneCode,
                              String continentCode, String currencyIsoCode, String countryFlag,
                              List<LanguageDto> languages) {

    /** Must be called inside a transaction (languages are lazy). */
    public static CountryResponse from(CountryInfo c) {
        return new CountryResponse(c.getId(), c.getIsoCode(), c.getName(), c.getCapitalCity(), c.getPhoneCode(),
                c.getContinentCode(), c.getCurrencyIsoCode(), c.getCountryFlag(),
                c.getLanguages().stream().map(l -> new LanguageDto(l.getIsoCode(), l.getName())).toList());
    }
}
