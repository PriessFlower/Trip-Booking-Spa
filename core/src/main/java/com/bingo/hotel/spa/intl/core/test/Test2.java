package com.bingo.hotel.spa.intl.core.test;

import java.util.ArrayList;
import java.util.List;

public class Test2 {
    public static void main(String[] args) {

//        long startTime = System.currentTimeMillis();
//        String input = HttpUtils.sendGet("https://www.timeanddate.com/scripts/completion.php?query=linfen&xd=3&mode=ci");
//        List<DataRecord> records = parseData(input);
//
//        long endTime = System.currentTimeMillis();
//        System.out.println("time = " + (endTime - startTime));
        getCityInfo("linfen");
        
    }
    public static DataRecord getCityInfo(String cityName) {
        long startTime = System.currentTimeMillis();
        String input = HttpUtils.sendGet("https://www.timeanddate.com/scripts/completion.php?query=" + cityName + "&xd=3&mode=ci");
        List<DataRecord> records = parseData(input);
        for (DataRecord record : records) {
            if(record.getName().contains(cityName)) {

            }
        }
        long endTime = System.currentTimeMillis();
        System.out.println("time = " + (endTime - startTime));
        return records.get(0);
    }
    public static List<DataRecord> parseData(String input) {
        List<DataRecord> records = new ArrayList<>();
        String[] lines = input.split("\n");
        for (String line : lines) {
            String[] parts = line.split("\t");

            if (parts.length < 11) {
                continue;  // 如果数据不完整则跳过
            }

            DataRecord record = new DataRecord();
            record.url = parts[0];
            record.code = Integer.parseInt(parts[1]);
            record.countryCode = parts[2];
            record.regionCode = parts[3];
            record.name = parts[4];
            record.province = parts[5];
            record.country = parts[6];
            record.imageUrl = parts[7];
//            record.location = parts[8];
//            record.value = Double.parseDouble(parts[9]);
//            record.type = parts[10];

            records.add(record);
        }

        return records;
    }
}
