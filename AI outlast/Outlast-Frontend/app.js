/* AI Outlast — a spectator's view of the existing Spring Boot simulation. */
const API_BASE = 'http://localhost:8081/api/game';
const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const initials = name => String(name || '?').split(/[\s_]+/).filter(Boolean).map(w => w[0]).join('').slice(0, 2).toUpperCase();
const names = ['Aria', 'Ethan', 'Chloe', 'Marcus', 'Sophia', 'Julian', 'Layla', 'Gavin', 'Zoe', 'Kaleb', 'Nora', 'Devon', 'Mia', 'Tristan', 'Iris', 'Derek'];
const colors = ['#9f592e', '#14766e', '#7259a3', '#786018'];
const stats = {TRUSTWORTHINESS:'Trustworthiness', EMOTIONAL_STABILITY:'Emotional stability', COOPERATION:'Cooperation', SOCIAL_INTELLIGENCE:'Social intelligence', SURVIVAL_GRIT:'Survival grit', ASSERTIVENESS:'Assertiveness'};
const archetypes = {CALM_STRATEGIST:'Calm strategist', HONEST_PHILOSOPHER:'Honest philosopher', CURIOUS_ANALYST:'Curious analyst', CHAOTIC_PROVOCATEUR:'Chaotic provocateur', LOGICAL_ENGINEER:'Logical engineer', DIPLOMAT:'Diplomat', BOLD_RISK_TAKER:'Bold risk-taker', INVESTIGATOR:'Investigator'};
const playback = {queue:[], timer:null, selectingShown:false};
const vm = {state:null, players:new Map(), provisionalTeams:new Map(), origins:new Map(), nextIndex:0, selectedId:null, immuneId:null, phase:'draft', currentSender:null, events:[], socialMoves:[], socialKey:'', logs:[], eliminations:[], juryVotes:[], juryTally:new Map(), challenge:null, merged:false, winnerShown:false, lastActivity:Date.now(), rosterKey:'', profileKey:'', polling:false, busy:false, timer:null, epoch:0};

function archetype(player){return archetypes[player?.personalityType] || player?.personalityType?.archetype || player?.archetype || 'Finding their footing';}
function byName(name){const raw=String(name || '').trim(); const id=raw.match(/^Contestant_(.+)$/i)?.[1]; return (id && vm.players.get(id)) || [...vm.players.values()].find(p => p.name.toLowerCase()===raw.toLowerCase());}
function displayName(name){return byName(name)?.name || name;}
function shuffle(items){const result=[...items]; for(let i=result.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1)); [result[i],result[j]]=[result[j],result[i]];} return result;}
function legalCounts(tribes){return Array.from({length:17},(_,i)=>i).filter(n=>n>=tribes*2 && n%tribes===0);}
function syncPlayerOptions(){const prior=Number($('playersSelect').value)||6; const allowed=legalCounts(Number($('teamsSelect').value)); $('playersSelect').innerHTML=allowed.map(n=>`<option value="${n}">${n} contestants</option>`).join(''); $('playersSelect').value=String(allowed.includes(prior)?prior:allowed[0]); buildContestants();}
function buildContestants(){const count=Number($('playersSelect').value), pool=shuffle(names); $('castCount').textContent=String(count).padStart(2,'0'); $('contestantGrid').innerHTML=Array.from({length:count},(_,i)=>`<div class="contestant-row"><span class="idx">${String(i+1).padStart(2,'0')}</span><input aria-label="Contestant ${i+1} name" maxlength="40" required value="${pool[i]}" autocomplete="off"><select aria-label="Contestant ${i+1} gender"><option value="MALE" ${i%2===0?'selected':''}>Male</option><option value="FEMALE" ${i%2?'selected':''}>Female</option></select></div>`).join('');}
async function request(path, options={}){const controller=new AbortController(); const timeout=setTimeout(()=>controller.abort(),12000); try{const res=await fetch(API_BASE+path,{...options,signal:controller.signal}); const data=await res.json(); if(!res.ok) throw new Error(data.error || data.message || 'The island could not respond. Please try again.'); return data;}finally{clearTimeout(timeout);}}
function showNotice(message){$('connectionNotice').textContent=message; $('connectionNotice').classList.toggle('hidden',!message);}
function enterGame(){ document.body.classList.add('watching'); $('setupView').classList.add('hidden'); $('gameView').classList.remove('hidden'); $('resetBtn').classList.remove('hidden'); }
async function launch(event){event.preventDefault(); const customPlayers=[...$('contestantGrid').children].map(row=>({name:row.querySelector('input').value.trim(),gender:row.querySelector('select').value})); const teamCount=Number($('teamsSelect').value); const count=customPlayers.length; const unique=new Set(customPlayers.map(p=>p.name.toLowerCase())); $('setupError').classList.add('hidden'); if(customPlayers.some(p=>!p.name)||unique.size!==count){$('setupError').textContent='Give every contestant a different name so you can follow their story.'; $('setupError').classList.remove('hidden'); return;} if(!legalCounts(teamCount).includes(count)) return;
  $('launchBtn').disabled=true; $('launchBtn').textContent='Preparing the island…';
  try{await request('/start',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({useDefaults:false,numberOfTeams:teamCount,numberOfPlayers:count,customPlayers,stepByStep:$('stepMode').checked})}); enterGame(); startPolling();}catch(e){$('setupError').textContent=e.name==='AbortError'?'The island is taking longer to respond. Refresh to check whether your game started.':e.message.includes('fetch')?'The game server is not available. Start the local backend, then try again.':e.message; $('setupError').classList.remove('hidden'); $('launchBtn').disabled=false; $('launchBtn').innerHTML='Set foot on the island <span aria-hidden="true">→</span>';}
}

function ingestState(data){vm.state=data; for(const p of data.activePlayers || []) vm.players.set(String(p.id),{...vm.players.get(String(p.id)),...p,id:String(p.id)}); for(const team of data.teams || []) for(const p of team.players || []){vm.players.set(String(p.id),{...vm.players.get(String(p.id)),...p,id:String(p.id)}); if(!vm.origins.has(String(p.id)) && team.teamName!=='Merged Tribe') vm.origins.set(String(p.id),team.teamName);} if(data.teams?.length===1 && data.teams[0].teamName==='Merged Tribe') vm.merged=true; }
function setScene(phase, title, description, label){vm.phase=phase; $('islandScene').classList.toggle('council',phase==='council'); $('sceneTitle').textContent=title; $('sceneDescription').textContent=description; $('sceneEyebrow').textContent=label || ({draft:'THE DRAFT',camp:'CAMP LIFE',challenge:'IMMUNITY CHALLENGE',council:'TRIBAL COUNCIL',finale:'THE FINAL SHOWDOWN'}[phase]); $('sceneLocation').textContent=({draft:'THE SHORE',camp:vm.merged?'THE MERGED CAMP':'THE CAMPS',challenge:'THE CHALLENGE GROUNDS',council:'THE COUNCIL FIRE',finale:'THE FINAL COUNCIL'}[phase]); document.querySelectorAll('[data-phase]').forEach(el=>{const active=el.dataset.phase===phase;el.classList.toggle('active',active);if(active)el.setAttribute('aria-current','step');else el.removeAttribute('aria-current');});}
function announce(text,danger=false){addEvent({kind:danger?'danger':'host',speaker:'The Host',text});}
function focusStory(){
  $('storyFeed').scrollTop=0;
  $('newEventsBtn').classList.add('hidden');
  $('storySection').scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion:reduce)').matches?'instant':'smooth',block:'start'});
}
function gameEvent(text,label='GAME EVENT',danger=false){addEvent({speaker:'Game event',kind:danger?'danger':'event',text,label});}
function addEvent(event){
  event.round=vm.state?.currentRound || 1;
  vm.events.push(event);if(vm.events.length>400)vm.events.shift();
  if(event.kind==='host'||(event.speaker==='The Host'&&event.kind==='danger')){
    $('hostCard').classList.remove('hidden');
    $('hostSpeech').textContent=event.text;
    const previous=vm.events.filter(e=>e!==event&&(e.kind==='host'||(e.speaker==='The Host'&&e.kind==='danger'))).reverse();
    $('hostHistory').classList.toggle('hidden',!previous.length);
    $('hostHistoryList').innerHTML=previous.map(e=>`<p>${esc(e.text)}</p>`).join('');
    vm.lastActivity=Date.now();
    return;
  }
  const feed=$('storyFeed'),following=feed.scrollTop<60,oldHeight=feed.scrollHeight;
  feed.querySelector('.empty')?.remove();
  const node=document.createElement('article');node.className=`story-event ${event.kind || ''}`;
  const marker=event.speaker==='The Host'?'H':event.speaker==='Game event'?'•':initials(displayName(event.speaker));
  const tag=event.label || (event.kind==='secret'?'PLAYER WHISPER':event.kind==='host'?'THE HOST':'');
  const heading=`<div class="event-heading"><strong>${esc(displayName(event.speaker))}</strong><small>${esc(tag)}</small></div>`;
  const content=event.kind==='secret'?`<div class="secret-message"><span class="whisper-recipient">To ${esc(displayName(event.recipient))}</span><p>${esc(event.text)}</p></div>`:`<p>${esc(event.text)}</p>`;
  node.innerHTML=`<span class="event-marker" aria-hidden="true">${esc(marker)}</span><div class="event-body">${heading}${content}</div>`;
  feed.prepend(node);while(feed.children.length>400)feed.lastChild.remove();
  if(following)feed.scrollTop=0;
  else {feed.scrollTop+=feed.scrollHeight-oldHeight;$('newEventsBtn').classList.remove('hidden');}
  vm.lastActivity=Date.now();
}

function parseInitialization(line){const m=line.match(/^Initialized: (.+?) \| Model: (.+?) \| Personality: (.+?) \| Stats: (.*?) \| Intro: "(.*)"$/);if(!m)return false; let p=byName(m[1]); if(!p){const id=String(vm.players.size+1);p={id,name:m[1],modelId:m[2],archetype:m[3],stats:{},relations:{},introduction:m[5]}; for(const [,k,v] of m[4].matchAll(/([A-Z_]+)=(-?\d+)/g))p.stats[k]=Number(v);vm.players.set(id,p);}else{p.archetype=m[3];p.introduction ||= m[5];} addEvent({speaker:m[1],text:m[5],label:'ARRIVES ON THE ISLAND'});return true;}
function recordCaptain(teamName,name){let team=vm.provisionalTeams.get(teamName);if(!team){team={teamName,captainName:name,names:[]};vm.provisionalTeams.set(teamName,team);} team.captainName=name;if(!team.names.includes(name))team.names.push(name);gameEvent(`${name} is captain of ${teamName}.`,'CAPTAIN SELECTED');setScene('draft','The first alliances start here.','The captains are choosing who they want beside them.');}
function recordPick(teamName,name){let team=vm.provisionalTeams.get(teamName);if(!team){team={teamName,names:[]};vm.provisionalTeams.set(teamName,team);}if(!team.names.includes(name))team.names.push(name);const player=byName(name);if(player)vm.origins.set(player.id,teamName);addEvent({speaker:name,text:`Joins ${teamName}. A new tribe begins to take shape.`,label:'DRAFTED'});}
function eliminate(name,text){const p=byName(name);if(p)p.eliminated=true;if(!vm.eliminations.some(e=>e.name===name))vm.eliminations.push({name,round:vm.state?.currentRound || 1,text});vm.immuneId=null;gameEvent(text,'ELIMINATION',true);renderHistory();}
function routeLine(raw){const line=raw.trim();if(!line)return;vm.logs.push(raw);if(vm.logs.length>600)vm.logs.shift();if(parseInitialization(line))return;let m;
  if((m=line.match(/^(.+?) Captain: (.+)$/))&&!line.startsWith('[')){recordCaptain(m[1],m[2]);return;}
  if((m=line.match(/^--> Team (.+?) \(Captain: .+?\) drafts (.+)!$/))){recordPick(m[1],m[2]);return;}
  if(line.includes('CAMP LIFE ROUND 1:')){setScene('camp','Get close. Keep your guard up.','The tribes settle in. Private conversations can change everything.');return;}
  if(line.includes('MERGING TEAMS INTO ONE TRIBE')){vm.merged=true;setScene('camp','One camp. No place to hide.','The tribes have merged. From now on, immunity belongs to one contestant.','THE MERGE');gameEvent('The tribes have merged into one camp.','THE MERGE');return;}
  if(line.includes('>>> THE FINAL SHOWDOWN <<<')){setScene('finale','Two remain. One will outlast.','A final challenge, then the jury decides who deserves to win.');gameEvent('The final showdown begins.','FINALE');return;}
  if(line.includes('FINAL TRIBAL COUNCIL: THE JURY VOTES')){setScene('finale','Their fate is in the jury’s hands.','The contestants voted out after the merge now choose the Sole Survivor.','THE JURY');return;}
  if((m=line.match(/^-+ Losing Team \((.+?)\) Voting Strategy Discussions -+$/))){setScene('council','Someone’s torch goes out tonight.',`${m[1]} gathers for Tribal Council. Promises are about to become votes.`);return;}
  if((m=line.match(/^- Secret Msg to (.+?):\s*"(.*)"\s*\[(?:DECEPTIVE|HONEST)\]/))){if(vm.currentSender){addEvent({kind:'secret',speaker:vm.currentSender,recipient:m[1],text:m[2]});if(line.includes('[ALLIANCE PROPOSED]')||line.includes('[DECEPTIVE]'))recordSocialMove(line.includes('[ALLIANCE PROPOSED]')?'Alliance proposed':'Deceptive whisper',m[2],vm.currentSender,m[1],line.includes('[DECEPTIVE]'));}return;}
  if(line.includes('Secret Msg:')&&line.includes('stayed silent')){if(vm.currentSender)addEvent({speaker:vm.currentSender,text:'Keeps their thoughts to themselves.',label:'LAYING LOW'});return;}
  if((m=line.match(/^- (.+?) received (\d+) vote\(s\)\.$/))){gameEvent(`${m[1]} receives ${m[2]} vote${m[2]==='1'?'':'s'}.`,'VOTE COUNT');return;}
  if(!(m=line.match(/^\[([A-Z][A-Z0-9 _()/-]*?)\]\s?(.*)$/)))return;handleTag(m[1],m[2]);
}
function handleTag(tag,text){let m;
  if(tag==='INTENT'){vm.currentSender=text.match(/^(.*?)\s+\([^)]*\):?$/)?.[1] || text.replace(/:$/,'');return;}
  if(tag==='HOST' && (m=text.match(/^Challenge Created(?: \(Fallback\))?: (.+)$/))){vm.challenge={title:m[1],scenario:'',objective:''};if(vm.phase!=='finale')setScene('challenge',m[1],'The host is setting the challenge. Every plan will be put to the test.');announce(m[1]);return;}
  if(tag==='HOST' && (m=text.match(/^(Scenario(?: \(Fallback\))?|Objective): (.+)$/))){if(vm.challenge){vm.challenge[m[1].startsWith('Scenario')?'scenario':'objective']=m[2];$('sceneDescription').textContent=vm.challenge.objective || vm.challenge.scenario;} addEvent({speaker:'The Host',text:m[2],kind:'host',label:'THE CHALLENGE'});return;}
  if(tag==='HOST JUDGING'||tag==='HOST JUDGING (FALLBACK)'){if((m=text.match(/^Winner: (.+?) \| Loser: (.+)$/))){announce(`${m[1]} wins safety. ${m[2]} must face the vote.`);}else announce(text.replace(/^Reasoning: /,''));return;}
  if(tag==='HOST ANNOUNCEMENT'){if((m=text.match(/^(.+?) has won INDIVIDUAL IMMUNITY/))){vm.immuneId=byName(m[1])?.id || m[1];}announce(text);return;}
  if(['HOST','HOST TWIST','JURY REVEAL'].includes(tag)){announce(text);return;}
  if(tag==='ELIMINATED'){const name=text.match(/^(.+?) (?:has been voted out|came up short)/)?.[1];if(name)eliminate(name,text);else gameEvent(text,'ELIMINATION',true);return;}
  if(tag==='PITCH'||tag==='PLAN PITCH'){if((m=text.match(/^(.+?):\s*"?(.*?)"?$/)))addEvent({speaker:m[1],text:m[2],label:'THE PLAN'});return;}
  if(tag==='CAPTAIN PLAN'){if((m=text.match(/^Captain (.+?) created final plan: "(.*)"$/)))addEvent({speaker:m[1],text:m[2],label:'THE TRIBE’S STRATEGY'});return;}
  if(tag==='TRIBAL VOTE'){addEvent({speaker:text.match(/^(.+?) (?:cast|has NO VOTE)/)?.[1] || 'The Host',text,label:'AT COUNCIL'});return;}
  if(tag==='JURY VOTE'){if((m=text.match(/^(.+?) votes for (.+?):\s*"(.*)"$/))){vm.juryVotes.push({juror:m[1],winner:m[2],reason:m[3]});vm.juryTally.set(m[2],(vm.juryTally.get(m[2])||0)+1);addEvent({speaker:m[1],text:`“${m[3]}” — Votes for ${m[2]}.`,label:'JURY VOTE'});}return;}
  if(['BETRAYAL PLANNED','BETRAYAL EXECUTED','DECEPTION'].includes(tag)){recordSocialMove(tag,text,null,null,true);gameEvent(text,tag.replaceAll('_',' '),tag==='BETRAYAL EXECUTED');return;}
  if(['TIE','TIE BREAK','NEW CAPTAIN'].includes(tag)){gameEvent(text,tag);return;}
}

function currentTeams(){if(vm.state?.teams?.length)return vm.state.teams.map(t=>({...t,players:(t.players || []).map(p=>vm.players.get(String(p.id)) || p)}));if(vm.provisionalTeams.size)return [...vm.provisionalTeams.values()].map(t=>({...t,teamId:t.teamName,captain:byName(t.captainName),players:t.names.map(byName).filter(Boolean)}));return [{teamId:'arrivals',teamName:'The arrivals',players:[...vm.players.values()]}];}
function teamColor(player){const name=vm.origins.get(player.id);const distinct=[...new Set(vm.origins.values())];return colors[Math.max(0,distinct.indexOf(name))%colors.length];}
function renderRoster(){const teams=currentTeams();const signature=JSON.stringify([teams,vm.selectedId,vm.immuneId]);if(signature===vm.rosterKey)return;vm.rosterKey=signature;$('tribesBoard').innerHTML=teams.map((team,i)=>`<div class="tribe" style="--tribe-color:${colors[i%colors.length]}"><div class="tribe-heading"><span class="tribe-swatch"></span><h3>${esc(team.teamName)}</h3><span class="tribe-count">${team.players.filter(p=>!p.eliminated).length} remaining</span></div><div class="player-grid">${team.players.map(p=>{const immune=p.id===vm.immuneId || p.name===vm.immuneId;const captain=String(team.captain?.id)===p.id;return `<button type="button" class="player-card${p.id===vm.selectedId?' selected':''}${p.eliminated?' out':''}${immune?' immune':''}" data-player-id="${esc(p.id)}" aria-pressed="${p.id===vm.selectedId}" style="--tribe-color:${teamColor(p)}"><span class="player-avatar" aria-hidden="true">${esc(initials(p.name))}</span><span class="player-badge">${p.eliminated?'Out':immune?'♢ Immune':captain?'Captain':''}</span><span class="player-name">${esc(p.name)}</span><span class="player-archetype">${esc(archetype(p))}</span></button>`;}).join('')}</div></div>`).join(''); $('rosterTitle').textContent=vm.merged?'The merged tribe':'On the island';}

function recordSocialMove(label,text,sender=null,recipient=null,deceptive=false){
  vm.socialMoves.push({label,text,sender,recipient,deceptive});
  if(vm.socialMoves.length>60)vm.socialMoves.shift();
}
function renderSocialPanel(){
  const bonds=[];
  for(const p of vm.players.values()){
    if(p.eliminated)continue;
    for(const r of Object.values(p.relations || {})){
      if(r.targetEliminated || !['PROPOSED','CONFIRMED','BROKEN'].includes(r.alliance))continue;
      bonds.push({name:p.name,target:r.name || vm.players.get(String(r.id))?.name || 'Unknown contestant',status:r.alliance});
    }
  }
  const signature=JSON.stringify([bonds,vm.socialMoves,[...vm.players.values()].map(p=>[p.id,p.name])]);
  if(signature===vm.socialKey)return;
  vm.socialKey=signature;
  $('socialBonds').innerHTML='<h3 class="social-subtitle">Current bonds</h3><p class="social-hint">Each contestant’s view of their alliance.</p>'+(bonds.length?bonds.map(b=>`<div class="social-bond ${b.status.toLowerCase()}"><strong>${esc(b.name)} → ${esc(b.target)}</strong><span>${({PROPOSED:'Proposed',CONFIRMED:'Considers an ally',BROKEN:'Broken'})[b.status]}</span></div>`).join(''):'<p class="empty">No alliances recorded yet.</p>');
  $('socialMoves').innerHTML=vm.socialMoves.length?[...vm.socialMoves].reverse().map(e=>`<article class="social-move ${e.deceptive?'deceptive':''}"><span class="eyebrow">${esc(e.label)}${e.deceptive&&e.label==='Alliance proposed'?' · deceptive':''}</span>${e.sender?`<strong>${esc(displayName(e.sender))} → ${esc(displayName(e.recipient))}</strong>`:''}<p>${esc(e.text)}</p></article>`).join(''):'<p class="empty">The first promises are still to come.</p>';
}

function renderProfile(){const p=vm.players.get(vm.selectedId);if(!p)return;const signature=JSON.stringify([p,vm.immuneId]);if(signature===vm.profileKey)return;vm.profileKey=signature;const panel=$('profilePanel');const wasOpen=panel.querySelector('details')?.open;const relations=Object.values(p.relations || {}).sort((a,b)=>b.trustScore-a.trustScore);panel.innerHTML=`<div class="profile-header" style="--tribe-color:${teamColor(p)}"><span class="player-avatar" aria-hidden="true">${esc(initials(p.name))}</span><div><h2>${esc(p.name)}</h2><p>${esc(archetype(p))}</p><div class="profile-status">${p.eliminated?'Voted off the island':p.id===vm.immuneId?'Holds individual immunity':vm.origins.get(p.id)?esc(vm.origins.get(p.id))+' tribe':'On the island'}</div></div></div><p class="profile-intro">${esc(p.introduction || 'Their story is still being written.')}</p><div class="profile-subheading">STRENGTHS & INSTINCTS</div>${Object.entries(stats).map(([key,label])=>{const raw=p.stats?.[key];const value=Number.isFinite(Number(raw))?Math.max(0,Math.min(100,Number(raw))):0;return `<div class="stat-row"><span>${label}</span><strong>${raw==null?'—':esc(raw)}</strong><div class="stat-bar" role="meter" aria-label="${label}" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${value}"><span style="width:${value}%"></span></div></div>`;}).join('')}<div class="profile-subheading">THEIR VIEW OF THE OTHERS</div>${relations.length?relations.map(r=>`<div class="relation"><div class="relation-top"><span class="relation-name">${esc(r.name)}${r.targetEliminated?' · out':''}</span><span class="relation-label ${r.alliance==='BROKEN'?'broken':''}">${esc(({NONE:'NO ALLIANCE',PROPOSED:'PROPOSED',CONFIRMED:'ALLY',BROKEN:'BROKEN'})[r.alliance]||'NO ALLIANCE')}</span></div><div class="trust-meter" title="Trust: ${esc(r.trustScore)} out of 100"><span style="width:${Math.max(0,Math.min(100,Number(r.trustScore)||0))}%"></span></div><p class="relation-note">${esc(r.notes || `Trust: ${r.trustScore ?? 50}/100`)}</p></div>`).join(''):'<p class="empty">No bonds yet. That will change at camp.</p>'}<details class="private-summary" ${wasOpen?'open':''}><summary>Peek at their private thoughts</summary><p>${esc(p.gameSummary || 'Taking in the island.')}</p></details>`;}
function selectPlayer(id){if(!vm.players.has(id))return;vm.selectedId=id;vm.profileKey='';renderRoster();renderProfile();}
function renderHistory(){$('eliminatedCount').textContent=`${vm.eliminations.length} OUT`;$('islandHistory').innerHTML=vm.eliminations.length?vm.eliminations.map((e,i)=>`<div class="history-item"><span class="history-number">${String(i+1).padStart(2,'0')}</span><div>${esc(e.name)}<small>${esc(e.text.includes('came up short')?'Runner-up':'Voted out')} · round ${e.round}</small>${byName(e.name)?`<button type="button" class="text-button" data-player-id="${esc(byName(e.name).id)}">Their story</button>`:''}</div></div>`).join(''):'<p class="empty">Every torch is still burning.</p>';}
function renderState(){const data=vm.state;if(!data)return;const active=data.activePlayers?.length || 0;$('roundNumber').textContent=String(data.currentRound||1).padStart(2,'0');$('sceneIndex').textContent=String(data.currentRound||1).padStart(2,'0');$('survivorCount').textContent=active || (data.status==='RUNNING'?vm.players.size:'0');$('tribeCount').textContent=data.teams?.length || vm.provisionalTeams.size || '—';const statuses={RUNNING:'The story unfolds…',PAUSED:'Scene complete — click Next scene to continue',COMPLETED:'A Sole Survivor is crowned',FAILED:'The journey was interrupted',STOPPING:'Leaving the island…',CANCELLED:'The game has stopped',NOT_STARTED:'Waiting for the cast'};$('gameStatus').textContent=statuses[data.status] || 'On the island';$('resetBtn').textContent=['STOPPING','CANCELLED'].includes(data.status)?'Finish reset':'Leave game';const resume=$('resumeBtn');resume.classList.toggle('hidden',!(data.status==='PAUSED'&&data.paused&&data.stepByStep)&&data.status!=='COMPLETED');resume.innerHTML=data.status==='COMPLETED'?'View the winner':'Next scene <span aria-hidden="true">→</span>';resume.classList.toggle('ready',data.status==='PAUSED'&&data.paused&&data.stepByStep);if(!vm.selectedId&&vm.players.size)vm.selectedId=vm.players.keys().next().value;renderRoster();renderProfile();renderSocialPanel();$('techLog').textContent=vm.logs.join('\n');if(data.status==='COMPLETED'){setScene('finale',`${data.winnerName} outlasted them all.`,'The jury has spoken. Revisit the cast and the choices that brought them here.','SOLE SURVIVOR');if(!vm.winnerShown){vm.winnerShown=true;showWinner();}}if(data.status==='FAILED')showNotice('The game stopped before the finale. Open “Behind the scenes” below to see what happened.');}
function showWinner(){const winner=byName(vm.state?.winnerName);$('winnerName').textContent=vm.state?.winnerName || 'The Sole Survivor';$('winnerAvatar').textContent=initials(vm.state?.winnerName);$('winnerArchetype').textContent=winner?archetype(winner):'';$('winnerTally').innerHTML=[...vm.juryTally].map(([name,n])=>`<span>${esc(name)} <strong>${n} vote${n===1?'':'s'}</strong></span>`).join('');if(!$('winnerModal').open)$('winnerModal').showModal();}

// Pace only presentation. The server and its existing checkpoints are unchanged.
function readingDelay(text){return Math.min(14000,Math.max(4500,String(text).split(/\s+/).length*280));}
function queuePresentation(data,lines){
  for(const line of lines)playback.queue.push({line});
  if(playback.queue.at(-1)?.state)playback.queue.pop();
  playback.queue.push({state:data});
  if(!vm.state)vm.state={status:'RUNNING',activePlayers:[],teams:[],currentRound:1};
  if(!playback.timer)presentNext();
}
function presentNext(){
  playback.timer=null;
  while(playback.queue.length){
    const item=playback.queue[0];
    if(item.line && !playback.selectingShown && /^.+? Captain: .+$/.test(item.line.trim()) && !item.line.trim().startsWith('[')){
      playback.selectingShown=true;
      setScene('draft','Selecting captains…','The tribe leaders will be revealed shortly.','THE DRAFT');
      renderPlayback();
      playback.timer=setTimeout(presentNext,4500);return;
    }
    playback.queue.shift();
    if(item.state){ingestState(item.state);continue;}
    const before=vm.events.at(-1),phase=vm.phase,title=$('sceneTitle').textContent;
    routeLine(item.line);
    const event=vm.events.at(-1);
    if(event!==before || phase!==vm.phase || title!==$('sceneTitle').textContent){
      renderPlayback();
      playback.timer=setTimeout(presentNext,readingDelay(event!==before?event.text:$('sceneDescription').textContent));return;
    }
  }
  renderState();
}
function renderPlayback(){
  renderRoster();renderProfile();renderSocialPanel();
  $('techLog').textContent=vm.logs.join('\n');
  $('gameStatus').textContent='The story unfolds…';
  $('resumeBtn').classList.add('hidden');$('resumeBtn').classList.remove('ready');
}

async function poll(){if(vm.busy||!vm.polling)return;vm.busy=true;const epoch=vm.epoch;try{const data=await request('/state');if(epoch!==vm.epoch)return;const packet=await request('/logs?from='+vm.nextIndex);if(epoch!==vm.epoch)return;showNotice('');queuePresentation(data,packet.logs || []);vm.nextIndex=packet.nextIndex ?? vm.nextIndex;if(['COMPLETED','FAILED'].includes(data.status))stopPolling();}catch(e){if(epoch===vm.epoch)showNotice('Connection to the island interrupted. Reconnecting automatically…');}finally{vm.busy=false;if(vm.polling&&epoch===vm.epoch)vm.timer=setTimeout(poll,1500);}}
function startPolling(){if(vm.polling)return;vm.polling=true;poll();}
function stopPolling(){vm.polling=false;clearTimeout(vm.timer);vm.timer=null;}
async function advance(){if(playback.timer||playback.queue.length)return;if(vm.state?.status==='COMPLETED'){showWinner();return;}const button=$('resumeBtn');button.disabled=true;button.classList.remove('ready');focusStory();try{await request('/next',{method:'POST'});button.classList.add('hidden');$('gameStatus').textContent='The next scene begins…';showNotice('');}catch(e){showNotice(e.message);}finally{button.disabled=false;}}
async function reset(){if(vm.state?.status!=='COMPLETED'&&!confirm('Leave this game and reset the island?'))return;const button=$('resetBtn');button.disabled=true;try{await request('/reset',{method:'POST'});vm.epoch++;stopPolling();clearTimeout(playback.timer);playback.queue=[];location.reload();}catch(e){showNotice('The game could not be reset. '+e.message);button.disabled=false;}}
async function reconnect(){try{const data=await request('/state');if(['RUNNING','PAUSED','COMPLETED','FAILED'].includes(data.status)){enterGame();startPolling();}}catch(_){/* Setup remains usable while the local backend starts. */}}
document.addEventListener('DOMContentLoaded',()=>{syncPlayerOptions();$('teamsSelect').addEventListener('change',syncPlayerOptions);$('playersSelect').addEventListener('change',buildContestants);$('randomizeBtn').addEventListener('click',buildContestants);$('setupForm').addEventListener('submit',launch);$('resumeBtn').addEventListener('click',advance);$('newEventsBtn').addEventListener('click',focusStory);$('resetBtn').addEventListener('click',reset);$('winnerCloseBtn').addEventListener('click',reset);$('winnerContinueBtn').addEventListener('click',()=>$('winnerModal').close());document.addEventListener('click',event=>{const button=event.target.closest('[data-player-id]');if(button){selectPlayer(button.dataset.playerId);if(window.matchMedia('(max-width:800px)').matches)$('profilePanel').scrollIntoView({behavior:'smooth',block:'start'});}});setScene('draft','Strangers on the shore.','The cast is arriving. Every new face could be an ally—or a threat.','THE ARRIVAL');reconnect();});
