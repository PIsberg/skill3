package se.deversity.skill3.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetupCommandTest {

    @Test
    void probeMinorReturnsNegativeForMissingInterpreter() {
        assertEquals(-1, SetupCommand.probeMinor(List.of("skill3-not-a-python-xyz")));
    }
}
