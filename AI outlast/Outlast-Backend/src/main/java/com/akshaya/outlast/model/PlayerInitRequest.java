package com.akshaya.outlast.model;

import com.akshaya.outlast.utils.Gender;

public record PlayerInitRequest(
    String name,
    Gender gender
) {}
