package br.ufs.stockmarket.market.domain;

public enum OrderStatus {
    PENDING,
    OPEN,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED
}
