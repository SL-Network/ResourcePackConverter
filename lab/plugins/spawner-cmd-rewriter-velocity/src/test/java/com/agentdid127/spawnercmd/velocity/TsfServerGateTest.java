package com.agentdid127.spawnercmd.velocity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsfServerGateTest {
    @Test
    void matchesPrefixCaseInsensitive() {
        TsfServerGate gate = new TsfServerGate("terra_swoop_force", org.slf4j.LoggerFactory.getLogger("test"), false);
        assertTrue(gate.matchesServerName("terra_swoop_force"));
        assertTrue(gate.matchesServerName("terra_swoop_force_1"));
        assertTrue(gate.matchesServerName("Terra_Swoop_Force_EU"));
        assertFalse(gate.matchesServerName("lobby"));
        assertFalse(gate.matchesServerName("force_terra_swoop"));
        assertFalse(gate.matchesServerName(null));
    }
}
