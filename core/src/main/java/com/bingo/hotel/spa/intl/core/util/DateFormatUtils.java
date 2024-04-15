package com.bingo.hotel.spa.intl.core.util;

import com.alibaba.schedulerx.shade.org.apache.commons.collections.map.LRUMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

@Slf4j
public class DateFormatUtils {
    private static final int MAX_SDF_SIZE = 1000;
    private static final ThreadLocal<LRUMap> dateFormatThreadLocal = new ThreadLocal<LRUMap>() {
        @Override
        protected LRUMap initialValue() {
            return new LRUMap(MAX_SDF_SIZE);
        }
    };
    private final static String[] WEEKDAYS = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};

    public static SimpleDateFormat getDateFormat(String pattern) {
        LRUMap sdfMap = dateFormatThreadLocal.get();
        SimpleDateFormat sdf = (SimpleDateFormat) sdfMap.get(pattern);
        if (sdf == null) {
            sdf = new SimpleDateFormat(pattern);
            sdf.setLenient(false);
            sdfMap.put(pattern, sdf);
        }
        return sdf;
    }


    public static SimpleDateFormat getDateFormatWithTimezone(String pattern, String timezone) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setTimeZone(TimeZone.getTimeZone(timezone));
        return sdf;
    }

    public static void destroy() {
        dateFormatThreadLocal.remove();
    }

    public static Date parse2d2M4y(String source) {
        try {
            return getDateFormat("dd/MM/yyyy").parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date parseyyyyMMdd(String source) {
        try {
            return getDateFormat("yyyyMMdd").parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static String parseyyyyMMddHHmm(Date date) {
        return getDateFormat("yyyyMMddHHmm").format(date);
    }

    public static Date parseyyMMddHHmmss(String source) {
        try {
            return getDateFormat("yyMMddHHmmss").parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date parse4y2M2d(String source) {
        try {
            return getDateFormat("yyyy-MM-dd").parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date parse4y2M2d2h2m2s(String source) {
        try {
            return getDateFormat("yyyy-MM-dd HH:mm:ss").parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date parse4y2M2d2h2m2sWithTimezone(String source, String timezone) {
        try {
            return getDateFormatWithTimezone("yyyy-MM-dd HH:mm:ss", timezone).parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date parse4y2M2d2h2m(String source) {
        try {
            return getDateFormat("yyyy-MM-dd HH:mm").parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date parse4y2M2dT2h2m2s(String source) {
        try {
            return getDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date parse4y2M2dT2h2m2sZ(String source) {
        try {
            return getDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(source);
        } catch (Exception e) {
            return null;
        }
    }


    public static Date parse2M2d4y(String source) {
        try {
            return getDateFormat("MM/dd/yyyy").parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static String format4y2M2d2h2m2s(long date) {
        if (date <= 0) {
            return null;
        }
        try {
            return getDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
        } catch (Exception e) {
            return null;
        }
    }

    public static String format4Y2M2d(Date date) {
        return getDateFormat("yyyy/MM/dd").format(date);
    }

    public static Date parse4Y2M1d2H2m2s(String source) {
        try {
            return getDateFormat("yyyy/MM/d HH:mm:ss").parse(source);
        } catch (Exception e) {
            return null;
        }
    }


    public static Date parseHHmmOrHHmmss(String source) {
        Date time = parseHHmmss(source);
        if (time == null) {
            time = parseHHmm(source);
        }
        return time;
    }

    public static Date parseHHmm(String source) {
        try {
            if (StringUtils.equals("24:00", source)) {
                source = "23:59";
            }
            return getDateFormat("HH:mm").parse(source);
        } catch (Exception e) {
            return null;
        }
    }


    public static Date parseHHmmss(String source) {
        try {
            if (StringUtils.equals("24:00:00", source)) {
                source = "23:59:59";
            }
            return getDateFormat("HH:mm:ss").parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static String formatHHmm(Date date) {
        try {
            return getDateFormat("HH:mm").format(date);
        } catch (Exception e) {
            return null;
        }
    }

    public static String formatHHmmss(Date date) {
        try {
            return getDateFormat("HH:mm:ss").format(date);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date parseHHmmWithoutDelimiter(String source) {
        try {
            return getDateFormat("HH_MM_SS").parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date parse(String source, String pattern) {
        try {
            return getDateFormat(pattern).parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static String format4y2M2d(Date date) {
        return getDateFormat("yyyy-MM-dd").format(date);
    }

    public static String format2d2M4y(Date date) {
        return getDateFormat("dd/MM/yyyy").format(date);
    }

    public static String format2M2d4y(Date date) {
        return getDateFormat("MM/dd/yyyy").format(date);
    }

    public static String format4y2M2d2h2m2s(Date date) {
        return getDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    public static String format4y2M2dT2h2m2s(Date date) {
        return getDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(date);
    }

    public static String format4y2M2dT2h2m2sSSSXXX(Date date) {
        return getDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(date);
    }

    public static String format4y2M2dT2h2m2sXXX(Date date) {
        return getDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(date);
    }

    public static Date parse4y2M2dT2h2m2sSSSXXX(String source) {
        try {
            return getDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date parse4y2M2dT2h2m2sSXXX(String source) {
        try {
            return getDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(source);
        } catch (Exception e) {
            log.error("解析失败，source:{}", source);
            return null;
        }
    }

    public static String format4y2M2d2h2m(Date date) {
        return getDateFormat("yyyy-MM-dd HH:mm").format(date);
    }

    public static Calendar dateToCalender(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal;
    }

    public static int diff(Date startDate, Date endDate) {
        return (int) Math.ceil((endDate.getTime() - startDate.getTime()) / 86400000.0D);
    }

    public static int diffHour(Date startDate, Date endDate) {
        return (int) Math.ceil((endDate.getTime() - startDate.getTime()) / 3600000.0D);
    }

    public static int diffSecond(Date startDate, Date endDate) {
        return (int) Math.ceil((endDate.getTime() - startDate.getTime()) / 1000.0D);
    }

    public static Date dateAddDays(Date date, int days) {
        Calendar cal = dateToCalender(date);
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }

    public static Date dateAddHours(Date date, int hours) {
        Calendar cal = dateToCalender(date);
        cal.add(Calendar.HOUR_OF_DAY, hours);
        return cal.getTime();
    }

    public static Date dateAddMinutes(Date date, int minutes) {
        Calendar cal = dateToCalender(date);
        cal.add(Calendar.MINUTE, minutes);
        return cal.getTime();
    }

    public static String formatyyyyMMdd(Date date) {
        return getDateFormat("yyyyMMdd").format(date);
    }

    public static int getHour(Date time) {
        try {
            String hourStr = getDateFormat("H").format(time);
            return Integer.parseInt(hourStr);
        } catch (Exception e) {
            return 0;
        }
    }

    public static String getYear(Date time) {
        try {
            return getDateFormat("yyyy").format(time);
        } catch (Exception e) {
            return StringUtils.EMPTY;
        }
    }

    public static String getMonth(Date time) {
        try {
            return getDateFormat("MM").format(time);
        } catch (Exception e) {
            return StringUtils.EMPTY;
        }
    }

    public static String getDay(Date time) {
        try {
            return getDateFormat("dd").format(time);
        } catch (Exception e) {
            return StringUtils.EMPTY;
        }
    }

    public static int getWeekday(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal.get(Calendar.DAY_OF_WEEK) - 1;
    }

    public static String formatWeekday(Date date) {
        int w = getWeekday(date);
        return WEEKDAYS[w];
    }

    public static boolean isSameYearMonth(Date d1, Date d2) {
        try {
            String yearMonthStr1 = getDateFormat("yyyyMM").format(d1);
            String yearMonthStr2 = getDateFormat("yyyyMM").format(d2);
            return StringUtils.equals(yearMonthStr1, yearMonthStr2);
        } catch (Exception e) {
            return false;
        }
    }


    public static Date getLastDay(Date date) {
        Calendar calendar = dateToCalender(date);
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        date = calendar.getTime();
        return date;
    }


    public static Date getEarlyMorning(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        return calendar.getTime();
    }

    public static String formatDateToString(String date, SimpleDateFormat format, SimpleDateFormat toFormat) {
        try {
            Date d = format.parse(date);
            return toFormat.format(d);
        } catch (Exception e) {
            log.error("DateFormatUtils formatDateToString is error date:{}", date);
        }
        return "";
    }

    public static int getBetweenDays(String start, String end) {
        return getBetweenDays(start, end, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    public static int getBetweenDays(String start, String end, DateTimeFormatter dateFormat) {
        LocalDate leaveArrLocalDate = parse(start, dateFormat);
        LocalDate backArrLocalDate = parse(end, dateFormat);

        return (int) (ChronoUnit.DAYS.between(leaveArrLocalDate, backArrLocalDate));
    }

    private static LocalDate parse(String date, DateTimeFormatter dateFormat) {
        return LocalDate.parse(date, dateFormat);
    }

    public static Boolean dateBefore(String startTime, String endTime) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date startDate = sdf.parse(startTime);
            Date endDate = sdf.parse(endTime);
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            start.setTime(startDate);
            end.setTime(endDate);
            return start.before(end);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static Boolean dateBefore(Date startDate, Date endDate) {
        try {

            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            start.setTime(startDate);
            end.setTime(endDate);
            return start.before(end);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String getStartOfDay() {
        LocalDate currentDate = LocalDate.now();
        // 将当前日期与时间设置为00:00:00
        LocalDateTime startOfDay = currentDate.atStartOfDay();
        return format4y2M2d2h2m2s(Date.from(startOfDay.atZone(java.time.ZoneId.systemDefault()).toInstant()));
    }

    public static String getEndOfDay() {
        LocalDateTime currentDate = LocalDateTime.now();
        // 将当前日期与时间设置为00:00:00
        LocalDateTime startOfDay = currentDate.withHour(23).withMinute(59).withSecond(59);
        return format4y2M2d2h2m2s(Date.from(startOfDay.atZone(java.time.ZoneId.systemDefault()).toInstant()));
    }

    public static long daysDifference(Date date1, Date date2) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        // 将 Date 对象转换为 LocalDate 对象
        LocalDate localDate1 = LocalDate.parse(sdf.format(date1));
        LocalDate localDate2 = LocalDate.parse(sdf.format(date2));
        return ChronoUnit.DAYS.between(localDate1, localDate2);
    }
    public static long getSecondsUntilTomorrowOneAM() {
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 获取次日凌晨一点的时间
        LocalDateTime tomorrowOneAM = now
                .toLocalDate()                     // 转换为LocalDate
                .plusDays(1)                       // 加一天得到次日
                .atTime(LocalTime.of(1, 0));      // 在次日基础上设置时间为凌晨一点

        // 计算两个时间点之间的duration
        Duration duration = Duration.between(now, tomorrowOneAM);

        // 返回两个时间点之间的秒数
        return duration.getSeconds();
    }

    public static void main(String[] args) {
        System.out.println();
    }
}
