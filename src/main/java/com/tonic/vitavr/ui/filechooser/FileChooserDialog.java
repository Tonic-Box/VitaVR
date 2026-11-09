package com.tonic.vitavr.ui.filechooser;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A file dialog in place of JFileChooser: a breadcrumb path bar, pinned and recent places, a sortable file table and
 * type filters, in the client's side theme. Opens in the directory of the last pick and remembers the directory of each
 * approved one.
 */
public final class FileChooserDialog extends JDialog
{
    private final FileChooserPanel panel;
    private final QuickAccessStore store;
    private FileChooserResult result = FileChooserResult.cancelled();

    private FileChooserDialog(Window owner, String title, QuickAccessStore store)
    {
        super(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        this.store = store;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        panel = new FileChooserPanel(store);
        panel.fileChooserListener(new FileChooserPanel.FileChooserListener()
        {
            @Override
            public void onFilesSelected(List<File> files)
            {
                if (!files.isEmpty())
                {
                    result = FileChooserResult.approved(files);
                    File first = files.get(0);
                    store.lastDirectory(first.isDirectory() ? first : first.getParentFile());
                }
                dispose();
            }

            @Override
            public void onCancelled()
            {
                result = FileChooserResult.cancelled();
                dispose();
            }
        });
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosed(WindowEvent e)
            {
                panel.dispose();
            }
        });
        add(panel);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog and blocks until it closes. */
    public FileChooserResult showDialog()
    {
        setVisible(true);
        return result;
    }

    public static FileChooserResult showOpenDialog(Component parent, String title, ExtensionFileFilter... filters)
    {
        return builder().mode(FileChooserMode.OPEN_FILE).title(title).filters(filters).build(parent).showDialog();
    }

    public static FileChooserResult showSaveDialog(Component parent, String title, String suggestedName, ExtensionFileFilter... filters)
    {
        return builder().mode(FileChooserMode.SAVE_FILE).title(title).fileName(suggestedName).filters(filters).build(parent).showDialog();
    }

    public static FileChooserResult showDirectoryDialog(Component parent, String title)
    {
        return builder().mode(FileChooserMode.SELECT_DIRECTORY).title(title).build(parent).showDialog();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static final class Builder
    {
        private FileChooserMode mode = FileChooserMode.OPEN_FILE;
        private String title = "Select File";
        private File directory;
        private String fileName = "";
        private final List<ExtensionFileFilter> filters = new ArrayList<>();
        private QuickAccessStore store;

        private Builder()
        {
        }

        public Builder mode(FileChooserMode mode)
        {
            this.mode = mode;
            return this;
        }

        public Builder title(String title)
        {
            this.title = title;
            return this;
        }

        /** Starts in this directory instead of the remembered one. */
        public Builder directory(File directory)
        {
            this.directory = directory;
            return this;
        }

        public Builder fileName(String name)
        {
            this.fileName = name == null ? "" : name;
            return this;
        }

        public Builder filters(ExtensionFileFilter... filters)
        {
            if (filters != null)
            {
                for (ExtensionFileFilter filter : filters)
                {
                    if (filter != null)
                    {
                        this.filters.add(filter);
                    }
                }
            }
            return this;
        }

        /** The places store to use instead of the shared one. */
        public Builder store(QuickAccessStore store)
        {
            this.store = store;
            return this;
        }

        public FileChooserDialog build(Component parent)
        {
            QuickAccessStore places = store == null ? QuickAccessStore.shared() : store;
            FileChooserDialog dialog = new FileChooserDialog(windowOf(parent), title, places);
            FileChooserPanel panel = dialog.panel;
            if (!filters.isEmpty())
            {
                panel.fileFilters(filters.toArray(new ExtensionFileFilter[0]));
            }
            panel.mode(mode);
            File start = directory != null ? directory : places.lastDirectory();
            if (start == null || !start.isDirectory())
            {
                start = new File(System.getProperty("user.home"));
            }
            panel.currentDirectory(start);
            if (!fileName.isEmpty())
            {
                panel.selectedFileName(fileName);
                panel.focusFileNameField();
            }
            return dialog;
        }

        private static Window windowOf(Component component)
        {
            if (component == null)
            {
                return null;
            }
            return component instanceof Window ? (Window) component : SwingUtilities.getWindowAncestor(component);
        }
    }
}
