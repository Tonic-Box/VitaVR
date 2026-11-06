package com.tonic.vitavr.ui.filechooser;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;

/** Paints file rows in alternating bands, the icon centred, sizes right-aligned and secondary text muted. */
final class FileListRenderer extends DefaultTableCellRenderer
{
    private final FileListModel model;

    FileListRenderer(FileListModel model)
    {
        this.model = model;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                   int row, int column)
    {
        super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
        if (isSelected)
        {
            setBackground(ChooserStyle.SELECTION);
        }
        else
        {
            setBackground(row % 2 == 0 ? ChooserStyle.BG_TERTIARY : ChooserStyle.BG_SECONDARY);
        }
        setForeground(ChooserStyle.TEXT);
        setIcon(null);
        setHorizontalAlignment(JLabel.LEFT);
        setBorder(BorderFactory.createEmptyBorder(0, ChooserStyle.px(4), 0, ChooserStyle.px(4)));
        FileListModel.FileEntry entry = model.entryAt(table.convertRowIndexToModel(row));
        if (entry == null)
        {
            return this;
        }
        switch (table.convertColumnIndexToModel(column))
        {
            case FileListModel.COL_ICON:
                setIcon((Icon) value);
                setText("");
                setHorizontalAlignment(JLabel.CENTER);
                break;
            case FileListModel.COL_NAME:
                setText(entry.name());
                break;
            case FileListModel.COL_SIZE:
                setText(entry.formattedSize());
                setHorizontalAlignment(JLabel.RIGHT);
                if (entry.isDirectory() && !isSelected)
                {
                    setForeground(ChooserStyle.TEXT_MUTED);
                }
                break;
            case FileListModel.COL_DATE:
                setText(entry.formattedDate());
                break;
            case FileListModel.COL_TYPE:
                setText(entry.type());
                if (!isSelected)
                {
                    setForeground(ChooserStyle.TEXT_MUTED);
                }
                break;
            default:
                setText(value == null ? "" : value.toString());
        }
        return this;
    }
}
