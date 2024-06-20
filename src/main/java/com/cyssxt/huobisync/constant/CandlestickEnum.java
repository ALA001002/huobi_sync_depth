package com.cyssxt.huobisync.constant;


import java.util.Calendar;

/**
 * 行情粒度：1min, 5min, 15min, 30min, 60min, 1day, 1mon, 1week, 1year
 * @author Administrator
 */
public enum CandlestickEnum {
    /**
     * 1分钟
     */
    MIN1("1min", Calendar.MINUTE,1,"1m"),
    /**
     * 5分钟
     */
    MIN5("5min", Calendar.MINUTE,1,"5m"),
    /**
     * 15分钟
     */
    MIN15("15min", Calendar.MINUTE,5, "15m"),
    /**
     * 30分钟
     */
    MIN30("30min", Calendar.MINUTE,15,"30m"),
    /**
     * 60分钟
     */
    MIN60("60min", Calendar.MINUTE,10, "1h"),
    /**
     * 4小时
     */
    HOUR4("4hour", Calendar.MINUTE,10,"4h"),
    /**
     * 1天
     */
    DAY1("1day", Calendar.MINUTE,10,"1d"),
    /**
     * 1周
     */
    WEEK1("1week", Calendar.MINUTE,10,"1w"),
    /**
     * 1个月
     */
    MON1("1mon", Calendar.MINUTE,180,"1M"),
    /**
     * 1年
     */
    //YEAR1("1year"),
    ;

    private final String code;
    private final int type;
    private final int interval;
    private final String binanceInterval;

    CandlestickEnum(String code, int type,int interval, String binanceInterval) {
        this.code = code;
        this.type = type;
        this.interval = interval;
        this.binanceInterval = binanceInterval;
    }

    public int getType() {
        return type;
    }

    public int getInterval() {
        return interval;
    }

    public String getCode(){
        return code;
    }

    public String getBinanceInterval() {
        return binanceInterval;
    }
}
