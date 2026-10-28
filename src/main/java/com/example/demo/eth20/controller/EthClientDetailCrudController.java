package com.example.demo.eth20.controller;

import com.example.demo.eth20.entity.EthClientDetail;
import com.example.demo.eth20.service.jpa.EthClientDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/eth/client-details")
@Tag(name = "eth_client_detail CRUD")
public class EthClientDetailCrudController {
    private final EthClientDetailService service;
    @GetMapping public Page<EthClientDetail> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }
    @GetMapping("/{id}") public EthClientDetail get(@PathVariable String id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public EthClientDetail create(@RequestBody EthClientDetail body) { return service.create(body); }
    @PutMapping("/{id}") public EthClientDetail update(@PathVariable String id, @RequestBody EthClientDetail body) { return service.update(id, body); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) { service.delete(id); }
}