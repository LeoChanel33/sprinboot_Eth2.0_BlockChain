package com.example.demo.eth20.controller;

import com.example.demo.eth20.entity.EthAdmin;
import com.example.demo.eth20.service.jpa.EthAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/eth/admins")
@Tag(name = "eth_admin CRUD")
public class EthAdminCrudController {
    private final EthAdminService service;
    @GetMapping public Page<EthAdmin> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }
    @GetMapping("/{id}") public EthAdmin get(@PathVariable Integer id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public EthAdmin create(@RequestBody EthAdmin body) { return service.create(body); }
    @PutMapping("/{id}") public EthAdmin update(@PathVariable Integer id, @RequestBody EthAdmin body) { return service.update(id, body); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) { service.delete(id); }
}