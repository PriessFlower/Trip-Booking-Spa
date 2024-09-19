package com.bingo.hotel.spa.intl.core.util;

import com.bingo.hotel.spa.intl.core.api.common.exception.SysRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

@Slf4j
public class DateUtil {
    private static final int DATE_STRING_LENGTH = 19;

    public static final FastDateFormat DATE_FORMAT = FastDateFormat.getInstance("yyyy-MM-dd");

    public static final FastDateFormat DATE_FORMAT_HMS = FastDateFormat.getInstance("yyyy-MM-dd");

    /**
     * Time is linear variable
     * 次日
     * [---------]
     * ^
     * |
     *
     * @param checkInDate
     * @return
     */
    public static boolean isInNextDay(Date checkInDate) {
        DateTime checkInTime = new DateTime(checkInDate);
        DateTime nextDayStart = DateTime.now().plusDays(1).withTimeAtStartOfDay();
        DateTime nextDayEnd = DateTime.now().plusDays(1).withTimeAtStartOfDay().plusHours(24);
        return checkInTime.isBefore(nextDayEnd) && checkInTime.isAfter(nextDayStart) || checkInTime.isEqual(nextDayStart.getMillis());
    }

    /**
     * 次日及以后
     *
     * @param checkInDate
     * @return
     */
    public static boolean isNextAndAfterDay(Date checkInDate) {
        DateTime checkInTime = new DateTime(checkInDate);
        DateTime nextDayStart = DateTime.now().plusDays(1).withTimeAtStartOfDay();
        return checkInTime.isAfter(nextDayStart) || checkInTime.isEqual(nextDayStart.getMillis());
    }

    public static Date addDay(Date date, int days) {
        LocalDateTime dateTime = LocalDateTime.fromDateFields(date);
        return dateTime.plusDays(days).toDate();
    }

    /**
     * 将XMLGregorianCalendar转换为Date
     *
     * @param cal
     * @return
     */
    public static Date xmlDate2Date(XMLGregorianCalendar cal) {
        if (cal == null) {
            return null;
        }
        return cal.toGregorianCalendar().getTime();
    }

    public static int diff(Date startDate, Date endDate) {

        return (int) Math.ceil((endDate.getTime() - startDate.getTime()) / 86400000.0D);
    }

    public static int diffMinute(Date startDate, Date endDate) {
        return (int) Math.ceil((endDate.getTime() - startDate.getTime()) / (1000 * 60));
    }

    public static int diffSecond(Date startDate, Date endDate) {
        return (int) Math.ceil((endDate.getTime() - startDate.getTime()) / 1000);
    }

    public static int diffHour(Date startDate, Date endDate) {
        return (int) Math.ceil((endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60));
    }

    /**
     * java int类型的除法会省略掉小数位 导致计算的时间差偏小 因此先把int类型转成double
     *
     * @param startDate
     * @param endDate
     * @return
     */
    public static int diffHourPrecise(Date startDate, Date endDate) {
        return (int) Math.ceil((double) (endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60));
    }

    public static long diff(Date date) {
        return (date.getTime() - System.currentTimeMillis()) / 1000;
    }

    public static int getDayOfWeek(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        return cal.get(Calendar.DAY_OF_WEEK) - 1;
    }

    public static String retifyDateStr(String timestamp) {
        if (timestamp != null && timestamp.length() > DATE_STRING_LENGTH) {
            return timestamp.substring(0, DATE_STRING_LENGTH);
        }
        return timestamp;
    }

    // 2011-03-12 23:04:32
    public static String shortDate(String timestamp) {
        if (timestamp == null || timestamp.length() < DATE_STRING_LENGTH) {
            return timestamp;
        }
        return timestamp.substring(5, 10);
    }

    public static String shortDateTime(String timestamp) {
        if (timestamp == null || timestamp.length() < DATE_STRING_LENGTH) {
            return timestamp;
        }
        return timestamp.substring(5, 16);
    }

    public static String getWeekCn(int num) {
        String[] ch_Chars = new String[]{"日", "一", "二", "三", "四", "五", "六"};
        if (0 <= num && num <= 6) {
            return ch_Chars[num];
        }
        return String.valueOf(num);
    }

    public static boolean isValidWeek(String weekNum) {
        try {
            int num = Integer.parseInt(weekNum);
            if (0 <= num && num <= 6) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static String getTimeDesc(int minute) {
        if (minute <= 120) {
            return minute + "分钟";
        } else {
            int hours = minute / 60;
            minute = minute % 60;
            return hours + "小时" + (minute > 0 ? minute + "分钟" : "");
        }
    }

    public static int getDiffTimeZoneRawOffset(String timeZoneId) {
        return TimeZone.getDefault().getRawOffset() - TimeZone.getTimeZone(timeZoneId).getRawOffset();
    }

    // public static void main(String[] args) {
    // //System.out.println(getTimeDesc(245));
    // Date t = getGMT8Time("2014-03-11 23:59:59","+0800");
    // System.out.println(DateFormatUtils.format4y2M2d2h2m2s(t));
    // }

    public static String getYesterdayYMD() {
        SimpleDateFormat ymd = new SimpleDateFormat("yyyy-MM-dd ");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        return ymd.format(cal.getTime());
    }

    public static String getTodayYMD() {
        SimpleDateFormat ymd = new SimpleDateFormat("yyyy-MM-dd ");
        Calendar cal = Calendar.getInstance();
        return ymd.format(cal.getTime());
    }

    /**
     * yyyy-MM-ddTHH:mm:ssZ
     *
     * @return
     */
    public static String getTZDate() {
        //2018-06-13T09:30:47Z
        SimpleDateFormat ymd = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        return ymd.format(Calendar.getInstance().getTime());
    }

    public static Date getToDay0H0M0S() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public static Date getTomorrow0H0M0S() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
    public static Calendar getCalendarTomorrow0H0M0S() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }


    public static Date getTomorrowByHour(int hour) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public static Date getYesterday0H0M0S() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }


    public static Date getToday23H59M59S() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 0);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public static Date getTomorrow23H59M59S() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, +1);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        return cal.getTime();
    }

    public static boolean isToday(Date date) {
        Date today = DateUtils.truncate(new Date(), Calendar.DAY_OF_MONTH);
        date = DateUtils.truncate(date, Calendar.DAY_OF_MONTH);
        return today.getTime() == date.getTime();
    }

    /**
     * 闭区间date [date1,date2]
     *
     * @param date
     * @param date1
     * @param date2
     * @return
     */
    public static boolean isBetween(Date date, Date date1, Date date2) {
        if (date1 == null || date2 == null || date == null) {
            throw new IllegalArgumentException("The dates must not be null");
        }
        return isAfterDayClose(date, date1) && isBeforeDayClose(date, date2);
    }

    public static boolean isBeforeDayClose(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            throw new IllegalArgumentException("The dates must not be null");
        }
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        if (cal1.get(Calendar.ERA) < cal2.get(Calendar.ERA)) {
            return true;
        }
        if (cal1.get(Calendar.ERA) > cal2.get(Calendar.ERA)) {
            return false;
        }
        if (cal1.get(Calendar.YEAR) < cal2.get(Calendar.YEAR)) {
            return true;
        }
        if (cal1.get(Calendar.YEAR) > cal2.get(Calendar.YEAR)) {
            return false;
        }

        return cal1.get(Calendar.DAY_OF_YEAR) <= cal2.get(Calendar.DAY_OF_YEAR);
    }

    public static boolean isAfterDayClose(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            throw new IllegalArgumentException("The dates must not be null");
        }
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        if (cal1.get(Calendar.ERA) < cal2.get(Calendar.ERA)) {
            return false;
        }
        if (cal1.get(Calendar.ERA) > cal2.get(Calendar.ERA)) {
            return true;
        }
        if (cal1.get(Calendar.YEAR) < cal2.get(Calendar.YEAR)) {
            return false;
        }
        if (cal1.get(Calendar.YEAR) > cal2.get(Calendar.YEAR)) {
            return true;
        }
        return cal1.get(Calendar.DAY_OF_YEAR) >= cal2.get(Calendar.DAY_OF_YEAR);
    }

    public static int getNights(final Date checkInDate, final Date checkOutDate) {
        LocalDate checkIn = LocalDate.fromDateFields(checkInDate);
        LocalDate checkOut = LocalDate.fromDateFields(checkOutDate);
        return Days.daysBetween(checkIn, checkOut).getDays();
    }

    public static int getNights(String checkInStr, String checkOutStr) throws ParseException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date checkInDate = simpleDateFormat.parse(checkInStr);
        Date checkOutDate = simpleDateFormat.parse(checkOutStr);

        LocalDate checkIn = LocalDate.fromDateFields(checkInDate);
        LocalDate checkOut = LocalDate.fromDateFields(checkOutDate);
        return Days.daysBetween(checkIn, checkOut).getDays();
    }

    /**
     * 得到日期所属年份
     *
     * @param date
     * @return
     */
    public static int getYear(Date date) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date);
        return cal1.get(Calendar.YEAR);
    }

    /**
     * 得到日期所属年份
     *
     * @param date
     * @return
     */
    public static int getrDayOfYear(Date date) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date);
        return cal1.get(Calendar.DAY_OF_YEAR);
    }


    public static boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            throw new IllegalArgumentException("The dates must not be null");
        }
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1
                .get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
    }

    public static boolean isBeforeDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            throw new IllegalArgumentException("The dates must not be null");
        }
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        if (cal1.get(Calendar.ERA) < cal2.get(Calendar.ERA)) {
            return true;
        }
        if (cal1.get(Calendar.ERA) > cal2.get(Calendar.ERA)) {
            return false;
        }
        if (cal1.get(Calendar.YEAR) < cal2.get(Calendar.YEAR)) {
            return true;
        }
        if (cal1.get(Calendar.YEAR) > cal2.get(Calendar.YEAR)) {
            return false;
        }
        return cal1.get(Calendar.DAY_OF_YEAR) < cal2.get(Calendar.DAY_OF_YEAR);
    }

    public static boolean isAfterDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            throw new IllegalArgumentException("The dates must not be null");
        }
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        if (cal1.get(Calendar.ERA) < cal2.get(Calendar.ERA)) {
            return false;
        }
        if (cal1.get(Calendar.ERA) > cal2.get(Calendar.ERA)) {
            return true;
        }
        if (cal1.get(Calendar.YEAR) < cal2.get(Calendar.YEAR)) {
            return false;
        }
        if (cal1.get(Calendar.YEAR) > cal2.get(Calendar.YEAR)) {
            return true;
        }
        return cal1.get(Calendar.DAY_OF_YEAR) > cal2.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * 如果start的小时分钟数晚于end的小时分钟数，返回true
     *
     * @param start
     * @param end
     * @return
     */
    public static boolean isAfterHourMinute(Date start, Date end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("The dates must not be null");
        }
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(start);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(end);
        if (cal1.get(Calendar.HOUR_OF_DAY) > cal2.get(Calendar.HOUR_OF_DAY)) {
            return true;
        }
        if (cal1.get(Calendar.MINUTE) > cal2.get(Calendar.MINUTE)) {
            return true;
        }
        return false;
    }

    public static XMLGregorianCalendar toXmlCalendar(Date date) {
        XMLGregorianCalendar xmlGregorianCalendar = null;
        try {
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTime(date);
            xmlGregorianCalendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(cal);
        } catch (Exception e) {
            throw new SysRuntimeException("日期格式转换错误", e);
        }
        return xmlGregorianCalendar;
    }

    /**
     * 获取星期下标 eg: 星期一 => 0, 星期二 => 1 ...
     *
     * @param date 日期
     * @return 星期数组下标
     */
    public synchronized static int getDayOfWeek(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 2;
        return dayOfWeek < 0 ? dayOfWeek + 7 : dayOfWeek;
    }

    /**
     * 获取时间 小时:分;秒 HH:mm:ss
     *
     * @return
     */
    public static String getTimeShort(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
        String dateString = formatter.format(date);
        return dateString;
    }

    /**
     * <pre>
     * 判断某个日期()是否在指定时间段内-闭区间,
     * 返回true表示在指定日期段内
     *
     * 例如 2016-10-08 10:22:00 [2016-10-05 10:22:00,2016-10-08 10:22:00]
     *
     * </pre>
     *
     * @param startDate
     * @param endDate
     * @param now
     * @return
     */
    public static boolean isInTimePeriodMinute(Date startDate, Date endDate, Date now) {
        return !(DateUtil.diffMinute(startDate, now) < 0 || DateUtil.diffMinute(now, endDate) < 0);
    }

    /**
     * <pre>
     * 判断某个日期是否在指定时间段内-闭区间,
     * 返回true表示在指定日期段内
     *
     * 例如 2016-10-08 10:22:00 [2016-10-05 10:22:00,2016-10-08 10:22:00]
     *
     *
     * </pre>
     *
     * @param startDate
     * @param endDate
     * @param now
     * @return
     */
    public static boolean isInTimePeriodDay(Date startDate, Date endDate, Date now) {
        return !(DateUtil.isBeforeDay(now, startDate) || DateUtil.isAfterDay(now, endDate));
    }

    /**
     * /Date(1487053489965+0800)/ 转化成 yyyy-mm-dd
     *
     * @param dateString
     * @return
     */
    public static Date jsonDateToDate(String dateString) {
        dateString = dateString.replace("/Date(", "").replace(")/", "");
        String time = dateString.substring(0, dateString.length() - 5);
        return new Date(Long.parseLong(time));
    }


    public static Date getDate(String date) {
        try {
            //date 2013-08-09
            return DATE_FORMAT.parse(date);
        } catch (Exception e) {
            log.error("DateUtil getDateStr ParseException", e);
        }
        return null;
    }

    public static String getDateStr(Date date) {
        try {
            //date 2013-08-09
            return DATE_FORMAT.format(date);
        } catch (Exception e) {
            log.error("DateUtil getDateStr FormatException", e);
        }
        return null;
    }

    /**
     * 判断是否对应格式的日期字符串
     *
     * @param dateStr
     * @param pattern
     * @return
     */
    public static boolean isValidDateStr(String dateStr, String pattern) {
        DateFormat dateFormat = new SimpleDateFormat(pattern);
        try {
            //采用严格的解析方式，防止类似 “2017-05-35” 类型的字符串通过
            dateFormat.setLenient(false);
            dateFormat.parse(dateStr);

            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * 计算当前日期过去几天的日期
     * @param num
     * @return
     */
    public static String getPastDay(int num) {
        // 获取当前日期
        java.time.LocalDate currentDate = java.time.LocalDate.now();
        // 计算当前日期前几天的日期
        java.time.LocalDate pastDate = currentDate.minusDays(num);
        // 格式化日期为 yyyy-MM-dd 格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return pastDate.format(formatter);
    }

    /**
     * 计算当前日期未来几天的日期
     * @param num
     * @return
     */
    public static String getFutureDay(int num) {
        // 获取当前日期
        java.time.LocalDate currentDate = java.time.LocalDate.now();
        // 计算当前日期前一周的日期
        java.time.LocalDate pastDate = currentDate.plusDays(num);
        // 格式化日期为 yyyy-MM-dd 格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return pastDate.format(formatter);
    }


    public static void main(String[] arg) {
        System.out.println(DateUtil.getPastDay(-6));
    }
}
