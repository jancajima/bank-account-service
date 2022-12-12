package com.nttdata.bankaccountservice.service.impl;

import com.nttdata.bankaccountservice.document.BankAccount;
import com.nttdata.bankaccountservice.dto.BankCreditDto;
import com.nttdata.bankaccountservice.dto.BankDebtDto;
import com.nttdata.bankaccountservice.dto.CustomerDto;
import com.nttdata.bankaccountservice.dto.OperationDto;
import com.nttdata.bankaccountservice.dto.TransactionBetweenAccountsDto;
import com.nttdata.bankaccountservice.dto.TransactionDto;
import com.nttdata.bankaccountservice.dto.TransactionPayCreditThirdDto;
import com.nttdata.bankaccountservice.repository.BankAccountRepository;
import com.nttdata.bankaccountservice.service.AccountTypeService;
import com.nttdata.bankaccountservice.service.BankAccountService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Bank Account Service Implementation.
 */
@Service
public class BankAccountServiceImpl implements BankAccountService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BankAccountServiceImpl.class);

    @Autowired
    private WebClient.Builder webClient;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private AccountTypeService accountTypeService;

    @Override
    public Flux<BankAccount> findAll() {
        return this.bankAccountRepository.findAll();
    }

    @Override
    public Mono<BankAccount> register(BankAccount bankAccount) {
        return this.bankAccountRepository.save(bankAccount);
    }

    @Override
    public Mono<BankAccount> update(BankAccount bankAccount) {
        return this.bankAccountRepository.save(bankAccount);
    }

    @Override
    public Mono<BankAccount> findById(String id) {
        LOGGER.info("find by id " + id + "id");
        return this.bankAccountRepository.findById(id);
    }

    @Override
    public Mono<Void> delete(String id) {
        return bankAccountRepository.deleteById(id);
    }

    @Override
    public Mono<Boolean> existsById(String id) {
        return this.bankAccountRepository.existsById(id);
    }

    @Override
    public Mono<CustomerDto> findCustomerById(String customerId) {
        LOGGER.info("Consulted customer from the bank-account-service");
        Mono<CustomerDto> customer = this.webClient.build().get().uri("/costumer/{id}", customerId)
                .retrieve().bodyToMono(CustomerDto.class);
        return customer;
    }

    @Override
    public Mono<BankAccount> validateRegister(BankAccount bankAccount) {
        return this.webClient.build().get().uri("/customer/{id}", bankAccount.getCustomerId())
            .retrieve()
            .bodyToMono(CustomerDto.class)
            .flatMap(dc -> {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                bankAccount.setCreationDate(LocalDate.now().format(formatter));
                if (dc.getType().equals("business")) {
                    return this.accountTypeService.findById(bankAccount.getType()).filter(obj ->
                        obj.getCode().equals("3"))
                        .flatMap(x -> register(bankAccount));
                } else {
                    return findCustomerHasDebt(bankAccount.getCustomerId()).flatMap(x -> {
                        LOGGER.info(x);
                        if (x.equals("1")) {
                            throw new RuntimeException("Customer has overdue debt");
                        } else {
                            return register(bankAccount);
                        }
                    }).switchIfEmpty(register(bankAccount));
                }
            });
    }

    @Override
    public Flux<BankAccount> findByCustomerIdAndType(String customerId, String type) {
        return this.bankAccountRepository.findByCustomerIdAndType(
                customerId, type);
    }

    @Override
    public Mono<BankAccount> validateBankAccount(String customerId, String type) {
        LOGGER.info("validateBankAccount");
        return this.bankAccountRepository.findByCustomerIdAndType(
            customerId, type)
            .next();
    }

    @Override
    public Mono<BankAccount> doDeposit(OperationDto transaction) {
        return findById(transaction.getAccountId()).flatMap(x -> {
            float newAmount = x.getAmount() + transaction.getAmount();
            TransactionDto t = new TransactionDto(LocalDate.now().toString(),
                transaction.getAmount(), "deposit", x.getCustomerId(),
                transaction.getAccountId(), newAmount, x.getDebitCardId());
            x.setAmount(newAmount);
            x.setNumberOfTransactions(x.getNumberOfTransactions() + 1);
            return this.webClient.build().post().uri("/transaction/")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(Mono.just(t), TransactionDto.class)
                .retrieve()
                .bodyToMono(TransactionDto.class)
                .flatMap(y -> update(x).map(z -> {
                    doCommission(t);
                    return x;
                }));
        });
    }

    @Override
    public Mono<BankAccount> doWithDrawl(OperationDto transaction) {
        return findById(transaction.getAccountId()).flatMap(x -> {
            float newAmount = x.getAmount() - transaction.getAmount();
            if (newAmount >= 0) {
                TransactionDto t = new TransactionDto(LocalDate.now().toString(),
                    transaction.getAmount(),"withdrawl", x.getCustomerId(),
                    transaction.getAccountId(), newAmount, x.getDebitCardId());
                x.setAmount(newAmount);
                x.setNumberOfTransactions(x.getNumberOfTransactions() + 1);
                return this.webClient.build().post().uri("/transaction/")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(Mono.just(t), TransactionDto.class)
                    .retrieve()
                    .bodyToMono(TransactionDto.class)
                    .flatMap(y -> update(x).map(z -> {
                        doCommission(t);
                        return x;
                    }));
            } else {
                return Mono.empty();
            }
        });
    }

    @Override
    public Mono<BankAccount> doTransactionBetweenAccounts(TransactionBetweenAccountsDto t) {
        OperationDto sender = new OperationDto(t.getSenderAccountId(), t.getAmount());
        OperationDto receptor = new OperationDto(t.getReceptorAccountId(), t.getAmount());
        return doWithDrawl(sender).flatMap(x -> doDeposit(receptor));
    }

    @Override
    public Mono<BankAccount> doCommission(TransactionDto transaction) {
        return findById(transaction.getAccountId()).flatMap(x -> {
            if (x.getNumberOfTransactions() > x.getTransactionLimit()) {
                float newAmount = x.getAmount() - x.getCommission();
                if (newAmount >= 0) {
                    transaction.setType("commission");
                    transaction.setCustomerId(x.getCustomerId());
                    transaction.setAmount(x.getCommission());
                    transaction.setAccountAmount(newAmount);
                    x.setAmount(newAmount);
                    return this.webClient.build().post().uri("/transaction/")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(Mono.just(transaction), TransactionDto.class)
                        .retrieve()
                        .bodyToMono(TransactionDto.class)
                        .flatMap(y -> update(x));
                } else {
                    return Mono.empty();
                }
            } else {
                return Mono.empty();
            }
        });
    }

    @Override
    public Flux<BankAccount> findAccountsByDebitCard(String debitCardId) {
        return this.bankAccountRepository.findByDebitCardId(debitCardId);
    }

    @Override
    public Mono<BankAccount> associateToDebitCard(String bankAccountId, String debitCardId) {
        return findById(bankAccountId).map(account -> {
            account.setDebitCardId(debitCardId);
            account.setAssociationDate(LocalDateTime.now().toString());
            account.setPrimaryAccount(false);
            return account;
        }).flatMap(account -> update(account));
    }

    @Override
    public Mono<BankAccount> makePrimaryAccount(String bankAccountId) {
        LOGGER.info("making primary account " + bankAccountId + "id");
        return findById(bankAccountId).map(account -> {
            LOGGER.info("found account " + account + "id");
            account.setPrimaryAccount(true);
            return account;
        }).flatMap(account -> update(account));
    }

    public Flux<BankAccount> findByCustomerId(String customerId) {
        LOGGER.info("findByCustomerId");
        return this.bankAccountRepository.findByCustomerId(customerId);
    }

    @Override
    public Mono<String> findCustomerHasDebt(String customerId) {
        return this.webClient.build().get().uri("/bankDebt/debtByCustomerId/{id}", customerId)
            .retrieve().bodyToFlux(BankDebtDto.class).next()
            .flatMap(x -> Mono.just("1"));
    }

    @Override
    public Mono<BankAccount> doPayCreditThird(TransactionPayCreditThirdDto t) {
        OperationDto sender = new OperationDto(t.getSenderAccountId(), t.getAmount());
        OperationDto receptor = new OperationDto(t.getReceptorCreditId(), t.getAmount());
        return this.webClient.build().put().uri("/bankCredit/paycredit")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(Mono.just(receptor), OperationDto.class)
            .retrieve()
            .bodyToMono(BankCreditDto.class)
            .flatMap(y -> doWithDrawl(sender));
    }

    @Override
    public Mono<BankAccount> findByNumberAccount(String numberAccount) {
        return bankAccountRepository.findByNumberAccount(numberAccount);
    }
}
