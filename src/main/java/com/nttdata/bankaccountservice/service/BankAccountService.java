package com.nttdata.bankaccountservice.service;

import com.nttdata.bankaccountservice.document.BankAccount;
import com.nttdata.bankaccountservice.dto.CustomerDto;
import com.nttdata.bankaccountservice.dto.OperationDto;
import com.nttdata.bankaccountservice.dto.TransactionBetweenAccountsDto;
import com.nttdata.bankaccountservice.dto.TransactionDto;
import com.nttdata.bankaccountservice.dto.TransactionPayCreditThirdDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Bank Account service interface.
 */
public interface BankAccountService {

    Flux<BankAccount> findAll();

    Mono<BankAccount> register(BankAccount bankAccount);

    Mono<BankAccount> update(BankAccount bankAccount);

    Mono<BankAccount> findById(String id);

    Mono<Void> delete(String id);

    Mono<Boolean> existsById(String id);

    Mono<CustomerDto> findCustomerById(String customerId);

    Flux<BankAccount> findByCustomerIdAndType(String customerId, String type);

    Mono<BankAccount> validateRegister(BankAccount bankAccount);

    Mono<BankAccount> doDeposit(OperationDto transaction);

    Mono<BankAccount> doWithDrawl(OperationDto transaction);

    Mono<BankAccount> doTransactionBetweenAccounts(TransactionBetweenAccountsDto t);

    Mono<BankAccount> validateBankAccount(String customerId, String type);

    Mono<BankAccount> doCommission(TransactionDto transactionDto);

    Flux<BankAccount> findAccountsByDebitCard(String debitCardId);

    Mono<BankAccount> associateToDebitCard(String bankAccountId, String debitCardId);

    Mono<BankAccount> makePrimaryAccount(String bankAccountId);

    Flux<BankAccount> findByCustomerId(String customerId);

    Mono<String> findCustomerHasDebt(String customerId);

    Mono<BankAccount> doPayCreditThird(TransactionPayCreditThirdDto t);

    Mono<BankAccount> findByNumberAccount(String numberAccount);
}
