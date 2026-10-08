package com.ncba.countryintegration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/** All fields optional (partial update). Null = leave unchanged. If languages is supplied it replaces the list. */
public record UpdateCountryRequest(
        @Size(min = 1, max = 150) String name,
        @Size(max = 150) String capitalCity,
        @Size(max = 20) String phoneCode,
        @Size(max = 10) String continentCode,
        @Size(max = 10) String currencyIsoCode,
        @Size(max = 512) String countryFlag,
        @Valid List<LanguageDto> languages) { }
