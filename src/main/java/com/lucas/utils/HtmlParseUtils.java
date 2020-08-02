package com.lucas.utils;

import com.lucas.pojo.Content;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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
