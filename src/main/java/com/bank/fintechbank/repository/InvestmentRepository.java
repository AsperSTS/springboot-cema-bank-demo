package com.bank.fintechbank.repository;

import com.bank.fintechbank.model.Investment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {
    List<Investment> findByCustomerId(Long customerId);
}
