package com.ncba.countryintegration.soap;

import java.util.List;

public record FullCountryInfoData(String isoCode, String name, String capitalCity, String phoneCode,
                                  String continentCode, String currencyIsoCode, String countryFlag,
                                  List<LanguageData> languages) {
    public record LanguageData(String isoCode, String name) { }
}
