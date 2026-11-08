package com.tonic.vitavr.ui.gestures;

import com.tonic.vitavr.gestures.model.Condition;
import com.tonic.vitavr.gestures.store.GestureJson;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The conditions a binding or a step needs, all of which must hold, each with what it checks and its number or item.
 */
final class ConditionsPanel extends JPanel
{
    private final EditorContext context;
    private final Consumer<List<Condition>> changed;
    private final List<Condition> conditions = new ArrayList<>();

    ConditionsPanel(EditorContext context, List<Condition> start, String title, Consumer<List<Condition>> changed)
    {
        this.context = context;
        this.changed = changed;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        EditorKit.titled(this, title);
        for (Condition condition : start)
        {
            conditions.add(GestureJson.copy(condition, Condition.class));
        }
        rebuild();
    }

    private void rebuild()
    {
        removeAll();
        if (conditions.isEmpty())
        {
            add(EditorKit.row(EditorKit.muted("None: it always runs")));
        }
        for (int i = 0; i < conditions.size(); i++)
        {
            add(row(i));
        }
        add(EditorKit.row(EditorKit.button("Add condition", () ->
        {
            conditions.add(new Condition());
            rebuild();
            send();
        })));
        revalidate();
        repaint();
    }

    private JPanel row(int index)
    {
        Condition condition = conditions.get(index);
        JPanel row = EditorKit.row(EditorKit.combo(Condition.Kind.values(), condition.getKind(), kind ->
        {
            if (kind != condition.getKind())
            {
                condition.setKind(kind);
                rebuild();
                send();
            }
        }));
        if (condition.getKind().takesItem())
        {
            row.add(new ItemRefField(context, condition.getItem(), item ->
            {
                condition.setItem(item);
                send();
            }));
        }
        else
        {
            row.add(EditorKit.spinner(condition.getValue(), 0, 255, 1, value ->
            {
                condition.setValue(value);
                send();
            }));
        }
        row.add(EditorKit.button("Remove", () ->
        {
            conditions.remove(index);
            rebuild();
            send();
        }));
        return row;
    }

    private void send()
    {
        List<Condition> copy = new ArrayList<>();
        for (Condition condition : conditions)
        {
            copy.add(GestureJson.copy(condition, Condition.class));
        }
        changed.accept(copy);
    }
}
