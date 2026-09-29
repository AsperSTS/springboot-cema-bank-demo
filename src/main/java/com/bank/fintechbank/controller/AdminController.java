package com.bank.fintechbank.controller;

import com.bank.fintechbank.repository.AccountRepository;
import com.bank.fintechbank.repository.CustomerRepository;
import com.bank.fintechbank.repository.TransactionRepository;
import com.bank.fintechbank.repository.CreditRepository;
import com.bank.fintechbank.service.BankService;
import com.bank.fintechbank.model.Customer;
import com.bank.fintechbank.model.RoleChangeAudit;
import com.bank.fintechbank.repository.RoleChangeAuditRepository;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private CreditRepository creditRepository;
    @Autowired private BankService bankService;
    @Autowired private RoleChangeAuditRepository roleAudit;

    @PostMapping("/users/{id}/role")
    @Transactional
    public ResponseEntity<?> changeRole(Authentication auth, @PathVariable Long id, @RequestBody Map<String, String> body) {
        String next = body.get("role");
        if (next == null || !java.util.Set.of("ROLE_USER", "ROLE_ANALYST", "ROLE_EXECUTIVE", "ROLE_ADMIN").contains(next))
            return ResponseEntity.badRequest().body(Map.of("error", "Rol inválido"));
        Customer target = customerRepository.findById(id).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();
        if (target.getUsername().equals(auth.getName()))
            return ResponseEntity.badRequest().body(Map.of("error", "No puedes cambiar tu propio rol"));
        String previous = target.getRole();
        if (previous.equals(next)) return ResponseEntity.ok(Map.of("message", "El usuario ya tiene ese rol"));
        target.setRole(next);
        target.setTier(next.equals("ROLE_USER") ? "ESENCIAL" : "EQUIPO");
        customerRepository.save(target);
        roleAudit.save(new RoleChangeAudit(auth.getName(), target.getUsername(), previous, next));
        return ResponseEntity.ok(Map.of("message", "Rol actualizado", "role", next));
    }

    @GetMapping("/role-audit")
    public ResponseEntity<?> roleAudit() {
        return ResponseEntity.ok(roleAudit.findTop30ByOrderByChangedAtDescIdDesc());
    }

    @GetMapping("/credits")
    @Transactional(readOnly = true)
    public ResponseEntity<?> pendingCredits() {
        return ResponseEntity.ok(creditRepository.findByStatus("PENDING").stream().map(c -> Map.of(
            "id", c.getId(), "customer", c.getCustomer().getUsername(), "amount", c.getAmount(), "rate", c.getInterestRate()
        )).toList());
    }

    @PostMapping("/credits/{id}/{decision}")
    public ResponseEntity<?> decide(@PathVariable Long id, @PathVariable String decision) {
        if (!decision.equals("approve") && !decision.equals("reject")) return ResponseEntity.badRequest().body(Map.of("error", "Decisión inválida"));
        try {
            bankService.decideCredit(id, decision.equals("approve"));
            return ResponseEntity.ok(Map.of("message", "Solicitud revisada"));
        } catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getAdminDashboard() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalUsers = customerRepository.count();
        long totalAccounts = accountRepository.count();
        long totalTransactions = transactionRepository.count();
        
        // Sum of all balances
        java.math.BigDecimal totalMoney = accountRepository.totalBalance();

        stats.put("totalUsers", totalUsers);
        stats.put("totalAccounts", totalAccounts);
        stats.put("totalTransactions", totalTransactions);
        stats.put("totalMoney", totalMoney);
        stats.put("recentTransactions", transactionRepository.findTop50ByOrderByDateDescIdDesc());

        return ResponseEntity.ok(stats);
    }
}
