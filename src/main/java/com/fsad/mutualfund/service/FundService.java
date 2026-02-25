package com.fsad.mutualfund.service;

import com.fsad.mutualfund.dto.FundDetailResponse;
import com.fsad.mutualfund.dto.FundResponse;
import com.fsad.mutualfund.entity.MutualFund;

import java.util.List;

public interface FundService {
    List<FundResponse> getAllFunds(String category, Integer maxRisk);
    FundDetailResponse getFundDetail(Long fundId);
    FundResponse createFund(MutualFund fund);
    FundResponse updateFund(Long fundId, MutualFund fund);
    void deleteFund(Long fundId);
}
