package com.tonic.vitavr;

import com.google.inject.Provides;
import com.tonic.vitavr.ui.SidePanel;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;

@PluginDescriptor(
        name = "VitaVR",
        description = "Play in a VR headset: first person or tabletop, with your interfaces around you",
        tags = {"vita", "vr", "virtual", "reality", "headset", "openxr"}
)
public class VitaVRPlugin extends Plugin
{
    @Inject
    private ClientToolbar clientToolbar;
    @Inject
    private VrSession session;

    private NavigationButton navButton;
    private SidePanel panel;

    @Provides
    VitaVRConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(VitaVRConfig.class);
    }

    @Override
    protected void startUp()
    {
        panel = injector.getInstance(SidePanel.class);

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

        navButton = NavigationButton.builder()
                .tooltip("VitaVR")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        // Queued so the GPU plugin applies its own flags first
        if ("gpu".equals(event.getGroup()))
        {
            SwingUtilities.invokeLater(session::gpuSettingsChanged);
        }
        else if (VitaVRConfig.GROUP.equals(event.getGroup()))
        {
            session.settingsChanged(event.getKey());
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        session.applied(event.getActor(), event.getHitsplat());
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        session.gameTick();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        session.performed(event.getMenuAction());
    }

    @Override
    protected void shutDown()
    {
        // Never leave the client without its desktop renderer
        SidePanel closing = panel;
        Runnable stop = () ->
        {
            session.stop();
            closing.close();
        };
        if (SwingUtilities.isEventDispatchThread())
        {
            stop.run();
        }
        else
        {
            SwingUtilities.invokeLater(stop);
        }
        clientToolbar.removeNavigation(navButton);
    }
}
