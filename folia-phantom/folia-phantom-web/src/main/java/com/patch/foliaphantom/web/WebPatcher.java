package com.patch.foliaphantom.web;

import com.patch.foliaphantom.patcher.PluginPatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Minimal CheerpJ entry point used by the static web UI. */
public final class WebPatcher {

    /**
     * Patches one plugin and returns a compact text report.
     * First line: patched-class count, tab, skipped-class count.
     * Remaining lines: fully qualified names of patched classes.
     */
    public String patch(String inputPath) throws IOException {
        PluginPatcher patcher = new PluginPatcher(Path.of("/files"), false);
        patcher.patchPlugin(Path.of(inputPath));

        StringBuilder report = new StringBuilder()
                .append(patcher.getPatchedClassCount())
                .append('\t')
                .append(patcher.getSkippedClassCount());
        for (String className : patcher.getPatchedClassNames()) {
            report.append('\n').append(className);
        }
        return report.toString();
    }

    /** Removes a temporary output after JavaScript has copied it into a browser Blob. */
    public void delete(String path) throws IOException {
        Files.deleteIfExists(Path.of(path));
    }
}
