package com.fsad.mutualfund.service;

import com.fsad.mutualfund.dto.TransactionRequest;
import com.fsad.mutualfund.entity.PortfolioHolding;
import com.fsad.mutualfund.entity.Transaction;

import java.util.List;

public interface TransactionService {
    Transaction buyFund(Long userId, TransactionRequest request);
    Transaction sellFund(Long userId, TransactionRequest request);
    List<Transaction> getTransactionHistory(Long userId);
    List<PortfolioHolding> getPortfolio(Long userId);
}
