package com.example.demo.eth20.repository;
import com.example.demo.eth20.entity.EthLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EthLogRepository extends JpaRepository<EthLog, Integer> {

}