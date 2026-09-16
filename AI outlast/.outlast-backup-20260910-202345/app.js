/* ==========================================================================
   AI Outlast — Control Room
   Talks to the Spring Boot backend at API_BASE and renders the live
   simulation as: Setup -> Meet the Cast -> Captain Choosing -> Team Pick
   -> Stage (host + one card per team, collapsing to one card at the merge).
   ========================================================================== */

const API_BASE = "http://localhost:8081/api/game";

const NAME_POOL = ["Aria","Ethan","Chloe","Marcus","Sophia","Julian","Layla","Gavin",
                    "Zoe","Kaleb","Nora","Devon","Mia","Tristan","Iris","Derek"];

const PHASE_LABELS = {
  "Setup":"setup",
  "Initialization":"arrivals",
  "Draft Phase Complete":"camp life",
  "Camp Life Introductions Complete":"challenge",
  "Challenge Judging Complete":"camp life",
  "Camp Life Complete":"tribal council",
  "Tribal Council Complete":"next round",
  "Merge Phase Initialized":"immunity challenge",
  "Immunity Challenge Ranked":"tribal council",
  "Merged Tribal Council Complete":"immunity challenge",
  "Game Over - Winner Crowned":"sole survivor crowned"
};

const HOST_TAGS = new Set(["HOST","HOST JUDGING","HOST JUDGING (FALLBACK)","HOST ANNOUNCEMENT","HOST TWIST","JURY REVEAL"]);
const NARRATOR_TAGS = new Set(["ELIMINATED","BETRAYAL PLANNED","BETRAYAL EXECUTED","DECEPTION","REVEAL","TIE","TIE BREAK","NEW CAPTAIN"]);
const DANGER_TAGS = new Set(["ELIMINATED","BETRAYAL PLANNED","BETRAYAL EXECUTED"]);

/* ---------------------------------------------------------------------- */
/* View model                                                              */
/* ---------------------------------------------------------------------- */
const vm = {
  status: "NOT_STARTED",
  currentPhase: "Setup",
  currentRound: 0,
  paused: false,
  stepByStep: false,
  stageActive: false,
  mergedMode: false,
  teamsByName: {},   // teamName -> {captainName, players:[names], picks:[{name,num}]}  (early game only)
  teamsById: {},     // teamId -> {name, cardEl, feedEl, rosterEl}                       (stage)
  nameToTeamId: {},  // lowercase player name -> teamId (stage routing)
  playersMeta: {},   // lowercase player name -> {archetype, gender, id}
  idToName: {},      // player id -> real name (backend logs whisper recipients as "Contestant_<id>")
  pickCounter: 0,
  seenTeamShapeKey: null,
  lastLogIndex: 0,
  currentImmunityWinner: null,   // lowercase name of whoever most recently won individual immunity
  betrayalTargets: new Set(),    // lowercase names named as a betrayal target, for blindside detection
  intrigue: { betrayals: [], eliminations: [] },
  lastActivityAt: Date.now(),    // last time something new happened — drives the "thinking" indicator
  lastScrolledPhaseKey: null,    // avoids re-scrolling for a phase we already brought into view
  scrolledCaptains: false,
  scrolledPicks: false,
  originTeam: {},    // lowercase player name -> the tribe they started on, locked in before the merge
  tribalCouncilActive: false,  // true while the pre-merge losing team is at tribal council
  losingTeamName: null,        // which team's card should be the only one shown, left side
  tribalVotesCast: [],         // names of who has voted so far this tribal (anonymous — no target)
  tribalVoteCounts: {},        // name -> vote count, populated once the host reveals the tally
  mergedRosterPlayers: [],     // current post-merge roster snapshot, kept fresh every poll
  finalShowdownActive: false,  // true once only 2 players remain for the last challenge
  finalShowdownShown: false,   // guards against inserting the banner twice
  juryVotes: [],       // [{juror, votedFor, reasoning}] in reveal order
  juryTally: {},        // finalist name -> vote count, built live as jury votes are revealed
};

let lastActiveSender = null;
let pollTimer = null;
let idleTimer = null;
let techBuffer = [];

/* ---------------------------------------------------------------------- */
/* Auto-scroll: bring each new phase's content into view instead of        */
/* leaving the user scrolled into an old part of the page.                 */
/* ---------------------------------------------------------------------- */
function scrollToEl(el){
  if(!el) return;
  requestAnimationFrame(() => el.scrollIntoView({ behavior: "smooth", block: "start" }));
}

function markActivity(){
  vm.lastActivityAt = Date.now();
  const ind = document.getElementById("thinkingIndicator");
  if(ind) ind.classList.add("hidden");
}

/* ---------------------------------------------------------------------- */
/* "Processing" indicator — shows when the backend has gone quiet for a    */
/* few seconds while a simulation is running (an LLM call is in flight).   */
/* ---------------------------------------------------------------------- */
function startIdleWatcher(){
  if(idleTimer) clearInterval(idleTimer);
  idleTimer = setInterval(() => {
    const ind = document.getElementById("thinkingIndicator");
    if(!ind) return;
    const running = vm.status === "RUNNING" && !vm.paused;
    const idleFor = Date.now() - vm.lastActivityAt;
    ind.classList.toggle("hidden", !(running && idleFor > 2200));
  }, 500);
}

function stopIdleWatcher(){
  if(idleTimer){ clearInterval(idleTimer); idleTimer = null; }
  const ind = document.getElementById("thinkingIndicator");
  if(ind) ind.classList.add("hidden");
}

/* ---------------------------------------------------------------------- */
/* Small helpers                                                           */
/* ---------------------------------------------------------------------- */
function escapeHtml(str){
  if(str === null || str === undefined) return "";
  return String(str).replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
}

function initials(name){
  if(!name) return "?";
  return name.split(/\s+/).map(w => w[0]).join("").slice(0,2).toUpperCase();
}

function archetypeOf(pt){
  if(!pt) return "Contestant";
  if(typeof pt === "object" && pt.archetype) return pt.archetype;
  if(typeof pt === "string") return pt.replace(/_/g," ").toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
  return "Contestant";
}

function avatarFor(name, meta){
  const g = meta && meta.gender;
  if(g === "FEMALE") return "👩";
  if(g === "MALE") return "👨";
  return initials(name);
}

function friendlyPhase(phase){
  return PHASE_LABELS[phase] || (phase || "").toLowerCase();
}

// The backend sometimes logs a whisper's recipient as the raw "Contestant_<id>"
// string instead of their real name. Resolve it back to a name when we can.
function resolveDisplayName(raw){
  if(!raw) return raw;
  const trimmed = raw.trim();
  const m = trimmed.match(/^Contestant_(.+)$/i);
  if(m && vm.idToName[m[1]]) return vm.idToName[m[1]];
  return trimmed;
}

function shapeKey(teams){
  return (teams || []).map(t => t.teamId).sort().join(",");
}

/* ---------------------------------------------------------------------- */
/* Setup / Launch page                                                     */
/* ---------------------------------------------------------------------- */
function buildContestantRows(){
  const count = parseInt(document.getElementById("playersSelect").value, 10);
  const grid = document.getElementById("contestantGrid");
  grid.innerHTML = "";
  const shuffled = [...NAME_POOL].sort(() => 0.5 - Math.random());
  for(let i = 0; i < count; i++){
    const name = shuffled[i] || `Contestant_${i+1}`;
    const gender = (i % 2 === 0) ? "M" : "F";
    const row = document.createElement("div");
    row.className = "contestant-row";
    row.innerHTML = `
      <span class="idx">${i+1}</span>
      <input type="text" value="${escapeHtml(name)}" placeholder="Contestant name">
      <div class="gender-toggle">
        <button type="button" class="${gender==='M'?'sel':''}" data-g="M">M</button>
        <button type="button" class="${gender==='F'?'sel':''}" data-g="F">F</button>
      </div>
    `;
    const [mBtn, fBtn] = row.querySelectorAll(".gender-toggle button");
    mBtn.addEventListener("click", () => { mBtn.classList.add("sel"); fBtn.classList.remove("sel"); });
    fBtn.addEventListener("click", () => { fBtn.classList.add("sel"); mBtn.classList.remove("sel"); });
    grid.appendChild(row);
  }
}

async function launchSimulation(){
  const rows = document.querySelectorAll(".contestant-row");
  const customPlayers = [];
  rows.forEach(row => {
    const name = row.querySelector("input[type=text]").value.trim();
    const genderBtn = row.querySelector(".gender-toggle button.sel");
    const gender = (genderBtn && genderBtn.dataset.g === "F") ? "FEMALE" : "MALE";
    customPlayers.push({ name: name || "Contestant", gender });
  });

  const payload = {
    useDefaults: false,
    numberOfTeams: parseInt(document.getElementById("teamsSelect").value, 10),
    numberOfPlayers: parseInt(document.getElementById("playersSelect").value, 10),
    customPlayers,
    stepByStep: document.getElementById("stepMode").checked
  };

  const btn = document.getElementById("launchBtn");
  const errEl = document.getElementById("setupError");
  errEl.classList.add("hidden");
  btn.disabled = true;
  btn.textContent = "connecting…";

  try{
    const res = await fetch(`${API_BASE}/start`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if(!res.ok){
      const err = await res.json().catch(() => ({}));
      throw new Error(err.error || "Failed to start simulation.");
    }
    beginDashboard();
    startPolling();
  }catch(e){
    errEl.textContent = e.message || "Could not reach the backend. Is it running on localhost:8081?";
    errEl.classList.remove("hidden");
    btn.disabled = false;
    btn.textContent = "▶ launch simulation";
  }
}

function beginDashboard(){
  document.getElementById("setupView").classList.add("hidden");
  document.getElementById("hostBanner").classList.remove("hidden");
  document.getElementById("earlyGameView").classList.remove("hidden");
  document.getElementById("resetBtn").classList.remove("hidden");
  markActivity();
  startIdleWatcher();
}

/* ---------------------------------------------------------------------- */
/* Header                                                                   */
/* ---------------------------------------------------------------------- */
function updateHeader(data){
  const phaseKey = data.currentPhase + "|" + data.currentRound;
  const isNewPhase = vm.lastScrolledPhaseKey !== null && phaseKey !== vm.lastScrolledPhaseKey;

  vm.status = data.status;
  vm.currentPhase = data.currentPhase;
  vm.currentRound = data.currentRound;
  vm.paused = data.paused;
  vm.stepByStep = data.stepByStep;

  const dot = document.getElementById("status-dot");
  dot.className = "dot";
  const labels = { NOT_STARTED:"not started", RUNNING:"running", PAUSED:"paused", COMPLETED:"complete", FAILED:"failed" };
  document.getElementById("status-text").textContent = labels[data.status] || (data.status || "").toLowerCase();
  if(data.status === "RUNNING") dot.classList.add("on");
  else if(data.status === "PAUSED") dot.classList.add("paused");
  else if(data.status === "FAILED") dot.classList.add("failed");

  document.getElementById("phase-label").textContent = friendlyPhase(data.currentPhase);
  document.getElementById("round-label").textContent = "round_" + String(data.currentRound || 0).padStart(2, "0");
  document.getElementById("resumeBtn").classList.toggle("hidden", !(data.paused && data.stepByStep));

  if(isNewPhase){
    markActivity();
    // Bring the host banner (which carries the new phase's opening line) to
    // the top of the viewport, so the user isn't stuck scrolled into an old
    // team feed and doesn't have to scroll back up manually every time.
    if(vm.stageActive) scrollToEl(document.getElementById("hostBanner"));
  }
  vm.lastScrolledPhaseKey = phaseKey;
}

/* ---------------------------------------------------------------------- */
/* Host banner                                                             */
/* ---------------------------------------------------------------------- */
function hostLine(text, danger){
  if(!text) return;
  const el = document.getElementById("hostLines");
  el.querySelectorAll(".host-line:not(.past)").forEach(n => n.classList.add("past"));
  const div = document.createElement("div");
  div.className = "host-line" + (danger ? " danger" : "");
  div.textContent = text;
  el.appendChild(div);
  el.scrollTop = el.scrollHeight;
  const lines = el.querySelectorAll(".host-line");
  if(lines.length > 8) lines[0].remove();
}

/* ---------------------------------------------------------------------- */
/* Meet the Cast (populated straight from /state.activePlayers)            */
/* ---------------------------------------------------------------------- */
function renderCastFromState(data){
  const players = data.activePlayers || [];
  if(!players.length) return;

  players.forEach(p => {
    vm.playersMeta[p.name.toLowerCase()] = {
      archetype: archetypeOf(p.personalityType),
      gender: p.gender,
      id: p.id
    };
    vm.idToName[p.id] = p.name;
  });

  const grid = document.getElementById("castGrid");
  grid.innerHTML = "";
  players.forEach(p => {
    const meta = vm.playersMeta[p.name.toLowerCase()];
    const div = document.createElement("div");
    div.className = "cast-card";
    div.innerHTML = `
      <div class="top">
        <div class="cast-avatar">${initials(p.name)}</div>
        <div>
          <div class="cast-name">${escapeHtml(p.name)}</div>
          <div class="cast-archetype">${escapeHtml(meta.archetype)}</div>
        </div>
      </div>
      <div class="cast-quote">"${escapeHtml(p.introduction || "…")}"</div>
    `;
    grid.appendChild(div);
  });
}

/* ---------------------------------------------------------------------- */
/* Captain choosing + Team pick (built from untagged log lines)            */
/* ---------------------------------------------------------------------- */
function ensureProvisionalTeam(teamName){
  if(!vm.teamsByName[teamName]){
    vm.teamsByName[teamName] = { captainName: null, players: [], picks: [] };
  }
  return vm.teamsByName[teamName];
}

function handleCaptainLine(teamName, captainName){
  markActivity();
  const team = ensureProvisionalTeam(teamName);
  team.captainName = captainName;
  if(!team.players.includes(captainName)) team.players.push(captainName);
  const section = document.getElementById("captainsSection");
  section.classList.remove("hidden");
  renderCaptainsSection();
  if(!vm.scrolledCaptains){
    vm.scrolledCaptains = true;
    scrollToEl(section);
  }
}

function handlePickLine(teamName, pickedName){
  markActivity();
  const team = ensureProvisionalTeam(teamName);
  if(!team.players.includes(pickedName)) team.players.push(pickedName);
  vm.pickCounter += 1;
  team.picks.push({ name: pickedName, num: vm.pickCounter });
  const section = document.getElementById("pickSection");
  section.classList.remove("hidden");
  renderPickBoard();
  if(!vm.scrolledPicks){
    vm.scrolledPicks = true;
    scrollToEl(section);
  }
}

function renderCaptainsSection(){
  const grid = document.getElementById("captainGrid");
  grid.innerHTML = "";
  Object.entries(vm.teamsByName).forEach(([teamName, team]) => {
    if(!team.captainName) return;
    const meta = vm.playersMeta[team.captainName.toLowerCase()];
    const archTag = meta ? `<span class="arch-tag">(${escapeHtml(meta.archetype)})</span>` : "";
    const div = document.createElement("div");
    div.className = "captain-card";
    div.innerHTML = `
      <div class="crown">👑</div>
      <div class="captain-avatar">${initials(team.captainName)}</div>
      <div class="captain-name">${escapeHtml(team.captainName)}${archTag}</div>
      <div class="captain-role">captain</div>
      <div class="captain-team">${escapeHtml(teamName)}</div>
    `;
    grid.appendChild(div);
  });
}

function renderPickBoard(){
  const board = document.getElementById("pickBoard");
  board.innerHTML = "";
  Object.entries(vm.teamsByName).forEach(([teamName, team]) => {
    const div = document.createElement("div");
    div.className = "pick-team";
    const rows = (team.picks || []).map(p => {
      const meta = vm.playersMeta[p.name.toLowerCase()];
      const archTag = meta ? `<span class="arch-tag">(${escapeHtml(meta.archetype)})</span>` : "";
      return `<div class="pick-row"><span class="pick-num">#${p.num}</span><span class="av">${avatarFor(p.name, meta)}</span><span class="picked-name">${escapeHtml(p.name)}</span>${archTag}</div>`;
    }).join("") || `<div class="feed-empty">waiting for the first pick…</div>`;
    div.innerHTML = `<div class="pick-team-head">${escapeHtml(teamName)} <span class="arch-tag">— captain ${escapeHtml(team.captainName || "?")}</span></div>${rows}`;
    board.appendChild(div);
  });
}

/* ---------------------------------------------------------------------- */
/* Stage: host-centered layout, one card per team (collapses at merge)     */
/* ---------------------------------------------------------------------- */
function activateStage(data){
  document.getElementById("earlyGameView").classList.add("hidden");
  const stageEl = document.getElementById("stage");
  stageEl.classList.remove("hidden");
  stageEl.innerHTML = "";
  vm.teamsById = {};
  const merged = (data.teams || []).length === 1;
  stageEl.classList.toggle("merged", merged);

  data.teams.forEach(team => {
    const cardEl = document.createElement("div");
    cardEl.className = "card";
    cardEl.innerHTML = `
      <div class="card-head">
        <div class="who"><span class="avatar-tiny">🏕️</span>${escapeHtml(team.teamName)}</div>
        <div class="phase-tag"><span class="pdot"></span><span class="phase-tag-text">${escapeHtml(friendlyPhase(data.currentPhase))}</span></div>
      </div>
      <div class="feed"><div class="feed-empty">waiting for the next event…</div></div>
      <div class="roster"></div>
    `;
    stageEl.appendChild(cardEl);
    const cardRef = {
      name: team.teamName,
      cardEl,
      feedEl: cardEl.querySelector(".feed"),
      rosterEl: cardEl.querySelector(".roster")
    };
    vm.teamsById[team.teamId] = cardRef;
    renderRoster(cardRef, team, data.currentPhase);
  });

  if(merged){
    buildIntriguePanel(stageEl);
    renderIntriguePanel(data.teams[0].players);
    vm.mergedRosterPlayers = data.teams[0].players;
  } else {
    updateTribalLayout(data);
  }

  vm.stageActive = true;
  vm.mergedMode = merged;
  vm.seenTeamShapeKey = shapeKey(data.teams);
  markActivity();
  scrollToEl(document.getElementById("hostBanner"));
}

function renderRoster(card, team, currentPhase){
  const el = card.rosterEl;
  el.innerHTML = "";
  const sorted = [...team.players].sort((a, b) => {
    if(a.eliminated !== b.eliminated) return a.eliminated ? 1 : -1;
    return a.name.localeCompare(b.name);
  });
  sorted.forEach(p => {
    vm.playersMeta[p.name.toLowerCase()] = { archetype: archetypeOf(p.personalityType), gender: p.gender, id: p.id };
    vm.idToName[p.id] = p.name;
    vm.nameToTeamId[p.name.toLowerCase()] = team.teamId;
    // Lock in the FIRST team name ever seen for this player — that's their
    // original pre-merge tribe. Once merged, this same function runs again
    // with team.teamName === "Merged Tribe", but we don't want that to
    // overwrite the tribe an alliance actually formed on.
    if(!vm.originTeam[p.name.toLowerCase()]) vm.originTeam[p.name.toLowerCase()] = team.teamName;

    const isCaptain = team.captain && team.captain.id === p.id;
    // Immunity is tracked per-round from the "has won INDIVIDUAL IMMUNITY"
    // host announcement (vm.currentImmunityWinner), and is cleared the
    // instant that round's tribal council eliminates someone. individualWins
    // is a cumulative counter across the whole game, so it can't be used to
    // tell whether THIS round's immunity is still in effect.
    const isImmune = !!vm.currentImmunityWinner && p.name.toLowerCase() === vm.currentImmunityWinner && !p.eliminated;
    const isFinalist = vm.finalShowdownActive && !p.eliminated;

    const chip = document.createElement("div");
    chip.className = "chip" + (isCaptain ? " captain" : "") + (p.eliminated ? " eliminated" : "") + (isImmune ? " immune" : "") + (isFinalist ? " finalist-chip" : "");
    chip.innerHTML = `<span class="av">${avatarFor(p.name, { gender: p.gender })}</span>${escapeHtml(p.name)}<span class="arch-tag">(${escapeHtml(archetypeOf(p.personalityType))})</span>${isCaptain ? '<span class="cap-star">★</span>' : ''}${isImmune ? ' <span class="immune-shield">🛡️</span><span class="tag-mini">immune</span>' : ''}${isFinalist ? ' 🔥' : ''}`;
    el.appendChild(chip);
  });
}

function reconcileStageFromState(data){
  const newKey = shapeKey(data.teams || []);
  if(newKey !== vm.seenTeamShapeKey){
    if((data.teams || []).length === 1 && !vm.mergedMode){
      const stageEl = document.getElementById("stage");
      const banner = document.createElement("div");
      banner.className = "merge-banner";
      banner.textContent = "⚡ the tribes have merged — one camp, one game";
      stageEl.parentNode.insertBefore(banner, stageEl);
    }
    activateStage(data);
    return;
  }
  data.teams.forEach(team => {
    const card = vm.teamsById[team.teamId];
    if(!card) return;
    renderRoster(card, team, data.currentPhase);
    const tagText = card.cardEl.querySelector(".phase-tag-text");
    if(tagText) tagText.textContent = friendlyPhase(data.currentPhase);
  });
  if(vm.mergedMode && data.teams[0]){
    renderIntriguePanel(data.teams[0].players);
    vm.mergedRosterPlayers = data.teams[0].players;
  } else if(!vm.mergedMode){
    updateTribalLayout(data);
  }
}

/* ---------------------------------------------------------------------- */
/* Final Showdown: last two players + the jury that crowns the winner      */
/* ---------------------------------------------------------------------- */
function showFinalShowdown(){
  if(vm.finalShowdownShown) return;
  vm.finalShowdownShown = true;
  vm.finalShowdownActive = true;
  markActivity();

  const stageEl = document.getElementById("stage");
  if(!stageEl) return;

  const finalists = (vm.mergedRosterPlayers || []).filter(p => !p.eliminated);
  const vsHtml = finalists.map(p => `
    <div class="finalist">
      <span class="av">${avatarFor(p.name, vm.playersMeta[p.name.toLowerCase()] || { gender: p.gender })}</span>
      <span class="finalist-name">${escapeHtml(p.name)}</span>
      <span class="arch-tag">(${escapeHtml(archetypeOf(p.personalityType))})</span>
    </div>
  `).join(`<div class="vs-mark">VS</div>`);

  const banner = document.createElement("div");
  banner.className = "final-showdown-banner";
  banner.id = "finalShowdownBanner";
  banner.innerHTML = `
    <div class="final-showdown-title">🔥 the final showdown</div>
    <div class="final-showdown-sub">two remain — the jury of those they voted out will crown the sole survivor</div>
    <div class="finalists-row">${vsHtml}</div>
    <div class="jury-panel" id="juryPanel">
      <div class="jury-panel-label">⚖️ the jury votes</div>
      <div class="jury-columns">
        <div class="intrigue-section">
          <div class="intrigue-label">votes revealed</div>
          <div class="intrigue-body" id="juryVotesList"><div class="feed-empty">waiting for the jury…</div></div>
        </div>
        <div class="intrigue-section">
          <div class="intrigue-label">tally</div>
          <div class="intrigue-body" id="juryTallyList"><div class="feed-empty">tally pending…</div></div>
        </div>
      </div>
    </div>
  `;
  stageEl.parentNode.insertBefore(banner, stageEl);
  scrollToEl(banner);
}

function recordJuryVote(jurorName, votedForName, reasoning){
  vm.juryVotes.push({ juror: jurorName, votedFor: votedForName, reasoning });
  vm.juryTally[votedForName] = (vm.juryTally[votedForName] || 0) + 1;
  markActivity();
  renderJuryPanel();
}

function renderJuryPanel(){
  const votesEl = document.getElementById("juryVotesList");
  if(votesEl){
    if(!vm.juryVotes.length){
      votesEl.innerHTML = `<div class="feed-empty">waiting for the jury…</div>`;
    } else {
      votesEl.innerHTML = vm.juryVotes.slice().reverse().map(v => `
        <div class="intrigue-item" title="${escapeHtml(v.reasoning || "")}">
          <span class="av">${avatarFor(v.juror, vm.playersMeta[v.juror.toLowerCase()])}</span>
          <span class="pair-name">${escapeHtml(v.juror)}</span>
          <span class="link-icon">→</span>
          <span class="pair-name">${escapeHtml(v.votedFor)}</span>
        </div>
      `).join("");
    }
  }
  const tallyEl = document.getElementById("juryTallyList");
  if(tallyEl){
    const entries = Object.entries(vm.juryTally);
    if(!entries.length){
      tallyEl.innerHTML = `<div class="feed-empty">tally pending…</div>`;
    } else {
      const maxVotes = Math.max(...entries.map(([, n]) => n));
      tallyEl.innerHTML = entries.sort((a, b) => b[1] - a[1]).map(([name, count]) => `
        <div class="intrigue-item${count === maxVotes ? " vote-leader" : ""}">
          <span class="av">${avatarFor(name, vm.playersMeta[name.toLowerCase()])}</span>
          <span class="pair-name">${escapeHtml(name)}</span>
          <span class="tag-mini${count === maxVotes ? " danger" : ""}">${count} vote${count === 1 ? "" : "s"}</span>
        </div>
      `).join("");
    }
  }
}

function removeEmptyNote(feedEl){
  const note = feedEl.querySelector(".feed-empty");
  if(note) note.remove();
}

/* ---------------------------------------------------------------------- */
/* Post-merge "Intrigue" panel: alliances, betrayals, blindsides           */
/* ---------------------------------------------------------------------- */
function buildIntriguePanel(stageEl){
  const panel = document.createElement("div");
  panel.className = "intrigue-panel";
  panel.id = "intriguePanel";
  panel.innerHTML = `
    <div class="intrigue-head"><span>🕵️</span> the intrigue</div>
    <div class="intrigue-section">
      <div class="intrigue-label">alliances</div>
      <div class="intrigue-body" id="allianceList"><div class="feed-empty">no confirmed alliances yet…</div></div>
    </div>
    <div class="intrigue-section">
      <div class="intrigue-label">betrayals</div>
      <div class="intrigue-body" id="betrayalList"><div class="feed-empty">none yet…</div></div>
    </div>
    <div class="intrigue-section">
      <div class="intrigue-label">blindsides</div>
      <div class="intrigue-body" id="blindsideList"><div class="feed-empty">none yet…</div></div>
    </div>
  `;
  stageEl.appendChild(panel);
  renderIntrigueLists();
}

// Reads each player's own view of their relations and returns deduped
// {a,b} pairs for every relation either side currently calls CONFIRMED.
function buildAlliances(players){
  const pairs = [];
  const seen = new Set();
  (players || []).forEach(p => {
    if(!p.relations) return;
    Object.values(p.relations).forEach(rel => {
      if(rel.alliance !== "CONFIRMED") return;
      const key = [p.id, rel.id].sort().join("-");
      if(seen.has(key)) return;
      seen.add(key);
      pairs.push({ a: p.name, b: rel.name });
    });
  });
  return pairs;
}

function renderIntriguePanel(players){
  const listEl = document.getElementById("allianceList");
  if(!listEl) return;
  const pairs = buildAlliances(players);
  if(!pairs.length){
    listEl.innerHTML = `<div class="feed-empty">no confirmed alliances yet…</div>`;
    return;
  }
  listEl.innerHTML = pairs.map(pr => {
    const teamA = vm.originTeam[pr.a.toLowerCase()];
    const teamB = vm.originTeam[pr.b.toLowerCase()];
    const tagA = teamA ? `<span class="team-tag">${escapeHtml(teamA)}</span>` : "";
    const tagB = teamB ? `<span class="team-tag">${escapeHtml(teamB)}</span>` : "";
    return `
    <div class="intrigue-item alliance-item">
      <span class="av">${avatarFor(pr.a, vm.playersMeta[pr.a.toLowerCase()])}</span>
      <span class="pair-name">${escapeHtml(pr.a)}</span>${tagA}
      <span class="link-icon">🤝</span>
      <span class="av">${avatarFor(pr.b, vm.playersMeta[pr.b.toLowerCase()])}</span>
      <span class="pair-name">${escapeHtml(pr.b)}</span>${tagB}
    </div>
  `;
  }).join("");
}

function renderIntrigueLists(){
  const bEl = document.getElementById("betrayalList");
  if(bEl){
    if(!vm.intrigue.betrayals.length){
      bEl.innerHTML = `<div class="feed-empty">none yet…</div>`;
    } else {
      bEl.innerHTML = vm.intrigue.betrayals.slice(-10).slice().reverse().map(b => `
        <div class="intrigue-item betrayal-item">
          <span class="tag-mini${b.type === "executed" ? " danger" : ""}">${b.type}</span>
          <span><b>${escapeHtml(b.by)}</b> turned on ally <b>${escapeHtml(b.target)}</b></span>
        </div>
      `).join("");
    }
  }
  const sEl = document.getElementById("blindsideList");
  if(sEl){
    const blindsides = vm.intrigue.eliminations.filter(e => e.blindside);
    if(!blindsides.length){
      sEl.innerHTML = `<div class="feed-empty">none yet…</div>`;
    } else {
      sEl.innerHTML = blindsides.slice().reverse().map(e => `
        <div class="intrigue-item blindside-item">
          <span class="tag-mini danger">blindside</span>
          <span><b>${escapeHtml(e.name)}</b> never saw it coming</span>
        </div>
      `).join("");
    }
  }
}

// Called for every NARRATOR_TAGS line so betrayals/eliminations feed the
// intrigue panel even before the merge happens (the data just won't be
// visible until the panel is built).
function trackIntrigue(tag, rest){
  if(tag === "BETRAYAL PLANNED" || tag === "BETRAYAL EXECUTED"){
    const mm = rest.match(/^(.+?) (?:is plotting to target|cast a vote against) their CONFIRMED ally (.+?)!?$/);
    if(mm){
      const by = mm[1].trim();
      const target = mm[2].trim().replace(/!$/, "");
      vm.betrayalTargets.add(target.toLowerCase());
      vm.intrigue.betrayals.push({ type: tag === "BETRAYAL PLANNED" ? "planned" : "executed", by, target });
      renderIntrigueLists();
    }
    return;
  }
  if(tag === "ELIMINATED"){
    const mm = rest.match(/^(.+?) has been voted out/);
    if(mm){
      const name = mm[1].trim();
      const blindside = vm.betrayalTargets.has(name.toLowerCase());
      vm.intrigue.eliminations.push({ name, blindside });
      renderIntrigueLists();
    }
    // Immunity only protects for the round that just concluded at tribal.
    vm.currentImmunityWinner = null;
  }
}

/* ---------------------------------------------------------------------- */
/* Pre-merge tribal council: only the losing team's card stays on the      */
/* left, with a vote-count panel on the right — mirrors the post-merge     */
/* Intrigue panel layout, but scoped to a single round's vote.             */
/* ---------------------------------------------------------------------- */
function startTribalCouncil(losingTeamName){
  if(vm.mergedMode) return; // post-merge tribal already has its own Intrigue panel
  vm.tribalCouncilActive = true;
  vm.losingTeamName = losingTeamName;
  vm.tribalVotesCast = [];
  vm.tribalVoteCounts = {};
  markActivity();
  scrollToEl(document.getElementById("stage"));
}

function recordVoteCast(voterName){
  if(!vm.tribalCouncilActive) return;
  vm.tribalVotesCast.push(voterName);
  renderTribalVotePanel();
}

function recordVoteTally(name, count){
  if(!vm.tribalCouncilActive) return;
  vm.tribalVoteCounts[name] = count;
  renderTribalVotePanel();
}

function buildTribalVotePanel(stageEl){
  if(document.getElementById("tribalPanel")) return;
  const panel = document.createElement("div");
  panel.className = "intrigue-panel";
  panel.id = "tribalPanel";
  panel.innerHTML = `
    <div class="intrigue-head"><span>🗳️</span> tribal council</div>
    <div class="intrigue-section">
      <div class="intrigue-label">votes cast</div>
      <div class="intrigue-body" id="votesCastList"><div class="feed-empty">waiting for the first vote…</div></div>
    </div>
    <div class="intrigue-section">
      <div class="intrigue-label">vote count</div>
      <div class="intrigue-body" id="voteTallyList"><div class="feed-empty">votes not revealed yet…</div></div>
    </div>
  `;
  stageEl.appendChild(panel);
  renderTribalVotePanel();
}

function removeTribalVotePanel(){
  const panel = document.getElementById("tribalPanel");
  if(panel) panel.remove();
}

function renderTribalVotePanel(){
  const castEl = document.getElementById("votesCastList");
  if(castEl){
    if(!vm.tribalVotesCast.length){
      castEl.innerHTML = `<div class="feed-empty">waiting for the first vote…</div>`;
    } else {
      castEl.innerHTML = vm.tribalVotesCast.map(name => `
        <div class="intrigue-item">
          <span class="av">${avatarFor(name, vm.playersMeta[name.toLowerCase()])}</span>
          <span class="pair-name">${escapeHtml(name)}</span>
          <span class="tag-mini">voted</span>
        </div>
      `).join("");
    }
  }
  const tallyEl = document.getElementById("voteTallyList");
  if(tallyEl){
    const entries = Object.entries(vm.tribalVoteCounts);
    if(!entries.length){
      tallyEl.innerHTML = `<div class="feed-empty">votes not revealed yet…</div>`;
    } else {
      const maxVotes = Math.max(...entries.map(([, n]) => n));
      tallyEl.innerHTML = entries.sort((a, b) => b[1] - a[1]).map(([name, count]) => `
        <div class="intrigue-item${count === maxVotes ? " vote-leader" : ""}">
          <span class="av">${avatarFor(name, vm.playersMeta[name.toLowerCase()])}</span>
          <span class="pair-name">${escapeHtml(name)}</span>
          <span class="tag-mini${count === maxVotes ? " danger" : ""}">${count} vote${count === 1 ? "" : "s"}</span>
        </div>
      `).join("");
    }
  }
}

// Called every poll (pre-merge only) to keep the losing-team-only layout in
// sync: hides the winning team's card while tribal is active, shows the
// vote panel, and restores the normal two-card view once the checkpoint
// confirms this round's tribal council has concluded.
function updateTribalLayout(data){
  const stageEl = document.getElementById("stage");
  if(!stageEl) return;

  if(vm.tribalCouncilActive && vm.losingTeamName){
    stageEl.classList.add("tribal-mode");
    Object.values(vm.teamsById).forEach(card => {
      card.cardEl.classList.toggle("hidden", card.name !== vm.losingTeamName);
    });
    buildTribalVotePanel(stageEl);
  } else {
    stageEl.classList.remove("tribal-mode");
    Object.values(vm.teamsById).forEach(card => card.cardEl.classList.remove("hidden"));
    removeTribalVotePanel();
  }

  // The checkpoint confirming this tribal concluded arrives bundled with (or
  // right after) the vote reveal + elimination lines, so it's safe to clear
  // here — the panel above already reflects this same tick's final tally.
  if(data.currentPhase === "Tribal Council Complete" && vm.tribalCouncilActive){
    vm.tribalCouncilActive = false;
    vm.losingTeamName = null;
  }
}

/* ---------------------------------------------------------------------- */
/* Message rendering inside a team card                                    */
/* ---------------------------------------------------------------------- */
function cardForSender(senderName){
  if(!senderName) return null;
  const teamId = vm.nameToTeamId[senderName.toLowerCase()];
  if(!teamId) return null;
  return vm.teamsById[teamId] || null;
}

// Chat bubbles come from LLM-generated dialogue and can run long — cap them
// at ~18 words so the feed stays scannable. Full text is kept in a title
// attribute (hover) so nothing is actually lost, just visually trimmed.
const MSG_WORD_LIMIT = 18;
const SECRET_WORD_LIMIT = 10;   // whispers get a tighter cap — they're meant to be a quick tell, not a paragraph
function truncateWords(text, limit){
  if(!text) return text;
  const words = text.trim().split(/\s+/);
  if(words.length <= limit) return text;
  return words.slice(0, limit).join(" ") + "…";
}

function addSpeech(senderName, text, tagWord){
  const card = cardForSender(senderName);
  if(!card || !text) return;
  removeEmptyNote(card.feedEl);
  const meta = vm.playersMeta[senderName.toLowerCase()];
  const sub = tagWord ? `<span class="sub">· ${escapeHtml(tagWord)}</span>` : "";
  const shown = truncateWords(text, MSG_WORD_LIMIT);
  const div = document.createElement("div");
  div.className = "msg";
  div.innerHTML = `<div class="av">${avatarFor(senderName, meta)}</div><div class="body"><div class="sender">${escapeHtml(senderName)}${sub}</div><div class="bubble" title="${escapeHtml(text)}">${escapeHtml(shown)}</div></div>`;
  card.feedEl.appendChild(div);
  card.feedEl.scrollTop = card.feedEl.scrollHeight;
}

function addThought(senderName, hint){
  const card = cardForSender(senderName);
  if(!card) return;
  removeEmptyNote(card.feedEl);
  const meta = vm.playersMeta[senderName.toLowerCase()];
  const div = document.createElement("div");
  div.className = "msg thought";
  div.innerHTML = `<div class="av">${avatarFor(senderName, meta)}</div><div class="body"><div class="sender">${escapeHtml(senderName)} <span class="sub">thinking</span></div><div class="bubble">weighing options — ${escapeHtml(hint || "strategy")}</div></div>`;
  card.feedEl.appendChild(div);
  card.feedEl.scrollTop = card.feedEl.scrollHeight;
}

function addSecret(senderName, recipientName, content){
  const card = cardForSender(senderName);
  if(!card) return;
  removeEmptyNote(card.feedEl);
  const meta = vm.playersMeta[senderName.toLowerCase()];
  const shown = truncateWords(content, SECRET_WORD_LIMIT);
  const div = document.createElement("div");
  div.className = "msg";
  div.innerHTML = `<div class="av">${avatarFor(senderName, meta)}</div><div class="body">
    <div class="sender">${escapeHtml(senderName)} <span class="sub">→ ${escapeHtml(recipientName)}</span></div>
    <div class="secret" onclick="this.classList.toggle('revealed')">
      <span>🔒</span><span class="hint">sent a private message — tap to peek</span>
      <span class="content" title="${escapeHtml(content)}">"${escapeHtml(shown)}"</span>
    </div></div>`;
  card.feedEl.appendChild(div);
  card.feedEl.scrollTop = card.feedEl.scrollHeight;
}

function addSilent(senderName){
  const card = cardForSender(senderName);
  if(!card) return;
  removeEmptyNote(card.feedEl);
  const meta = vm.playersMeta[senderName.toLowerCase()];
  const div = document.createElement("div");
  div.className = "msg";
  div.innerHTML = `<div class="av">${avatarFor(senderName, meta)}</div><div class="body">
    <div class="sender">${escapeHtml(senderName)}</div>
    <div class="secret" style="cursor:default;"><span>🤫</span><span class="hint">stayed silent this round</span></div>
    </div>`;
  card.feedEl.appendChild(div);
  card.feedEl.scrollTop = card.feedEl.scrollHeight;
}

/* ---------------------------------------------------------------------- */
/* Tech / engine log drawer                                                */
/* ---------------------------------------------------------------------- */
function logTech(line){
  techBuffer.push(line);
  if(techBuffer.length > 400) techBuffer.shift();
  document.getElementById("techDrawer").classList.remove("hidden");
  const drawer = document.getElementById("techDrawer");
  if(drawer.classList.contains("open")){
    const body = document.getElementById("techDrawerBody");
    const div = document.createElement("div");
    div.textContent = line;
    body.appendChild(div);
    body.scrollTop = body.scrollHeight;
    while(body.children.length > 400) body.removeChild(body.firstChild);
  }
}

/* ---------------------------------------------------------------------- */
/* Log line parsing + routing                                              */
/* ---------------------------------------------------------------------- */
function routeLine(line){
  if(!line || !line.trim()) return;
  logTech(line);

  let m;

  // Secret / whisper messages (no bracket tag)
  if((m = line.match(/^\s*-\s*Secret Msg to ([^:]+):\s*"([^"]*)"/))){
    addSecret(lastActiveSender, resolveDisplayName(m[1]), m[2]);
    return;
  }
  if(line.includes("Secret Msg:") && line.includes("stayed silent")){
    addSilent(lastActiveSender);
    return;
  }

  // Captain assignment: "TeamName Captain: PlayerName"
  if((m = line.match(/^(\w[\w'-]*) Captain: (.+)$/))){
    handleCaptainLine(m[1], m[2].trim());
    return;
  }

  // Draft pick: "--> Team X (Captain: Y) drafts Z!"
  if((m = line.match(/^--> Team (\S+) \(Captain: (.+?)\) drafts (.+)!$/))){
    handlePickLine(m[1], m[3].trim());
    return;
  }

  // Pre-merge tribal council kicks off: "--- Losing Team (X) Voting Strategy Discussions ---"
  if((m = line.match(/^-{2,}\s*Losing Team \((.+?)\)\s*Voting Strategy Discussions\s*-{2,}$/))){
    startTribalCouncil(m[1].trim());
    return;
  }

  // Anonymous vote tally reveal (no bracket tag): "- Name received N vote(s)."
  if((m = line.match(/^-\s+(.+?) received (\d+) vote\(s\)\.$/))){
    recordVoteTally(m[1].trim(), parseInt(m[2], 10));
    return;
  }

  // Down to the final two — one last challenge decides the Sole Survivor.
  if(/^>{2,}\s*THE FINAL SHOWDOWN\s*<{2,}$/.test(line.trim())){
    showFinalShowdown();
    return;
  }

  // Bracket-tagged lines: "[TAG] rest of line"
  if((m = line.match(/^\[([A-Z][A-Z0-9 _()/-]*?)\]\s?(.*)$/))){
    handleTag(m[1].trim(), m[2]);
    return;
  }

  // Everything else stays in the tech drawer only.
}

function handleTag(tag, rest){
  if(HOST_TAGS.has(tag)){
    hostLine(rest);
    // "<Name> has won INDIVIDUAL IMMUNITY! ..." — the source of truth for
    // who's currently protected, refreshed fresh every time it fires.
    const immunityMatch = rest.match(/^(.+?) has won INDIVIDUAL IMMUNITY/i);
    if(immunityMatch) vm.currentImmunityWinner = immunityMatch[1].trim().toLowerCase();
    return;
  }
  if(NARRATOR_TAGS.has(tag)){
    hostLine(rest, DANGER_TAGS.has(tag));
    trackIntrigue(tag, rest);
    return;
  }
  if(tag === "INTENT"){
    const mm = rest.match(/^([^\s(]+)(?:\s*\(([^)]+)\))?:?/);
    if(mm){
      lastActiveSender = mm[1];
      addThought(mm[1], mm[2]);
    }
    return;
  }
  if(tag === "PITCH" || tag === "PLAN PITCH" || tag === "INTRO"){
    const mm = rest.match(/^([^:]+):\s*"?(.*?)"?$/);
    if(mm){
      const sender = mm[1].trim();
      lastActiveSender = sender;
      addSpeech(sender, mm[2], (tag === "PITCH" || tag === "PLAN PITCH") ? "pitch" : null);
    }
    return;
  }
  if(tag === "CAPTAIN PLAN"){
    const mm = rest.match(/^Captain (.+?) created final plan: "(.*)"$/);
    if(mm) addSpeech(mm[1].trim(), mm[2], "plan");
    return;
  }
  if(tag === "TRIBAL VOTE"){
    // "X cast a vote." / "X cast two votes." / "X has NO VOTE tonight (...)"
    const mm = rest.match(/^(.+?) (?:cast (?:a|two) votes?|has NO VOTE tonight)/);
    if(mm) recordVoteCast(mm[1].trim());
    return;
  }
  if(tag === "JURY VOTE"){
    // "X votes for Y: "reasoning""
    const mm = rest.match(/^(.+?) votes for (.+?):\s*"(.*)"$/);
    if(mm) recordJuryVote(mm[1].trim(), mm[2].trim(), mm[3]);
    return;
  }
  // RANKINGS, HEDGE, STRATEGIC CUT, DEBUG, WARN, INFO, FAILED,
  // SUMMARY, REFLECT*, CHECKPOINT, etc. -> tech drawer only (already logged).
}

/* ---------------------------------------------------------------------- */
/* Polling loop                                                            */
/* ---------------------------------------------------------------------- */
async function poll(){
  const dot = document.getElementById("status-dot");
  // If a round-trip takes noticeably long, let the status dot pulse so the
  // header itself communicates "still talking to the backend" rather than
  // looking frozen.
  const slowFetchTimer = setTimeout(() => { if(dot) dot.classList.add("syncing"); }, 1200);
  try{
    const stateRes = await fetch(`${API_BASE}/state`);
    const data = await stateRes.json();

    updateHeader(data);

    // Process this tick's narrative log lines BEFORE re-rendering the
    // roster/intrigue panel from /state, so things that a log line just
    // changed (e.g. an ELIMINATED line clearing the immunity badge) are
    // reflected immediately instead of lagging one poll cycle behind.
    await fetchLogs();

    if(!vm.stageActive){
      renderCastFromState(data);
      if(data.teams && data.teams.length){
        activateStage(data);
      }
    } else {
      reconcileStageFromState(data);
    }

    if(data.status === "COMPLETED"){
      stopPolling();
      showWinner(data.winnerName);
    } else if(data.status === "FAILED"){
      stopPolling();
      alert("The simulation failed. Check the engine log for details.");
    }
  }catch(e){
    console.error("Poll error:", e);
  }finally{
    clearTimeout(slowFetchTimer);
    if(dot) dot.classList.remove("syncing");
  }
}

async function fetchLogs(){
  try{
    const res = await fetch(`${API_BASE}/logs?from=${vm.lastLogIndex}`);
    const data = await res.json();
    if(data.logs && data.logs.length){
      markActivity();
      data.logs.forEach(line => routeLine(line));
      vm.lastLogIndex = data.nextIndex;
    }
  }catch(e){
    console.error("Log fetch error:", e);
  }
}

function startPolling(){
  if(pollTimer) clearInterval(pollTimer);
  poll();
  pollTimer = setInterval(poll, 1500);
}

function stopPolling(){
  if(pollTimer){ clearInterval(pollTimer); pollTimer = null; }
  stopIdleWatcher();
}

/* ---------------------------------------------------------------------- */
/* Winner modal, resume, reset                                             */
/* ---------------------------------------------------------------------- */
function showWinner(name){
  const meta = name ? vm.playersMeta[name.toLowerCase()] : null;
  document.getElementById("winnerName").textContent = name || "Unknown";
  document.getElementById("winnerArchetype").textContent = meta ? meta.archetype : "";
  const avatarEl = document.getElementById("winnerAvatar");
  if(avatarEl) avatarEl.textContent = avatarFor(name || "?", meta) || initials(name);
  document.getElementById("winnerModal").classList.remove("hidden");
}

async function checkGameStateOnLoad(){
  try{
    const res = await fetch(`${API_BASE}/state`);
    const data = await res.json();
    if(data.status === "RUNNING" || data.status === "PAUSED"){
      beginDashboard();
      startPolling();
    } else if(data.status === "COMPLETED"){
      beginDashboard();
      showWinner(data.winnerName);
    }
  }catch(e){
    console.error("Backend not reachable:", e);
  }
}

/* ---------------------------------------------------------------------- */
/* Boot                                                                     */
/* ---------------------------------------------------------------------- */
document.addEventListener("DOMContentLoaded", () => {
  buildContestantRows();
  document.getElementById("playersSelect").addEventListener("change", buildContestantRows);
  document.getElementById("randomizeBtn").addEventListener("click", buildContestantRows);
  document.getElementById("launchBtn").addEventListener("click", launchSimulation);

  document.getElementById("resumeBtn").addEventListener("click", async () => {
    const btn = document.getElementById("resumeBtn");
    const original = btn.textContent;
    btn.disabled = true;
    btn.textContent = "resuming…";
    markActivity();
    try{ await fetch(`${API_BASE}/next`, { method: "POST" }); }
    finally{ btn.disabled = false; btn.textContent = original; }
  });

  document.getElementById("resetBtn").addEventListener("click", async () => {
    if(!confirm("Stop and reset the current simulation?")) return;
    stopPolling();
    try{ await fetch(`${API_BASE}/reset`, { method: "POST" }); }
    finally{ location.reload(); }
  });

  document.getElementById("winnerCloseBtn").addEventListener("click", async () => {
    try{ await fetch(`${API_BASE}/reset`, { method: "POST" }); }
    finally{ location.reload(); }
  });

  document.getElementById("techDrawerHead").addEventListener("click", () => {
    const d = document.getElementById("techDrawer");
    d.classList.toggle("open");
    document.getElementById("techToggleLabel").textContent = d.classList.contains("open") ? "hide" : "show";
    if(d.classList.contains("open")){
      const body = document.getElementById("techDrawerBody");
      body.innerHTML = "";
      techBuffer.forEach(line => {
        const div = document.createElement("div");
        div.textContent = line;
        body.appendChild(div);
      });
      body.scrollTop = body.scrollHeight;
    }
  });

  checkGameStateOnLoad();
});
