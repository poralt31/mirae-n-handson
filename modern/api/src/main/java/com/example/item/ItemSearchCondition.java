package com.example.item;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 문항 검색 조건. 레거시 {@code search.php} 의 {@code buildSearchQuery} 가 GET 파라미터를 해석하는 방식을
 * 그대로 옮겼다(규칙 ID 는 docs/item-bank/CROSS-CHECK.md). 레거시의 이상한 동작도 고치지 않고 옮긴다.
 * 경고 문구는 응답에 나가지 않으므로 만들지 않는다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ItemSearchCondition {

    static final int PAGE_SIZE = 20;
    static final int MAX_PAGE = 999;
    static final int MAX_KEYWORD_LENGTH = 100;
    static final int MAX_TAG_LENGTH = 50;

    /** PHP trim() 이 지우는 문자: 공백, \t, \n, \r, \0, \x0B. */
    private static final String PHP_TRIM_CHARS = " \t\n\r\0\u000B";
    /** PHP preg 의 {@code $} 는 끝의 개행 하나 앞에서도 맞는다. */
    private static final Pattern LEVEL_PATTERN = Pattern.compile("\\A[1-5]\\n?\\z");
    private static final Pattern PAGE_PATTERN = Pattern.compile("\\A[0-9]+\\n?\\z");
    /** PHP 숫자 문자열의 앞부분(앞 공백 · 부호 · 정수/소수 · 지수). 뒤에 붙은 문자는 무시한다. */
    private static final Pattern PHP_NUMERIC_PREFIX =
        Pattern.compile("\\A[ \\t\\n\\r\\u000B\\f]*([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)");

    /** 키워드(CX-02 · CX-03). 비었으면 null. {@code %} · {@code _} 는 이스케이프하지 않는다. */
    private final String keyword;
    /** 단원 코드(CX-04). 비었으면 null. 대소문자를 바꾸지 않는다. */
    private final String unitCode;
    /** 난이도 조건(CX-05 · CX-06). null 이면 {@code level < 5}. 아니면 {@code level = 값}. */
    private final Long level;
    /** 태그 이름(CX-07). 비었으면 null. */
    private final String tag;
    /** 정렬(CX-09 ~ CX-11). */
    private final Sort sort;
    /** 1 ~ 999 로 보정한 페이지 번호(CX-12). */
    private final int page;

    /**
     * 요청 파라미터(이름 → 값 목록)로 조건을 만든다.
     * CX-08: PHP 는 {@code name[]=a&name[]=b} 를 배열로 받아 첫 값을 쓰고, {@code name=a&name=b} 는 마지막 값이 남는다.
     */
    public static ItemSearchCondition from(Map<String, List<String>> params) {
        String q = phpParam(params, "q", "");
        String unit = phpParam(params, "unit", "");
        String level = phpParam(params, "level", "");
        String tag = phpParam(params, "tag", "");
        String sort = phpParam(params, "sort", "");
        String dir = phpParam(params, "dir", "");
        String pageRaw = phpParam(params, "page", "1");

        return new ItemSearchCondition(
            parseKeyword(q),
            emptyToNull(phpTrim(unit)),
            parseLevel(level),
            parseTag(tag),
            parseSort(sort, dir),
            parsePage(pageRaw));
    }

    /** 현재 페이지 20건. OFFSET = (page - 1) * 20. */
    public Pageable pageable() {
        return PageRequest.of(page - 1, PAGE_SIZE, sort);
    }

    // CX-02 · CX-03: trim 후 100자(코드 포인트 기준, mb_substr 와 같게)로 자른다.
    static String parseKeyword(String raw) {
        String q = phpTrim(raw);
        if (q.codePointCount(0, q.length()) > MAX_KEYWORD_LENGTH) {
            q = q.substring(0, q.offsetByCodePoints(0, MAX_KEYWORD_LENGTH));
        }
        return emptyToNull(q);
    }

    // CX-05 · CX-06: 빈값이면 null(level < 5), 1~5 는 그 값, 그 밖은 PHP (int) 변환 값.
    static Long parseLevel(String raw) {
        String level = phpTrim(raw);
        if (level.isEmpty()) {
            return null;
        }
        if (LEVEL_PATTERN.matcher(level).matches()) {
            return (long) (level.charAt(0) - '0');
        }
        return phpIntval(level);
    }

    // CX-07: trim 후 50자로 자른다.
    static String parseTag(String raw) {
        String tag = phpTrim(raw);
        if (tag.codePointCount(0, tag.length()) > MAX_TAG_LENGTH) {
            tag = tag.substring(0, tag.offsetByCodePoints(0, MAX_TAG_LENGTH));
        }
        return emptyToNull(tag);
    }

    // CX-09 ~ CX-11: 기준별 방향 기본값과 보조 정렬이 다르다. 모르는 기준은 기본 정렬.
    static Sort parseSort(String rawSort, String rawDir) {
        String sort = asciiLower(phpTrim(rawSort));
        String dir = asciiLower(phpTrim(rawDir));
        if (!dir.equals("asc") && !dir.equals("desc")) {
            dir = "";
        }
        boolean desc = dir.equals("desc");
        boolean asc = dir.equals("asc");
        return switch (sort) {
            case "id" -> desc ? Sort.by(Sort.Order.desc("id")) : Sort.by(Sort.Order.asc("id"));
            case "title" -> Sort.by(desc ? Sort.Order.desc("title") : Sort.Order.asc("title"), Sort.Order.asc("id"));
            case "unit" -> Sort.by(desc ? Sort.Order.desc("unitCode") : Sort.Order.asc("unitCode"),
                Sort.Order.desc("level"), Sort.Order.asc("id"));
            case "level" -> Sort.by(asc ? Sort.Order.asc("level") : Sort.Order.desc("level"), Sort.Order.asc("id"));
            case "created" -> Sort.by(asc ? Sort.Order.asc("createdAt") : Sort.Order.desc("createdAt"),
                Sort.Order.asc("id"));
            default -> Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id"));
        };
    }

    // CX-12: 숫자만(trim 하지 않음)이면 그 값, 아니면 1. 1 미만은 1, 999 초과는 999.
    static int parsePage(String raw) {
        long page = 1;
        if (PAGE_PATTERN.matcher(raw).matches()) {
            page = phpIntval(raw);
        }
        if (page < 1) {
            page = 1;
        }
        if (page > MAX_PAGE) {
            page = MAX_PAGE;
        }
        return (int) page;
    }

    /**
     * PHP 7.4 의 {@code (int)$string}. 앞부분의 숫자만 읽고(뒤 문자는 무시), 숫자가 없으면 0.
     * 지수 · 소수는 double 로 읽어 버림하고, long 범위를 넘으면 최대 · 최소값, 무한대면 0.
     */
    static long phpIntval(String value) {
        Matcher m = PHP_NUMERIC_PREFIX.matcher(value);
        if (!m.find()) {
            return 0L;
        }
        String number = m.group(1);
        if (number.matches("[+-]?[0-9]+")) {
            try {
                return Long.parseLong(number);
            } catch (NumberFormatException overflow) {
                return number.startsWith("-") ? Long.MIN_VALUE : Long.MAX_VALUE;
            }
        }
        double d = Double.parseDouble(number);
        if (Double.isInfinite(d) || Double.isNaN(d)) {
            return 0L;
        }
        if (new BigDecimal(d).compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
            || new BigDecimal(d).compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) < 0) {
            return d > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return (long) d;
    }

    /** CX-08: {@code name[]} 가 있으면 그 첫 값, 없으면 {@code name} 의 마지막 값, 둘 다 없으면 기본값. */
    static String phpParam(Map<String, List<String>> params, String name, String defaultValue) {
        List<String> arrayValues = params.get(name + "[]");
        if (arrayValues != null && !arrayValues.isEmpty()) {
            return nullToEmpty(arrayValues.get(0));
        }
        List<String> values = params.get(name);
        if (values != null && !values.isEmpty()) {
            return nullToEmpty(values.get(values.size() - 1));
        }
        return defaultValue;
    }

    static String phpTrim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && PHP_TRIM_CHARS.indexOf(value.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && PHP_TRIM_CHARS.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(start, end);
    }

    /** PHP strtolower 처럼 ASCII 대문자만 소문자로 바꾼다. */
    private static String asciiLower(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sb.append(c >= 'A' && c <= 'Z' ? (char) (c + 32) : c);
        }
        return sb.toString();
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
