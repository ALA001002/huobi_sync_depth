package com.cyssxt.huobisync.controller;

import com.bigo.project.bigo.marketsituation.domain.Kline;
import com.bigo.project.bigo.marketsituation.domain.RandomConfig;
import com.cyssxt.huobisync.repository.KlineRepository;
import com.cyssxt.huobisync.service.RandomService;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping(value="/kline")
public class KlineController {

    @Resource
    KlineRepository klineRepository;

    @Resource
    RandomService randomService;

    @RequestMapping(value="query")
    public List<Kline> queryKline(String symbol,String period){
        return klineRepository.queryBySymbolAndPeriod(symbol,period, Sort.by(Sort.Order.asc("timestamp")));
    }

    @RequestMapping(value="save")
    public RandomConfig save(@RequestBody RandomConfig randomConfig){
        return randomService.save(randomConfig);
    }
    @RequestMapping(value="info/{symbol}")
    public RandomConfig info(@PathVariable String symbol){
        return randomService.info(symbol);
    }
}
