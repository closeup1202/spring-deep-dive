package com.exam.txtemplate.repository;

import com.exam.txtemplate.domain.TransferRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRecordRepository extends JpaRepository<TransferRecord, Long> {
}
