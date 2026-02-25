package com.fsad.mutualfund.service.impl;

import com.fsad.mutualfund.dto.TransactionRequest;
import com.fsad.mutualfund.entity.*;
import com.fsad.mutualfund.repository.*;
import com.fsad.mutualfund.service.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final InvestorProfileRepository investorProfileRepository;
    private final MutualFundRepository fundRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final UserRepository userRepository;

    public TransactionServiceImpl(TransactionRepository transactionRepository,
                                  InvestorProfileRepository investorProfileRepository,
                                  MutualFundRepository fundRepository,
                                  PortfolioHoldingRepository holdingRepository,
                                  UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.investorProfileRepository = investorProfileRepository;
        this.fundRepository = fundRepository;
        this.holdingRepository = holdingRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public Transaction buyFund(Long userId, TransactionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        InvestorProfile profile = investorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Investor profile not found"));

        MutualFund fund = fundRepository.findById(request.getFundId())
                .orElseThrow(() -> new RuntimeException("Fund not found"));

        BigDecimal amount = request.getAmount();

        // Check wallet balance
        if (profile.getWalletBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient wallet balance. Available: " + profile.getWalletBalance());
        }

        // Calculate units
        BigDecimal nav = fund.getCurrentNav();
        BigDecimal units = amount.divide(nav, 4, RoundingMode.HALF_UP);

        // Deduct from wallet
        profile.setWalletBalance(profile.getWalletBalance().subtract(amount));
        investorProfileRepository.save(profile);

        // Update or create portfolio holding
        PortfolioHolding holding = holdingRepository
                .findByInvestorIdAndMutualFundId(userId, fund.getId())
                .orElse(PortfolioHolding.builder()
                        .investor(user)
                        .mutualFund(fund)
                        .unitsOwned(BigDecimal.ZERO)
                        .averageBuyPrice(BigDecimal.ZERO)
                        .build());

        // Weighted average price
        BigDecimal totalOldValue = holding.getUnitsOwned().multiply(holding.getAverageBuyPrice());
        BigDecimal totalNewValue = units.multiply(nav);
        BigDecimal totalUnits = holding.getUnitsOwned().add(units);

        if (totalUnits.compareTo(BigDecimal.ZERO) > 0) {
            holding.setAverageBuyPrice(
                    totalOldValue.add(totalNewValue).divide(totalUnits, 4, RoundingMode.HALF_UP));
        }
        holding.setUnitsOwned(totalUnits);
        holdingRepository.save(holding);

        // Record transaction
        Transaction transaction = Transaction.builder()
                .user(user)
                .type(Transaction.TransactionType.BUY)
                .amount(amount)
                .status(Transaction.TransactionStatus.SUCCESS)
                .referenceId("FUND-" + fund.getId())
                .description("Bought " + units.setScale(4, RoundingMode.HALF_UP) + " units of " + fund.getFundName())
                .build();

        return transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public Transaction sellFund(Long userId, TransactionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        InvestorProfile profile = investorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Investor profile not found"));

        MutualFund fund = fundRepository.findById(request.getFundId())
                .orElseThrow(() -> new RuntimeException("Fund not found"));

        PortfolioHolding holding = holdingRepository
                .findByInvestorIdAndMutualFundId(userId, fund.getId())
                .orElseThrow(() -> new RuntimeException("No holdings found for this fund"));

        BigDecimal amount = request.getAmount();
        BigDecimal nav = fund.getCurrentNav();
        BigDecimal unitsToSell = amount.divide(nav, 4, RoundingMode.HALF_UP);

        // Check if user has enough units
        if (holding.getUnitsOwned().compareTo(unitsToSell) < 0) {
            throw new RuntimeException("Insufficient units. You own: " + holding.getUnitsOwned());
        }

        // Credit wallet
        profile.setWalletBalance(profile.getWalletBalance().add(amount));
        investorProfileRepository.save(profile);

        // Update holding
        holding.setUnitsOwned(holding.getUnitsOwned().subtract(unitsToSell));
        if (holding.getUnitsOwned().compareTo(BigDecimal.ZERO) == 0) {
            holdingRepository.delete(holding);
        } else {
            holdingRepository.save(holding);
        }

        // Record transaction
        Transaction transaction = Transaction.builder()
                .user(user)
                .type(Transaction.TransactionType.SELL)
                .amount(amount)
                .status(Transaction.TransactionStatus.SUCCESS)
                .referenceId("FUND-" + fund.getId())
                .description("Sold " + unitsToSell.setScale(4, RoundingMode.HALF_UP) + " units of " + fund.getFundName())
                .build();

        return transactionRepository.save(transaction);
    }

    @Override
    public List<Transaction> getTransactionHistory(Long userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<PortfolioHolding> getPortfolio(Long userId) {
        return holdingRepository.findByInvestorId(userId);
    }
}
