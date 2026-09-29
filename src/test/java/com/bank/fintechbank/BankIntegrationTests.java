package com.bank.fintechbank;

import com.bank.fintechbank.model.Account;
import com.bank.fintechbank.model.Customer;
import com.bank.fintechbank.config.TokenAuthFilter;
import com.bank.fintechbank.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class BankIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CustomerRepository customers;
    @Autowired AccountRepository accounts;
    @Autowired TransactionRepository transactions;
    @Autowired CreditRepository credits;
    @Autowired InvestmentRepository investments;
    @Autowired RoleChangeAuditRepository roleAudit;
    @Autowired PasswordEncoder encoder;

    @BeforeEach void reset() {
        transactions.deleteAll(); credits.deleteAll(); investments.deleteAll(); roleAudit.deleteAll(); accounts.deleteAll(); customers.deleteAll();
    }
    private String body(Object value) throws Exception { return json.writeValueAsString(value); }
    private void register(String name) throws Exception {
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("username", name, "password", "LongPassword123!", "name", name))))
            .andExpect(status().isOk());
    }
    private String token(String name) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("username", name, "password", "LongPassword123!"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }
    private Account account(String name) { Customer c = customers.findByUsername(name).orElseThrow(); return accounts.findByCustomerId(c.getId()).get(0); }
    private void postAs(String route, String token, Object payload, int status) throws Exception {
        mvc.perform(post(route).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body(payload)))
            .andExpect(status == 200 ? status().isOk() : status().isBadRequest());
    }

    @Test void rejectsBadRegistrationAndPrivateAccess() throws Exception {
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("username", "abc", "password", "1234", "name", "A"))))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        register("alice");
        assertNull(account("alice").getAtmPin());
    }

    @Test void moneyIsValidatedOwnedAndTransferredAtomically() throws Exception {
        register("alice"); register("bobby");
        String alice = token("alice"); String bob = token("bobby");
        Account a = account("alice"); Account b = account("bobby");
        postAs("/api/deposit", alice, Map.of("accountId", a.getId(), "amount", "100.00"), 200);
        postAs("/api/deposit", bob, Map.of("accountId", a.getId(), "amount", "20.00"), 400);
        postAs("/api/withdraw", alice, Map.of("accountId", a.getId(), "amount", "-1.00"), 400);
        postAs("/api/withdraw", alice, Map.of("accountId", a.getId(), "amount", "101.00"), 400);
        postAs("/api/transfer", alice, Map.of("accountId", a.getId(), "destinationAccountNumber", b.getAccountNumber(), "amount", "25.00"), 200);
        assertEquals(0, new BigDecimal("75.00").compareTo(accounts.findById(a.getId()).orElseThrow().getBalance()));
        assertEquals(0, new BigDecimal("25.00").compareTo(accounts.findById(b.getId()).orElseThrow().getBalance()));
        assertEquals(3, transactions.count());
    }

    @Test void pinAndLogoutAreEnforced() throws Exception {
        register("alice"); String web = token("alice"); Account a = account("alice");
        mvc.perform(post("/api/atm/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("accountNumber", a.getAccountNumber(), "pin", "1234")))).andExpect(status().isUnauthorized());
        postAs("/api/atm/pin", web, Map.of("accountId", a.getId(), "pin", "483729"), 200);
        assertTrue(accounts.findById(a.getId()).orElseThrow().getAtmPin().startsWith("$2"));
        String dashboard = mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + web))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(dashboard.contains("atmPin"));
        assertFalse(dashboard.contains("483729"));
        assertFalse(dashboard.contains("password"));
        String atmResult = mvc.perform(post("/api/atm/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("accountNumber", a.getAccountNumber(), "pin", "483729"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String atm = json.readTree(atmResult).get("token").asText();
        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + web)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + atm)).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + atm)).andExpect(status().isUnauthorized());
    }

    @Test void onlyAdminCanApproveCreditOnce() throws Exception {
        register("alice"); register("adminuser");
        Customer admin = customers.findByUsername("adminuser").orElseThrow();
        admin.setRole("ROLE_ADMIN"); customers.save(admin);
        String alice = token("alice"); String adminToken = token("adminuser");
        postAs("/api/request-credit", alice, Map.of("amount", "500.00"), 200);
        Long creditId = credits.findByCustomerId(customers.findByUsername("alice").orElseThrow().getId()).get(0).getId();
        mvc.perform(post("/api/admin/credits/" + creditId + "/approve").header("Authorization", "Bearer " + alice))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/credits/" + creditId + "/approve").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
        mvc.perform(post("/api/admin/credits/" + creditId + "/approve").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
        assertEquals(0, new BigDecimal("500.00").compareTo(account("alice").getBalance()));
        assertEquals("APPROVED", credits.findById(creditId).orElseThrow().getStatus());
    }

    @Test void roleChangesRequireAdminAndLeaveAudit() throws Exception {
        register("adminuser"); register("staffuser");
        Customer admin = customers.findByUsername("adminuser").orElseThrow();
        Customer staff = customers.findByUsername("staffuser").orElseThrow();
        admin.setRole("ROLE_ADMIN"); customers.save(admin);
        String adminToken = token("adminuser");
        String staffToken = token("staffuser");
        String route = "/api/admin/users/" + staff.getId() + "/role";
        String payload = body(Map.of("role", "ROLE_ANALYST"));
        mvc.perform(post(route).header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
        mvc.perform(post(route).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
        assertEquals("ROLE_ANALYST", customers.findById(staff.getId()).orElseThrow().getRole());
        assertEquals(1, roleAudit.count());
        mvc.perform(post("/api/admin/users/" + admin.getId() + "/role").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("role", "ROLE_USER"))))
                .andExpect(status().isBadRequest());
    }

    @Test void disablingDemoRejectsKnownDemoAccess() throws Exception {
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", "demo_fake", "password", "LongPassword123!", "name", "Demo"))))
                .andExpect(status().isBadRequest());
        Customer demo = new Customer("demo_fake", encoder.encode("LongPassword123!"), "Demo");
        demo.setCurrentSessionToken(TokenAuthFilter.hash("known-demo-token"));
        demo.setSessionExpiresAt(LocalDateTime.now().plusMinutes(30));
        demo = customers.save(demo);
        Account account = new Account("8800000000999999", BigDecimal.ZERO, demo);
        account.setAtmPin(encoder.encode("483729"));
        accounts.save(account);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", "demo_fake", "password", "LongPassword123!"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/atm/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("accountNumber", account.getAccountNumber(), "pin", "483729"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer known-demo-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/demo/catalog")).andExpect(status().isNotFound());
    }
}
