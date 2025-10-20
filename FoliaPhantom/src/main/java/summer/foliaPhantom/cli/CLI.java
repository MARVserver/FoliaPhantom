package summer.foliaPhantom.cli;

import summer.foliaPhantom.PluginPatcher;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CLI {

    private static final Logger LOGGER = Logger.getLogger("FoliaPhantom-CLI");

    public static void main(String[] args) {
        System.out.println("▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        System.out.println("▓                                                                     ▓");
        System.out.println("▓    ██████╗ ██╗  ██╗  █████╗  ███╗   ██╗ ████████╗  ██████╗ ███╗   ███╗ ▓");
        System.out.println("▓    ██╔══██╗██║  ██║ ██╔══██╗ ████╗  ██║ ╚══██╔══╝ ██╔═══██╗████╗ ████║ ▓");
        System.out.println("▓    ██████╔╝███████║ ███████║ ██╔██╗ ██║    ██║    ██║   ██║██╔████╔██║ ▓");
        System.out.println("▓    ██╔═══╝ ██╔══██║ ██╔══██║ ██║╚██╗██║    ██║    ██║   ██║██║╚██╔╝██║ ▓");
        System.out.println("▓    ██║     ██║  ██║ ██║  ██║ ██║ ╚████║    ██║    ╚██████╔╝██║ ╚═╝ ██║ ▓");
        System.out.println("▓    ╚═╝     ╚═╝  ╚═╝ ╚═╝  ╚═╝ ╚═╝  ╚═══╝    ╚═╝     ╚═════╝ ╚═╝     ╚═╝ ▓");
        System.out.println("▓                                                                     ▓");
        System.out.println("▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");


        if (args.length == 0) {
            System.out.println("Usage: java -jar FoliaPhantom-cli.jar <path to jar file>");
            return;
        }

        File inputJar = new File(args[0]);
        if (!inputJar.exists()) {
            System.err.println("Error: File not found at " + inputJar.getAbsolutePath());
            return;
        }

        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File outputJar = new File(outputDir, inputJar.getName());
        PluginPatcher patcher = new PluginPatcher(LOGGER);
        try {
            patcher.patchPlugin(inputJar, outputJar);
            System.out.println("Successfully patched " + inputJar.getName() + " -> " + outputJar.getPath());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to patch " + inputJar.getName(), e);
        }
    }
}
