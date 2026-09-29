package com.bank.fintechbank;

import com.bank.fintechbank.repository.*;
import com.bank.fintechbank.service.DemoDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = "cema.demo.enabled=true")
class DemoDataIntegrationTests {
    @Autowired DemoDataService demo;
    @Autowired CustomerRepository customers;
    @Autowired AccountRepository accounts;
    @Autowired TransactionRepository transactions;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    @Test void seedsOnceAndProtectsStaffActions() throws Exception {
        assertTrue(demo.isSeeded());
        assertEquals(115, customers.count());
        assertEquals(100, customers.countByRole("ROLE_USER"));
        assertEquals(2, customers.countByRole("ROLE_ADMIN"));
        assertEquals(6, customers.countByRole("ROLE_EXECUTIVE"));
        assertEquals(7, customers.countByRole("ROLE_ANALYST"));
        long total = transactions.count();
        assertTrue(total > 9000);
        demo.seedOnce();
        assertEquals(115, customers.count());
        assertEquals(total, transactions.count());

        String catalog = mvc.perform(get("/api/demo/catalog")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(115, json.readTree(catalog).get("users").size());
        assertFalse(customers.findByUsername("demo_cliente_001").orElseThrow().getPassword().contains("CemaDemo"));
        String analyst = login("demo_analista_01", "CemaDemo!2026#009");
        mvc.perform(get("/api/staff/dashboard").header("Authorization", "Bearer " + analyst)).andExpect(status().isOk());
        mvc.perform(post("/api/staff/credits/1/approve").header("Authorization", "Bearer " + analyst))
                .andExpect(status().isForbidden());
        String customer = login("demo_cliente_001", "CemaDemo!2026#016");
        mvc.perform(get("/api/staff/dashboard").header("Authorization", "Bearer " + customer)).andExpect(status().isForbidden());
        long id = accounts.findByCustomerId(customers.findByUsername("demo_cliente_001").orElseThrow().getId()).get(0).getId();
        String from = LocalDate.now().minusYears(2).toString();
        String to = LocalDate.now().toString();
        String statement = mvc.perform(get("/api/statement").param("accountId", String.valueOf(id))
                .param("from", from).param("to", to).header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(json.readTree(statement).get("totalMovements").asLong() >= 89);
        mvc.perform(get("/api/statement").param("accountId", String.valueOf(id))
                .param("from", from).param("to", to).header("Authorization", "Bearer " + analyst))
                .andExpect(status().isNotFound());
    }
}
