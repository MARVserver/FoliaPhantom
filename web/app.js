const fileInput = document.getElementById("file");
const patchButton = document.getElementById("patch");
const status = document.getElementById("status");

let patcherPromise;

fileInput.addEventListener("change", () => {
  const file = fileInput.files?.[0];
  const valid = Boolean(file && file.name.toLowerCase().endsWith(".jar"));
  patchButton.disabled = !valid;
  status.textContent = valid ? file.name : "Select a .jar file.";
});

patchButton.addEventListener("click", async () => {
  const file = fileInput.files?.[0];
  if (!file) return;

  patchButton.disabled = true;
  const safeName = sanitizeFileName(file.name);
  const inputPath = `/str/${safeName}`;
  const outputPath = `/files/patched-${safeName}`;

  try {
    status.textContent = "Loading Java…";
    const patcher = await getPatcher();

    status.textContent = "Patching…";
    const bytes = new Uint8Array(await file.arrayBuffer());
    cheerpOSAddStringFile(inputPath, bytes);

    await patcher.patch(inputPath);

    status.textContent = "Downloading…";
    const blob = await cjFileBlob(outputPath);
    downloadBlob(blob, `patched-${file.name}`);
    status.textContent = "Done.";
  } catch (error) {
    console.error(error);
    status.textContent = `Error: ${await errorMessage(error)}`;
  } finally {
    cheerpOSRemoveStringFile(inputPath);
    patchButton.disabled = false;
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
    })();
  }
  return patcherPromise;
}

function sanitizeFileName(name) {
  const sanitized = name.replace(/[^a-zA-Z0-9._-]/g, "_");
  return sanitized || "plugin.jar";
}

function downloadBlob(blob, fileName) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

async function errorMessage(error) {
  try {
    if (typeof error?.getMessage === "function") {
      return (await error.getMessage()) || String(error);
    }
  } catch {
    // Fall back to the JavaScript representation.
  }
  return error?.message || String(error);
}
