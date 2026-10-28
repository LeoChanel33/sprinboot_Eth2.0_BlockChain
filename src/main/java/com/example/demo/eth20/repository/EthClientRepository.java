package com.example.demo.eth20.repository;
import com.example.demo.eth20.entity.EthClient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EthClientRepository extends JpaRepository<EthClient, Integer> {
    boolean existsByAccount(String account);
    boolean existsByAccountAndIdNot(String account, Integer id);
}