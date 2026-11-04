package com.tonic.vitavr.panels;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsPanelTest
{
    @Test
    void steppedLandsOnTheStepsGridWithinItsBounds()
    {
        assertEquals(45, SettingsPanel.stepped(37, 15, 1, 15, 90));
        assertEquals(15, SettingsPanel.stepped(15, 15, -1, 15, 90));
        assertEquals(90, SettingsPanel.stepped(90, 15, 1, 15, 90));
        assertEquals(0.85, SettingsPanel.stepped(0.8, 0.05, 1, 0.3, 1.5), 1e-9);
    }
}
