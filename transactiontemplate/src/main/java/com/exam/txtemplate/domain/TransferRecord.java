package com.exam.txtemplate.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "transfer_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class TransferRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @ToString.Include
    private Long fromAccountId;

    @ToString.Include
    private Long toAccountId;

    @ToString.Include
    private long amount;

    @ToString.Include
    private String status; // SUCCESS, FAILED

    @ToString.Include
    private LocalDateTime transferredAt;

    private int retryCount;

    public static TransferRecord success(Long fromId, Long toId, long amount, int retryCount) {
        TransferRecord r = new TransferRecord();
        r.fromAccountId = fromId;
        r.toAccountId   = toId;
        r.amount        = amount;
        r.status        = "SUCCESS";
        r.retryCount    = retryCount;
        r.transferredAt = LocalDateTime.now();
        return r;
    }

    public static TransferRecord failed(Long fromId, Long toId, long amount, String reason) {
        TransferRecord r = new TransferRecord();
        r.fromAccountId = fromId;
        r.toAccountId   = toId;
        r.amount        = amount;
        r.status        = "FAILED:" + reason;
        r.retryCount    = 0;
        r.transferredAt = LocalDateTime.now();
        return r;
    }
}
