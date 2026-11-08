package com.tonic.vitavr.ui.gestures;

import com.tonic.vitavr.gestures.model.ItemRef;
import com.tonic.vitavr.gestures.store.GestureJson;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.function.Consumer;

/**
 * Shows an item reference with buttons to change it, or to add an item used when the ones before it are not carried.
 */
final class ItemRefField extends JPanel
{
    private final EditorContext context;
    private final Consumer<ItemRef> changed;
    private final JLabel shown = EditorKit.label("");
    private ItemRef ref;

    ItemRefField(EditorContext context, ItemRef start, Consumer<ItemRef> changed)
    {
        super(new BorderLayout(6, 0));
        setOpaque(false);
        this.context = context;
        this.changed = changed;
        this.ref = start == null ? new ItemRef() : GestureJson.copy(start, ItemRef.class);
        add(shown, BorderLayout.CENTER);
        add(EditorKit.row(EditorKit.button("Change…", this::change), EditorKit.button("Or else…", this::orElse)), BorderLayout.EAST);
        show(ref);
    }

    private void show(ItemRef now)
    {
        boolean empty = now.getMatch() == ItemRef.Match.EXACT && now.getIds().isEmpty() || now.getMatch() == ItemRef.Match.FIRST_OF && now.getOptions().isEmpty();
        shown.setText(empty ? "No item chosen" : now.describe());
        shown.setToolTipText(shown.getText());
    }

    private void change()
    {
        ItemRef picked = ItemPicker.choose(SwingUtilities.getWindowAncestor(this), context, ref.getMatch() == ItemRef.Match.FIRST_OF ? null : ref);
        if (picked != null)
        {
            set(picked);
        }
    }

    private void orElse()
    {
        ItemRef picked = ItemPicker.choose(SwingUtilities.getWindowAncestor(this), context, null);
        if (picked == null)
        {
            return;
        }
        ItemRef combined;
        if (ref.getMatch() == ItemRef.Match.FIRST_OF)
        {
            combined = GestureJson.copy(ref, ItemRef.class);
        }
        else
        {
            combined = new ItemRef();
            combined.setMatch(ItemRef.Match.FIRST_OF);
            if (!(ref.getMatch() == ItemRef.Match.EXACT && ref.getIds().isEmpty()))
            {
                combined.getOptions().add(ref);
            }
        }
        combined.getOptions().add(picked);
        set(combined);
    }

    private void set(ItemRef now)
    {
        ref = now;
        show(now);
        changed.accept(GestureJson.copy(now, ItemRef.class));
    }
}
