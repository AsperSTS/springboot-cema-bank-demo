package com.bank.fintechbank.controller;

import com.bank.fintechbank.model.*;
import com.bank.fintechbank.repository.*;
import com.bank.fintechbank.service.BankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import java.util.*;

@RestController
@RequestMapping("/api")
public class BankRestController {
    @Autowired private BankService bank;
    @Autowired private CustomerRepository customers;
    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository transactions;
    @Autowired private CreditRepository credits;
    @Autowired private InvestmentRepository investments;
    @Autowired private PasswordEncoder encoder;
    @Value("${cema.demo.enabled:false}") private boolean demoEnabled;
    private final SecureRandom random = new SecureRandom();

    private String text(Map<String, ?> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? null : value.toString();
    }
    private Long id(Map<String, ?> payload, String key) {
        try { return Long.valueOf(text(payload, key)); }
        catch (Exception e) { throw new IllegalArgumentException("Cuenta inválida"); }
    }
    private BigDecimal amount(Map<String, ?> payload) {
        try { return bank.validAmount(new BigDecimal(text(payload, "amount"))); }
        catch (Exception e) { throw new IllegalArgumentException("Importe inválido: usa un valor positivo con dos decimales"); }
    }
    private Customer current(Authentication auth) {
        return customers.findByUsername(auth.getName()).orElseThrow();
    }
    private String newToken(Customer customer) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        customer.setCurrentSessionToken(com.bank.fintechbank.config.TokenAuthFilter.hash(token));
        customer.setSessionExpiresAt(LocalDateTime.now().plusMinutes(30));
        customer.setFailedAttempts(0);
        customers.save(customer);
        return token;
    }
    private ResponseEntity<?> bad(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() == null ? "Solicitud inválida" : e.getMessage()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload) {
        try {
            bank.registerCustomer(text(payload, "username"), text(payload, "password"), text(payload, "name"));
            return ResponseEntity.ok(Map.of("message", "Cuenta creada. Configura tu PIN al iniciar sesión."));
        } catch (IllegalArgumentException e) { return bad(e); }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        Customer c = customers.findByUsername(text(payload, "username")).orElse(null);
        if (c == null || (!demoEnabled && c.getUsername().startsWith("demo_"))
                || c.isAccountLocked() || c.getLockedUntil() != null && c.getLockedUntil().isAfter(LocalDateTime.now()))
            return ResponseEntity.status(401).body(Map.of("error", "Credenciales inválidas o acceso temporalmente bloqueado"));
        if (!encoder.matches(text(payload, "password") == null ? "" : text(payload, "password"), c.getPassword())) {
            fail(c);
            return ResponseEntity.status(401).body(Map.of("error", "Credenciales inválidas o acceso temporalmente bloqueado"));
        }
        c.setLockedUntil(null);
        return ResponseEntity.ok(Map.of("token", newToken(c), "role", c.getRole(), "name", c.getName(), "username", c.getUsername()));
    }
    private void fail(Customer c) {
        c.setFailedAttempts(c.getFailedAttempts() + 1);
        if (c.getFailedAttempts() >= 5) { c.setLockedUntil(LocalDateTime.now().plusMinutes(15)); c.setFailedAttempts(0); }
        customers.save(c);
    }

    @PostMapping("/atm/login")
    public ResponseEntity<?> atmLogin(@RequestBody Map<String, String> payload) {
        Account account = accounts.findByAccountNumber(text(payload, "accountNumber"));
        if (account == null) return ResponseEntity.status(401).body(Map.of("error", "Cuenta o PIN inválidos"));
        Customer c = customers.findById(account.getCustomer().getId()).orElseThrow();
        if ((!demoEnabled && c.getUsername().startsWith("demo_"))
                || c.isAccountLocked() || c.getLockedUntil() != null && c.getLockedUntil().isAfter(LocalDateTime.now()))
            return ResponseEntity.status(401).body(Map.of("error", "Cuenta o PIN inválidos"));
        String pin = text(payload, "pin");
        if (account.getAtmPin() == null || pin == null || !encoder.matches(pin, account.getAtmPin())) {
            fail(c);
            return ResponseEntity.status(401).body(Map.of("error", "Cuenta o PIN inválidos"));
        }
        c.setLockedUntil(null);
        return ResponseEntity.ok(Map.of("token", newToken(c), "name", c.getName()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(Authentication auth) {
        Customer c = current(auth);
        c.setCurrentSessionToken(null);
        c.setSessionExpiresAt(null);
        customers.save(c);
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada"));
    }
    @GetMapping("/auth/me")
    public ResponseEntity<?> me(Authentication auth) {
        Customer c = current(auth);
        return ResponseEntity.ok(Map.of("id", c.getId(), "username", c.getUsername(), "name", c.getName(), "role", c.getRole(), "tier", c.getTier()));
    }
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(Authentication auth) {
        Customer c = current(auth);
        List<Account> owned = accounts.findByCustomerId(c.getId());
        List<Transaction> history = owned.stream().flatMap(a -> transactions.findTop100ByAccountIdOrderByDateDescIdDesc(a.getId()).stream())
            .sorted(Comparator.comparing(Transaction::getDate).reversed()).limit(100).toList();
        return ResponseEntity.ok(Map.of("accounts", owned, "credits", credits.findByCustomerId(c.getId()),
            "investments", investments.findByCustomerId(c.getId()), "transactions", history, "tier", c.getTier()));
    }
    @GetMapping("/statement")
    @Transactional(readOnly = true)
    public ResponseEntity<?> statement(Authentication auth, @RequestParam Long accountId,
                                        @RequestParam LocalDate from, @RequestParam LocalDate to,
                                        @RequestParam(defaultValue = "0") int page) {
        if (from == null || to == null || from.isAfter(to) || to.isAfter(LocalDate.now())
                || from.isBefore(LocalDate.now().minusYears(3)) || page < 0 || page > 10000)
            return ResponseEntity.badRequest().body(Map.of("error", "Elige un periodo válido de los últimos tres años"));
        Account account = accounts.findById(accountId).orElse(null);
        Customer holder = current(auth);
        if (account == null || !account.getCustomer().getId().equals(holder.getId()))
            return ResponseEntity.status(404).body(Map.of("error", "Cuenta no encontrada"));
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(23, 59, 59, 999999999);
        Transaction before = transactions.findFirstByAccountIdAndDateBeforeOrderByDateDescIdDesc(accountId, start);
        Transaction last = transactions.findFirstByAccountIdAndDateLessThanEqualOrderByDateDescIdDesc(accountId, end);
        var movements = transactions.findByAccountIdAndDateBetweenOrderByDateDescIdDesc(accountId, start, end, PageRequest.of(page, 25));
        Map<String, Object> response = new HashMap<>();
        response.put("accountNumber", account.getAccountNumber());
        response.put("holder", holder.getName());
        response.put("currency", "MXN");
        response.put("from", from);
        response.put("to", to);
        response.put("openingBalance", before == null ? BigDecimal.ZERO : before.getBalanceAfter());
        response.put("closingBalance", last == null ? before == null ? BigDecimal.ZERO : before.getBalanceAfter() : last.getBalanceAfter());
        response.put("movements", movements.getContent());
        response.put("totalMovements", movements.getTotalElements());
        response.put("totalPages", movements.getTotalPages());
        response.put("page", page);
        return ResponseEntity.ok(response);
    }
    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(Authentication auth, @RequestBody Map<String, Object> body) {
        try { bank.deposit(auth.getName(), id(body, "accountId"), amount(body)); return ResponseEntity.ok(Map.of("message", "Depósito simulado registrado")); }
        catch (IllegalArgumentException e) { return bad(e); }
    }
    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(Authentication auth, @RequestBody Map<String, Object> body) {
        try { bank.withdraw(auth.getName(), id(body, "accountId"), amount(body)); return ResponseEntity.ok(Map.of("message", "Retiro simulado registrado")); }
        catch (IllegalArgumentException e) { return bad(e); }
    }
    @PostMapping("/atm/deposit")
    public ResponseEntity<?> atmDeposit(Authentication auth, @RequestBody Map<String, Object> body) {
        try { bank.deposit(auth.getName(), id(body, "accountId"), amount(body), "ATM"); return ResponseEntity.ok(Map.of("message", "Depósito simulado registrado")); }
        catch (IllegalArgumentException e) { return bad(e); }
    }
    @PostMapping("/atm/withdraw")
    public ResponseEntity<?> atmWithdraw(Authentication auth, @RequestBody Map<String, Object> body) {
        try { bank.withdraw(auth.getName(), id(body, "accountId"), amount(body), "ATM"); return ResponseEntity.ok(Map.of("message", "Retiro simulado registrado")); }
        catch (IllegalArgumentException e) { return bad(e); }
    }
    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(Authentication auth, @RequestBody Map<String, Object> body) {
        try { bank.transfer(auth.getName(), id(body, "accountId"), text(body, "destinationAccountNumber"), amount(body)); return ResponseEntity.ok(Map.of("message", "Transferencia realizada")); }
        catch (IllegalArgumentException e) { return bad(e); }
    }
    @PostMapping("/request-credit")
    public ResponseEntity<?> credit(Authentication auth, @RequestBody Map<String, Object> body) {
        try { bank.requestCredit(current(auth).getId(), amount(body)); return ResponseEntity.ok(Map.of("message", "Solicitud recibida; está pendiente de revisión")); }
        catch (IllegalArgumentException e) { return bad(e); }
    }
    @PostMapping("/invest")
    public ResponseEntity<?> invest(Authentication auth, @RequestBody Map<String, Object> body) {
        try { bank.invest(auth.getName(), id(body, "accountId"), amount(body)); return ResponseEntity.ok(Map.of("message", "Inversión simulada registrada")); }
        catch (IllegalArgumentException e) { return bad(e); }
    }
    @PostMapping("/atm/pin")
    public ResponseEntity<?> pin(Authentication auth, @RequestBody Map<String, Object> body) {
        try { bank.changePin(auth.getName(), id(body, "accountId"), text(body, "pin")); return ResponseEntity.ok(Map.of("message", "PIN actualizado")); }
        catch (IllegalArgumentException e) { return bad(e); }
    }
}
