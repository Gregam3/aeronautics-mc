package com.example.createstockexchange.data;

public enum StockType {
    PLAYER,          // player-owned; price driven by bank balance delta (EMA)
    SERVER_RANDOM,   // server-owned; price driven by random walk
    SERVER_BUSINESS  // server-owned; price driven by in-world vendor sales (EMA)
}
