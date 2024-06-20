package com.cyssxt.huobisync.service;

import com.bigo.project.bigo.marketsituation.domain.Bline;
import com.bigo.project.bigo.marketsituation.domain.Kline;
import com.bigo.project.bigo.marketsituation.domain.SlipDot;
import com.cyssxt.huobisync.constant.CandlestickEnum;
import com.cyssxt.huobisync.repository.BlineRepository;
import com.cyssxt.huobisync.repository.KlineRepository;
import com.cyssxt.huobisync.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class MarketService {
    @Resource
    KlineRepository klineRepository;

    @Resource
    BlineRepository blineRepository;

    @Resource
    RedisCache redisCache;

    @Resource
    SlipDotService slipDotService;

    @Value("${huobi.bline.url}")
    private String blineUrl;

    @Value("${huobi.kline.url}")
    private String klineUrl;

    @Value("${huobi.deep.url}")
    private String deepUrl;


    @Value("${filter.startTime}")
    private Long startTime;

    public Long getStartTime() {
        return Optional.ofNullable(startTime).orElse(0L);
    }

    public String getBlineUrl() {
        return blineUrl;
    }

    public String getKlineUrl() {
        return klineUrl;
    }

    public String getDeepUrl() {
        return deepUrl;
    }

    public Long getMaxTradeIdBySymbol(String symbol) {
        Object cache = redisCache.getCacheObject(symbol + "_max_trade_id");
        if (cache == null) {
            return blineRepository.queryMaxTradeId(symbol);
        } else {
            return Long.valueOf(cache.toString());
        }
    }

    public void calContractInfo(String symbol, Bline bline) {
        log.info("calContractInfo ={},{}",symbol + "_price",bline.getPrice());
        redisCache.setCacheObject(symbol + "_price", bline.getPrice());
        redisCache.setCacheObject(symbol + "_max_trade_id", bline.getTradeId());
    }

    public void calKlineLastPoint(String symbol, Bline bline) {
        for (CandlestickEnum period : CandlestickEnum.values()) {
            String klineKey = symbol + period.getCode();
            List<Kline> klineList = redisCache.getCacheObject(klineKey);
            if (CollectionUtils.isEmpty(klineList)) {
                return;
            }
            Kline lastPoint = klineList.get(0);
            lastPoint.setClose(bline.getPrice());
            //如果b线的价格大于最后一个k线的高点，则将K线高点设为b线的价格
            if (bline.getPrice().compareTo(lastPoint.getHigh()) > 0) {
                lastPoint.setHigh(bline.getPrice());
            }
            //如果b线的价格小于于最后一个k线的低点，则将K线低点设为b线的价格
            if (bline.getPrice().compareTo(lastPoint.getLow()) < 0) {
                lastPoint.setLow(bline.getPrice());
            }
            redisCache.setCacheObject(klineKey, klineList);
        }
    }

    public Long getMaxTimestampBySymbolAndPeriod(Kline klineQuery) {
        String symbol = klineQuery.getSymbol();
        String period = klineQuery.getPeriod();
        String key = symbol + "_" + period + "_max_ts";
        Object cache = redisCache.getCacheObject(key);
        if (cache == null) {
            return klineRepository.getMaxTimestampBySymbolAndPeriod(symbol,period);
        } else {
            return Long.valueOf(cache.toString());
        }
    }
    private Date getEndTimeByPeriod(Date startTime, CandlestickEnum period) {
        Date endTime;
        switch (period) {
            case MIN1:
                endTime = DateUtils.addMinutes(startTime, 1);
                break;
            case MIN5:
                endTime = DateUtils.addMinutes(startTime, 5);
                break;
            case MIN15:
                endTime = DateUtils.addMinutes(startTime, 15);
                break;
            case MIN30:
                endTime = DateUtils.addMinutes(startTime, 30);
                break;
            case MIN60:
                endTime = DateUtils.addMinutes(startTime, 60);
                break;
            case HOUR4:
                endTime = DateUtils.addHours(startTime, 4);
                break;
            case DAY1:
                endTime = DateUtils.addDays(startTime, 1);
                break;
            case WEEK1:
                endTime = DateUtils.addDays(startTime, 7);
                break;
            case MON1:
                endTime = DateUtils.addMonths(startTime, 1);
                break;
            default:
                endTime = startTime;
        }
        return endTime;
    }


    public void dealSlipDotBeforeInsert(List<Kline> klineList, CandlestickEnum period) {
        for (Kline kline : klineList) {
            Date startTime = new Date(kline.getTimestamp() * 1000);
            Date endTime = getEndTimeByPeriod(startTime, period);
            List<SlipDot> slipDotList = slipDotService.listSlipDotByTime(startTime, endTime, kline.getSymbol());
            if (CollectionUtils.isEmpty(slipDotList)) {
                continue;
            }
            BigDecimal low = kline.getLow();
            BigDecimal high = kline.getHigh();
            for (SlipDot dot : slipDotList) {
                //如果滑点开始时间早于k线开始时间，则开要加上滑点
                if (dot.getStartDotTime().before(startTime)) {
                    kline.setRealOpen(kline.getOpen());
                    kline.setOpen(kline.getOpen().add(dot.getAdjustPrice()));
                }
                //如果滑点结束时间晚于于k线结束时间，则收要加上滑点
                if (dot.getStopDotTime() == null || dot.getStopDotTime().after(endTime)) {
                    kline.setRealClose(kline.getClose());
                    kline.setClose(kline.getClose().add(dot.getAdjustPrice()));
                }
                List<Bline> blineList = blineRepository.listByTime(startTime.getTime(), endTime.getTime(), kline.getSymbol());
                Boolean upSlip = dot.getAdjustPrice().compareTo(BigDecimal.ZERO) >= 0;
                for (Bline bline : blineList) {
                    if (upSlip) {
                        if (bline.getPrice().compareTo(high) > 0) {
                            high = bline.getPrice();
                        }
                        if (bline.getRealPrice().compareTo(low) < 1) {
                            low = bline.getPrice();
                        }
                    } else {
                        if (bline.getRealPrice().compareTo(high) >= 0) {
                            high = bline.getPrice();
                        }
                        if (bline.getPrice().compareTo(low) < 1) {
                            low = bline.getPrice();
                        }
                    }
                }
            }
            kline.setRealHigh(kline.getHigh());
            kline.setHigh(high);
            kline.setRealLow(kline.getLow());
            kline.setLow(low);
        }
    }
}
