package com.bank.fintechbank.controller;

import com.bank.fintechbank.service.DemoDataService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(name = "cema.demo.enabled", havingValue = "true")
public class DemoController {
    private final DemoDataService data;
    public DemoController(DemoDataService data) { this.data = data; }

    @GetMapping("/catalog")
    public ResponseEntity<?> catalog() {
        if (!data.isSeeded()) return ResponseEntity.status(503).body(Map.of("error", "La población demo aún no está lista"));
        return ResponseEntity.ok(Map.of("mode", "DEMO", "notice", "Identidades y credenciales ficticias. Solo para este entorno local.",
                "users", data.identities()));
    }
}
