package com.bank.fintechbank.repository;

import com.bank.fintechbank.model.RoleChangeAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoleChangeAuditRepository extends JpaRepository<RoleChangeAudit, Long> {
    List<RoleChangeAudit> findTop30ByOrderByChangedAtDescIdDesc();
}
