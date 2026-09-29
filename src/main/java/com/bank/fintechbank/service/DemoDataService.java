package com.bank.fintechbank.service;

import com.bank.fintechbank.model.*;
import com.bank.fintechbank.repository.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DemoDataService {
    public static final String VERSION = "cema-demo-2026-v1";
    private static final String[] GIVEN = {"Valeria", "Santiago", "Camila", "Mateo", "Regina", "Emiliano", "Lucía", "Sebastián", "Renata", "Daniel", "Mariana", "Andrés", "Sofía", "Diego", "Jimena", "Julián", "Paula", "Gabriel", "Fernanda", "Nicolás"};
    private static final String[] FAMILY = {"Morales", "Navarro", "Castillo", "Ríos", "Vargas", "Medina", "Campos", "Ortega", "Silva", "Cortés", "Mendoza", "Pineda", "Salazar", "Luna", "Ibarra", "Robles", "Serrano", "Aguilar", "Fuentes", "Reyes"};

    private final DemoSeedRunRepository runs;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final PasswordEncoder encoder;

    public DemoDataService(DemoSeedRunRepository runs, CustomerRepository customers, AccountRepository accounts,
                           TransactionRepository transactions, PasswordEncoder encoder) {
        this.runs = runs; this.customers = customers; this.accounts = accounts;
        this.transactions = transactions; this.encoder = encoder;
    }

    public record DemoIdentity(int index, String username, String password, String pin, String name,
                               String role, String tier, String accountNumber) {}

    public List<DemoIdentity> identities() {
        List<DemoIdentity> result = new ArrayList<>();
        for (int i = 1; i <= 115; i++) {
            String role = i <= 2 ? "ROLE_ADMIN" : i <= 8 ? "ROLE_EXECUTIVE" : i <= 15 ? "ROLE_ANALYST" : "ROLE_USER";
            String tier = i <= 15 ? "EQUIPO" : i <= 75 ? "ESENCIAL" : i <= 105 ? "PREFERENTE" : "PATRIMONIAL";
            String username = i <= 2 ? "demo_admin_%02d".formatted(i) : i <= 8 ? "demo_ejecutivo_%02d".formatted(i - 2)
                    : i <= 15 ? "demo_analista_%02d".formatted(i - 8) : "demo_cliente_%03d".formatted(i - 15);
            String name = GIVEN[(i - 1) % GIVEN.length]
                    + (i > 20 ? " " + GIVEN[((i - 1) / 20 + 5) % GIVEN.length] : "")
                    + " " + FAMILY[(i * 7) % FAMILY.length] + " " + FAMILY[(i * 11 + 3) % FAMILY.length];
            // Las claves son públicas solo en el entorno de demostración.
            result.add(new DemoIdentity(i, username, "CemaDemo!2026#%03d".formatted(i), "%06d".formatted(483000 + i),
                    name, role, tier, "8800%012d".formatted(i)));
        }
        return result;
    }

    public boolean isSeeded() { return runs.existsById(VERSION); }

    @Transactional
    public void seedOnce() {
        if (runs.existsById(VERSION)) return;
        List<DemoIdentity> identities = identities();
        List<Account> made = new ArrayList<>();
        for (DemoIdentity spec : identities) {
            if (customers.findByUsername(spec.username()).isPresent())
                throw new IllegalStateException("El usuario reservado para la demo ya existe: " + spec.username());
            Customer customer = new Customer(spec.username(), encoder.encode(spec.password()), spec.name());
            customer.setRole(spec.role());
            customer.setTier(spec.tier());
            customers.save(customer);
            Account account = new Account(spec.accountNumber(), BigDecimal.ZERO, customer);
            account.setAtmPin(encoder.encode(spec.pin()));
            made.add(accounts.save(account));
        }

        LocalDate start = LocalDate.now().minusYears(2);
        List<Transaction> history = new ArrayList<>();
        for (int month = 0; month < 24; month++) {
            LocalDate base = start.plusMonths(month);
            for (int index = 0; index < made.size(); index++) {
                Account account = made.get(index);
                int n = index + 1;
                BigDecimal income = BigDecimal.valueOf(15000L + (n % 19) * 1400L);
                BigDecimal bill = BigDecimal.valueOf(1800L + (n % 7) * 340L);
                BigDecimal cash = BigDecimal.valueOf(500L + (n % 5) * 200L);
                add(history, account, "DEPOSIT", income, base.atTime(9, 10), "Abono de prueba", "WEB", ref(month, n, 1));
                add(history, account, "PAYMENT", bill, base.plusDays(7).atTime(13, 25), "Pago de servicios simulado", "WEB", ref(month, n, 2));
                add(history, account, "WITHDRAWAL", cash, base.plusDays(14).atTime(17, 30), "Retiro en cajero simulado", "ATM", ref(month, n, 3));
            }
            if (month % 3 == 0) {
                for (int index = 0; index < made.size(); index++) {
                    Account from = made.get(index);
                    Account to = made.get((index + 1) % made.size());
                    BigDecimal amount = BigDecimal.valueOf(350L + (index % 8) * 75L);
                    String reference = ref(month, index + 1, 4);
                    LocalDateTime date = base.plusDays(19).atTime(11, 40);
                    add(history, from, "TRANSFER_OUT", amount, date, "A " + to.getAccountNumber(), "WEB", reference);
                    add(history, to, "TRANSFER_IN", amount, date, "De " + from.getAccountNumber(), "WEB", reference);
                }
            }
        }
        // Un movimiento reciente hace útil el panel desde el primer inicio.
        for (int index = 0; index < made.size(); index++) {
            add(history, made.get(index), "DEPOSIT", BigDecimal.valueOf(250L + index),
                    LocalDate.now().atTime(LocalTime.of(8, 30)), "Ajuste de demostración", "WEB", ref(24, index + 1, 1));
        }
        accounts.saveAll(made);
        transactions.saveAll(history);
        runs.save(new DemoSeedRun(VERSION));
    }

    private String ref(int month, int customer, int sequence) {
        return "DEMO-%02d-%03d-%d".formatted(month, customer, sequence);
    }

    private void add(List<Transaction> history, Account account, String type, BigDecimal amount,
                     LocalDateTime date, String detail, String channel, String reference) {
        boolean debit = type.equals("PAYMENT") || type.equals("WITHDRAWAL") || type.equals("TRANSFER_OUT");
        account.setBalance(debit ? account.getBalance().subtract(amount) : account.getBalance().add(amount));
        Transaction tx = new Transaction(type, amount, date, account);
        tx.setDetail(detail);
        tx.setBalanceAfter(account.getBalance());
        tx.setChannel(channel);
        tx.setReference(reference);
        history.add(tx);
    }
}

@Configuration
class DemoDataStartup {
    @Bean
    @ConditionalOnProperty(name = "cema.demo.enabled", havingValue = "true")
    ApplicationRunner runDemoSeed(DemoDataService data) {
        return args -> data.seedOnce();
    }
}
