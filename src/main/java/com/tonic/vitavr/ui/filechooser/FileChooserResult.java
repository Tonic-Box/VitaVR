package com.tonic.vitavr.ui.filechooser;

import java.io.File;
import java.util.Collections;
import java.util.List;

/** The outcome of a chooser dialog: approved with one or more files, or cancelled. */
public final class FileChooserResult
{
    private final boolean approved;
    private final List<File> selectedFiles;

    private FileChooserResult(boolean approved, List<File> selectedFiles)
    {
        this.approved = approved;
        this.selectedFiles = selectedFiles == null ? Collections.emptyList() : List.copyOf(selectedFiles);
    }

    public static FileChooserResult approved(File file)
    {
        return new FileChooserResult(true, Collections.singletonList(file));
    }

    public static FileChooserResult approved(List<File> files)
    {
        return new FileChooserResult(true, files);
    }

    public static FileChooserResult cancelled()
    {
        return new FileChooserResult(false, null);
    }

    public boolean approved()
    {
        return approved;
    }

    public boolean isCancelled()
    {
        return !approved;
    }

    /** @return the first selected file, or null when cancelled */
    public File selectedFile()
    {
        return selectedFiles.isEmpty() ? null : selectedFiles.get(0);
    }

    public List<File> selectedFiles()
    {
        return selectedFiles;
    }
}
