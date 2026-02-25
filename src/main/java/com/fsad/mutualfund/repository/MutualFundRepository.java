package com.fsad.mutualfund.repository;

import com.fsad.mutualfund.entity.MutualFund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MutualFundRepository extends JpaRepository<MutualFund, Long> {
    List<MutualFund> findByCategory(MutualFund.Category category);

    List<MutualFund> findByRiskRatingLessThanEqual(int maxRisk);

    List<MutualFund> findByCategoryAndRiskRatingLessThanEqual(MutualFund.Category category, int maxRisk);

    Optional<MutualFund> findByTickerSymbol(String tickerSymbol);

    boolean existsByFundName(String fundName);
}
