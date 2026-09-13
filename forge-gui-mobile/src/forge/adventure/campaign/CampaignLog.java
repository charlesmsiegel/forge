package forge.adventure.campaign;

import forge.adventure.util.Config;
import forge.adventure.world.WorldSave;
import forge.item.PaperCard;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local playtest instrumentation (MVP.md §34): one JSON object per line appended to
 * {@code <user adventure dir>/<plane>/telemetry.jsonl}. No external service.
 */
public final class CampaignLog {
    private static String path;

    private CampaignLog() {
    }

    /** Override the output file (tests); null restores the default location. */
    public static void setPath(String p) {
        path = p;
    }

    private static String resolvePath() {
        if (path == null) {
            try {
                path = WorldSave.getSaveDir() + File.separator + "telemetry.jsonl";
            } catch (Throwable t) {
                path = "telemetry.jsonl";
            }
        }
        return path;
    }

    /** Convenience builder: {@code CampaignLog.event("match").with("won", true).write();} */
    public static Event event(String name) {
        return new Event(name);
    }

    public static final class Event {
        private final Map<String, Object> fields = new LinkedHashMap<>();

        private Event(String name) {
            fields.put("ts", Instant.now().toString());
            fields.put("event", name);
            try {
                fields.put("plane", Config.instance().getPlane());
            } catch (Throwable ignored) {
            }
        }

        public Event with(String key, Object value) {
            fields.put(key, value);
            return this;
        }

        public Event withCards(String key, Collection<PaperCard> cards) {
            fields.put(key, cards.stream().map(CampaignLog::describe).toList());
            return this;
        }

        public void write() {
            CampaignLog.write(fields);
        }
    }

    /** Stable textual identity of a printing: {@code Name|EDITION|collectorNumber[|foil]}. */
    public static String describe(PaperCard card) {
        return card.getName() + "|" + card.getEdition() + "|" + card.getCollectorNumber() + (card.isFoil() ? "|foil" : "");
    }

    static synchronized void write(Map<String, Object> fields) {
        String target = resolvePath();
        try {
            File file = new File(target);
            File dir = file.getParentFile();
            if (dir != null)
                dir.mkdirs();
            try (Writer w = new FileWriter(file, true)) {
                w.write(toJson(fields));
                w.write('\n');
            }
        } catch (IOException e) {
            System.err.println("CampaignLog: cannot write " + target + ": " + e);
        }
    }

    static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, value);
        return sb.toString();
    }

    private static void appendJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                appendString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                appendJson(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof Iterable<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object o : list) {
                if (!first) sb.append(',');
                first = false;
                appendJson(sb, o);
            }
            sb.append(']');
        } else if (value instanceof PaperCard card) {
            appendString(sb, describe(card));
        } else {
            appendString(sb, String.valueOf(value));
        }
    }

    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
