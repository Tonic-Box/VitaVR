package com.tonic.vitavr;

import com.tonic.Logger;
import com.tonic.model.ui.components.FancyButton;
import com.tonic.model.ui.components.FancyCard;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.api.GameState;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;

/**
 * The side panel: moves the session into VR and back.
 */
public class SidePanel extends PluginPanel
{
    private static final Color LAUNCH_COLOR = new Color(64, 169, 211);
    private static final Color BACK_COLOR = Color.RED;

    private final VrSession session;
    private final Client client;
    private final JButton launchButton;
    private final JCheckBox recordBox;
    private final JLabel statusLabel;

    @Inject
    public SidePanel(VrSession session, Client client, VitaVRConfig config, ConfigManager configManager)
    {
        this.session = session;
        this.client = client;

        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 10, 0);

        FancyCard card = new FancyCard("VitaVR", "Moves your logged in session into a VR headset, and back.");
        add(card, c);
        c.gridy++;

        launchButton = new FancyButton("Launch in VR");
        launchButton.setFocusable(false);
        launchButton.addActionListener(e -> toggle());
        add(launchButton, c);
        c.gridy++;

        recordBox = new JCheckBox("Record the session");
        recordBox.setToolTipText("Records the session to a 1080p 60 fps video with the interfaces and pointers in it; you choose where to save it when you are back on the desktop");
        recordBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        recordBox.setFocusable(false);
        recordBox.setSelected(config.recordSession());
        recordBox.addActionListener(e -> configManager.setConfiguration(VitaVRConfig.GROUP, VitaVRConfig.RECORD_SESSION, recordBox.isSelected()));
        add(recordBox, c);
        c.gridy++;

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setForeground(Color.LIGHT_GRAY);
        add(statusLabel, c);
        c.gridy++;

        c.weighty = 1;
        add(new JPanel(), c);
        refresh(null);

        // The headset can end VR by itself
        session.setOnEnded(this::refresh);
    }

    private void toggle()
    {
        if (session.isRunning())
        {
            session.stop();
            refresh(null);
            return;
        }

        if (client.getGameState() != GameState.LOGGED_IN)
        {
            refresh("Log in first.");
            return;
        }

        try
        {
            session.start();
            refresh(null);
        }
        catch (RuntimeException e)
        {
            Logger.error("[VitaVR] Could not launch VR", e);
            session.stop();
            refresh(e.getMessage());
        }
    }

    /**
     * Updates the button and status for the current session state.
     * @param problem The error to show, or null.
     */
    private void refresh(String problem)
    {
        boolean running = session.isRunning();
        launchButton.setText(running ? "Back to desktop" : "Launch in VR");
        launchButton.setBackground(running ? BACK_COLOR : LAUNCH_COLOR);
        recordBox.setEnabled(!running);
        statusLabel.setText(problem != null ? problem : running ? "In VR" : " ");
    }
}
