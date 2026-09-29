package com.bank.fintechbank.repository;

import com.bank.fintechbank.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountIdOrderByDateDesc(Long accountId);
    Page<Transaction> findByAccountIdAndDateBetweenOrderByDateDescIdDesc(Long accountId, LocalDateTime from, LocalDateTime to, Pageable page);
    Transaction findFirstByAccountIdAndDateBeforeOrderByDateDescIdDesc(Long accountId, LocalDateTime date);
    Transaction findFirstByAccountIdAndDateLessThanEqualOrderByDateDescIdDesc(Long accountId, LocalDateTime date);
    List<Transaction> findTop100ByAccountIdOrderByDateDescIdDesc(Long accountId);
    List<Transaction> findTop50ByOrderByDateDescIdDesc();
}
