package de.lardtom.camdrones.planner.model;

public final class MissionNames {
    public static final int MAX_LENGTH = 24;
    public static final String DEFAULT_PREFIX = "Mission ";

    private MissionNames() {
    }

    public static String clean(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        name.codePoints().forEach(cp -> {
            if (Character.isWhitespace(cp)) {
                out.append(' ');
            } else if (cp != '§' && !Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT) {
                out.appendCodePoint(cp);
            }
        });
        String text = out.toString().strip().replaceAll(" {2,}", " ");
        if (text.codePointCount(0, text.length()) > MAX_LENGTH) {
            text = text.substring(0, text.offsetByCodePoints(0, MAX_LENGTH)).strip();
        }
        return text;
    }

    public static String defaultName(int number) {
        return DEFAULT_PREFIX + Math.max(1, number);
    }

    public static String orDefault(String name, int number) {
        String clean = clean(name);
        return clean.isEmpty() ? defaultName(number) : clean;
    }
}
