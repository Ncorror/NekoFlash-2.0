"use strict";
document.querySelectorAll("[data-copy-checksum]").forEach((button) => {
  button.addEventListener("click", async () => {
    const value = document.querySelector("#apk-checksum").textContent.trim();
    const initial = button.textContent;
    try {
      if (!navigator.clipboard) throw new Error("Clipboard unavailable");
      await navigator.clipboard.writeText(value);
      button.textContent = button.dataset.copied;
      button.setAttribute("aria-live", "polite");
      setTimeout(() => { button.textContent = initial; }, 2200);
    } catch {
      const range = document.createRange();
      range.selectNodeContents(document.querySelector("#apk-checksum"));
      const selection = window.getSelection();
      selection.removeAllRanges();
      selection.addRange(range);
      button.textContent = button.dataset.selected;
    }
  });
});
