package com.vucelich.villagerworkers.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Parses {@code @Name come|stop|resume|status}. Names are matched exactly, including spaces.
 * The longest matching name wins. This class does not check permissions.
 */
public final class ChatCommands {
    private ChatCommands() {}

    public enum Verb {
        COME,
        STOP,
        RESUME,
        STATUS;

        public static Optional<Verb> parse(String word) {
            if (word == null) {
                return Optional.empty();
            }
            String normalized = word.trim().toLowerCase(Locale.ROOT);
            for (Verb verb : values()) {
                if (verb.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return Optional.of(verb);
                }
            }
            return Optional.empty();
        }
    }

    public record Match(String name, List<Integer> workerIndexes, Optional<Verb> verb) {
        public boolean ambiguous() {
            return workerIndexes.size() > 1;
        }
    }

    public static Optional<Match> match(String message, List<String> workerNames) {
        if (message == null || !message.startsWith("@") || workerNames == null) {
            return Optional.empty();
        }
        String body = message.substring(1);
        int bestLength = -1;
        List<Integer> hits = new ArrayList<>();
        for (int i = 0; i < workerNames.size(); i++) {
            String name = workerNames.get(i);
            if (name == null || name.isEmpty() || !body.startsWith(name)) {
                continue;
            }
            boolean boundary = body.length() == name.length() || Character.isWhitespace(body.charAt(name.length()));
            if (!boundary) {
                continue;
            }
            if (name.length() > bestLength) {
                bestLength = name.length();
                hits.clear();
                hits.add(i);
            } else if (name.length() == bestLength) {
                hits.add(i);
            }
        }
        if (hits.isEmpty()) {
            return Optional.empty();
        }
        String name = workerNames.get(hits.get(0));
        String rest = body.substring(name.length()).trim();
        return Optional.of(new Match(name, List.copyOf(hits), Verb.parse(rest)));
    }
}
