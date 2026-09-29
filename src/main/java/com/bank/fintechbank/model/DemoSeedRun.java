package com.bank.fintechbank.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class DemoSeedRun {
    @Id private String version;
    private LocalDateTime createdAt;

    public DemoSeedRun() {}
    public DemoSeedRun(String version) {
        this.version = version;
        this.createdAt = LocalDateTime.now();
    }
    public String getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
