package com.fsad.mutualfund.dto;

import lombok.Data;
import java.util.List;

@Data
public class RiskQuestionnaireRequest {
    private List<Integer> answers; // Score values for each question (1-5)
}
