package com.caCommand.caCommand.models;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ItrRecommendation {
    private String recommendedItr;
    private int confidence;
    private List<String> reasons = new ArrayList<>();
}
