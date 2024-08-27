package com.bingo.hotel.spa.intl.core.test;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import static com.alibaba.fastjson.JSONPatch.OperationType.test;

public class Test {

    public static void main(String[] args) {
        String html = HttpUtils.sendGet("https://www.timeanddate.com/time/zone/@1368855");
        Document doc = Jsoup.parse(html);
        // table table--left table--inner-borders-rows
        Elements tables = doc.select("table.table.table--left.table--inner-borders-rows");
        Element table = tables.get(0);
        // 获取表格的行
        Elements rows = table.select("tr");
        Element tr = rows.get(1);
        Element td = tr.selectFirst("td");
        String text = td.text();
        String[] s = text.split(" ");
        System.out.println(s[1]);
    }
}
