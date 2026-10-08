package com.ncba.countryintegration.controller;

import com.ncba.countryintegration.dto.CountryRequest;
import com.ncba.countryintegration.dto.CountryResponse;
import com.ncba.countryintegration.dto.PagedResponse;
import com.ncba.countryintegration.dto.UpdateCountryRequest;
import com.ncba.countryintegration.service.CountryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/countries")
public class CountryController {

    private final CountryService service;

    public CountryController(CountryService service) {
        this.service = service;
    }

    /** Steps 3-6. 201 when a new record is stored, 200 when an existing record was refreshed / served from store. */
    @PostMapping
    public ResponseEntity<CountryResponse> lookup(@Valid @RequestBody CountryRequest request) {
        CountryService.LookupResult r = service.lookupAndStore(request.name());
        HttpStatus status = r.created() ? HttpStatus.CREATED : HttpStatus.OK;
        ResponseEntity.BodyBuilder b = ResponseEntity.status(status);
        if (r.servedFromStore()) {
            b.header("X-Data-Source", "stored-fallback");
        }
        return b.body(r.country());
    }

    @GetMapping
    public PagedResponse<CountryResponse> getAll(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return service.findAll(PageRequest.of(Math.max(page, 0), safeSize, Sort.by("id")));
    }

    @GetMapping("/{id}")
    public CountryResponse getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public CountryResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCountryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
