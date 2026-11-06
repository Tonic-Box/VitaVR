package com.tonic.vitavr.ui.filechooser;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** A file filter by extension; directories always pass, and "*" passes everything. */
public final class ExtensionFileFilter
{
    private static final String ALL = "*";

    private final String description;
    private final Set<String> extensions = new LinkedHashSet<>();

    /**
     * @param description the human-readable kind, such as "Widget design"
     * @param extensions  extensions without dots, such as "json"
     */
    public ExtensionFileFilter(String description, String... extensions)
    {
        this.description = description;
        for (String ext : extensions)
        {
            this.extensions.add(ext.toLowerCase());
        }
    }

    public static ExtensionFileFilter allFiles()
    {
        return new ExtensionFileFilter("All Files", ALL);
    }

    public boolean accept(File file)
    {
        if (file == null)
        {
            return false;
        }
        if (file.isDirectory() || isAllFiles())
        {
            return true;
        }
        String ext = extensionOf(file.getName());
        return ext != null && extensions.contains(ext);
    }

    /** @return the lower-cased extension of a file name, or null when it has none */
    static String extensionOf(String name)
    {
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1)
        {
            return name.substring(dot + 1).toLowerCase();
        }
        return null;
    }

    /** @return the description with its patterns, such as "Widget design (*.json)" */
    public String description()
    {
        if (isAllFiles())
        {
            return description + " (*.*)";
        }
        StringBuilder sb = new StringBuilder(description).append(" (");
        boolean first = true;
        for (String ext : extensions)
        {
            if (!first)
            {
                sb.append(", ");
            }
            sb.append("*.").append(ext);
            first = false;
        }
        return sb.append(')').toString();
    }

    public String rawDescription()
    {
        return description;
    }

    public Set<String> extensions()
    {
        return Collections.unmodifiableSet(extensions);
    }

    public boolean isAllFiles()
    {
        return extensions.contains(ALL);
    }

    @Override
    public String toString()
    {
        return description();
    }
}
