package com.jorge.homecenix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class HomeNameTest {

    @Test
    void lowercasesValidNames() {
        assertEquals(Optional.of("mi_casa-1"), HomeName.normalize("Mi_Casa-1"));
    }

    @Test
    void rejectsNamesUnsafeForDocumentIds() {
        assertTrue(HomeName.normalize("").isEmpty());
        assertTrue(HomeName.normalize("..").isEmpty());
        assertTrue(HomeName.normalize("a/b").isEmpty());
        assertTrue(HomeName.normalize("a".repeat(33)).isEmpty());
    }
}
