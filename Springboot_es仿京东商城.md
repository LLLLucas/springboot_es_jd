# Springboot_es仿京东商城

1、创建Springboot项目，pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.2.5.RELEASE</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>com.lucas</groupId>
    <artifactId>springboot_es_jd</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>springboot_es_jd</name>
    <description>Demo project for Spring Boot</description>

    <properties>
        <java.version>1.8</java.version>
        <elasticsearch.version>7.8.0</elasticsearch.version>

    </properties>

    <dependencies>
        <!--jsoup解析网页的,用来爬虫，获取京东商城的数据-->
        <dependency>
            <groupId>org.jsoup</groupId>
            <artifactId>jsoup</artifactId>
            <version>1.10.2</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>fastjson</artifactId>
            <version>1.2.55</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
            <exclusions>
                <exclusion>
                    <groupId>org.junit.vintage</groupId>
                    <artifactId>junit-vintage-engine</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>

```

2、application.properties配置

```properties
server.port=9090
# 关闭thymeleaf缓存
spring.thymeleaf.cache=false
```

3、导入static下的img，js，css，以及templates下的index.html，这些都要在项目目录下寻找

4、es配置注入到容器中，ElasticSearchConfig.java

```java
@Configuration
public class ElasticSearchConfig {
    //注入es到spring
    @Bean
    public RestHighLevelClient restHighLevelClient(){
        RestHighLevelClient client = new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http")));
        return client;
    }
}

```

5、HtmlParseUtils.java用来解析网页来获取数据

```java
package com.lucas.utils;

public class HtmlParseUtils {
    public static void main(String[] args)throws Exception {
        new HtmlParseUtils().parseJD("java").forEach(System.out::println);
    }


    public List<Content> parseJD(String keywords)throws Exception{

        ArrayList<Content> contents = new ArrayList<>();

        //获取请求，https://search.jd.com/Search?keyword=java
        //前提需要联网，ajax不能获取到
        String url="https://search.jd.com/Search?keyword="+keywords;
        //解析网页（jsoup返回document就是浏览器document对象）
        Document document = Jsoup.parse(new URL(url), 30000);
        //所有在js中使用的方法，在这里都能用
        Element element = document.getElementById("J_goodsList");
        //获取所有的li列元素
        Elements li = element.getElementsByTag("li");
        //获取元素中的内容，这里的el就是每一个列标签
        for (Element element1 : li) {

            //关于这图片特别多的网站，都是延迟加载的
            //source-data-lazy-img

            String img = element1.getElementsByTag("img").eq(0).attr("src");
            String price = element1.getElementsByClass("p-price").eq(0).text();
            String title = element1.getElementsByClass("p-name").eq(0).text();

            Content content = new Content();
            content.setImg(img);
            content.setPrice(price);
            content.setTitle(title);
            contents.add(content);

        }
        return contents;
    }
}

```

6、IndexController.java

```java
@Controller
public class IndexController {

    @GetMapping({"/","/index"})
    public String index(){
        return "index";
    }
}

```

7、Content.java

```java

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Content {
    private String title;
    private String img;
    private String price;

}

```

8、ContentService.java

```java
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
```

9、ContentController.java

```java
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
```

10.index.html

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">

<head>
    <meta charset="utf-8"/>
    <title>狂神说Java-ES仿京东实战</title>
    <link rel="stylesheet" th:href="@{/css/style.css}"/>

</head>

<body class="pg">
<div class="page" id="app">
    <div id="mallPage" class=" mallist tmall- page-not-market ">

        <!-- 头部搜索 -->
        <div id="header" class=" header-list-app">
            <div class="headerLayout">
                <div class="headerCon ">
                    <!-- Logo-->
                    <h1 id="mallLogo">
                        <img th:src="@{/images/jdlogo.png}" alt="">
                    </h1>

                    <div class="header-extra">

                        <!--搜索-->
                        <div id="mallSearch" class="mall-search">
                            <form name="searchTop" class="mallSearch-form clearfix">
                                <fieldset>
                                    <legend>天猫搜索</legend>
                                    <div class="mallSearch-input clearfix">
                                        <div class="s-combobox" id="s-combobox-685">
                                            <div class="s-combobox-input-wrap">
                                                <input v-model="keyword" type="text" autocomplete="off" value="dd" id="mq"
                                                       class="s-combobox-input" aria-haspopup="true">
                                            </div>
                                        </div>
                                        <button @click.prevent="searchKey" type="submit" id="searchbtn">搜索</button>
                                    </div>
                                </fieldset>
                            </form>
                            <ul class="relKeyTop">
                                <li><a>狂神说Java</a></li>
                                <li><a>狂神说前端</a></li>
                                <li><a>狂神说Linux</a></li>
                                <li><a>狂神说大数据</a></li>
                                <li><a>狂神聊理财</a></li>
                            </ul>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 商品详情页面 -->
        <div id="content">
            <div class="main">
                <!-- 品牌分类 -->
                <form class="navAttrsForm">
                    <div class="attrs j_NavAttrs" style="display:block">
                        <div class="brandAttr j_nav_brand">
                            <div class="j_Brand attr">
                                <div class="attrKey">
                                    品牌
                                </div>
                                <div class="attrValues">
                                    <ul class="av-collapse row-2">
                                        <li><a href="#"> 狂神说 </a></li>
                                        <li><a href="#"> Java </a></li>
                                    </ul>
                                </div>
                            </div>
                        </div>
                    </div>
                </form>

                <!-- 排序规则 -->
                <div class="filter clearfix">
                    <a class="fSort fSort-cur">综合<i class="f-ico-arrow-d"></i></a>
                    <a class="fSort">人气<i class="f-ico-arrow-d"></i></a>
                    <a class="fSort">新品<i class="f-ico-arrow-d"></i></a>
                    <a class="fSort">销量<i class="f-ico-arrow-d"></i></a>
                    <a class="fSort">价格<i class="f-ico-triangle-mt"></i><i class="f-ico-triangle-mb"></i></a>
                </div>

                <!-- 商品详情 -->
                <div class="view grid-nosku">

                    <div class="product" v-for="result in results">
                        <div class="product-iWrap">
                            <!--商品封面-->
                            <div class="productImg-wrap">
                                <a class="productImg">
                                    <img :src="result.img">
                                </a>
                            </div>
                            <!--价格-->
                            <p class="productPrice">
                                <em>{{result.price}}</em>
                            </p>
                            <!--标题-->
                            <p class="productTitle">
                                <a v-html="result.title">  </a>
                            </p>
                            <!-- 店铺名 -->
                            <div class="productShop">
                                <span>店铺： 狂神说Java </span>
                            </div>
                            <!-- 成交信息 -->
                            <p class="productStatus">
                                <span>月成交<em>999笔</em></span>
                                <span>评价 <a>3</a></span>
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<!--使用前后端实现前后端分离-->
<script th:src="@{/js/axios.min.js}"></script>

<script th:src="@{/js/vue.min.js}"></script>
<script>
    new Vue({
        el:'#app',
        data:{
            keyword:'', //搜索关键字
            results:[]   //搜索结果
        },
        methods:{
            searchKey(){
                var keyword=this.keyword;
                console.log(keyword);
                //对接后端的接口
                axios.get('/search/'+keyword+"/1/10").then(response=>{
                    console.log(response);
                    this.results=response.data;//绑定数据
                });
            }
        }
    })
</script>
</body>
</html>

```

首先，要先运行`localhost:9090/parse/java`将获取的内容存到es中，

然后在运行http://localhost:9090/

在输入框内输入java，点击搜索

截图显示：

![image-20200802170800597](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20200802170800597.png)

