package com.cyssxt.huobisync.service.task;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bigo.project.bigo.marketsituation.domain.BgSlipConfig;
import com.bigo.project.bigo.marketsituation.domain.BinanceBline;
import com.bigo.project.bigo.marketsituation.domain.Kline;
import com.cyssxt.huobisync.constant.SymbolEnum;
import com.cyssxt.huobisync.service.BgSlipConfigService;
import com.cyssxt.huobisync.view.HbKlineResult;
import com.cyssxt.huobisync.util.HttpClientUtil;
import com.cyssxt.huobisync.constant.CandlestickEnum;
import com.cyssxt.huobisync.repository.KlineRepository;
import com.cyssxt.huobisync.service.MarketService;
import com.cyssxt.huobisync.service.RedisCache;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class SyncKlineTask implements Runnable {
    private String symbol;
    private CandlestickEnum period;
    private MarketService marketService;
    private RedisCache redisCache;
    private KlineRepository klineRepository;
    private BgSlipConfigService bgSlipConfigService;
//    private static final String klineUrl = "https://api.huobi.pro/market/history/kline?size=100&symbol=";
//    private static final String klineUrl = "https://api.huobi.de.com/market/history/kline?size=100&symbol=";
    private String url;
    public SyncKlineTask(String symbol, CandlestickEnum period, MarketService marketService, RedisCache redisCache, KlineRepository klineRepository, BgSlipConfigService bgSlipConfigService) {
//        this.url = marketService.getKlineUrl() + symbol + "&period=" + period.getCode();
        this.url = marketService.getKlineUrl() + symbol.toUpperCase() + "&interval=" + period.getBinanceInterval();
        this.symbol = symbol;
        this.period = period;
        this.marketService = marketService;
        this.redisCache = redisCache;
        this.klineRepository = klineRepository;
        this.bgSlipConfigService = bgSlipConfigService;
    }

    @Override
    public void run() {
        log.info("SyncKlineTask symbol={},url={}",symbol,url);
        String resultJson = HttpClientUtil.get(url);
        log.info("SyncKlineTask symbol={},url={} end",symbol,url);
        try {
            /*HbKlineResult result = JSONObject.toJavaObject(JSON.parseObject(resultJson), HbKlineResult.class);
            if (result == null || !"ok".equals(result.getStatus())) {
                log.error("请求K线数据失败，url：{}，返回报文：{}", url, resultJson);
                return;
            }
            List<Kline> list = result.getData();*/
            if (resultJson == null || "code".contains(resultJson)) {
                log.error("请求线数据失败，url：{}，返回报文：{}", url, resultJson);
                return;
            }
            JSONArray array = JSONArray.parseArray(resultJson);
            if (array.size() == 0) {
                log.error("请求K线数据返回空数据：array:{}", array);
                return;
            }
            List<Kline> list = new ArrayList<>();
            Kline resultKline = null;
            for (int i = 0; i < array.size(); i++) {
                resultKline = new Kline();
                JSONArray object = array.getJSONArray(i);
                resultKline.setId((Long) object.get(0));
                resultKline.setOpen(new BigDecimal((String)object.get(1)));
                resultKline.setHigh(new BigDecimal((String)object.get(2)));
                resultKline.setLow(new BigDecimal((String)object.get(3)));
                resultKline.setClose(new BigDecimal((String)object.get(4)));
                resultKline.setAmount(new BigDecimal((String)object.get(5)));
                resultKline.setVol(new BigDecimal((String)object.get(7)));
                resultKline.setCount(Long.valueOf((Integer)object.get(8)));

                resultKline.setTimestamp(resultKline.getId());
                resultKline.setSymbol(symbol);
                resultKline.setPeriod(period.getCode());

                list.add(resultKline);
            }
            list = list.stream().sorted(Comparator.comparing(Kline::getId).reversed()).collect(Collectors.toList());
//            for (Kline kline : list) {
//                kline.setTimestamp(kline.getId());
//                kline.setSymbol(symbol);
//                kline.setPeriod(period.getCode());
////                bgSlipConfigService.calcPrice(kline, symbol);
//            }
            Kline klineQuery = new Kline();
            klineQuery.setSymbol(symbol);
            klineQuery.setPeriod(period.getCode());
            Long lastKid = marketService.getMaxTimestampBySymbolAndPeriod(klineQuery);
            //记录今天的交易数据，最高价，最低价，交易量
            if (period.getCode().equals(CandlestickEnum.DAY1.getCode())) {
                //天粒度，第一条数据就是今天的数据
                Kline todayKline = list.get(0);
                redisCache.setCacheObject(symbol + "_today_kline", todayKline);
            }
            //已经写入数据库的数据过滤掉
            if (lastKid != null) {
            /*    if(symbol.equals(SymbolEnum.BIXBTC.getCode()) || symbol.equals(SymbolEnum.BIXETH.getCode()) || symbol.equals(SymbolEnum.BIXUSDT.getCode())) {
                    list = list.stream().filter(a -> a.getTimestamp() > lastKid && a.getTimestamp() >= marketService.getStartTime()).collect(Collectors.toList());
                } else {
                }*/
                    list = list.stream().filter(a -> a.getTimestamp() > lastKid).collect(Collectors.toList());
            }

            if (list.size() > 1) {
                //最新的一条数据不入库，因为收还没确定
                Kline lastKline = list.get(0);
                list.remove(0);
                //火币返回的数据是根据时间戳倒序排列的，存入数据库需要重新排序
                list.sort((a, b) -> (int) (a.getTimestamp() - b.getTimestamp()));
//                marketService.dealSlipDotBeforeInsert(list, period);
                klineRepository.saveAll(list);
                log.info("kline-save={}",list.size());
                list = klineRepository.queryTopSize(symbol,period.getCode(),499);
                list.add(0, lastKline);
                redisCache.deleteObject(symbol + period.getCode());
                redisCache.setCacheObject(symbol + period.getCode(), list);
                redisCache.setCacheObject(symbol + "_" + period.getCode() + "_max_ts", list.get(1).getTimestamp());
            }
        }catch (Exception e){
            log.error("e={}",e);
        }
    }

    public static void main(String[] args) {
        String result = "[{\"id\":1291614858,\"price\":\"39033.79000000\",\"qty\":\"0.00435000\",\"quoteQty\":\"169.79698650\",\"time\":1647244324279,\"isBuyerMaker\":false,\"isBestMatch\":true}]";
        List<BinanceBline> blineList = JSONArray.parseArray(result, BinanceBline.class);
        JSONArray array = JSONArray.parseArray(result);
        List<Kline> klineList = new ArrayList<>();
        Kline kline = null;
        for (int i = 0; i < array.size(); i++) {
            kline = new Kline();
            JSONArray object = array.getJSONArray(i);
            kline.setId((Long) object.get(0));
            kline.setOpen(new BigDecimal((String)object.get(1)));
            kline.setHigh(new BigDecimal((String)object.get(2)));
            kline.setLow(new BigDecimal((String)object.get(3)));
            kline.setClose(new BigDecimal((String)object.get(4)));
            kline.setAmount(new BigDecimal((String)object.get(5)));
            kline.setVol(new BigDecimal((String)object.get(7)));
            kline.setCount(Long.valueOf((Integer)object.get(8)));
            klineList.add(kline);
        }

    }
}
