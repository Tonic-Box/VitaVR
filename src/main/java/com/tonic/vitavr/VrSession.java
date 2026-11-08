package com.tonic.vitavr;

import com.tonic.Logger;
import com.tonic.services.WorldOverlays;
import com.tonic.vitavr.gestures.store.GestureStore;
import com.tonic.vitavr.render.VrCallbacks;
import com.tonic.vitavr.render.scene.SceneRenderer;
import com.tonic.vitavr.render.scene.gpu.GpuScene;
import com.tonic.vitavr.render.scene.hd.HdScene;
import com.tonic.vitavr.ui.StreamWindow;
import com.tonic.vitavr.ui.filechooser.ExtensionFileFilter;
import com.tonic.vitavr.ui.filechooser.FileChooserDialog;
import com.tonic.vitavr.ui.filechooser.FileChooserResult;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.Renderable;
import net.runelite.api.MenuAction;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.gpu.GpuPluginConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A VR session that renders through 117 HD when it is running, else through the GPU plugin, and restores the desktop on exit.
 */
@Singleton
public class VrSession
{
    private static final String GPU = "net.runelite.client.plugins.gpu.GpuPlugin";
    private static final String HD = "rs117.hd.HdPlugin";

    private static final String RUNELITE_GROUP = "runelite";
    private static final String GAME_SIZE = "gameSize";
    private static final DateTimeFormatter SAVED_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private PluginManager pluginManager;
    @Inject
    private ConfigManager configManager;
    @Inject
    private VitaVRConfig config;
    @Inject
    private RenderCallbackManager renderCallbacks;

    /** Hides the desktop's flat overheads, hitsplats and health bars. */
    private final RenderCallback noFlatOverheads = new RenderCallback()
    {
        @Override
        public boolean addEntity(Renderable renderable, boolean drawingUi)
        {
            return !drawingUi;
        }
    };

    /** Called on the Swing thread with the reason when VR ends by itself or recording fails. */
    @Setter
    private Consumer<String> onEnded = reason -> {};
    /** Called on the Swing thread when the user closes the stream window. */
    @Setter
    private Runnable onStreamClosed = () -> {};
    private final StreamWindow streamWindow = new StreamWindow(this::streamClosedByUser);

    @Getter
    private boolean running;
    private Plugin gpu;
    private Plugin hd;
    private boolean gpuWasEnabled;
    private boolean hdWasEnabled;
    private boolean pausedHd;
    private boolean startedGpu;
    private HdScene hdScene;
    private final Map<String, String> desktopSettings = new LinkedHashMap<>();
    private volatile VrCallbacks callbacks;
    private GestureStore gestureStore;

    /**
     * @return The gestures, read from ~/.runelite/vitavr/gestures on first use.
     */
    public synchronized GestureStore gestures()
    {
        if (gestureStore == null)
        {
            gestureStore = new GestureStore(RuneLite.RUNELITE_DIR.toPath().resolve("vitavr").resolve("gestures"), config.gestureProfile(), config::gestureProfile);
            gestureStore.load();
        }
        return gestureStore;
    }

    /**
     * Starts VR. Must be called on the Swing thread.
     * @throws IllegalStateException If VR could not be started; the desktop is left as it was.
     */
    public void start()
    {
        if (running)
        {
            return;
        }
        running = true;
        hd = plugin(HD);
        hdWasEnabled = hd != null && pluginManager.isPluginEnabled(hd);
        hdScene = runningHd();
        try
        {
            if (hdScene == null)
            {
                useGpu();
            }
        }
        catch (IllegalStateException e)
        {
            stop();
            throw e;
        }

        override(RUNELITE_GROUP, GAME_SIZE, config.hudCanvasWidth() + "x" + config.hudCanvasHeight());
        for (String[] setting : hdScene != null ? HdScene.VR_SETTINGS : GpuScene.VR_SETTINGS)
        {
            override(setting[0], setting[1], setting[2]);
        }

        // Queued after the renderer sets its draw callbacks
        clientThread.invoke(this::install);
        if (config.streamWindow())
        {
            streamWindow.open();
        }
        Logger.info(hdScene != null ? "[VitaVR] Entering VR through 117 HD" : "[VitaVR] Entering VR through the GPU plugin");
    }

    /**
     * Stops VR and restores the desktop renderer and settings.
     * Safe to call more than once. Must be called on the Swing thread.
     */
    public void stop()
    {
        if (!running)
        {
            return;
        }
        running = false;
        streamWindow.close();
        clientThread.invoke(() ->
        {
            VrCallbacks closed = callbacks;
            uninstall();
            Path recorded = closed == null ? null : closed.getRecorded();
            String problem = closed == null ? null : closed.getRecordingProblem();
            SwingUtilities.invokeLater(() ->
            {
                restoreDesktop();
                if (problem != null)
                {
                    onEnded.accept(problem);
                }
                if (recorded != null)
                {
                    offerRecording(recorded);
                }
            });
        });
    }

    /**
     * Wraps the renderer's draw callbacks. Runs on the client thread.
     */
    private void install()
    {
        if (!running)
        {
            return;
        }
        DrawCallbacks drawing = client.getDrawCallbacks();
        SceneRenderer renderer;
        if (hdScene != null)
        {
            if (drawing != hdScene.plugin())
            {
                ended("117 HD is not drawing");
                return;
            }
            renderer = hdScene;
        }
        else
        {
            if (drawing == null)
            {
                ended("The GPU plugin did not start");
                return;
            }
            try
            {
                renderer = new GpuScene(drawing, client, configManager.getConfig(GpuPluginConfig.class));
            }
            catch (IllegalStateException e)
            {
                ended(e.getMessage());
                return;
            }
        }
        callbacks = new VrCallbacks(renderer, client, config, configManager, streamWindow, gestures(), null, this::ended);
        client.setDrawCallbacks(callbacks);
        callbacks.applyFlags();
        WorldOverlays.setHidden(true);
        renderCallbacks.register(noFlatOverheads);
    }

    /**
     * Restores the renderer's draw callbacks and closes the headset. Runs on the client thread.
     */
    private void uninstall()
    {
        if (callbacks == null)
        {
            return;
        }
        WorldOverlays.setHidden(false);
        renderCallbacks.unregister(noFlatOverheads);
        if (client.getDrawCallbacks() == callbacks)
        {
            client.setDrawCallbacks(callbacks.getRenderer().plugin());
            callbacks.restoreFlags();
        }
        callbacks.close();
        callbacks = null;
    }

    /**
     * Reapplies VitaVR's flags after a GPU plugin settings change.
     * Must be called on the Swing thread, after the GPU plugin has handled the change.
     */
    public void gpuSettingsChanged()
    {
        if (callbacks != null)
        {
            callbacks.gpuSettingsChanged();
        }
    }

    /**
     * Refreshes the headset's settings and interfaces after a settings change. Safe on any thread.
     *
     * @param key The changed setting's key.
     */
    public void settingsChanged(String key)
    {
        if (VitaVRConfig.STREAM_WINDOW.equals(key))
        {
            SwingUtilities.invokeLater(this::followStreamSetting);
        }
        VrCallbacks shown = callbacks;
        if (shown != null)
        {
            shown.settingsChanged(key);
        }
    }

    /**
     * Opens or closes the stream window to match its setting while in VR. Runs on the Swing thread.
     */
    private void followStreamSetting()
    {
        if (running && config.streamWindow())
        {
            streamWindow.open();
        }
        else if (!config.streamWindow())
        {
            streamWindow.close();
        }
    }

    /**
     * Unticks the stream window setting after the user closed the window. Runs on the Swing thread.
     */
    private void streamClosedByUser()
    {
        configManager.setConfiguration(VitaVRConfig.GROUP, VitaVRConfig.STREAM_WINDOW, false);
        onStreamClosed.run();
    }

    /**
     * Notes a hitsplat for its picture in the headset. Runs on the client thread.
     * @param actor The actor it landed on.
     * @param hitsplat The hitsplat.
     */
    public void applied(Actor actor, Hitsplat hitsplat)
    {
        VrCallbacks shown = callbacks;
        if (shown != null)
        {
            shown.applied(actor, hitsplat);
        }
    }

    /**
     * Notes a performed menu action for the pointer's click cross. Runs on the client thread.
     * @param action The menu action.
     */
    public void performed(MenuAction action)
    {
        VrCallbacks shown = callbacks;
        if (shown != null)
        {
            shown.performed(action);
        }
    }

    /**
     * Ends VR after the runtime closed it or it failed. Runs on the client thread.
     */
    private void ended(String reason)
    {
        Logger.info("[VitaVR] VR ended: " + reason);
        SwingUtilities.invokeLater(() ->
        {
            stop();
            onEnded.accept(reason);
        });
    }

    /**
     * Overrides a setting for VR, remembering its desktop value.
     */
    private void override(String group, String key, String value)
    {
        desktopSettings.put(group + "." + key, configManager.getConfiguration(group, key));
        configManager.setConfiguration(group, key, value);
    }

    private void restoreDesktop()
    {
        // Restore settings first so the desktop renderer starts with the right canvas and frame rate
        for (Map.Entry<String, String> setting : desktopSettings.entrySet())
        {
            int dot = setting.getKey().indexOf('.');
            String group = setting.getKey().substring(0, dot);
            String key = setting.getKey().substring(dot + 1);
            if (setting.getValue() == null)
            {
                configManager.unsetConfiguration(group, key);
            }
            else
            {
                configManager.setConfiguration(group, key, setting.getValue());
            }
        }
        desktopSettings.clear();

        if (startedGpu)
        {
            stopPlugin(gpu);
        }

        // Starting the GPU plugin disables 117 HD, so restore both before HD restarts
        if (gpu != null)
        {
            pluginManager.setPluginEnabled(gpu, gpuWasEnabled);
        }
        if (hd != null)
        {
            pluginManager.setPluginEnabled(hd, hdWasEnabled);
        }
        if (pausedHd)
        {
            startPlugin(hd);
        }
        startedGpu = false;
        pausedHd = false;
        gpu = null;
        hd = null;
        hdScene = null;
        Logger.info("[VitaVR] Back to desktop");
    }

    /**
     * Asks where to save the recording, deleting it if declined. Must be called on the Swing thread.
     */
    private void offerRecording(Path recorded)
    {
        String suggested = "vitavr-" + LocalDateTime.now().format(SAVED_STAMP) + ".mp4";
        FileChooserResult chosen = FileChooserDialog.showSaveDialog(SwingUtilities.getWindowAncestor(client.getCanvas()), "Save the session's recording", suggested,
                new ExtensionFileFilter("MP4 video", "mp4"));
        try
        {
            if (chosen.approved() && chosen.selectedFile() != null)
            {
                Path target = chosen.selectedFile().toPath();
                if (!target.getFileName().toString().toLowerCase().endsWith(".mp4"))
                {
                    target = target.resolveSibling(target.getFileName() + ".mp4");
                }
                Files.move(recorded, target, StandardCopyOption.REPLACE_EXISTING);
                Logger.info("[VitaVR] Saved the recording to " + target);
                return;
            }
            Files.deleteIfExists(recorded);
        }
        catch (IOException e)
        {
            onEnded.accept("The recording could not be saved; it is still at " + recorded);
            Logger.warn("[VitaVR] The recording could not be saved, it is still at " + recorded + ": " + e.getMessage());
        }
    }

    /**
     * Wraps 117 HD if it is running and VitaVR can draw through it.
     *
     * @return The wrapped 117 HD, or null to draw through the GPU plugin.
     */
    private HdScene runningHd()
    {
        if (hd == null || !pluginManager.isPluginActive(hd))
        {
            return null;
        }
        try
        {
            return new HdScene(hd);
        }
        catch (IllegalStateException e)
        {
            Logger.warn("[VitaVR] " + e.getMessage() + "; drawing through the GPU plugin instead");
            return null;
        }
    }

    /**
     * Pauses 117 HD and starts the GPU plugin if it is not running.
     */
    private void useGpu()
    {
        gpu = plugin(GPU);
        if (gpu == null)
        {
            throw new IllegalStateException("The GPU plugin is missing");
        }
        gpuWasEnabled = pluginManager.isPluginEnabled(gpu);

        if (hd != null && pluginManager.isPluginActive(hd))
        {
            if (!stopPlugin(hd))
            {
                throw new IllegalStateException("Could not pause 117 HD");
            }
            pausedHd = true;
        }
        if (!pluginManager.isPluginActive(gpu))
        {
            // A disabled plugin refuses to start
            pluginManager.setPluginEnabled(gpu, true);
            if (!startPlugin(gpu))
            {
                throw new IllegalStateException("Could not start the GPU plugin");
            }
            startedGpu = true;
        }
    }

    private Plugin plugin(String className)
    {
        for (Plugin plugin : pluginManager.getPlugins())
        {
            if (plugin.getClass().getName().equals(className))
            {
                return plugin;
            }
        }
        return null;
    }

    private boolean startPlugin(Plugin plugin)
    {
        try
        {
            return pluginManager.startPlugin(plugin);
        }
        catch (PluginInstantiationException e)
        {
            Logger.error("[VitaVR] Failed to start " + plugin.getName(), e);
            return false;
        }
    }

    private boolean stopPlugin(Plugin plugin)
    {
        try
        {
            return pluginManager.stopPlugin(plugin);
        }
        catch (PluginInstantiationException e)
        {
            Logger.error("[VitaVR] Failed to stop " + plugin.getName(), e);
            return false;
        }
    }
}
