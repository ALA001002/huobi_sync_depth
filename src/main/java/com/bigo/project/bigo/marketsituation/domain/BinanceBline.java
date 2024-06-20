package com.bigo.project.bigo.marketsituation.domain;

import lombok.Data;

import java.util.Date;

@Data
public class BinanceBline {

    private Long id;

    private String price;

    private String qty;

    private String quoteQty;

    private Long time;

    private Boolean isBuyerMaker;

    private Boolean isBestMatch;
}
