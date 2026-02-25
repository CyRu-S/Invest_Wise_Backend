package com.fsad.mutualfund.repository;

import com.fsad.mutualfund.entity.NavHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface NavHistoryRepository extends JpaRepository<NavHistory, Long> {
    List<NavHistory> findByMutualFundIdOrderByNavDateAsc(Long fundId);

    List<NavHistory> findByMutualFundIdAndNavDateBetweenOrderByNavDateAsc(Long fundId, LocalDate start, LocalDate end);

    void deleteByMutualFundId(Long fundId);
}
