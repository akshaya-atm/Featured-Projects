(() => {
  "use strict";
  const slides = window.ELMS_SLIDES;
  const root = document.querySelector("#slides");
  const dots = document.querySelector("#dots");
  const notes = document.querySelector("#notes");
  const notesButton = document.querySelector("#notesButton");
  let index = Math.min(slides.length - 1, Math.max(0, Number(location.hash.slice(1)) - 1 || 0));
  let playing = false;
  let startedAt = 0;
  let timer = null;
  const duration = 20000;
  const escape = (text) => String(text).replace(/[&<>"']/g, character => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[character]));

  slides.forEach((slide, i) => {
    const hasCode = Boolean(slide.code);
    const extraTabIndex = hasCode ? 2 : 1;
    const article = document.createElement("article");
    article.className = "slide";
    article.setAttribute("aria-labelledby", `title-${i}`);
    article.hidden = i !== index;
    article.innerHTML = `
      <div class="visual">
        <div class="tabs" role="tablist" aria-label="Evidence for slide ${i + 1}">
          <button class="tab" id="tab-${i}-0" role="tab" aria-selected="true" aria-controls="pane-${i}-0" tabindex="0">Visual</button>
          ${hasCode ? `<button class="tab" id="tab-${i}-1" role="tab" aria-selected="false" aria-controls="pane-${i}-1" tabindex="-1">${escape(slide.codeLabel)}</button>` : ""}
          ${slide.extra ? `<button class="tab" id="tab-${i}-${extraTabIndex}" role="tab" aria-selected="false" aria-controls="pane-${i}-${extraTabIndex}" tabindex="-1">${escape(slide.extraTab)}</button>` : ""}
          <span class="evidence-label">ELMS / ${String(i + 1).padStart(2, "0")}</span>
        </div>
        <div class="pane" id="pane-${i}-0" role="tabpanel" aria-labelledby="tab-${i}-0" tabindex="0">
          <div class="screenshot">
            <div class="placeholder-frame"><span class="placeholder-label">Screenshot placeholder ${String(i + 1).padStart(2,"0")}</span><h3>${escape(slide.screenshotTitle)}</h3><p>${escape(slide.screenshotHint)}</p><code>assets/${escape(slide.screenshot)}</code></div>
          </div>
        </div>
        ${hasCode ? `<div class="pane code-pane" id="pane-${i}-1" role="tabpanel" aria-labelledby="tab-${i}-1" tabindex="0" hidden><p class="code-caption">${escape(slide.codeFile)}</p><pre><code>${escape(slide.code)}</code></pre></div>` : ""}
        ${slide.extra ? `<div class="pane review-pane" id="pane-${i}-${extraTabIndex}" role="tabpanel" aria-labelledby="tab-${i}-${extraTabIndex}" tabindex="0" hidden>${slide.extra.map(([heading, text]) => `<h3>${escape(heading)}</h3><p>${escape(text)}</p>`).join("")}</div>` : ""}
        ${slide.footer ? `<div class="visual-footer">${escape(slide.footer)}</div>` : ""}
      </div>
      <div class="content"><p class="kicker">${String(i + 1).padStart(2,"0")} / ${escape(slide.section)}</p><h2 class="slide-title" id="title-${i}">${escape(slide.title)} <span>${escape(slide.accent)}</span></h2><p class="lead">${escape(slide.lead)}</p><div class="tags">${slide.tags.map(tag => `<span class="tag">${escape(tag)}</span>`).join("")}</div><ol class="points">${slide.points.map(([heading, text], n) => `<li class="point"><span class="number" aria-hidden="true">${n + 1}</span><p><strong>${escape(heading)}.</strong> ${escape(text)}</p></li>`).join("")}</ol></div>`;
    root.append(article);
    // Missing files intentionally leave the labeled placeholder in place.
    const image = new Image();
    image.alt = slide.screenshotTitle;
    image.addEventListener("load", () => {
      const host = article.querySelector(".screenshot");
      host.append(image);
      host.classList.add("has-image");
    });
    image.src = `assets/${slide.screenshot}`;
    const tabButtons = [...article.querySelectorAll('[role="tab"]')];
    const selectTab = (selected, focus = false) => {
      tabButtons.forEach((tab, t) => {
        const active = t === selected;
        tab.setAttribute("aria-selected", String(active));
        tab.tabIndex = active ? 0 : -1;
        document.getElementById(tab.getAttribute("aria-controls")).hidden = !active;
      });
      if (focus) tabButtons[selected].focus();
      startedAt = performance.now();
    };
    tabButtons.forEach((tab, t) => {
      tab.addEventListener("click", () => selectTab(t));
      tab.addEventListener("keydown", event => {
        let target;
        if (event.key === "ArrowRight") target = (t + 1) % tabButtons.length;
        if (event.key === "ArrowLeft") target = (t - 1 + tabButtons.length) % tabButtons.length;
        if (event.key === "Home") target = 0;
        if (event.key === "End") target = tabButtons.length - 1;
        if (target !== undefined) { event.preventDefault(); event.stopPropagation(); selectTab(target, true); }
      });
    });
    const dot = document.createElement("button");
    dot.className = "dot";
    dot.setAttribute("aria-label", `Slide ${i + 1}: ${slide.section}`);
    dot.addEventListener("click", () => show(i));
    dots.append(dot);
  });

  function renderNotes() {
    const slide = slides[index];
    document.querySelector("#notesTitle").textContent = `${index + 1}. ${slide.title} ${slide.accent}`;
    document.querySelector("#notesBody").innerHTML = `<p>${escape(slide.notes)}</p><h3>Source references</h3><ul>${slide.sources.map(source => `<li><code>${escape(source)}</code></li>`).join("")}</ul>`;
  }
  function show(next) {
    const focusedInsideSlide = root.contains(document.activeElement);
    index = (next + slides.length) % slides.length;
    [...root.children].forEach((slide, i) => { slide.hidden = i !== index; });
    [...dots.children].forEach((dot, i) => dot.setAttribute("aria-current", String(i === index)));
    document.querySelector("#counter").textContent = `Slide ${index + 1} of ${slides.length}`;
    try { history.replaceState(null, "", `#${index + 1}`); } catch { /* Some file viewers restrict history. */ }
    document.querySelector(".viewport").scrollTop = 0;
    if (focusedInsideSlide) dots.children[index].focus();
    startedAt = performance.now();
    document.querySelector("#progressFill").style.width = "0%";
    renderNotes();
  }
  function setPlaying(value) {
    playing = value;
    clearInterval(timer);
    document.querySelector("#playButton").textContent = value ? "Pause auto" : "Auto play";
    document.querySelector("#playButton").setAttribute("aria-pressed", String(value));
    startedAt = performance.now();
    document.querySelector("#progressFill").style.width = "0%";
    if (value) timer = setInterval(() => {
      const progress = (performance.now() - startedAt) / duration;
      document.querySelector("#progressFill").style.width = `${Math.min(100, progress * 100)}%`;
      if (progress >= 1) { if (index === slides.length - 1) setPlaying(false); else show(index + 1); }
    }, 100);
  }
  function toggleNotes() {
    notes.hidden = !notes.hidden;
    notesButton.setAttribute("aria-pressed", String(!notes.hidden));
    if (!notes.hidden) { setPlaying(false); document.querySelector("#closeNotes").focus(); }
    else notesButton.focus();
  }
  document.querySelector("#prevButton").addEventListener("click", () => show(index - 1));
  document.querySelector("#nextButton").addEventListener("click", () => show(index + 1));
  document.querySelector("#playButton").addEventListener("click", () => setPlaying(!playing));
  notesButton.addEventListener("click", toggleNotes);
  document.querySelector("#closeNotes").addEventListener("click", toggleNotes);
  document.querySelector("#fullscreenButton").addEventListener("click", async () => {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else if (document.documentElement.requestFullscreen) await document.documentElement.requestFullscreen();
      else throw new Error("Fullscreen unavailable");
    } catch { document.querySelector("#announce").textContent = "Fullscreen is unavailable in this viewer. Open the deck in a browser to present."; }
  });
  document.addEventListener("fullscreenchange", () => { document.querySelector("#fullscreenButton").textContent = document.fullscreenElement ? "Exit fullscreen" : "Fullscreen"; });
  document.addEventListener("visibilitychange", () => { if (document.hidden) setPlaying(false); });
  document.addEventListener("keydown", event => {
    if (event.altKey || event.ctrlKey || event.metaKey || event.target.matches("input,textarea,select,[contenteditable=true]")) return;
    if (event.key === "Escape" && !notes.hidden) { toggleNotes(); return; }
    if (event.key.toLowerCase() === "n") { toggleNotes(); return; }
    if (!notes.hidden) return;
    if (event.key === "ArrowRight" || event.key === "PageDown") { event.preventDefault(); show(index + 1); }
    if (event.key === "ArrowLeft" || event.key === "PageUp") { event.preventDefault(); show(index - 1); }
    if (event.key === "Home") { event.preventDefault(); show(0); }
    if (event.key === "End") { event.preventDefault(); show(slides.length - 1); }
    if (event.key === " " && !event.target.closest("button,a")) { event.preventDefault(); setPlaying(!playing); }
  });
  window.addEventListener("hashchange", () => { const requested = Number(location.hash.slice(1)); if (Number.isInteger(requested) && requested >= 1 && requested <= slides.length) show(requested - 1); });
  show(index);
})();
