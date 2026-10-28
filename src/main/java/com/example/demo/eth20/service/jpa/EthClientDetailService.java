package com.example.demo.eth20.service.jpa;

import com.example.demo.eth20.entity.EthClientDetail;
import com.example.demo.eth20.repository.EthClientDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor @Transactional
public class EthClientDetailService {
    private final EthClientDetailRepository repository;

    @Transactional(readOnly = true)
    public Page<EthClientDetail> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page >= 0; size must be 1..100");
        return repository.findAll(PageRequest.of(page, size, Sort.by("hashKey")));
    }
    @Transactional(readOnly = true)
    public EthClientDetail get(String id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "EthClientDetail not found"));
    }
    public EthClientDetail create(EthClientDetail body) {

        prepare(body, null, true);
        return repository.saveAndFlush(body);
    }
    public EthClientDetail update(String id, EthClientDetail body) {
        var existing = get(id);
        body.setHashKey(id);
        prepare(body, existing, false);
        return repository.saveAndFlush(body);
    }
    public void delete(String id) { repository.delete(get(id)); repository.flush(); }
    private void prepare(EthClientDetail body, EthClientDetail existing, boolean creating) {
        if (body.getHashKey() == null || body.getHashKey().isBlank() || body.getHashKey().length() > 255)
            throw new IllegalArgumentException("hashKey is required and must be at most 255 characters");
        if (body.getCash() != null && !Double.isFinite(body.getCash()))
            throw new IllegalArgumentException("cash must be finite");
        if (creating && repository.existsById(body.getHashKey()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "hashKey already exists");
        if (body.getCreateDate() == null)
            body.setCreateDate(creating ? java.time.LocalDate.now() : existing.getCreateDate());
        body.setModifyDate(java.time.LocalDate.now());
    }
}