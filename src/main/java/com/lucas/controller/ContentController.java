package com.lucas.controller;


import com.lucas.service.ContentService;
import org.elasticsearch.client.RestHighLevelClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ContentController {
    @Autowired
    private ContentService contentService;
    @Qualifier("restHighLevelClient")
    @Autowired
    private RestHighLevelClient client;

    //把获取的数据放的es中
    @GetMapping("parse/{keyword}")
    public String parse(@PathVariable("keyword") String keyword)throws Exception{
        Boolean aBoolean = contentService.parseContent(keyword);
        System.out.println(aBoolean);
        return aBoolean.toString();

    }
    //获取数据实现搜索功能
    @GetMapping("/search/{keyword}/{pageNo}/{pageSize}")
    public List<Map<String,Object>> search(@PathVariable("keyword") String keyword,
                                           @PathVariable("pageNo")int pageNo,
                                           @PathVariable("pageSize")int pageSize)throws Exception{
        List<Map<String, Object>> list = contentService.searchHighlighter(keyword, pageNo, pageSize);
        return list;
    }



}
