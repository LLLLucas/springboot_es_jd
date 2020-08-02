package com.lucas.service;

import com.alibaba.fastjson.JSON;
import com.lucas.pojo.Content;
import com.lucas.utils.HtmlParseUtils;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ContentService {
    @Qualifier("restHighLevelClient")
    @Autowired
    private RestHighLevelClient client;

    //1、解析数据放入es索引中
    public Boolean parseContent(String keyword)throws Exception{
        List<Content> contents = new HtmlParseUtils().parseJD(keyword);
        //查询出来的数据放入到es中
        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.timeout("10s");
        //批量处理请求
        for (int i = 0; i < contents.size(); i++) {
            bulkRequest.add(
                    new IndexRequest("jingdong_goods")
                            .id(""+(i+1))
                            .source(JSON.toJSONString(contents.get(i)), XContentType.JSON));
        }
        BulkResponse bulk = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        System.out.println(bulk.hasFailures()); //是否失败，如果false就是成功了
        return !bulk.hasFailures();
    }

    //获取数据
    public List<Map<String,Object>> searchPage(String keyword, int pageNo, int pageSize)throws Exception{
        if(pageNo<=0){
            pageNo=1;
        }
        //条件搜索
        SearchRequest searchRequest = new SearchRequest("jingdong_goods");
        SearchSourceBuilder searchSourceBuilder=new SearchSourceBuilder();
        //分页
        searchSourceBuilder.from(pageNo);
        searchSourceBuilder.size(pageSize);

        //精准匹配
        TermQueryBuilder builder = QueryBuilders.termQuery("title", keyword);
        searchSourceBuilder.query(builder);
        searchSourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));

        //执行搜索
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        List<Map<String,Object>> list=new ArrayList<>();
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Map<String, Object> sourceAsMap = hit.getSourceAsMap();
            list.add(sourceAsMap);

        }
        return list;

    }

    //获取数据，高亮搜索
    public List<Map<String,Object>> searchHighlighter(String keyword, int pageNo, int pageSize)throws Exception{
        if(pageNo<=0){
            pageNo=1;
        }
        //条件搜索
        SearchRequest searchRequest = new SearchRequest("jingdong_goods");
        SearchSourceBuilder searchSourceBuilder=new SearchSourceBuilder();
        //分页
        searchSourceBuilder.from(pageNo);
        searchSourceBuilder.size(pageSize);

        //精准匹配
        TermQueryBuilder builder = QueryBuilders.termQuery("title", keyword);
        searchSourceBuilder.query(builder);
        searchSourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));

        //高亮
        HighlightBuilder highlightBuilder=new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.requireFieldMatch(false);  //多个高亮显示关闭!
        highlightBuilder.preTags("<span style='color:red'>");
        highlightBuilder.postTags("</span>");
        searchSourceBuilder.highlighter(highlightBuilder);

        //执行搜索
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        List<Map<String,Object>> list=new ArrayList<>();
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            //原来的结果
            Map<String, Object> sourceAsMap = hit.getSourceAsMap();
            //高亮的部分
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
            HighlightField title = highlightFields.get("title");
            //解析高亮字段,将原来的字段换成高亮字段
            if (title!=null){
                Text[] fragments = title.fragments();
                String new_name="";
                for (Text fragment : fragments) {
                    new_name += fragment;//将高亮显示的内容拆分后组装到new_name
                }
                sourceAsMap.put("title",new_name);//替换原来的内容

            }

            list.add(sourceAsMap);

        }
        return list;

    }
}
