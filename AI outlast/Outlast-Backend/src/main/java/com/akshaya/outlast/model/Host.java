package com.akshaya.outlast.model;

import com.akshaya.outlast.utils.PersonalityType;

public record Host(
    String name,
    String modelId,
    PersonalityType personalityType
) {}
