package com.exam.txtemplate.repository;

import com.exam.txtemplate.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
}
