package xyz.lychee.gatekeeper.shared.util;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConditionSet {
    private static final Pattern TERM_PATTERN = Pattern.compile("^(.+?)(>=|<=|>|<|=)(.+)$");
    private final Term[][] orClauses;

    private ConditionSet(Term[][] orClauses) {
        this.orClauses = orClauses;
    }

    public static ConditionSet compile(String expr) {
        if (expr == null || expr.trim().isEmpty()) return null;

        String[] orParts = expr.split("\\|");
        List<Term[]> compiledOrs = new ArrayList<>(orParts.length);

        for (String orPart : orParts) {
            if (orPart.trim().isEmpty()) continue;

            String[] andParts = orPart.split("&");
            List<Term> compiledAnds = new ArrayList<>(andParts.length);

            for (String andPart : andParts) {
                if (andPart.trim().isEmpty()) continue;
                Term t = parseTerm(andPart.trim());
                if (t != null) compiledAnds.add(t);
            }

            if (!compiledAnds.isEmpty()) {
                compiledOrs.add(compiledAnds.toArray(new Term[0]));
            }
        }

        if (compiledOrs.isEmpty()) return null;
        return new ConditionSet(compiledOrs.toArray(new Term[0][]));
    }

    private static Term parseTerm(String expression) {
        Matcher m = TERM_PATTERN.matcher(expression);
        if (!m.matches()) return null;

        String path = m.group(1).trim();
        String opStr = m.group(2);
        String valuePart = m.group(3).trim();

        if (path.isEmpty() || valuePart.isEmpty()) return null;

        Object[] segments = compilePath(path);

        if ("=".equals(opStr)) {
            return new Term(segments, null, 0, valuePart);
        } else {
            try {
                double d = Double.parseDouble(valuePart);
                Op op;
                switch (opStr) {
                    case ">=":
                        op = Op.GREATER_THAN_EQUAL;
                        break;
                    case "<=":
                        op = Op.LESS_THAN_EQUAL;
                        break;
                    case ">":
                        op = Op.GREATER_THAN;
                        break;
                    default:
                        op = Op.LESS_THAN;
                        break;
                }
                return new Term(segments, op, d, null);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private static Object[] compilePath(String path) {
        String[] raw = path.split(":");
        Object[] segments = new Object[raw.length];

        for (int i = 0; i < raw.length; i++) {
            String seg = raw[i].trim();
            if (seg.matches("\\d+")) {
                segments[i] = Integer.parseInt(seg);
            } else {
                segments[i] = seg;
            }
        }
        return segments;
    }

    public boolean evaluate(Object json) {
        if (json == null) return false;

        for (Term[] andBlock : orClauses) {
            boolean allMatch = true;
            for (Term t : andBlock) {
                if (!t.matches(json)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return true;
        }
        return false;
    }

    public enum Op {
        GREATER_THAN, LESS_THAN, GREATER_THAN_EQUAL, LESS_THAN_EQUAL
    }

    private static final class Term {
        private final Object[] pathSegments;
        private final Op op;
        private final double expectedNumber;
        private final String expectedText;

        public Term(Object[] pathSegments, Op op, double expectedNumber, String expectedText) {
            this.pathSegments = pathSegments;
            this.op = op;
            this.expectedNumber = expectedNumber;
            this.expectedText = expectedText;
        }

        boolean matches(Object root) {
            Object cur = find(root);
            if (cur == null) return false;

            if (op == null) {
                return String.valueOf(cur).equalsIgnoreCase(expectedText);
            } else {
                if (cur instanceof Number) {
                    return compareNumber(((Number) cur).doubleValue());
                }
                return false;
            }
        }

        private Object find(Object root) {
            Object cur = root;

            for (Object seg : pathSegments) {
                if (cur == null) return null;

                if (cur instanceof JsonObject && seg instanceof String) {
                    cur = ((JsonObject) cur).get((String) seg);
                } else if (cur instanceof JsonArray && seg instanceof Integer) {
                    cur = ((JsonArray) cur).get((Integer) seg);
                } else {
                    return null;
                }
            }
            return cur;
        }

        private boolean compareNumber(double actual) {
            switch (op) {
                case GREATER_THAN:
                    return actual > expectedNumber;
                case LESS_THAN:
                    return actual < expectedNumber;
                case GREATER_THAN_EQUAL:
                    return actual >= expectedNumber;
                case LESS_THAN_EQUAL:
                    return actual <= expectedNumber;
                default:
                    return false;
            }
        }
    }

    @Getter
    public static final class Provider {
        private final String name;
        private final String url;
        private final int priority;
        private final Map<String, String> headers;
        private final ConditionSet condition;

        public Provider(String name, String url, int priority, Map<String, String> headers, ConditionSet condition) {
            this.name = name;
            this.url = url;
            this.priority = priority;
            this.headers = headers;
            this.condition = condition;
        }

        public boolean matches(Object json) {
            return condition == null || condition.evaluate(json);
        }
    }
}