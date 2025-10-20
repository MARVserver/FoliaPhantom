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


        File inputFile;
        if (args.length == 0) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter path to JAR file or directory: ");
            String path = scanner.nextLine();
            inputFile = new File(path);
        } else {
            inputFile = new File(args[0]);
        }
        if (!inputFile.exists()) {
            System.err.println("Error: File or directory not found at " + inputFile.getAbsolutePath());
            return;
        }

        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        PluginPatcher patcher = new PluginPatcher(LOGGER);

        if (inputFile.isDirectory()) {
            File[] jarsToPatch = inputFile.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (jarsToPatch == null || jarsToPatch.length == 0) {
                System.out.println("No JARs found in the input directory. Nothing to do.");
                return;
            }
            System.out.println("Found " + jarsToPatch.length + " JAR(s) to patch.");
            for (File inputJar : jarsToPatch) {
                patchJar(patcher, inputJar, outputDir);
            }
        } else {
            patchJar(patcher, inputFile, outputDir);
        }
    }

    private static void patchJar(PluginPatcher patcher, File inputJar, File outputDir) {
        File outputJar = new File(outputDir, inputJar.getName());
        try {
            System.out.println("Patching " + inputJar.getName() + "...");
            patcher.patchPlugin(inputJar, outputJar);
            System.out.println("Successfully patched " + inputJar.getName() + " -> " + outputJar.getPath());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to patch " + inputJar.getName(), e);
        }
    }
}
