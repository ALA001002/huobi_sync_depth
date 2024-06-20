package com.cyssxt.huobisync.service.task;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bigo.project.bigo.marketsituation.domain.Deep;
import com.bigo.project.bigo.marketsituation.domain.DeepAsks;
import com.bigo.project.bigo.marketsituation.domain.DeepBids;
import com.cyssxt.huobisync.service.MarketService;
import com.cyssxt.huobisync.service.RedisCache;
import com.cyssxt.huobisync.util.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SyncDeepTask implements Runnable{

    String symbolCode;
    RedisCache redisCache;
    MarketService marketService;

    private String url;
    public SyncDeepTask(String symbolCode, RedisCache redisCache, MarketService marketService) {
        this.symbolCode = symbolCode;
        this.redisCache = redisCache;
        this.marketService = marketService;
        this.url = marketService.getDeepUrl() + symbolCode.toLowerCase();
    }

    @Override
    public void run() {
        try {
            log.info("SyncDeepTask symbol={},url={}",symbolCode,url);
            String resultJson = HttpClientUtil.get(url);
            log.info("SyncDeepTask symbol={},url={} end",symbolCode,url);

            if (resultJson == null || "code".contains(resultJson)) {
                log.error("请求深度信息数据失败，url：{}，返回报文：{}", url, resultJson);
                return;
            }
            Deep deep = new Deep();
            JSONObject jsonObject = JSONObject.parseObject(resultJson);
            jsonObject = jsonObject.getJSONObject("tick");
            Long lastUpdateId = jsonObject.getLong("version");
            deep.setLastUpdateId(lastUpdateId);// id

            JSONArray bidsArray = jsonObject.getJSONArray("bids");
            List<DeepBids> bidsList = new ArrayList<>();
            for (int i = 0; i < bidsArray.size(); i++) {
                List<BigDecimal> paramList = (List<BigDecimal>) JSONArray.toJSON(bidsArray.getJSONArray(i));
                DeepBids bids = new DeepBids();
                bids.setPrice(paramList.get(0).setScale(8,BigDecimal.ROUND_HALF_UP));
                bids.setPendOrderVolume(paramList.get(1).setScale(8,BigDecimal.ROUND_HALF_UP));
                bidsList.add(bids);
            }
            deep.setBidsList(bidsList);

            JSONArray asksArray = jsonObject.getJSONArray("asks");
            List<DeepAsks> asksList = new ArrayList<>();
            for (int i = 0; i < asksArray.size(); i++) {
                List<BigDecimal> paramList = (List<BigDecimal>) JSONArray.toJSON(asksArray.getJSONArray(i));
                DeepAsks asks = new DeepAsks();
                asks.setPrice(paramList.get(0).setScale(8,BigDecimal.ROUND_HALF_UP));
                asks.setPendOrderVolume(paramList.get(1).setScale(8,BigDecimal.ROUND_HALF_UP));
                asksList.add(asks);
            }
            deep.setAsksList(asksList);
            if(deep !=null && deep.getLastUpdateId() != null) redisCache.setCacheObject(symbolCode+"_deep", deep);
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("请求深度信息数据失败，url：{}，错误信息：{}", url, ex.getMessage(), ex);
        }
    }

    public static void main(String[] args) {
        String result = "{\"ch\":\"market.btcusdt.depth.step0\",\"status\":\"ok\",\"ts\":1673862447288,\"tick\":{\"ts\":1673862446725,\"version\":161635748109,\"bids\":[[20793.63,0.046654],[20792.17,0.2794],[20791.89,0.15],[20791.61,0.15],[20791.58,0.692151],[20790.81,0.15],[20790.78,0.15],[20790.77,0.15],[20790.76,0.15],[20790.06,0.15]],\"asks\":[[20793.64,0.462436],[20794.42,0.15],[20794.43,0.15],[20794.44,0.0542],[20794.45,0.008083],[20795.07,0.025832],[20795.49,0.024914],[20796.04,0.05],[20796.05,8.542194],[20796.53,0.011455]]}}";
//        List<DeepResult> list = JSONArray.parseArray(i, DeepResult.class);
//        System.out.println(list);
//
        JSONObject jsonObject = JSONObject.parseObject(result);
        jsonObject = jsonObject.getJSONObject("tick");
        Long lastUpdateId = jsonObject.getLong("version");
        JSONArray bidsArray = jsonObject.getJSONArray("bids");
        Deep deep = new Deep();
        List<DeepBids> bidsList = new ArrayList<>();
        for (int i = 0; i < bidsArray.size(); i++) {
            List<BigDecimal> paramList = (List<BigDecimal>) JSONArray.toJSON(bidsArray.getJSONArray(i));
            DeepBids bids = new DeepBids();
            bids.setPrice(paramList.get(0));
            bids.setPendOrderVolume(paramList.get(1));
            bidsList.add(bids);
        }
        deep.setBidsList(bidsList);

        JSONArray asksArray = jsonObject.getJSONArray("asks");
        List<DeepAsks> asksList = new ArrayList<>();
        for (int i = 0; i < asksArray.size(); i++) {
            List<BigDecimal> paramList = (List<BigDecimal>) JSONArray.toJSON(asksArray.getJSONArray(i));
            DeepAsks asks = new DeepAsks();
            asks.setPrice(paramList.get(0));
            asks.setPendOrderVolume(paramList.get(1));
            asksList.add(asks);
        }
        deep.setAsksList(asksList);
        System.out.println(jsonObject);

    }
}
