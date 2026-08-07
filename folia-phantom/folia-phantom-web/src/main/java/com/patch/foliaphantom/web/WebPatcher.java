package com.patch.foliaphantom.web;

import com.patch.foliaphantom.patcher.PluginPatcher;

import java.io.IOException;
import java.nio.file.Path;

/** Minimal CheerpJ entry point used by the static web UI. */
public final class WebPatcher {

    public void patch(String inputPath) throws IOException {
        PluginPatcher patcher = new PluginPatcher(Path.of("/files"), false);
        patcher.patchPlugin(Path.of(inputPath));
    }
}
