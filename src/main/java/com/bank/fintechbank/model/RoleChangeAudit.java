package com.bank.fintechbank.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class RoleChangeAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String actorUsername;
    @Column(nullable = false) private String targetUsername;
    @Column(nullable = false) private String oldRole;
    @Column(nullable = false) private String newRole;
    @Column(nullable = false) private LocalDateTime changedAt;

    public RoleChangeAudit() {}
    public RoleChangeAudit(String actorUsername, String targetUsername, String oldRole, String newRole) {
        this.actorUsername = actorUsername;
        this.targetUsername = targetUsername;
        this.oldRole = oldRole;
        this.newRole = newRole;
        this.changedAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public String getActorUsername() { return actorUsername; }
    public String getTargetUsername() { return targetUsername; }
    public String getOldRole() { return oldRole; }
    public String getNewRole() { return newRole; }
    public LocalDateTime getChangedAt() { return changedAt; }
}
