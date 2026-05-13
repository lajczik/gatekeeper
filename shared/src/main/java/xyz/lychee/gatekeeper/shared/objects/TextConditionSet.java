package xyz.lychee.gatekeeper.shared.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextConditionSet extends AbstractConditionSet {
    private static final Pattern TERM_PATTERN = Pattern.compile("^(equals|contains|starts_with|ends_with)=(.+)$");
    private final Term[][] orClauses;

    private TextConditionSet(Term[][] orClauses) {
        this.orClauses = orClauses;
    }

    public static TextConditionSet compile(String expr) {
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
        return new TextConditionSet(compiledOrs.toArray(new Term[0][]));
    }

    private static Term parseTerm(String expression) {
        Matcher m = TERM_PATTERN.matcher(expression);
        if (!m.matches()) return null;

        String opStr = m.group(1);
        String valuePart = m.group(2).trim();

        if (valuePart.isEmpty()) return null;

        switch (opStr) {
            case "equals":
                return new Term(StringOp.EQUALS, valuePart);
            case "contains":
                return new Term(StringOp.CONTAINS, valuePart);
            case "starts_with":
                return new Term(StringOp.STARTS_WITH, valuePart);
            case "ends_with":
                return new Term(StringOp.ENDS_WITH, valuePart);
            default:
                return null;
        }
    }

    @Override
    public boolean evaluate(String str) {
        if (str == null || orClauses == null) return false;

        for (Term[] andBlock : orClauses) {
            String lowerCase = str.toLowerCase();
            for (Term t : andBlock) {
                if (t.matches(lowerCase)) {
                    return true;
                }
            }
        }
        return false;
    }

    public enum StringOp {
        EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH
    }

    private static final class Term {
        private final StringOp op;
        private final String expectedText;

        public Term(StringOp op, String expectedText) {
            this.op = op;
            this.expectedText = expectedText.toLowerCase();
        }

        boolean matches(String text) {
            if (text == null) return false;

            switch (op) {
                case EQUALS:
                    return text.equals(expectedText);
                case CONTAINS:
                    return text.contains(expectedText);
                case STARTS_WITH:
                    return text.startsWith(expectedText);
                case ENDS_WITH:
                    return text.endsWith(expectedText);
                default:
                    return false;
            }
        }
    }
}