package com.example.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlateNosTest {

    @Test
    void normalizeStripsSpaceDotMiddleDotAndUppercases() {
        assertEquals("粤B12345", PlateNos.normalize(" 粤b·12345 "));
        assertEquals("粤B12345", PlateNos.normalize("粤B.12345"));
        assertEquals("", PlateNos.normalize(null));
        assertEquals("", PlateNos.normalize(" ·. "));
        assertEquals("AB", PlateNos.normalize("A B"));
    }
}
