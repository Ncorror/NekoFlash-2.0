"use strict";
(() => {
  const tabs = [...document.querySelectorAll('[role="tab"][data-screen]')];
  const image = document.querySelector('#app-screen');
  const panel = document.querySelector('#screen-panel');
  const caption = document.querySelector('#screen-caption');
  const counter = document.querySelector('.screenshot-counter');
  function select(tab, focus) {
    tabs.forEach(item => {
      const active = item === tab;
      item.setAttribute('aria-selected', String(active));
      item.tabIndex = active ? 0 : -1;
    });
    panel.setAttribute('aria-labelledby', tab.id);
    image.src = tab.dataset.image;
    image.alt = 'NekoFlash — ' + tab.dataset.title;
    caption.textContent = tab.dataset.caption;
    counter.textContent = String(tabs.indexOf(tab) + 1).padStart(2, '0') + ' — 04';
    if (focus) tab.focus();
  }
  tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => select(tab, false));
    tab.addEventListener('keydown', event => {
      let next;
      if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
      if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
      if (event.key === 'Home') next = 0;
      if (event.key === 'End') next = tabs.length - 1;
      if (next !== undefined) { event.preventDefault(); select(tabs[next], true); }
    });
  });
  const checksumDetails = document.querySelector('.checksum-details');
  document.addEventListener('click', event => {
    if (checksumDetails && checksumDetails.open && !checksumDetails.contains(event.target)) checksumDetails.open = false;
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && checksumDetails && checksumDetails.open) { checksumDetails.open = false; checksumDetails.querySelector('summary').focus(); }
  });
  document.querySelectorAll('[data-copy-checksum]').forEach(button => {
    button.addEventListener('click', async () => {
      const node = document.querySelector('#apk-checksum');
      const initial = button.textContent;
      let clipboardTimer;
      button.textContent = '…';
      button.disabled = true;
      button.setAttribute('aria-busy', 'true');
      try {
        if (!navigator.clipboard) throw new Error('Clipboard unavailable');
        await Promise.race([navigator.clipboard.writeText(node.textContent.trim()), new Promise((_, reject) => { clipboardTimer = setTimeout(() => reject(new Error('Clipboard did not respond')), 1000); })]);
        button.textContent = button.dataset.copied;
        button.setAttribute('aria-live', 'polite');
        setTimeout(() => { button.textContent = initial; }, 2200);
      } catch {
        const range = document.createRange(); range.selectNodeContents(node);
        const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
        button.textContent = button.dataset.selected;
      } finally {
        clearTimeout(clipboardTimer);
        button.disabled = false;
        button.removeAttribute('aria-busy');
      }
    });
  });
})();
