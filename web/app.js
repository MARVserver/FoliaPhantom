const fileInput = document.getElementById("file");
const dropZone = document.getElementById("drop-zone");
const patchButton = document.getElementById("patch");
const clearButton = document.getElementById("clear");
const status = document.getElementById("status");
const selectionSection = document.getElementById("selection");
const selectionSummary = document.getElementById("selection-summary");
const selectionList = document.getElementById("selection-list");
const progressWrap = document.getElementById("progress-wrap");
const progress = document.getElementById("progress");
const reportSection = document.getElementById("report");
const reportSummary = document.getElementById("report-summary");
const resultsContainer = document.getElementById("results");
const reportDownload = document.getElementById("report-download");

let patcherPromise;
let objectUrls = [];
let selectedFileList = [];
let isBusy = false;

reportDownload.hidden = true;

fileInput.addEventListener("change", () => {
    if (isBusy) return;
    setSelectedFiles(Array.from(fileInput.files ?? []));
});

dropZone.addEventListener("click", () => {
    if (!isBusy) fileInput.click();
});

dropZone.addEventListener("keydown", event => {
    if (isBusy || (event.key !== "Enter" && event.key !== " ")) return;
    event.preventDefault();
    fileInput.click();
});

for (const eventName of ["dragenter", "dragover"]) {
    dropZone.addEventListener(eventName, event => {
        event.preventDefault();
        if (!isBusy) dropZone.classList.add("is-dragging");
    });
}

dropZone.addEventListener("dragleave", () => {
    dropZone.classList.remove("is-dragging");
});

dropZone.addEventListener("drop", event => {
    event.preventDefault();
    dropZone.classList.remove("is-dragging");
    if (isBusy) return;
    setSelectedFiles(Array.from(event.dataTransfer?.files ?? []));
});

clearButton.addEventListener("click", () => {
    if (isBusy) return;
    fileInput.value = "";
    selectedFileList = [];
    clearReport();
    renderSelection();
    status.textContent = "Select one or more .jar files.";
});

patchButton.addEventListener("click", async () => {
    const files = selectedFiles();
    const patchableCount = files.filter(isPatchableJar).length;
    if (patchableCount === 0 || isBusy) return;

    clearReport();
    setBusy(true);
    progress.max = Math.max(files.length, 1);
    progress.value = 0;
    progressWrap.hidden = false;

    const results = [];
    let currentPatchable = 0;

    try {
        status.textContent = "Loading Java runtime…";
        const patcher = await getPatcher();

        for (let index = 0; index < files.length; index++) {
            const file = files[index];

            if (!isJar(file)) {
                results.push(emptyResult(file.name, "skipped", "Not a .jar file."));
                progress.value = index + 1;
                renderReport(results, files.length);
                continue;
            }

            if (isAlreadyPatched(file)) {
                results.push(emptyResult(file.name, "skipped", "Already patched."));
                progress.value = index + 1;
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
                const resultUrl = URL.createObjectURL(blob);
                objectUrls.push(resultUrl);

                results.push({
                    file: file.name,
                    status: parsed.failed > 0 ? "partial" : "success",
                    patched: parsed.patched,
                    skipped: parsed.skipped,
                    failed: parsed.failed,
                    classes: parsed.classes,
                    failures: parsed.failures,
                    error: parsed.failed > 0
                        ? `${parsed.failed} class${parsed.failed === 1 ? " was" : "es were"} left unchanged after a transform error.`
                        : "",
                    downloadUrl: resultUrl,
                    downloadName: `patched-${file.name}`
                });

                try {
                    await patcher.delete(outputPath);
                } catch (cleanupError) {
                    console.warn("Could not remove temporary output", cleanupError);
                }
            } catch (error) {
                console.error(error);
                results.push(emptyResult(file.name, "failed", await errorMessage(error)));
            } finally {
                cheerpOSRemoveStringFile(inputPath);
            }

            progress.value = index + 1;
            renderReport(results, files.length);
        }

        const counts = resultCounts(results);
        status.textContent = `Done. ${counts.success} succeeded, ${counts.partial} partial, ${counts.failed} failed, ${counts.skipped} skipped.`;
        updateReportDownload(results);

        const downloadable = results.filter(hasOutput);
        if (downloadable.length === 1 && patchableCount === 1) {
            const result = downloadable[0];
            triggerDownload(result.downloadUrl, result.downloadName);
        }
    } catch (error) {
        console.error(error);
        status.textContent = `Error: ${await errorMessage(error)}. You can clear the selection and try again.`;
    } finally {
        setBusy(false);
        progress.value = progress.max;
    }
});

function setSelectedFiles(files) {
    selectedFileList = uniqueFiles(files);
    clearReport();
    renderSelection();

    const patchable = selectedFileList.filter(isPatchableJar);
    const ignored = selectedFileList.length - patchable.length;

    if (selectedFileList.length === 0) {
        status.textContent = "Select one or more .jar files.";
    } else if (ignored > 0) {
        status.textContent = `${patchable.length} ready, ${ignored} will be skipped.`;
    } else {
        status.textContent = `${patchable.length} file${patchable.length === 1 ? "" : "s"} ready to patch.`;
    }
}

function uniqueFiles(files) {
    const seen = new Set();
    return files.filter(file => {
        const key = `${file.name}\u0000${file.size}\u0000${file.lastModified}`;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
    });
}

function renderSelection() {
    const files = selectedFiles();
    selectionSection.hidden = files.length === 0;
    selectionList.replaceChildren(...files.map(createSelectionElement));

    if (files.length > 0) {
        const patchable = files.filter(isPatchableJar).length;
        selectionSummary.textContent = `${files.length} selected · ${patchable} ready`;
    } else {
        selectionSummary.textContent = "0 files selected";
    }

    patchButton.disabled = isBusy || files.filter(isPatchableJar).length === 0;
    clearButton.disabled = isBusy || files.length === 0;
}

function createSelectionElement(file) {
    const item = document.createElement("li");
    item.className = "selection-item";

    const name = document.createElement("span");
    name.className = "selection-name";
    name.textContent = file.name;

    const state = document.createElement("span");
    state.className = "selection-state";
    if (!isJar(file)) {
        state.textContent = "Skipped · not a JAR";
    } else if (isAlreadyPatched(file)) {
        state.textContent = "Skipped · already patched";
    } else {
        state.textContent = `Ready · ${formatBytes(file.size)}`;
    }

    item.append(name, state);
    return item;
}

function setBusy(busy) {
    isBusy = busy;
    fileInput.disabled = busy;
    dropZone.setAttribute("aria-disabled", String(busy));
    dropZone.classList.remove("is-dragging");
    renderSelection();
}

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
    return selectedFileList;
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

function hasOutput(result) {
    return result.status === "success" || result.status === "partial";
}

function emptyResult(file, resultStatus, error) {
    return {
        file,
        status: resultStatus,
        patched: 0,
        skipped: 0,
        failed: 0,
        classes: [],
        failures: [],
        error
    };
}

function sanitizeFileName(name) {
    const sanitized = name.replace(/[^a-zA-Z0-9._-]/g, "_");
    return sanitized || "plugin.jar";
}

function formatBytes(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    const units = ["KB", "MB", "GB"];
    let value = bytes / 1024;
    let unit = units[0];

    for (let index = 1; index < units.length && value >= 1024; index++) {
        value /= 1024;
        unit = units[index];
    }

    return `${value >= 10 ? value.toFixed(0) : value.toFixed(1)} ${unit}`;
}

function parsePatchReport(text) {
    const lines = text.split("\n");
    const [patchedText = "0", skippedText = "0", failedText = "0"] =
        (lines.shift() ?? "").split("\t");
    const classes = [];
    const failures = [];

    for (const line of lines) {
        if (!line) continue;
        const fields = line.split("\t");
        const kind = fields.shift();

        if (kind === "P" && fields[0]) {
            classes.push(fields[0]);
        } else if (kind === "F") {
            failures.push({
                className: fields[0] || "unknown class",
                type: fields[1] || "java.lang.RuntimeException",
                message: fields.slice(2).join("\t")
            });
        }
    }

    return {
        patched: Number.parseInt(patchedText, 10) || 0,
        skipped: Number.parseInt(skippedText, 10) || 0,
        failed: Number.parseInt(failedText, 10) || failures.length,
        classes,
        failures
    };
}

function resultCounts(results) {
    return {
        success: results.filter(result => result.status === "success").length,
        partial: results.filter(result => result.status === "partial").length,
        failed: results.filter(result => result.status === "failed").length,
        skipped: results.filter(result => result.status === "skipped").length
    };
}

function renderReport(results, total) {
    reportSection.hidden = false;
    resultsContainer.replaceChildren(...results.map(createResultElement));

    const counts = resultCounts(results);
    reportSummary.textContent = `${results.length}/${total} processed · ${counts.success} success · ${counts.partial} partial · ${counts.failed} failed · ${counts.skipped} skipped`;
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
    meta.textContent = hasOutput(result)
        ? `${result.patched} patched / ${result.skipped} skipped / ${result.failed} unchanged`
        : result.status;

    head.append(name, meta);
    article.append(head);

    if (result.error) {
        const error = document.createElement("div");
        error.className = "result-error";
        error.textContent = result.error;
        article.append(error);
    }

    if (hasOutput(result)) {
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

        if (result.failures.length > 0) {
            const details = document.createElement("details");
            const summary = document.createElement("summary");
            summary.textContent = `${result.failures.length} unchanged classes`;
            const pre = document.createElement("pre");
            pre.textContent = result.failures.map(formatFailure).join("\n");
            details.append(summary, pre);
            article.append(details);
        }
    }

    return article;
}

function formatFailure(failure) {
    const detail = failure.message ? `: ${failure.message}` : "";
    return `${failure.className} — ${failure.type}${detail}`;
}

function updateReportDownload(results) {
    const rows = [
        [
            "file",
            "status",
            "patched_classes",
            "skipped_classes",
            "unchanged_classes",
            "patched_class_names",
            "unchanged_class_details",
            "error"
        ],
        ...results.map(result => [
            result.file,
            result.status,
            result.patched,
            result.skipped,
            result.failed,
            result.classes.join(";"),
            result.failures.map(formatFailure).join(";"),
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

function triggerDownload(url, fileName) {
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
    progress.value = 0;
    progressWrap.hidden = true;
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
