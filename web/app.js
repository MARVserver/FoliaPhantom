const fileInput = document.getElementById("file");
const patchButton = document.getElementById("patch");
const status = document.getElementById("status");
const reportSection = document.getElementById("report");
const reportSummary = document.getElementById("report-summary");
const resultsContainer = document.getElementById("results");
const reportDownload = document.getElementById("report-download");

let patcherPromise;
let objectUrls = [];

reportDownload.hidden = true;

fileInput.addEventListener("change", () => {
  clearReport();

  const files = selectedFiles();
  const patchable = files.filter(isPatchableJar);
  const ignored = files.length - patchable.length;

  patchButton.disabled = patchable.length === 0;

  if (files.length === 0) {
    status.textContent = "Select one or more .jar files.";
  } else if (ignored > 0) {
    status.textContent = `${patchable.length} ready, ${ignored} ignored.`;
  } else {
    status.textContent = `${patchable.length} file${patchable.length === 1 ? "" : "s"} ready.`;
  }
});

patchButton.addEventListener("click", async () => {
  const files = selectedFiles();
  const patchableCount = files.filter(isPatchableJar).length;
  if (patchableCount === 0) return;

  clearReport();
  patchButton.disabled = true;
  fileInput.disabled = true;

  const results = [];
  let currentPatchable = 0;

  try {
    status.textContent = "Loading Java…";
    const patcher = await getPatcher();

    for (let index = 0; index < files.length; index++) {
      const file = files[index];

      if (!isJar(file)) {
        results.push({
          file: file.name,
          status: "skipped",
          patched: 0,
          skipped: 0,
          classes: [],
          error: "Not a .jar file."
        });
        renderReport(results, files.length);
        continue;
      }

      if (isAlreadyPatched(file)) {
        results.push({
          file: file.name,
          status: "skipped",
          patched: 0,
          skipped: 0,
          classes: [],
          error: "Already patched."
        });
        renderReport(results, files.length);
        continue;
      }

      currentPatchable += 1;
      status.textContent = `Patching ${currentPatchable}/${patchableCount}: ${file.name}`;

      const safeName = `input-${index}-${sanitizeFileName(file.name)}`;
      const inputPath = `/str/${safeName}`;
      const outputPath = `/files/patched-${safeName}`;

      try {
        const bytes = new Uint8Array(await file.arrayBuffer());
        cheerpOSAddStringFile(inputPath, bytes);

        const rawReport = await patcher.patch(inputPath);
        const parsed = parsePatchReport(String(rawReport));
        const blob = await cjFileBlob(outputPath);
        const downloadUrl = URL.createObjectURL(blob);
        objectUrls.push(downloadUrl);

        results.push({
          file: file.name,
          status: "success",
          patched: parsed.patched,
          skipped: parsed.skipped,
          classes: parsed.classes,
          error: "",
          downloadUrl,
          downloadName: `patched-${file.name}`
        });

        try {
          await patcher.delete(outputPath);
        } catch (cleanupError) {
          console.warn("Could not remove temporary output", cleanupError);
        }
      } catch (error) {
        console.error(error);
        results.push({
          file: file.name,
          status: "failed",
          patched: 0,
          skipped: 0,
          classes: [],
          error: await errorMessage(error)
        });
      } finally {
        cheerpOSRemoveStringFile(inputPath);
      }

      renderReport(results, files.length);
    }

    const succeeded = results.filter(result => result.status === "success").length;
    const failed = results.filter(result => result.status === "failed").length;
    const skipped = results.filter(result => result.status === "skipped").length;
    status.textContent = `Done. ${succeeded} succeeded, ${failed} failed, ${skipped} skipped.`;
    updateReportDownload(results);

    if (succeeded === 1 && patchableCount === 1) {
      const result = results.find(item => item.status === "success");
      if (result) downloadUrl(result.downloadUrl, result.downloadName);
    }
  } catch (error) {
    console.error(error);
    status.textContent = `Error: ${await errorMessage(error)}`;
  } finally {
    fileInput.disabled = false;
    patchButton.disabled = selectedFiles().filter(isPatchableJar).length === 0;
  }
});

async function getPatcher() {
  if (!patcherPromise) {
    patcherPromise = (async () => {
      await cheerpjInit({ version: 17, status: "none" });

      const jarUrl = new URL("./pasta-web.jar", window.location.href);
      const libraryPath = `/app${jarUrl.pathname}`;
      const library = await cheerpjRunLibrary(libraryPath);
      const WebPatcher = await library.com.patch.foliaphantom.web.WebPatcher;
      return await new WebPatcher();
    })().catch(error => {
      patcherPromise = undefined;
      throw error;
    });
  }
  return patcherPromise;
}

function selectedFiles() {
  return Array.from(fileInput.files ?? []);
}

function isJar(file) {
  return file.name.toLowerCase().endsWith(".jar");
}

function isAlreadyPatched(file) {
  return file.name.toLowerCase().startsWith("patched-");
}

function isPatchableJar(file) {
  return isJar(file) && !isAlreadyPatched(file);
}

function sanitizeFileName(name) {
  const sanitized = name.replace(/[^a-zA-Z0-9._-]/g, "_");
  return sanitized || "plugin.jar";
}

function parsePatchReport(text) {
  const lines = text.split("\n");
  const [patchedText = "0", skippedText = "0"] = (lines.shift() ?? "").split("\t");
  return {
    patched: Number.parseInt(patchedText, 10) || 0,
    skipped: Number.parseInt(skippedText, 10) || 0,
    classes: lines.filter(Boolean)
  };
}

function renderReport(results, total) {
  reportSection.hidden = false;
  resultsContainer.replaceChildren(...results.map(createResultElement));

  const succeeded = results.filter(result => result.status === "success").length;
  const failed = results.filter(result => result.status === "failed").length;
  const skipped = results.filter(result => result.status === "skipped").length;
  reportSummary.textContent = `${results.length}/${total} processed · ${succeeded} success · ${failed} failed · ${skipped} skipped`;
}

function createResultElement(result) {
  const article = document.createElement("article");
  article.className = "result";

  const head = document.createElement("div");
  head.className = "result-head";

  const name = document.createElement("strong");
  name.className = "result-name";
  name.textContent = result.file;

  const meta = document.createElement("span");
  meta.className = "result-meta";
  meta.textContent = result.status === "success"
    ? `${result.patched} patched / ${result.skipped} skipped`
    : result.status;

  head.append(name, meta);
  article.append(head);

  if (result.error) {
    const error = document.createElement("div");
    error.className = "result-error";
    error.textContent = result.error;
    article.append(error);
  }

  if (result.status === "success") {
    const links = document.createElement("div");
    links.className = "links";

    const download = document.createElement("a");
    download.href = result.downloadUrl;
    download.download = result.downloadName;
    download.textContent = "Download JAR";
    links.append(download);
    article.append(links);

    if (result.classes.length > 0) {
      const details = document.createElement("details");
      const summary = document.createElement("summary");
      summary.textContent = `${result.classes.length} patched classes`;
      const pre = document.createElement("pre");
      pre.textContent = result.classes.join("\n");
      details.append(summary, pre);
      article.append(details);
    }
  }

  return article;
}

function updateReportDownload(results) {
  const rows = [
    ["file", "status", "patched_classes", "skipped_classes", "patched_class_names", "error"],
    ...results.map(result => [
      result.file,
      result.status,
      result.patched,
      result.skipped,
      result.classes.join(";"),
      result.error
    ])
  ];

  const csv = rows.map(row => row.map(csvCell).join(",")).join("\r\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  objectUrls.push(url);
  reportDownload.href = url;
  reportDownload.hidden = false;
}

function csvCell(value) {
  const text = String(value ?? "");
  return `"${text.replaceAll('"', '""')}"`;
}

function downloadUrl(url, fileName) {
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
}

function clearReport() {
  for (const url of objectUrls) URL.revokeObjectURL(url);
  objectUrls = [];
  resultsContainer.replaceChildren();
  reportSummary.textContent = "";
  reportDownload.removeAttribute("href");
  reportDownload.hidden = true;
  reportSection.hidden = true;
}

async function errorMessage(error) {
  const parts = [];
  let current = error;

  for (let depth = 0; depth < 4 && current; depth++) {
    const name = await throwableName(current);
    const message = await throwableMessage(current);
    const typeName = await missingTypeName(current);
    const detail = message || typeName;
    const part = [name, detail].filter(Boolean).join(": ");
    if (part && !parts.includes(part)) parts.push(part);

    try {
      current = typeof current?.getCause === "function"
        ? await current.getCause()
        : null;
    } catch {
      current = null;
    }
  }

  return parts.join(" → ") || error?.message || String(error);
}

async function throwableName(error) {
  try {
    if (typeof error?.getClass === "function") {
      const clazz = await error.getClass();
      if (clazz && typeof clazz.getName === "function") {
        return String(await clazz.getName());
      }
    }
  } catch {
    // Fall through to the JavaScript name.
  }
  return error?.name || "";
}

async function throwableMessage(error) {
  try {
    if (typeof error?.getMessage === "function") {
      const message = await error.getMessage();
      if (message) return String(message);
    }
  } catch {
    // Fall through to the JavaScript message.
  }
  return error?.message || "";
}

async function missingTypeName(error) {
  try {
    if (typeof error?.typeName === "function") {
      const value = await error.typeName();
      return value ? `missing type ${String(value)}` : "";
    }
  } catch {
    // Not a TypeNotPresentException.
  }
  return "";
}
