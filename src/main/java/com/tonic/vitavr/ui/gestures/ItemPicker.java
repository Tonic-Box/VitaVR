package com.tonic.vitavr.ui.gestures;

import com.tonic.vitavr.gestures.actions.ItemNames;
import com.tonic.vitavr.gestures.actions.ItemView;
import com.tonic.vitavr.gestures.model.ItemRef;
import com.tonic.vitavr.ui.Style;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.util.List;

/**
 * Picks an item reference: search every item by name or take one from the inventory or what is worn, then say how it
 * matches, as that item, any dose or charge of it, a name pattern, or any item with an op.
 */
final class ItemPicker extends JDialog
{
    private static final String[] MATCHES = {"That exact item", "Any dose or charge of it", "Names like a pattern", "Any item with an op"};
    private static final String[] PICKS = {"Lowest dose first", "First slot first", "Last slot first"};

    private final EditorContext context;
    private final DefaultListModel<ItemIndex.Entry> results = new DefaultListModel<>();
    private final JList<ItemIndex.Entry> list = EditorKit.list();
    private final JTextField search = new JTextField();
    private final JComboBox<String> match = new JComboBox<>(MATCHES);
    private final JComboBox<String> pick = new JComboBox<>(PICKS);
    private final JTextField text = new JTextField();
    private final JLabel status = EditorKit.muted(" ");
    private ItemRef chosen;

    private ItemPicker(Window owner, EditorContext context, ItemRef start)
    {
        super(owner, "Choose an item", ModalityType.APPLICATION_MODAL);
        this.context = context;
        JPanel content = new JPanel(new BorderLayout(Style.px(6), Style.px(6)));
        content.setBackground(Style.BG_PRIMARY);
        content.setBorder(BorderFactory.createEmptyBorder(Style.px(10), Style.px(10), Style.px(10), Style.px(10)));
        setContentPane(content);

        style(search);
        search.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                searchLater();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                searchLater();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                searchLater();
            }
        });
        JPanel top = new JPanel(new BorderLayout(Style.px(6), 0));
        top.setOpaque(false);
        top.add(EditorKit.label("Search"), BorderLayout.WEST);
        top.add(search, BorderLayout.CENTER);
        top.add(EditorKit.row(EditorKit.button("From inventory", () -> fromGame(false)), EditorKit.button("From what I wear", () -> fromGame(true))), BorderLayout.EAST);
        content.add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setModel(results);
        list.setCellRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> shown, Object value, int index, boolean selected, boolean focused)
            {
                JLabel cell = (JLabel) super.getListCellRendererComponent(shown, value, index, selected, focused);
                ItemIndex.Entry entry = (ItemIndex.Entry) value;
                cell.setIcon(context.icon(entry.getId(), list::repaint));
                cell.setBackground(selected ? Style.SELECTION : Style.BG_SECONDARY);
                cell.setForeground(Style.TEXT);
                return cell;
            }
        });
        list.addListSelectionListener(e ->
        {
            if (!e.getValueIsAdjusting())
            {
                fillFrom(list.getSelectedValue());
            }
        });
        content.add(EditorKit.scroll(list), BorderLayout.CENTER);

        style(text);
        match.addActionListener(e -> fillFrom(list.getSelectedValue()));
        EditorKit.Form form = new EditorKit.Form();
        form.field("Matches", match);
        form.field("Name, pattern or op", text);
        form.field("Of several, use", pick);
        form.wide(status);
        form.wide(EditorKit.row(EditorKit.button("OK", this::accept), EditorKit.button("Cancel", this::dispose)));
        content.add(form, BorderLayout.SOUTH);

        start(start);
        setPreferredSize(new Dimension(Style.px(560), Style.px(560)));
        pack();
        setLocationRelativeTo(owner);
        if (!context.getIndex().isReady())
        {
            status.setText("Reading every item's name, a moment…");
            context.getIndex().build(context.getClient(), context.getClientThread(), () -> javax.swing.SwingUtilities.invokeLater(() ->
            {
                status.setText(" ");
                runSearch();
            }));
        }
    }

    /**
     * @param owner   The window to stand over.
     * @param context The editor's context.
     * @param start   The reference to start from, or null.
     * @return The reference chosen, or null when cancelled.
     */
    static ItemRef choose(Window owner, EditorContext context, ItemRef start)
    {
        ItemPicker picker = new ItemPicker(owner, context, start);
        picker.setVisible(true);
        return picker.chosen;
    }

    private void start(ItemRef start)
    {
        if (start == null || start.getMatch() == ItemRef.Match.FIRST_OF)
        {
            return;
        }
        match.setSelectedIndex(start.getMatch().ordinal());
        text.setText(start.getMatch() == ItemRef.Match.GROUP ? start.getOp() : start.getName());
        pick.setSelectedIndex(start.getPick().ordinal());
        search.setText(start.getMatch() == ItemRef.Match.GROUP ? "" : ItemNames.base(start.getName()).replace("*", ""));
    }

    private static void style(JComponent field)
    {
        field.setFont(Style.font(Style.FONT_SIZE));
        field.setBackground(Style.BG_SECONDARY);
        field.setForeground(Style.TEXT);
        field.setBorder(Style.fieldBorder(Style.BORDER));
        if (field instanceof JTextField)
        {
            ((JTextField) field).setCaretColor(Style.TEXT);
        }
    }

    private Timer pending;

    private void searchLater()
    {
        if (pending != null)
        {
            pending.stop();
        }
        pending = new Timer(150, e -> runSearch());
        pending.setRepeats(false);
        pending.start();
    }

    private void runSearch()
    {
        List<ItemIndex.Entry> found = context.getIndex().search(search.getText());
        results.clear();
        for (ItemIndex.Entry entry : found)
        {
            results.addElement(entry);
        }
    }

    private void fromGame(boolean worn)
    {
        context.read(game -> worn ? game.worn() : game.inventory(), items ->
        {
            results.clear();
            for (ItemView item : items)
            {
                ItemIndex.Entry known = context.getIndex().entry(item.getId());
                results.addElement(known != null ? known : new ItemIndex.Entry(item.getId(), item.getName(), item.getActions()));
            }
            status.setText(items.isEmpty() ? (worn ? "Nothing worn" : "The inventory is empty") : " ");
        });
    }

    private void fillFrom(ItemIndex.Entry entry)
    {
        if (entry == null)
        {
            return;
        }
        switch (match.getSelectedIndex())
        {
            case 1:
                text.setText(ItemNames.base(entry.getName()));
                break;
            case 2:
                text.setText("*" + ItemNames.base(entry.getName()) + "*");
                break;
            case 3:
                text.setText(firstAction(entry.getActions()));
                break;
            default:
                text.setText(entry.getName());
                break;
        }
    }

    private static String firstAction(List<String> actions)
    {
        for (String action : actions)
        {
            if (action != null && !action.isEmpty() && !action.equalsIgnoreCase("Drop"))
            {
                return action;
            }
        }
        return "Eat";
    }

    private void accept()
    {
        ItemRef.Match chosenMatch = ItemRef.Match.values()[match.getSelectedIndex()];
        ItemRef ref;
        switch (chosenMatch)
        {
            case EXACT:
                ItemIndex.Entry entry = list.getSelectedValue();
                if (entry == null)
                {
                    status.setText("Choose an item from the list");
                    return;
                }
                ref = ItemRef.exact(entry.getName(), entry.getId());
                break;
            case GROUP:
                ref = ItemRef.group(text.getText().trim());
                break;
            default:
                ref = ItemRef.named(chosenMatch, text.getText().trim());
                break;
        }
        if (chosenMatch != ItemRef.Match.EXACT && text.getText().trim().isEmpty())
        {
            status.setText("Type a name, pattern or op");
            return;
        }
        ref.setPick(ItemRef.Pick.values()[pick.getSelectedIndex()]);
        chosen = ref;
        dispose();
    }
}
