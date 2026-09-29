package com.bank.fintechbank.service;

import com.bank.fintechbank.model.*;
import com.bank.fintechbank.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class BankService {
    @Autowired private CustomerRepository customers;
    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository transactions;
    @Autowired private CreditRepository credits;
    @Autowired private InvestmentRepository investments;
    @Autowired private PasswordEncoder encoder;

    public BigDecimal validAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(new BigDecimal("1000000.00")) > 0) {
            throw new IllegalArgumentException("Ingresa un importe positivo de hasta 1,000,000 con dos decimales");
        }
        return amount;
    }

    @Transactional
    public Customer registerCustomer(String username, String password, String name) {
        if (username == null || !username.matches("[a-zA-Z0-9._-]{4,40}") || username.startsWith("demo_")
                || name == null || name.isBlank() || name.length() > 100 || password == null || password.length() < 12 || password.length() > 128) {
            throw new IllegalArgumentException("Revisa el nombre, usuario y contraseña (mínimo 12 caracteres)");
        }
        if (customers.findByUsername(username).isPresent()) throw new IllegalArgumentException("Ese usuario ya existe");
        Customer customer = customers.save(new Customer(username, encoder.encode(password), name.trim()));
        accounts.save(new Account(UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(), BigDecimal.ZERO, customer));
        return customer;
    }

    private Account owned(Long id, String username) {
        Account account = accounts.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada"));
        if (!account.getCustomer().getUsername().equals(username)) throw new IllegalArgumentException("Cuenta no encontrada");
        return account;
    }

    private void record(Account account, String type, BigDecimal amount, String detail, String channel, String reference) {
        Transaction tx = new Transaction(type, amount, LocalDateTime.now(), account);
        tx.setDetail(detail);
        tx.setBalanceAfter(account.getBalance());
        tx.setChannel(channel);
        tx.setReference(reference);
        transactions.save(tx);
    }

    private String reference() { return "CEMA-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(); }

    @Transactional
    public void deposit(String username, Long accountId, BigDecimal amount) {
        deposit(username, accountId, amount, "WEB");
    }

    @Transactional
    public void deposit(String username, Long accountId, BigDecimal amount, String channel) {
        validAmount(amount);
        Account account = owned(accountId, username);
        account.setBalance(account.getBalance().add(amount));
        record(account, "DEPOSIT", amount, "Depósito simulado", channel, reference());
    }

    @Transactional
    public void withdraw(String username, Long accountId, BigDecimal amount) {
        withdraw(username, accountId, amount, "WEB");
    }

    @Transactional
    public void withdraw(String username, Long accountId, BigDecimal amount, String channel) {
        validAmount(amount);
        Account account = owned(accountId, username);
        if (account.getBalance().compareTo(amount) < 0) throw new IllegalArgumentException("Saldo insuficiente");
        account.setBalance(account.getBalance().subtract(amount));
        record(account, "WITHDRAWAL", amount, "Retiro simulado", channel, reference());
    }

    @Transactional
    public void transfer(String username, Long fromId, String destinationNumber, BigDecimal amount) {
        validAmount(amount);
        if (destinationNumber == null || destinationNumber.isBlank()) throw new IllegalArgumentException("Ingresa una cuenta de destino");
        Account destination = accounts.findByAccountNumber(destinationNumber.trim().toUpperCase());
        if (destination == null || destination.getId().equals(fromId)) throw new IllegalArgumentException("Cuenta de destino inválida");
        // Bloqueamos las cuentas en orden para evitar bloqueos cruzados.
        Long first = Math.min(fromId, destination.getId());
        Long second = Math.max(fromId, destination.getId());
        Account a = accounts.findLockedById(first).orElseThrow();
        Account b = accounts.findLockedById(second).orElseThrow();
        Account source = a.getId().equals(fromId) ? a : b;
        Account target = a.getId().equals(fromId) ? b : a;
        if (!source.getCustomer().getUsername().equals(username)) throw new IllegalArgumentException("Cuenta de origen no encontrada");
        String tier = source.getCustomer().getTier();
        BigDecimal limit = switch (tier == null ? "ESENCIAL" : tier) {
            case "PREFERENTE" -> new BigDecimal("100000.00");
            case "PATRIMONIAL", "EQUIPO" -> new BigDecimal("250000.00");
            default -> new BigDecimal("25000.00");
        };
        if (amount.compareTo(limit) > 0) throw new IllegalArgumentException("El importe supera el límite por operación de tu nivel: " + limit);
        if (source.getBalance().compareTo(amount) < 0) throw new IllegalArgumentException("Saldo insuficiente");
        source.setBalance(source.getBalance().subtract(amount));
        target.setBalance(target.getBalance().add(amount));
        String reference = reference();
        record(source, "TRANSFER_OUT", amount, "A " + target.getAccountNumber(), "WEB", reference);
        record(target, "TRANSFER_IN", amount, "De " + source.getAccountNumber(), "WEB", reference);
    }

    @Transactional
    public void requestCredit(Long customerId, BigDecimal amount) {
        validAmount(amount);
        Customer customer = customers.findById(customerId).orElseThrow();
        // Solicitar no abona dinero. Un administrador debe aprobar después de revisar.
        credits.save(new Credit(amount, new BigDecimal("0.05"), false, customer));
    }

    @Transactional
    public void decideCredit(Long creditId, boolean approve) {
        Credit credit = credits.findLockedById(creditId).orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));
        if (!"PENDING".equals(credit.getStatus())) throw new IllegalArgumentException("Esta solicitud ya fue revisada");
        credit.setStatus(approve ? "APPROVED" : "REJECTED");
        credit.setApproved(approve);
        if (approve) {
            Long accountId = accounts.findByCustomerId(credit.getCustomer().getId()).get(0).getId();
            Account account = accounts.findLockedById(accountId).orElseThrow();
            account.setBalance(account.getBalance().add(credit.getAmount()));
            record(account, "CREDIT_DISBURSEMENT", credit.getAmount(), "Crédito aprobado", "BACKOFFICE", reference());
        }
    }

    @Transactional
    public void invest(String username, Long accountId, BigDecimal amount) {
        validAmount(amount);
        Account account = owned(accountId, username);
        if (account.getBalance().compareTo(amount) < 0) throw new IllegalArgumentException("Saldo insuficiente");
        account.setBalance(account.getBalance().subtract(amount));
        investments.save(new Investment(amount, new BigDecimal("0.08"), LocalDate.now().plusYears(1), account.getCustomer()));
        record(account, "INVESTMENT", amount, "Inversión simulada a 12 meses", "WEB", reference());
    }

    @Transactional
    public void changePin(String username, Long accountId, String pin) {
        if (pin == null || !pin.matches("\\d{4,6}") || pin.matches("(\\d)\\1+")) throw new IllegalArgumentException("Usa un PIN de 4 a 6 dígitos no repetidos");
        owned(accountId, username).setAtmPin(encoder.encode(pin));
    }
}
