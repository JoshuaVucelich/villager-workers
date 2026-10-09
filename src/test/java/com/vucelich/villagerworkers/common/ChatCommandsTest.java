package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatCommandsTest {
    @Test
    void ignoresChatThatIsNotAWorkerCommand() {
        assertTrue(ChatCommands.match("hello", List.of("Bob")).isEmpty());
        assertTrue(ChatCommands.match("@Nobody come", List.of("Bob")).isEmpty());
        assertTrue(ChatCommands.match("@", List.of("Bob")).isEmpty());
    }

    @Test
    void matchesVerbsAfterTheFullName() {
        Optional<ChatCommands.Match> match = ChatCommands.match("@Bob the Builder come", List.of("Bob", "Bob the Builder"));
        assertTrue(match.isPresent());
        assertEquals("Bob the Builder", match.get().name());
        assertEquals(List.of(1), match.get().workerIndexes());
        assertEquals(Optional.of(ChatCommands.Verb.COME), match.get().verb());
        assertFalse(match.get().ambiguous());
    }

    @Test
    void shorterNameDoesNotSwallowTheRestOfALongerName() {
        Optional<ChatCommands.Match> match = ChatCommands.match("@Bob the Builder come", List.of("Bob"));
        assertTrue(match.isPresent());
        assertEquals("Bob", match.get().name());
        assertTrue(match.get().verb().isEmpty());
    }

    @Test
    void sameNameIsAmbiguous() {
        Optional<ChatCommands.Match> match = ChatCommands.match("@Bob stop", List.of("Bob", "Bob"));
        assertTrue(match.isPresent());
        assertTrue(match.get().ambiguous());
        assertEquals(Optional.of(ChatCommands.Verb.STOP), match.get().verb());
    }

    @Test
    void verbsAreCaseInsensitive() {
        Optional<ChatCommands.Match> match = ChatCommands.match("@Ada  ReSuMe", List.of("Ada"));
        assertEquals(Optional.of(ChatCommands.Verb.RESUME), match.orElseThrow().verb());
    }

    @Test
    void unknownVerbIsNotAMatchFailure() {
        Optional<ChatCommands.Match> match = ChatCommands.match("@Ada dance", List.of("Ada"));
        assertTrue(match.isPresent());
        assertTrue(match.get().verb().isEmpty());
    }
}
