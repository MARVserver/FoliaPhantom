package com.patch.foliaphantom.cli;

import com.patch.foliaphantom.patcher.PluginPatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.stream.Stream;

/**
 * Command-line interface for patching Bukkit plugin JARs for Folia.
 *
 * <p>Supports a single JAR, all JARs in a directory, or interactive input when no path is supplied.</p>
 */
public final class CLI {

    private static final Logger log = LoggerFactory.getLogger(CLI.class);
    private static final String DEFAULT_OUTPUT_DIR = "patched-plugins";
    private static final String JAR_EXTENSION = ".jar";

    private CLI() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void main(String[] args) {
        configureLogger();

        final CliOptions options;
        try {
            options = parseArgs(args);
        } catch (IllegalArgumentException error) {
            System.err.println("Error: " + error.getMessage());
            System.err.println();
            printUsage();
            return;
        }

        if (options.help()) {
            printUsage();
            return;
        }

        if (!options.noBanner()) {
            displayBanner();
        }

        Path inputPath = resolveInputPath(options.inputPath());
        if (inputPath == null) {
            return;
        }

        PluginPatcher patcher = new PluginPatcher(options.outputDir(), true);

        try {
            if (Files.isDirectory(inputPath)) {
                patchDirectory(patcher, inputPath);
            } else {
                patchSingleFile(patcher, inputPath);
            }
            log.info("Patching completed. Output directory: {}", options.outputDir().toAbsolutePath());
        } catch (IOException error) {
            log.error("Patching failed: {}", error.getMessage(), error);
            System.exit(1);
        }
    }

    private static CliOptions parseArgs(String[] args) {
        Path inputPath = null;
        Path outputDir = Paths.get(DEFAULT_OUTPUT_DIR);
        boolean help = false;
        boolean noBanner = false;
        boolean endOfOptions = false;

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];

            if (!endOfOptions && "--".equals(argument)) {
                endOfOptions = true;
                continue;
            }

            if (!endOfOptions && ("-h".equals(argument) || "--help".equals(argument))) {
                help = true;
                continue;
            }

            if (!endOfOptions && "--no-banner".equals(argument)) {
                noBanner = true;
                continue;
            }

            if (!endOfOptions && ("-o".equals(argument) || "--output".equals(argument))) {
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException(argument + " requires a directory path");
                }
                outputDir = Paths.get(args[++index]);
                continue;
            }

            if (!endOfOptions && argument.startsWith("-")) {
                throw new IllegalArgumentException("Unknown option: " + argument);
            }

            if (inputPath != null) {
                throw new IllegalArgumentException("Only one input JAR or directory can be supplied");
            }
            inputPath = Paths.get(argument);
        }

        return new CliOptions(inputPath, outputDir, help, noBanner);
    }

    private static Path resolveInputPath(Path suppliedPath) {
        if (suppliedPath != null) {
            if (!Files.exists(suppliedPath)) {
                log.error("Input path does not exist: {}", suppliedPath.toAbsolutePath());
                return null;
            }
            return suppliedPath;
        }
        return promptForPath();
    }

    private static Path promptForPath() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Enter path to plugin JAR or directory: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                log.error("No path provided.");
                return null;
            }

            Path path = Paths.get(input);
            if (!Files.exists(path)) {
                log.error("Path does not exist: {}", path.toAbsolutePath());
                return null;
            }
            return path;
        }
    }

    private static void patchDirectory(PluginPatcher patcher, Path dir) throws IOException {
        log.info("Patching all JAR files in directory: {}", dir.toAbsolutePath());
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> jarFiles = files
                    .filter(Files::isRegularFile)
                    .filter(CLI::hasJarExtension)
                    .filter(path -> !path.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("patched-"))
                    .sorted()
                    .toList();

            if (jarFiles.isEmpty()) {
                log.warn("No patchable JAR files found in directory: {}", dir.toAbsolutePath());
                return;
            }

            log.info("Found {} patchable JAR{}.", jarFiles.size(), jarFiles.size() == 1 ? "" : "s");
            for (Path jarFile : jarFiles) {
                patcher.patchPlugin(jarFile);
            }
        }
    }

    private static void patchSingleFile(PluginPatcher patcher, Path jarFile) throws IOException {
        if (!Files.isRegularFile(jarFile)) {
            log.warn("Input is not a regular file: {}", jarFile.toAbsolutePath());
            return;
        }
        if (!hasJarExtension(jarFile)) {
            log.warn("File is not a JAR: {}", jarFile.getFileName());
            return;
        }
        if (jarFile.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("patched-")) {
            log.warn("Skipping already-patched JAR: {}", jarFile.getFileName());
            return;
        }
        patcher.patchPlugin(jarFile);
    }

    private static boolean hasJarExtension(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(JAR_EXTENSION);
    }

    private static void configureLogger() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
    }

    private static void displayBanner() {
        System.out.println();
        System.out.println("  ███████╗ ██████╗ ██╗     ██╗ █████╗ ");
        System.out.println("  ██╔════╝██╔═══██╗██║     ██║██╔══██╗");
        System.out.println("  █████╗  ██║   ██║██║     ██║███████║");
        System.out.println("  ██╔══╝  ██║   ██║██║     ██║██╔══██║");
        System.out.println("  ██║     ╚██████╔╝███████╗██║██║  ██║");
        System.out.println("  ╚═╝      ╚═════╝ ╚══════╝╚═╝╚═╝  ╚═╝");
        System.out.println("  Folia Phantom CLI — pasta v2.0.0");
        System.out.println("  Bukkit → Folia bytecode transformer");
        System.out.println();
    }

    private static void printUsage() {
        System.out.println("pasta — Bukkit to Folia bytecode transformer");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar Folia-Phantom-CLI-2.0.0.jar [options] <plugin.jar|directory>");
        System.out.println("  java -jar Folia-Phantom-CLI-2.0.0.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -o, --output <dir>  Write patched JARs to this directory");
        System.out.println("  --no-banner         Suppress the startup banner");
        System.out.println("  -h, --help          Show this help message");
        System.out.println("  --                   Stop parsing options (for paths beginning with '-')");
        System.out.println();
        System.out.println("Default output directory: " + DEFAULT_OUTPUT_DIR);
    }

    private record CliOptions(Path inputPath, Path outputDir, boolean help, boolean noBanner) {
    }
}
