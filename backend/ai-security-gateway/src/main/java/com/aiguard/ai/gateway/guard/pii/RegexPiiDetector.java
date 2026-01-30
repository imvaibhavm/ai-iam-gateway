package com.aiguard.ai.gateway.guard.pii;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RegexPiiDetector implements PiiDetector {

    private static final Pattern EMAIL =
            Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", Pattern.CASE_INSENSITIVE);

    // Simple phone patterns (India + generic)
    private static final Pattern PHONE =
            Pattern.compile("\\b(\\+91[-\\s]?)?[6-9]\\d{9}\\b|\\b\\d{10}\\b");

    // PAN format: ABCDE1234F
    private static final Pattern PAN =
            Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");

    // Aadhaar: 12 digits (spaces allowed)
    private static final Pattern AADHAAR =
            Pattern.compile("\\b\\d{4}\\s?\\d{4}\\s?\\d{4}\\b");

    // Credit card: 13-19 digits, spaces/hyphen allowed
    private static final Pattern CREDIT_CARD =
            Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b");

    // UUID
private static final Pattern UUID =
        Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\\b");

// IPv4
private static final Pattern IPV4 =
        Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\b");

// IPv6 (basic)
private static final Pattern IPV6 =
        Pattern.compile("\\b(?:[A-Fa-f0-9]{1,4}:){2,7}[A-Fa-f0-9]{1,4}\\b");

// JWT token
private static final Pattern JWT =
        Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9._-]+\\.[A-Za-z0-9._-]+\\b");

// Common API keys / tokens
private static final Pattern OPENAI_KEY =
        Pattern.compile("\\bsk-[A-Za-z0-9]{20,}\\b");

private static final Pattern HF_TOKEN =
        Pattern.compile("\\bhf_[A-Za-z0-9]{20,}\\b");

private static final Pattern GITHUB_TOKEN =
        Pattern.compile("\\bghp_[A-Za-z0-9]{20,}\\b|\\bgithub_pat_[A-Za-z0-9_]{20,}\\b");

private static final Pattern AWS_ACCESS_KEY =
        Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b");


    @Override
    public PiiResult detectAndMask(String text) {
        if (text == null) text = "";

        List<Found> found = new ArrayList<>();
        findAll(found, text, EMAIL, PiiType.EMAIL);
        findAll(found, text, PHONE, PiiType.PHONE);
        findAll(found, text, PAN, PiiType.PAN);
        findAll(found, text, AADHAAR, PiiType.AADHAAR);
        findAll(found, text, CREDIT_CARD, PiiType.CREDIT_CARD);
        findAll(found, text, UUID, PiiType.UUID);
findAll(found, text, IPV4, PiiType.IP_ADDRESS);
findAll(found, text, IPV6, PiiType.IP_ADDRESS);

findAll(found, text, JWT, PiiType.JWT);

// API keys/tokens
findAll(found, text, OPENAI_KEY, PiiType.API_KEY);
findAll(found, text, HF_TOKEN, PiiType.API_KEY);
findAll(found, text, GITHUB_TOKEN, PiiType.API_KEY);
findAll(found, text, AWS_ACCESS_KEY, PiiType.API_KEY);


        // remove overlaps by choosing longest first
        found.sort((a, b) -> {
            int lenA = a.end - a.start;
            int lenB = b.end - b.start;
            if (lenA != lenB) return Integer.compare(lenB, lenA);
            return Integer.compare(a.start, b.start);
        });

        List<Found> dedup = new ArrayList<>();
        for (Found f : found) {
            boolean overlaps = false;
            for (Found d : dedup) {
                if (rangesOverlap(f.start, f.end, d.start, d.end)) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) dedup.add(f);
        }

        // stable numbering per type
        Map<PiiType, Integer> counters = new EnumMap<>(PiiType.class);
        dedup.sort(Comparator.comparingInt(a -> a.start)); // mask left to right

        StringBuilder masked = new StringBuilder();
        List<PiiEntity> entities = new ArrayList<>();

        int idx = 0;
        for (Found f : dedup) {
            masked.append(text, idx, f.start);

            int n = counters.merge(f.type, 1, Integer::sum);
            String repl = "[" + f.type.name() + "_" + n + "]";

            masked.append(repl);

            entities.add(new PiiEntity(
                    f.type,
                    text.substring(f.start, f.end),
                    f.start,
                    f.end,
                    repl
            ));

            idx = f.end;
        }
        masked.append(text.substring(idx));

        return new PiiResult(text, masked.toString(), entities);
    }

    private void findAll(List<Found> found, String text, Pattern pattern, PiiType type) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            // basic validation for credit cards using luhn
            if (type == PiiType.CREDIT_CARD) {
                String raw = m.group();
                String digits = raw.replaceAll("[^0-9]", "");
                if (digits.length() < 13 || digits.length() > 19) continue;
                if (!luhnValid(digits)) continue;
            }
            found.add(new Found(type, m.start(), m.end()));
        }
    }

    private boolean rangesOverlap(int s1, int e1, int s2, int e2) {
        return s1 < e2 && s2 < e1;
    }

    private boolean luhnValid(String digits) {
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alt = !alt;
        }
        return sum % 10 == 0;
    }

    private record Found(PiiType type, int start, int end) {}
}
