package com.tonic.vitavr.ui;

import com.tonic.Logger;
import com.tonic.model.ui.components.FancyButton;
import com.tonic.model.ui.components.FancyCard;
import com.tonic.vitavr.VitaVRConfig;
import com.tonic.vitavr.VrSession;
import com.tonic.vitavr.gestures.actions.VitaGame;
import com.tonic.vitavr.gestures.model.Profile;
import com.tonic.vitavr.gestures.store.GestureStore;
import com.tonic.vitavr.gestures.store.Gestures;
import com.tonic.vitavr.ui.gestures.GestureEditorWindow;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import java.awt.*;
import javax.inject.Inject;
import javax.swing.*;

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
    private final JCheckBox hideChatBox;
    private final JCheckBox streamBox;
    private final JLabel statusLabel;
    private final JComboBox<String> profileBox = new JComboBox<>();
    private final ClientThread clientThread;
    private final ItemManager itemManager;
    private GestureEditorWindow editor;
    private boolean loadingProfiles;
    private final Runnable profilesChanged = () -> SwingUtilities.invokeLater(this::showProfiles);

    @Inject
    public SidePanel(VrSession session, Client client, VitaVRConfig config, ConfigManager configManager, ClientThread clientThread, ItemManager itemManager)
    {
        this.session = session;
        this.client = client;
        this.clientThread = clientThread;
        this.itemManager = itemManager;

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

        streamBox = new JCheckBox("Stream window");
        streamBox.setToolTipText("Opens a window showing the VR view with the interfaces, for window capture in streaming software; it uses the recording's view settings");
        streamBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        streamBox.setFocusable(false);
        streamBox.setSelected(config.streamWindow());
        streamBox.addActionListener(e -> configManager.setConfiguration(VitaVRConfig.GROUP, VitaVRConfig.STREAM_WINDOW, streamBox.isSelected()));
        add(streamBox, c);
        c.gridy++;

        hideChatBox = new JCheckBox("Hide chat in recordings and the stream");
        hideChatBox.setToolTipText("Leaves the chatbox out of the video and the stream window; it still shows in the headset");
        hideChatBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        hideChatBox.setFocusable(false);
        hideChatBox.setSelected(config.recordHideChat());
        hideChatBox.addActionListener(e -> configManager.setConfiguration(VitaVRConfig.GROUP, VitaVRConfig.RECORD_HIDE_CHAT, hideChatBox.isSelected()));
        add(hideChatBox, c);
        c.gridy++;

        JLabel profileLabel = new JLabel("Gesture profile");
        profileLabel.setForeground(Color.LIGHT_GRAY);
        add(profileLabel, c);
        c.gridy++;
        profileBox.setFocusable(false);
        profileBox.addActionListener(e ->
        {
            Object chosen = profileBox.getSelectedItem();
            GestureStore store = session.gestures();
            if (!loadingProfiles && chosen != null && !chosen.equals(store.snapshot().getActive()))
            {
                store.update(gestures -> gestures.setActive((String) chosen));
            }
        });
        add(profileBox, c);
        c.gridy++;

        JButton editorButton = new FancyButton("Gesture editor…");
        editorButton.setFocusable(false);
        editorButton.addActionListener(e -> openEditor());
        add(editorButton, c);
        c.gridy++;

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setForeground(Color.LIGHT_GRAY);
        add(statusLabel, c);
        c.gridy++;

        c.weighty = 1;
        add(new JPanel(), c);
        refresh(null);

        session.gestures().addListener(profilesChanged);
        showProfiles();

        // The headset can end VR by itself
        session.setOnEnded(this::refresh);
        session.setOnStreamClosed(() -> streamBox.setSelected(false));
    }

    /**
     * Stops following the gestures and closes the editor, when the plugin stops. Call on the Swing thread.
     */
    public void close()
    {
        session.gestures().removeListener(profilesChanged);
        if (editor != null)
        {
            editor.close();
            editor = null;
        }
    }

    private void showProfiles()
    {
        Gestures gestures = session.gestures().snapshot();
        loadingProfiles = true;
        try
        {
            profileBox.removeAllItems();
            for (Profile profile : gestures.getProfiles())
            {
                profileBox.addItem(profile.getName());
            }
            profileBox.setSelectedItem(gestures.activeProfile() == null ? null : gestures.activeProfile().getName());
        }
        finally
        {
            loadingProfiles = false;
        }
    }

    private void openEditor()
    {
        if (editor == null)
        {
            editor = GestureEditorWindow.create(session.gestures(), session.gestureRunner(), new VitaGame(client), client, clientThread, itemManager);
        }
        editor.open();
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
