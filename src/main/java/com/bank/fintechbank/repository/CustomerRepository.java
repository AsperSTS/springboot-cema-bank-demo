package com.bank.fintechbank.repository;

import com.bank.fintechbank.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByUsername(String username);
    Optional<Customer> findByCurrentSessionToken(String currentSessionToken);
    long countByRole(String role);
}
