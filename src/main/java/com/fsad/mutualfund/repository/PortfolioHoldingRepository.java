package com.fsad.mutualfund.repository;

import com.fsad.mutualfund.entity.PortfolioHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, Long> {
    List<PortfolioHolding> findByInvestorId(Long investorId);
    Optional<PortfolioHolding> findByInvestorIdAndMutualFundId(Long investorId, Long fundId);
}
