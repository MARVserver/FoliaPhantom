import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";

function createJar(name, entries) {
  const root = mkdtempSync(join(tmpdir(), "pasta-web-e2e-"));
  const jarPath = join(root, name);
  const args = ["--create", "--file", jarPath];

  for (const [entryName, content] of Object.entries(entries)) {
    const path = join(root, entryName);
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, content);
    args.push("-C", root, entryName);
  }

  execFileSync("jar", args);
  return { root, jarPath };
}

function readJarEntry(jarPath, entryName) {
  return execFileSync("unzip", ["-p", jarPath, entryName], {
    encoding: "utf8"
  });
}

async function dropJar(page, jarPath, fileName) {
  const base64 = readFileSync(jarPath).toString("base64");

  await page.locator("#drop-zone").evaluate((zone, payload) => {
    const binary = atob(payload.base64);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index++) {
      bytes[index] = binary.charCodeAt(index);
    }

    const transfer = new DataTransfer();
    transfer.items.add(new File(
      [bytes],
      payload.fileName,
      { type: "application/java-archive" }
    ));

    zone.dispatchEvent(new DragEvent("drop", {
      bubbles: true,
      cancelable: true,
      dataTransfer: transfer
    }));
  }, { base64, fileName });
}

test("browser patching covers warmup, reset, success, partial output, and metrics", async ({ page }) => {
  const success = createJar("paper-fixture.jar", {
    "paper-plugin.yml": [
      "name: PaperFixture",
      "version: 1.0",
      "main: example.Main",
      "api-version: '1.21'",
      ""
    ].join("\n")
  });
  const partial = createJar("partial-fixture.jar", {
    "plugin.yml": [
      "name: PartialFixture",
      "version: 1.0",
      "main: example.Main",
      ""
    ].join("\n"),
    "Broken.class": Buffer.from("not-a-class org/bukkit/ scheduler marker")
  });

  try {
    await page.goto("/");

    await dropJar(page, success.jarPath, "paper-fixture.jar");
    await expect(page.locator("#selection-summary")).toContainText("1 selected");
    await expect(page.locator("#status")).toContainText("ready");

    await page.locator("#clear").click();
    await expect(page.locator("#selection")).toBeHidden();
    await expect(page.locator("#status")).toHaveText("Select one or more .jar files.");

    await page.locator("#file").setInputFiles(success.jarPath);
    const successDownloadPromise = page.waitForEvent("download");
    await page.locator("#patch").click();
    const successDownload = await successDownloadPromise;
    const successPath = await successDownload.path();

    expect(successPath).not.toBeNull();
    expect(successDownload.suggestedFilename()).toBe("patched-paper-fixture.jar");
    expect(readJarEntry(successPath, "paper-plugin.yml"))
      .toContain("folia-supported: true");
    await expect(page.locator("#status")).toContainText("1 succeeded");
    await expect(page.locator(".result-meta")).toContainText(/(ms|s)$/);
    await expect(page.locator("#report-summary")).toContainText("runtime");
    await expect(page.locator("#report-summary")).toContainText("patch");

    await page.locator("#file").setInputFiles(partial.jarPath);
    const partialDownloadPromise = page.waitForEvent("download");
    await page.locator("#patch").click();
    const partialDownload = await partialDownloadPromise;
    const partialPath = await partialDownload.path();

    expect(partialPath).not.toBeNull();
    expect(readJarEntry(partialPath, "plugin.yml"))
      .toContain("folia-supported: true");
    await expect(page.locator("#status")).toContainText("1 partial");
    await expect(page.locator(".result-error"))
      .toContainText("left unchanged after a transform error");
    await expect(page.locator("summary"))
      .toContainText("1 unchanged classes");

    const reportPromise = page.waitForEvent("download");
    await page.locator("#report-download").click();
    const reportDownload = await reportPromise;
    const reportPath = await reportDownload.path();
    const report = readFileSync(reportPath, "utf8");

    expect(report).toContain("\"runtime_init_ms\"");
    expect(report).toContain("\"patch_ms\"");
    expect(report).toContain("\"partial\"");
  } finally {
    rmSync(success.root, { recursive: true, force: true });
    rmSync(partial.root, { recursive: true, force: true });
  }
});
