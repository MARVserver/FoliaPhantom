package com.patch.foliaphantom.web;

import com.patch.foliaphantom.patcher.PluginPatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Minimal CheerpJ entry point used by the static web UI. */
public final class WebPatcher {

    /**
     * Patches one plugin and returns a compact tab-separated report.
     * Header: patched count, skipped count, unchanged-after-error count.
     * P-lines contain patched classes. F-lines contain classes that were left unchanged.
     */
    public String patch(String inputPath) throws IOException {
        // CheerpJ does not benefit from the desktop ForkJoinPool here. Keep ASM work sequential so
        // one browser task owns the mutable class tree at a time and worker emulation cannot leak.
        PluginPatcher patcher = new PluginPatcher(Path.of("/files"), false, false);
        patcher.patchPlugin(Path.of(inputPath));

        StringBuilder report = new StringBuilder()
                .append(patcher.getPatchedClassCount())
                .append('\t')
                .append(patcher.getSkippedClassCount())
                .append('\t')
                .append(patcher.getFailedClassCount());

        for (String className : patcher.getPatchedClassNames()) {
            report.append('\n').append("P\t").append(cleanField(className));
        }
        for (PluginPatcher.TransformationFailure failure : patcher.getTransformationFailures()) {
            report.append('\n')
                    .append("F\t")
                    .append(cleanField(failure.className())).append('\t')
                    .append(cleanField(failure.exceptionType())).append('\t')
                    .append(cleanField(failure.message()));
        }
        return report.toString();
    }

    /** Removes a temporary output after JavaScript has copied it into a browser Blob. */
    public void delete(String path) throws IOException {
        Files.deleteIfExists(Path.of(path));
    }

    private static String cleanField(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }
}
