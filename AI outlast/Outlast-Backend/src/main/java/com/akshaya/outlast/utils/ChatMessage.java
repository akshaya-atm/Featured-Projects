package com.akshaya.outlast.utils;

import com.akshaya.outlast.model.Player;

public record ChatMessage(Player from, Player to, String message)  {}
