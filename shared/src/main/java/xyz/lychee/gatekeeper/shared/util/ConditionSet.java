package xyz.lychee.gatekeeper.shared.util;

import lombok.Getter;
import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import java.util.*;

public final class ConditionSet {
    private final List<List<Term>> orClauses;

    private ConditionSet(List<List<Term>> orClauses) {
        this.orClauses = orClauses;
    }

    public static ConditionSet compile(String expr) {
        if (expr == null || expr.trim().isEmpty()) return null;

        String[] orParts = expr.split("\\|");
        List<List<Term>> orClauses = new ArrayList<>(orParts.length);

        for (String orPart : orParts) {
            String trimmedOr = orPart.trim();
            if (trimmedOr.isEmpty()) continue;

            String[] andParts = trimmedOr.split("&");
            List<Term> andList = new ArrayList<>(andParts.length);

            for (String andPart : andParts) {
                String e = andPart.trim();
                if (e.isEmpty()) continue;
                Term t = parseTerm(e);
                if (t != null) andList.add(t);
            }
            if (!andList.isEmpty()) orClauses.add(andList);
        }
        return new ConditionSet(orClauses);
    }

    private static Term parseTerm(String expression) {
        int pos;
        String opStr;
        if ((pos = expression.indexOf(">=")) >= 0) opStr = ">=";
        else if ((pos = expression.indexOf("<=")) >= 0) opStr = "<=";
        else if ((pos = expression.indexOf(">")) >= 0) opStr = ">";
        else if ((pos = expression.indexOf("<")) >= 0) opStr = "<";
        else if ((pos = expression.indexOf("=")) >= 0) opStr = "=";
        else return null;

        String path = expression.substring(0, pos).trim();
        String valuePart = expression.substring(pos + opStr.length()).trim();
        if (path.isEmpty() || valuePart.isEmpty()) return null;

        String[] segments = splitPath(path);

        if (">=".equals(opStr) || ">".equals(opStr) || "<=".equals(opStr) || "<".equals(opStr)) {
            try {
                double d = Double.parseDouble(valuePart);
                Op op;
                switch (opStr) {
                    case ">=":
                        op = Op.GTE;
                        break;
                    case "<=":
                        op = Op.LTE;
                        break;
                    case ">":
                        op = Op.GT;
                        break;
                    default:
                        op = Op.LT;
                        break;
                }
                return new Term(segments, op, d);
            } catch (NumberFormatException ex) {
                return null;
            }
        } else {
            String low = valuePart.toLowerCase(Locale.ROOT);
            if ("true".equals(low) || "false".equals(low)) {
                boolean b = Boolean.parseBoolean(low);
                return new Term(segments, Op.EQ, b);
            }
            try {
                double d = Double.parseDouble(valuePart);
                return new Term(segments, Op.EQ, d);
            } catch (NumberFormatException ignored) {}
            return new Term(segments, Op.EQ, valuePart);
        }
    }

    private static String[] splitPath(String path) {
        String[] raw = path.split(":");
        for (int i = 0; i < raw.length; i++) raw[i] = raw[i].trim();
        return raw;
    }

    public boolean evaluate(JsonObject node) {
        for (List<Term> andBlock : orClauses) {
            boolean allMatch = true;
            for (Term t : andBlock) {
                if (!t.matches(node)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return true;
        }
        return false;
    }

    public enum Op {EQ, GT, LT, GTE, LTE}

    private enum ValueType {NUMBER, BOOLEAN, TEXT}

    private static final class Term {
        final String[] pathSegments;
        final Op op;
        final ValueType valueType;
        final double expectedNumber;
        final boolean expectedBool;
        final String expectedText;

        Term(String[] pathSegments, Op op, double expectedNumber) {
            this.pathSegments = pathSegments;
            this.op = op;
            this.valueType = ValueType.NUMBER;
            this.expectedNumber = expectedNumber;
            this.expectedBool = false;
            this.expectedText = null;
        }

        Term(String[] pathSegments, Op op, boolean expectedBool) {
            this.pathSegments = pathSegments;
            this.op = op;
            this.valueType = ValueType.BOOLEAN;
            this.expectedNumber = 0;
            this.expectedBool = expectedBool;
            this.expectedText = null;
        }

        Term(String[] pathSegments, Op op, String expectedText) {
            this.pathSegments = pathSegments;
            this.op = op;
            this.valueType = ValueType.TEXT;
            this.expectedNumber = 0;
            this.expectedBool = false;
            this.expectedText = expectedText;
        }

        boolean matches(JsonObject root) {
            Object cur = find(root);
            if (cur == null) return false;

            switch (valueType) {
                case NUMBER:
                    if (!(cur instanceof Number)) return false;
                    double actual = ((Number) cur).doubleValue();
                    return compareNumber(actual);

                case BOOLEAN:
                    boolean actualBool = parseBool(cur);
                    return op == Op.EQ && actualBool == expectedBool;

                case TEXT:
                    String actualText = String.valueOf(cur);
                    return op == Op.EQ && actualText.equalsIgnoreCase(expectedText);

                default:
                    return false;
            }
        }

        /** Przechodzi po polach obiektu NanoJSON */
        private Object find(Object root) {
            Object cur = root;

            for (String seg : pathSegments) {
                if (cur == null) return null;

                if (cur instanceof JsonObject) {
                    cur = ((JsonObject)cur).get(seg);
                } else if (cur instanceof JsonArray) {
                    int idx;
                    try {
                        idx = Integer.parseInt(seg);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    JsonArray arr = (JsonArray) cur;
                    if (idx < 0 || idx >= arr.size()) return null;
                    cur = arr.get(idx);
                } else {
                    return null;
                }
            }

            return cur;
        }

        private boolean compareNumber(double actual) {
            switch (op) {
                case EQ: return actual == expectedNumber;
                case GT: return actual > expectedNumber;
                case LT: return actual < expectedNumber;
                case GTE: return actual >= expectedNumber;
                case LTE: return actual <= expectedNumber;
                default: return false;
            }
        }

        private boolean parseBool(Object obj) {
            if (obj instanceof Boolean) return (Boolean) obj;
            if (obj instanceof String) return Boolean.parseBoolean(obj.toString());
            if (obj instanceof Number) return ((Number)obj).intValue() != 0;
            return false;
        }
    }

    @Getter
    public static final class Provider {
        private final String url;
        private final Map<String, String> headers = new HashMap<>();
        private final int priority;
        private final ConditionSet condition;

        public Provider(String url, int priority, List<String> headers, ConditionSet condition) {
            this.url = url;
            this.priority = priority;
            this.condition = condition;
            for (String header : headers) {
                String[] parts = header.split(":");
                if (parts.length != 2) continue;
                this.headers.put(parts[0].trim(), parts[1].trim());
            }
        }

        public boolean matches(JsonObject json) {
            if (condition == null) return true;
            return condition.evaluate(json);
        }
    }
}
