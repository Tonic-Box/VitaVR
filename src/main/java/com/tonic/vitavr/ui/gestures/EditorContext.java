package com.tonic.vitavr.ui.gestures;

import com.tonic.vitavr.gestures.actions.Game;
import com.tonic.vitavr.gestures.actions.GestureRunner;
import com.tonic.vitavr.gestures.store.GestureStore;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.Image;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * What the editor's parts share: the gestures, the runner, the game read on the client thread, and item names and icons.
 */
@Getter
final class EditorContext
{
    private static final int ICON = 24;

    private final GestureStore store;
    private final GestureRunner runner;
    private final Game game;
    private final Client client;
    private final ClientThread clientThread;
    private final ItemManager itemManager;
    private final ItemIndex index = new ItemIndex();
    private final Map<Integer, ImageIcon> icons = new HashMap<>();

    EditorContext(GestureStore store, GestureRunner runner, Game game, Client client, ClientThread clientThread, ItemManager itemManager)
    {
        this.store = store;
        this.runner = runner;
        this.game = game;
        this.client = client;
        this.clientThread = clientThread;
        this.itemManager = itemManager;
    }

    /**
     * Reads something from the game on the client thread and hands it over on the Swing thread.
     *
     * @param reading What to read.
     * @param then    Given what was read, on the Swing thread.
     * @param <T>     What is read.
     */
    <T> void read(Function<Game, T> reading, Consumer<T> then)
    {
        clientThread.invoke(() ->
        {
            T read = reading.apply(game);
            SwingUtilities.invokeLater(() -> then.accept(read));
        });
    }

    /**
     * @param id     An item id.
     * @param loaded Run on the Swing thread once the icon has loaded.
     * @return The item's icon, blank until loaded.
     */
    ImageIcon icon(int id, Runnable loaded)
    {
        ImageIcon cached = icons.get(id);
        if (cached != null)
        {
            return cached;
        }
        AsyncBufferedImage image = itemManager.getImage(id);
        ImageIcon icon = new ImageIcon(image.getScaledInstance(ICON, ICON, Image.SCALE_SMOOTH));
        icons.put(id, icon);
        image.onLoaded(() -> SwingUtilities.invokeLater(() ->
        {
            icon.setImage(image.getScaledInstance(ICON, ICON, Image.SCALE_SMOOTH));
            loaded.run();
        }));
        return icon;
    }
}
