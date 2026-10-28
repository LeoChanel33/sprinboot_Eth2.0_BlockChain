package com.example.demo.eth20.controller;

import com.example.demo.eth20.entity.EthLog;
import com.example.demo.eth20.service.jpa.EthLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/eth/logs")
@Tag(name = "eth_log CRUD")
public class EthLogCrudController {
    private final EthLogService service;
    @GetMapping public Page<EthLog> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }
    @GetMapping("/{id}") public EthLog get(@PathVariable Integer id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public EthLog create(@RequestBody EthLog body) { return service.create(body); }
    @PutMapping("/{id}") public EthLog update(@PathVariable Integer id, @RequestBody EthLog body) { return service.update(id, body); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) { service.delete(id); }
}