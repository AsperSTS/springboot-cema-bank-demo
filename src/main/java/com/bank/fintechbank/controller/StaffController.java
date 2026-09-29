package com.bank.fintechbank.controller;

import com.bank.fintechbank.repository.*;
import com.bank.fintechbank.service.BankService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/staff")
public class StaffController {
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final CreditRepository credits;
    private final BankService bank;

    public StaffController(CustomerRepository customers, AccountRepository accounts, TransactionRepository transactions,
                           CreditRepository credits, BankService bank) {
        this.customers = customers; this.accounts = accounts; this.transactions = transactions;
        this.credits = credits; this.bank = bank;
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public ResponseEntity<?> dashboard() {
        return ResponseEntity.ok(Map.of("totalUsers", customers.countByRole("ROLE_USER"),
                "totalStaff", customers.count() - customers.countByRole("ROLE_USER"),
                "totalAccounts", accounts.count(), "totalTransactions", transactions.count(),
                "totalMoney", accounts.totalBalance(), "recentTransactions", transactions.findTop50ByOrderByDateDescIdDesc()));
    }

    @GetMapping("/customers")
    @Transactional(readOnly = true)
    public ResponseEntity<?> customers(@RequestParam(defaultValue = "0") int page) {
        if (page < 0 || page > 10000) return ResponseEntity.badRequest().body(Map.of("error", "Página inválida"));
        var list = customers.findAll(PageRequest.of(page, 25));
        return ResponseEntity.ok(Map.of("items", list.getContent().stream().map(c -> Map.of(
                "id", c.getId(), "username", c.getUsername(), "name", c.getName(), "role", c.getRole(), "tier", c.getTier()
        )).toList(), "page", page, "totalPages", list.getTotalPages(), "totalUsers", list.getTotalElements()));
    }

    @GetMapping("/credits")
    @Transactional(readOnly = true)
    public ResponseEntity<?> pendingCredits() {
        return ResponseEntity.ok(credits.findByStatus("PENDING").stream().map(c -> Map.of(
                "id", c.getId(), "customer", c.getCustomer().getUsername(), "amount", c.getAmount(), "rate", c.getInterestRate()
        )).toList());
    }

    @PostMapping("/credits/{id}/{decision}")
    public ResponseEntity<?> decide(@PathVariable Long id, @PathVariable String decision) {
        if (!decision.equals("approve") && !decision.equals("reject"))
            return ResponseEntity.badRequest().body(Map.of("error", "Decisión inválida"));
        try {
            bank.decideCredit(id, decision.equals("approve"));
            return ResponseEntity.ok(Map.of("message", "Solicitud revisada"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
