package com.tonic.vitavr.ui.filechooser;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Component;
import java.awt.Dimension;
import java.util.function.Consumer;

/** The file-type drop-down; "All Files" is always offered last. */
final class FileFilterComboBox extends JComboBox<ExtensionFileFilter>
{
    private final DefaultComboBoxModel<ExtensionFileFilter> model = new DefaultComboBoxModel<>();
    private Consumer<ExtensionFileFilter> listener;

    FileFilterComboBox()
    {
        setModel(model);
        setFont(ChooserStyle.font(ChooserStyle.FONT_SIZE));
        setPreferredSize(new Dimension(ChooserStyle.px(180), ChooserStyle.px(28)));
        setRenderer(new FilterRenderer());
        model.addElement(ExtensionFileFilter.allFiles());
        addActionListener(e ->
        {
            if (listener != null)
            {
                listener.accept(selectedFilter());
            }
        });
    }

    void filterChangeListener(Consumer<ExtensionFileFilter> listener)
    {
        this.listener = listener;
    }

    void filters(ExtensionFileFilter... filters)
    {
        model.removeAllElements();
        if (filters != null)
        {
            for (ExtensionFileFilter filter : filters)
            {
                if (filter != null && !filter.isAllFiles())
                {
                    model.addElement(filter);
                }
            }
        }
        model.addElement(ExtensionFileFilter.allFiles());
        setSelectedIndex(0);
    }

    ExtensionFileFilter selectedFilter()
    {
        return (ExtensionFileFilter) getSelectedItem();
    }

    private static final class FilterRenderer extends JLabel implements ListCellRenderer<ExtensionFileFilter>
    {
        FilterRenderer()
        {
            setOpaque(true);
            setFont(ChooserStyle.font(ChooserStyle.FONT_SIZE));
            setBorder(BorderFactory.createEmptyBorder(ChooserStyle.px(4), ChooserStyle.px(8), ChooserStyle.px(4), ChooserStyle.px(8)));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ExtensionFileFilter> list, ExtensionFileFilter value,
                                                      int index, boolean isSelected, boolean cellHasFocus)
        {
            setBackground(isSelected ? ChooserStyle.SELECTION : ChooserStyle.BG_TERTIARY);
            setForeground(ChooserStyle.TEXT);
            setText(value == null ? "" : value.description());
            return this;
        }
    }
}
