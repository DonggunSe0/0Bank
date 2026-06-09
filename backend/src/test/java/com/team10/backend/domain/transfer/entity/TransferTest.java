package com.team10.backend.domain.transfer.entity;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.transfer.type.TransferStatus;
import com.team10.backend.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class TransferTest {

    @Test
    void success_createsSuccessTransfer() {
        Account senderAccount = account("100200300001");
        Account receiverAccount = account("100200300002");

        Transfer transfer = Transfer.success(senderAccount, receiverAccount, 50_000L, "점심값");

        assertSame(senderAccount, transfer.getSenderAccount());
        assertSame(receiverAccount, transfer.getReceiverAccount());
        assertEquals(50_000L, transfer.getAmount());
        assertEquals(TransferStatus.SUCCESS, transfer.getStatus());
        assertEquals("점심값", transfer.getMemo());
    }

    @Test
    void failed_createsFailedTransfer() {
        Account senderAccount = account("100200300001");
        Account receiverAccount = account("100200300002");

        Transfer transfer = Transfer.failed(senderAccount, receiverAccount, 50_000L, "점심값");

        assertSame(senderAccount, transfer.getSenderAccount());
        assertSame(receiverAccount, transfer.getReceiverAccount());
        assertEquals(50_000L, transfer.getAmount());
        assertEquals(TransferStatus.FAILED, transfer.getStatus());
        assertEquals("점심값", transfer.getMemo());
    }

    private Account account(String accountNumber) {
        return Account.create(mock(User.class), accountNumber, "테스트 계좌", AccountType.DEPOSIT);
    }
}
