package io.github.maxhayim.filescard;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** The file managers the card knows, in the order it prefers them. No Android types: unit tested. */
final class FileManagers {
    /** Amaze first: free, open source, no ads, and it has a TV tile. Every package is also in the manifest's queries. */
    static final List<String> KNOWN = Collections.unmodifiableList(Arrays.asList(
            "com.amaze.filemanager",           // Amaze File Manager
            "com.lonelycatgames.Xplore",       // X-plore
            "pl.solidexplorer2",               // Solid Explorer
            "nextapp.fx",                      // FX File Explorer
            "com.cxinventor.file.explorer",    // CX File Explorer
            "com.google.android.apps.nbu.files" // Files by Google
    ));

    private FileManagers() {
    }

    /** The first known file manager that is installed and can be opened, or null when there is none. */
    static String pick(Collection<String> openable) {
        for (String pkg : KNOWN) if (openable.contains(pkg)) return pkg;
        return null;
    }
}
