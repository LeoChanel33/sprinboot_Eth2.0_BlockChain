package com.example.demo.eth20.controller;

import com.example.demo.eth20.entity.EthClient;
import com.example.demo.eth20.service.jpa.EthClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/eth/clients")
@Tag(name = "eth_client CRUD")
public class EthClientCrudController {
    private final EthClientService service;
    @GetMapping public Page<EthClient> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }
    @GetMapping("/{id}") public EthClient get(@PathVariable Integer id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public EthClient create(@RequestBody EthClient body) { return service.create(body); }
    @PutMapping("/{id}") public EthClient update(@PathVariable Integer id, @RequestBody EthClient body) { return service.update(id, body); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) { service.delete(id); }
}