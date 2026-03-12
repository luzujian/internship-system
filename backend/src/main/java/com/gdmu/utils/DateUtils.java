package com.gdmu.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateUtils {
    
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String MONTH_PATTERN = "yyyy-MM";
    private static final String YEAR_PATTERN = "yyyy";
    
    public static String formatDate(Date date) {
        return format(date, DATE_PATTERN);
    }
    
    public static String formatDateTime(Date date) {
        return format(date, DATETIME_PATTERN);
    }
    
    public static String formatMonth(Date date) {
        return format(date, MONTH_PATTERN);
    }
    
    public static String formatYear(Date date) {
        return format(date, YEAR_PATTERN);
    }
    
    public static String format(Date date, String pattern) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.format(date);
    }
    
    public static Date parseDate(String dateStr) throws ParseException {
        return parse(dateStr, DATE_PATTERN);
    }
    
    public static Date parseDateTime(String dateTimeStr) throws ParseException {
        return parse(dateTimeStr, DATETIME_PATTERN);
    }
    
    public static Date parse(String dateStr, String pattern) throws ParseException {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.parse(dateStr);
    }
    
    public static Date addDays(Date date, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }
    
    public static Date addMonths(Date date, int months) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MONTH, months);
        return calendar.getTime();
    }
    
    public static Date addYears(Date date, int years) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.YEAR, years);
        return calendar.getTime();
    }
    
    public static Date getDaysAgo(int days) {
        return addDays(new Date(), -days);
    }
    
    public static Date getMonthsAgo(int months) {
        return addMonths(new Date(), -months);
    }
    
    public static Date getYearsAgo(int years) {
        return addYears(new Date(), -years);
    }
    
    public static Date getTodayStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
    
    public static Date getTodayEnd() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }
    
    public static Date getMonthStart(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
    
    public static Date getMonthEnd(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }
    
    public static int getQuarter(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int month = calendar.get(Calendar.MONTH);
        return (month / 3) + 1;
    }
    
    public static String getQuarterStr(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int year = calendar.get(Calendar.YEAR);
        int quarter = getQuarter(date);
        return year + "-Q" + quarter;
    }
    
    public static boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
    
    public static long getDaysBetween(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        long diff = endDate.getTime() - startDate.getTime();
        return diff / (24 * 60 * 60 * 1000);
    }
}
