package com.example.demo.eth20.repository;
import com.example.demo.eth20.entity.EthAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EthAdminRepository extends JpaRepository<EthAdmin, Integer> {
    boolean existsByAccount(String account);
    boolean existsByAccountAndIdNot(String account, Integer id);
}