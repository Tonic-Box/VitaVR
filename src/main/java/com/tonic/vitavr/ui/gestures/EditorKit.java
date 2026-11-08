package com.tonic.vitavr.ui.gestures;

import com.tonic.vitavr.ui.Style;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The gesture editor's dark widgets, and a grid of labelled rows to lay forms out in.
 */
final class EditorKit
{
    private EditorKit()
    {
    }

    static JButton button(String text, Runnable press)
    {
        JButton button = new JButton(text);
        button.setFont(Style.font(Style.FONT_SIZE));
        button.setBackground(Style.BG_TERTIARY);
        button.setForeground(Style.TEXT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Style.BORDER), BorderFactory.createEmptyBorder(Style.px(4), Style.px(10), Style.px(4), Style.px(10))));
        button.addActionListener(e -> press.run());
        return button;
    }

    static JLabel label(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(Style.font(Style.FONT_SIZE));
        label.setForeground(Style.TEXT);
        return label;
    }

    static JLabel muted(String text)
    {
        JLabel label = label(text);
        label.setForeground(Style.TEXT_MUTED);
        return label;
    }

    static JTextField field(String text, Consumer<String> committed)
    {
        JTextField field = new JTextField(text);
        field.setFont(Style.font(Style.FONT_SIZE));
        field.setBackground(Style.BG_SECONDARY);
        field.setForeground(Style.TEXT);
        field.setCaretColor(Style.TEXT);
        field.setBorder(Style.fieldBorder(Style.BORDER));
        String[] last = {text};
        Runnable commit = () ->
        {
            if (!field.getText().equals(last[0]))
            {
                last[0] = field.getText();
                committed.accept(field.getText());
            }
        };
        field.addActionListener(e -> commit.run());
        field.addFocusListener(new java.awt.event.FocusAdapter()
        {
            @Override
            public void focusLost(java.awt.event.FocusEvent e)
            {
                commit.run();
            }
        });
        return field;
    }

    static <T> JComboBox<T> combo(T[] choices, T chosen, Consumer<T> picked)
    {
        JComboBox<T> combo = new JComboBox<>(choices);
        combo.setFont(Style.font(Style.FONT_SIZE));
        combo.setBackground(Style.BG_SECONDARY);
        combo.setForeground(Style.TEXT);
        combo.setSelectedItem(chosen);
        combo.addActionListener(e ->
        {
            @SuppressWarnings("unchecked")
            T now = (T) combo.getSelectedItem();
            if (now != null)
            {
                picked.accept(now);
            }
        });
        return combo;
    }

    static JSpinner spinner(int value, int least, int most, int step, IntConsumer changed)
    {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(Math.max(least, Math.min(most, value)), least, most, step));
        spinner.setFont(Style.font(Style.FONT_SIZE));
        spinner.addChangeListener(e -> changed.accept((Integer) spinner.getValue()));
        return spinner;
    }

    static JCheckBox check(String text, boolean on, Consumer<Boolean> changed)
    {
        JCheckBox box = new JCheckBox(text, on);
        box.setFont(Style.font(Style.FONT_SIZE));
        box.setForeground(Style.TEXT);
        box.setBackground(Style.BG_PRIMARY);
        box.setFocusPainted(false);
        box.addActionListener(e -> changed.accept(box.isSelected()));
        return box;
    }

    static JPanel row(Component... parts)
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, Style.px(6), 0));
        row.setOpaque(false);
        for (Component part : parts)
        {
            row.add(part);
        }
        return row;
    }

    static JScrollPane scroll(JComponent inside)
    {
        JScrollPane scroll = new JScrollPane(inside);
        scroll.setBorder(BorderFactory.createLineBorder(Style.BORDER));
        scroll.getViewport().setBackground(Style.BG_SECONDARY);
        return scroll;
    }

    static <T> JList<T> list()
    {
        JList<T> list = new JList<>();
        list.setFont(Style.font(Style.FONT_SIZE));
        list.setBackground(Style.BG_SECONDARY);
        list.setForeground(Style.TEXT);
        list.setSelectionBackground(Style.SELECTION);
        list.setSelectionForeground(Color.WHITE);
        return list;
    }

    static void titled(JComponent component, String title)
    {
        TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Style.BORDER), title);
        border.setTitleColor(Style.TEXT_MUTED);
        border.setTitleFont(Style.boldFont(Style.FONT_SIZE));
        component.setBorder(BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(Style.px(4), Style.px(6), Style.px(4), Style.px(6))));
    }

    /**
     * A panel that fits the width of the scroll pane it is in and scrolls only up and down.
     */
    static final class FitsWidth extends JPanel implements javax.swing.Scrollable
    {
        FitsWidth(java.awt.LayoutManager layout)
        {
            super(layout);
        }

        @Override
        public java.awt.Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction)
        {
            return Style.px(16);
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction)
        {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }

    /**
     * A form of rows, each a label and a field filling the rest of the width.
     */
    static final class Form extends JPanel
    {
        private int rows;

        Form()
        {
            super(new GridBagLayout());
            setOpaque(false);
        }

        Form wide(Component field)
        {
            return field(null, field);
        }

        Form field(String name, Component field)
        {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = rows++;
            c.insets = new Insets(Style.px(3), 0, Style.px(3), Style.px(8));
            c.anchor = GridBagConstraints.WEST;
            if (name != null)
            {
                c.gridx = 0;
                add(label(name), c);
            }
            c.gridx = 1;
            c.gridwidth = name == null ? 2 : 1;
            if (name == null)
            {
                c.gridx = 0;
            }
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(field, c);
            return this;
        }

        Form end()
        {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = rows++;
            c.weighty = 1;
            add(new JPanel()
            {
                {
                    setOpaque(false);
                }
            }, c);
            return this;
        }
    }
}
