(() => {
  const $ = (selector) => document.querySelector(selector);
  const icon = (name) => `<svg aria-hidden="true"><use href="#i-${name}"/></svg>`;
  const params = new URLSearchParams(location.search);
  const files = {
    video: { name: 'coast-flight.mp4', icon: 'film', duration: 258 },
    audio: { name: 'field-recording.flac', icon: 'audio', duration: 198 },
    image: { name: 'coast-study.png', icon: 'image', duration: 0 },
    animation: { name: 'orbit-loop.gif', icon: 'image', duration: 1.52 },
  };
  const state = {
    media: files[params.get('media')] ? params.get('media') : 'video',
    view: ['preview', 'spectrogram', 'info'].includes(params.get('view')) ? params.get('view') : 'preview',
    theme: params.get('theme') === 'light' ? 'light' : 'dark',
    scenario: params.get('state') || 'ready',
    split: params.get('split') === '1',
    range: params.get('range') === 'file' ? 'file' : 'live',
    expanded: true,
    playing: false,
    time: 84,
    zoom: 100,
    panX: 0,
    panY: 0,
    repeat: true,
    speed: 1,
  };
  const frameDurations = [.08, .08, .16, .08, .24, .08, .08, .16, .08, .24, .08, .16];
  if (state.media === 'animation') state.time = .64;
  let toastTimer;
  const time = (value) => `${Math.floor(value / 60).toString().padStart(2, '0')}:${Math.floor(value % 60).toString().padStart(2, '0')}`;
  const unavailable = () => ['loading', 'unsupported', 'native'].includes(state.scenario);
  const hasAudio = () => ['video', 'audio'].includes(state.media) && state.scenario !== 'no-audio';
  const position = (value) => state.media === 'animation' ? `${time(value)}.${(Math.round(value * 1000) % 1000).toString().padStart(3, '0')}` : time(value);
  const animationFrame = () => {
    let end = 0;
    return frameDurations.findIndex((duration) => { end += duration; return state.time < end - .00001; }) + 1 || frameDurations.length;
  };

  function toast(message) {
    $('#toast').textContent = message;
    $('#toast').classList.add('visible');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => $('#toast').classList.remove('visible'), 2300);
  }

  function rangeTabs() {
    return `<div class="range-tabs" role="group" aria-label="Spectrogram range"><button data-range="file" aria-pressed="${state.range === 'file'}">File</button><button data-range="live" aria-pressed="${state.range === 'live'}">Live</button></div>`;
  }

  function graph() {
    return `<div class="graph" aria-label="Illustrative source-track spectrogram, frequency versus source time"><div class="frequency-axis" aria-hidden="true"><span>20k</span><span>10k</span><span>5k</span><span>2k</span><span>1k</span><span>Hz</span></div><div class="plot-area"><div class="plot-content" title="Seek to source time"><canvas class="spectral-canvas" aria-label="Synthetic spectrogram for layout review"></canvas><div class="plot-grid"></div><div class="plot-cursor"></div></div><div class="time-axis"></div></div><div class="db-legend" aria-label="Intensity from minus 96 to 0 decibels"><div class="db-gradient"></div><div class="db-values"><span>0</span><span>−24</span><span>−48</span><span>−72</span><span>−96</span></div></div></div>`;
  }

  function viewToolbar() {
    const format = { image: 'PNG', animation: 'Animated GIF', audio: 'FLAC', video: '1080p' }[state.media];
    return `<div class="view-toolbar"><div class="view-tabs" role="tablist" aria-label="Media views"><button role="tab" data-view="preview" aria-selected="${state.view === 'preview'}">Preview</button><button role="tab" data-view="spectrogram" aria-selected="${state.view === 'spectrogram'}" ${!hasAudio() ? 'disabled' : ''}>Spectrogram</button><button role="tab" data-view="info" aria-selected="${state.view === 'info'}">MediaInfo</button></div><div class="toolbar-actions"><span class="subtle-label">${format}</span><button class="icon-button" id="options-toggle" aria-label="Media options" aria-expanded="false">${icon('more')}</button></div></div><div id="media-options" class="secondary-popup" hidden>${hasAudio() ? '<div class="popup-row"><label for="audio-track">Audio track</label><select id="audio-track"><option>1 / Original</option><option>2 / Commentary</option></select></div><div class="popup-row"><label for="subtitles">Subtitles</label><select id="subtitles"><option>Off</option><option>English</option></select></div>' : '<div class="popup-row"><span>Image inspection</span><span>Fit / original size</span></div>'}<div class="popup-row"><span>${state.media === 'image' ? 'Zoom controls' : 'Playback speed'}</span><span>Available in the bottom toolbar</span></div></div>`;
  }

  function transport() {
    const blocked = unavailable() ? 'disabled' : '';
    const animated = state.media === 'animation';
    return `<div class="transport"><div class="seek-row"><span class="time-readout current-time">${position(state.time)}</span><input class="seek" id="seek" aria-label="Playback position" type="range" min="0" step="${animated ? '.01' : '1'}" max="${files[state.media].duration}" value="${state.time}" ${blocked}><span class="time-readout total-time">${position(files[state.media].duration)}</span></div><div class="transport-row"><button class="icon-button play-button" id="play-toggle" aria-label="${state.playing ? 'Pause' : 'Play'}" ${blocked}>${icon(state.playing ? 'pause' : 'play')}</button>${animated ? '<button class="icon-button" id="previous-frame" aria-label="Previous frame">|◀</button><button class="icon-button" id="next-frame" aria-label="Next frame">▶|</button>' : `<button class="icon-button step-button" id="seek-back" aria-label="Seek backward 10 seconds" ${blocked}>${icon('back')}</button><button class="icon-button step-button" id="seek-forward" aria-label="Seek forward 10 seconds" ${blocked}>${icon('forward')}</button>`}<span class="transport-status">${animated ? `Frame ${animationFrame()} / 12` : state.playing ? 'Playing' : 'Paused'}</span><span class="transport-spacer"></span>${animated ? `<button class="speed-button" id="repeat-toggle" aria-label="Repeat animation" aria-pressed="${state.repeat}">Loop ${state.repeat ? 'on' : 'off'}</button>` : '<div class="volume"><button class="icon-button" id="mute-toggle" aria-label="Mute" aria-pressed="false">' + icon('volume') + '</button><input type="range" min="0" max="100" value="72" aria-label="Volume"></div><button class="track-button secondary" id="track-options">Audio / subtitles</button>'}<button class="speed-button" id="speed-toggle" aria-label="Playback speed">${state.speed}×</button>${!animated ? `<button class="icon-button secondary" id="fit-video" aria-label="Fit video to editor">${icon('expand')}</button>` : ''}</div></div>`;
  }

  function animationContent() {
    return `<div class="media-viewport animation-viewport"><div class="animation-artwork" aria-label="Illustrative animation frame"><svg viewBox="0 0 320 320"><defs><radialGradient id="orb-color" cx=".35" cy=".3"><stop stop-color="#a5c6ff"/><stop offset=".5" stop-color="#5084e6"/><stop offset="1" stop-color="#263c74"/></radialGradient></defs><ellipse cx="160" cy="160" rx="135" ry="52" fill="none" stroke="#6c86bb" stroke-width="2" transform="rotate(-24 160 160)"/><circle cx="160" cy="160" r="74" fill="url(#orb-color)" stroke="none"/><g id="animation-orbit"><circle cx="270" cy="113" r="13" fill="#f0c889" stroke="none"/><circle cx="50" cy="207" r="6" fill="#a8b8d6" stroke="none"/></g></svg></div><span class="animation-source-note">640 × 640 / RGBA / Variable frame durations</span></div>`;
  }

  function failurePanel() {
    return `<div class="analysis-failure"><div><h3>Could not analyze this audio track</h3><p>Playback is available. Retry analysis or copy the diagnostic details.</p></div><button id="retry-analysis">Retry</button></div>`;
  }

  function drawer() {
    if (!hasAudio()) return '<div class="empty-analysis">This video has no audio track.</div>';
    if (state.scenario === 'analysis-error') return failurePanel();
    return `<section class="analysis-drawer ${state.expanded ? '' : 'collapsed'}" aria-label="Inline audio analysis"><div class="analysis-heading"><button class="drawer-toggle" id="drawer-toggle" aria-expanded="${state.expanded}">${icon('chevron')}Spectrogram</button><span class="analysis-meta">Original audio / 48 kHz</span>${rangeTabs()}</div><div class="analysis-plot">${graph()}</div></section>`;
  }

  function stateContent() {
    const variants = {
      loading: ['Opening media', 'Reading the file and preparing its video and audio tracks.'],
      unsupported: ['Cannot play this file', 'The file may be damaged or use a codec this media engine cannot decode. MediaInfo is still available.'],
      native: ['Media engine could not start', 'The bundled media engine is unavailable. Retry, or copy diagnostic details when reporting the problem.'],
    };
    const [title, message] = variants[state.scenario];
    return `<div class="state-content"><div class="state-card">${state.scenario === 'loading' ? '<div class="loading-track" role="progressbar" aria-label="Opening media"></div>' : '<div class="state-icon" aria-hidden="true">!</div>'}<h2>${title}</h2><p>${message}</p>${state.scenario === 'loading' ? '<div class="state-actions"><button id="cancel-opening">Cancel</button></div>' : '<div class="state-actions"><button class="primary-action" id="retry-media">Retry</button><button id="copy-diagnostics">Copy diagnostics</button></div><details><summary>Technical details</summary><code>Example diagnostic for design review.<br>No file or native runtime was loaded by this preview.</code></details>'}</div></div>`;
  }

  function audioContent() {
    if (state.scenario === 'analysis-error') return `<div class="audio-content"><div class="audio-heading"><div class="audio-file-icon">${icon('audio')}</div><div><h2>field-recording.flac</h2><p>Stereo / 48 kHz / 24-bit</p></div></div>${failurePanel()}<div class="state-content"><div class="state-card"><p>The audio can keep playing while analysis is unavailable.</p></div></div></div>`;
    return `<div class="audio-content"><div class="audio-heading"><div class="audio-file-icon">${icon('audio')}</div><div><h2>${files[state.media].name}</h2><p>${state.media === 'audio' ? 'FLAC / Stereo / 48 kHz / 24-bit' : 'Original audio / Stereo / 48 kHz'}</p></div>${rangeTabs()}</div><div class="analysis-plot">${graph()}</div><div class="analysis-hint">${state.range === 'live' ? 'Live source analysis follows playback. Volume and mute do not alter the graph.' : 'Full-track overview. Click the graph to seek to a source position.'}</div></div>`;
  }

  function imageContent() {
    return `<div class="media-viewport image-viewport"><img class="media-artwork" id="image-artwork" src="assets/coast.svg" draggable="false" alt="Original coastal illustration used as sample image content"></div><div class="image-footer"><span>1600 × 900 <span class="secondary"> / PNG / RGBA</span></span><div class="zoom-actions"><button id="zoom-fit" aria-label="Fit image">Fit</button><button id="zoom-original" aria-label="Original image size">100%</button><button id="zoom-out" aria-label="Zoom out">−</button><span class="zoom-value">100%</span><button id="zoom-in" aria-label="Zoom in">+</button></div></div>`;
  }

  function information() {
    const video = state.media === 'video';
    const audio = state.media === 'audio';
    const animated = state.media === 'animation';
    const table = (rows) => `<table class="property-table"><tbody>${rows.map(([key, value]) => `<tr><td>${key}</td><td>${value}</td></tr>`).join('')}</tbody></table>`;
    const general = [['File name', files[state.media].name], ['Format', video ? 'MPEG-4' : audio ? 'FLAC' : animated ? 'GIF' : 'PNG'], ['File size', video ? '864 MiB' : audio ? '54.2 MiB' : animated ? '184 KiB' : '8.4 MiB']];
    if (animated) general.push(['Duration', '1.520 s'], ['Frame count', '12 / variable duration']);
    if (video || audio) general.push(['Duration', video ? '4 min 18 s' : '3 min 18 s']);
    return `<div class="info-content"><div class="info-heading"><div><h2>MediaInfo</h2><p>${files[state.media].name}</p></div><button class="copy-button" id="copy-report">${icon('copy')}Copy report</button></div><section class="info-section"><h3>General</h3>${table(general)}</section>${video ? `<section class="info-section"><h3>Video <span class="track-tag">Track 1</span></h3>${table([['Format', 'AVC / H.264'], ['Profile', 'High@L4.1'], ['Dimensions', '1920 × 1080'], ['Frame rate', '24.000 FPS'], ['Color', 'YUV 4:2:0 / 8-bit / BT.709']])}</section>` : ''}${video || audio ? `<section class="info-section"><h3>Audio <span class="track-tag">Track 1 / Original</span></h3>${table([['Format', audio ? 'FLAC' : 'AAC LC'], ['Channels', '2 / Stereo'], ['Sampling rate', '48.0 kHz'], ['Bit depth', audio ? '24 bits' : 'Unavailable']])}</section>` : `<section class="info-section"><h3>Image</h3>${table([['Dimensions', animated ? '640 × 640' : '1600 × 900'], ['Color', 'RGBA'], ['Orientation', 'Normal']])}</section>`}<details class="info-report"><summary>Full text report</summary><pre id="report-text">${report()}</pre></details></div>`;
  }

  function report() {
    return `General\nComplete name : samples/${files[state.media].name}\nFormat        : ${state.media === 'video' ? 'MPEG-4' : state.media === 'audio' ? 'FLAC' : state.media === 'animation' ? 'GIF' : 'PNG'}\n\nIllustrative MediaInfo report for design review.\nThe implemented plugin will expose the full actual report.`;
  }

  function render() {
    if (!hasAudio() && state.view === 'spectrogram') state.view = 'preview';
    if (unavailable()) state.playing = false;
    document.body.dataset.theme = state.theme;
    $('#theme-toggle').textContent = state.theme === 'dark' ? 'Light theme' : 'Dark theme';
    $('#theme-toggle').setAttribute('aria-label', `Switch to ${state.theme === 'dark' ? 'light' : 'dark'} theme`);
    $('#ide-frame').classList.toggle('split', state.split);
    $('#split-toggle').setAttribute('aria-pressed', String(state.split));
    $('#scenario').value = state.scenario;
    $('#file-name').textContent = files[state.media].name;
    $('#status-file').textContent = files[state.media].name;
    $('#file-icon use').setAttribute('href', `#i-${files[state.media].icon}`);
    document.querySelectorAll('[data-file]').forEach((button) => button.classList.toggle('selected', button.dataset.file === state.media));
    document.querySelectorAll('[data-preset]').forEach((button) => button.setAttribute('aria-pressed', String(button.dataset.preset === (state.view === 'info' ? 'info' : state.media))));
    let content;
    if (state.view === 'info') content = information() + (state.media === 'image' ? '' : transport());
    else if (state.media === 'image') content = state.scenario === 'unsupported' || state.scenario === 'loading' ? stateContent() : imageContent();
    else if (unavailable()) content = stateContent() + transport();
    else if (state.media === 'animation') content = animationContent() + transport();
    else if (state.media === 'audio' || state.view === 'spectrogram') content = audioContent() + transport();
    else content = `<div class="main-content"><div class="media-viewport"><img class="media-artwork" src="assets/coast.svg" alt="Original coastal scene standing in for a paused video frame"><span class="viewport-note">FRAME 2016</span></div>${drawer()}</div>${transport()}`;
    $('#editor-body').innerHTML = viewToolbar() + content;
    if (unavailable()) document.querySelectorAll('.transport button,.transport input').forEach((element) => { element.disabled = true; });
    bindEditor();
    requestAnimationFrame(() => { paintGraphs(); updateClock(); });
  }

  function setMedia(media, view = 'preview') {
    state.media = media;
    state.view = view;
    state.scenario = 'ready';
    state.playing = false;
    state.time = media === 'animation' ? .64 : 84;
    state.speed = 1;
    state.repeat = true;
    state.expanded = true;
    state.range = 'live';
    state.zoom = 100;
    state.panX = state.panY = 0;
    render();
  }

  function seek(value) {
    state.time = Math.max(0, Math.min(files[state.media].duration, Number(value)));
    updateClock();
    if (state.range === 'live') paintGraphs();
  }

  function updateClock() {
    if ($('#seek')) $('#seek').value = state.time;
    document.querySelectorAll('.current-time').forEach((element) => { element.textContent = position(state.time); });
    if (state.media === 'animation') {
      const orbit = $('#animation-orbit');
      if (orbit) orbit.setAttribute('transform', `rotate(${(animationFrame() - 1) * 30} 160 160)`);
      if ($('.transport-status')) $('.transport-status').textContent = `Frame ${animationFrame()} / 12`;
    }
    const duration = files[state.media].duration || 1;
    document.querySelectorAll('.plot-cursor').forEach((element) => { element.style.left = `${state.range === 'file' ? state.time / duration * 100 : 72}%`; });
    document.querySelectorAll('.time-axis').forEach((axis) => {
      const start = state.range === 'file' ? 0 : Math.max(0, state.time - 10.8);
      const end = state.range === 'file' ? duration : start + 15;
      axis.innerHTML = Array.from({ length: 6 }, (_, i) => `<span>${time(start + (end - start) * i / 5)}</span>`).join('');
    });
  }

  function togglePlay() {
    if (unavailable() || state.media === 'image') return;
    state.playing = !state.playing;
    $('#play-toggle').innerHTML = icon(state.playing ? 'pause' : 'play');
    $('#play-toggle').setAttribute('aria-label', state.playing ? 'Pause' : 'Play');
    $('.transport-status').textContent = state.media === 'animation' ? `Frame ${animationFrame()} / 12` : state.playing ? 'Playing' : 'Paused';
  }

  async function copy(text) {
    try {
      await navigator.clipboard.writeText(text);
      toast('Copied');
    } catch {
      toast('Clipboard is unavailable in this browser preview.');
    }
  }

  function bindEditor() {
    const on = (selector, event, handler) => $(selector)?.addEventListener(event, handler);
    document.querySelectorAll('[data-view]').forEach((button) => button.addEventListener('click', () => { state.view = button.dataset.view; render(); }));
    document.querySelectorAll('[data-range]').forEach((button) => button.addEventListener('click', () => { state.range = button.dataset.range; render(); }));
    on('#drawer-toggle', 'click', () => { state.expanded = !state.expanded; render(); });
    on('#play-toggle', 'click', togglePlay);
    on('#seek', 'input', (event) => seek(event.target.value));
    on('#seek-back', 'click', () => seek(state.time - 10));
    on('#seek-forward', 'click', () => seek(state.time + 10));
    const stepFrame = (delta) => {
      if (state.playing) togglePlay();
      const target = Math.max(0, Math.min(11, animationFrame() - 1 + delta));
      seek(frameDurations.slice(0, target).reduce((sum, duration) => sum + duration, 0));
    };
    on('#previous-frame', 'click', () => stepFrame(-1));
    on('#next-frame', 'click', () => stepFrame(1));
    on('#repeat-toggle', 'click', (event) => {
      state.repeat = !state.repeat;
      event.currentTarget.setAttribute('aria-pressed', String(state.repeat));
      event.currentTarget.textContent = `Loop ${state.repeat ? 'on' : 'off'}`;
    });
    on('#speed-toggle', 'click', (event) => {
      const speeds = ['1×', '1.25×', '1.5×', '2×', '0.5×'];
      event.currentTarget.textContent = speeds[(speeds.indexOf(event.currentTarget.textContent) + 1) % speeds.length];
      state.speed = Number(event.currentTarget.textContent.replace('×', ''));
    });
    on('#mute-toggle', 'click', (event) => {
      const muted = event.currentTarget.getAttribute('aria-pressed') === 'true';
      event.currentTarget.setAttribute('aria-pressed', String(!muted));
      event.currentTarget.setAttribute('aria-label', muted ? 'Mute' : 'Unmute');
      toast(muted ? 'Unmuted (simulated)' : 'Muted (simulated). Source analysis is unchanged.');
    });
    const options = () => {
      const popup = $('#media-options');
      popup.hidden = !popup.hidden;
      $('#options-toggle').setAttribute('aria-expanded', String(!popup.hidden));
    };
    on('#options-toggle', 'click', options);
    on('#track-options', 'click', options);
    on('#audio-track', 'change', () => toast('Track selection preview. Analysis will restart for the selected source track.'));
    on('#fit-video', 'click', () => toast('Video fits the editor and keeps its aspect ratio.'));
    on('#copy-report', 'click', () => copy(report()));
    on('#copy-diagnostics', 'click', () => copy('AnyPlayer design preview. No native runtime or media file was loaded.'));
    for (const id of ['#retry-media', '#retry-analysis', '#cancel-opening']) on(id, 'click', () => { state.scenario = 'ready'; render(); });
    const zoom = (value) => {
      state.zoom = Math.max(10, Math.min(250, value));
      state.panX = state.panY = 0;
      $('#image-artwork').style.width = `${1600 * state.zoom / 100}px`;
      $('#image-artwork').style.height = `${900 * state.zoom / 100}px`;
      $('#image-artwork').style.transform = '';
      $('.zoom-value').textContent = `${Math.round(state.zoom)}%`;
    };
    on('#zoom-in', 'click', () => zoom(state.zoom + 25));
    on('#zoom-out', 'click', () => zoom(state.zoom - 25));
    const fitImage = () => {
      const viewport = $('#image-artwork').parentElement;
      zoom(Math.min(100, viewport.clientWidth * .82 / 1600 * 100, viewport.clientHeight * .82 / 900 * 100));
    };
    on('#zoom-fit', 'click', fitImage);
    on('#zoom-original', 'click', () => zoom(100));
    if ($('#image-artwork')) fitImage();
    document.querySelectorAll('.plot-content').forEach((plot) => plot.addEventListener('click', (event) => {
      const fraction = (event.clientX - plot.getBoundingClientRect().left) / plot.clientWidth;
      const value = state.range === 'file' ? fraction * files[state.media].duration : Math.max(0, state.time - 10.8) + fraction * 15;
      seek(value);
    }));
    const artwork = $('#image-artwork');
    let drag;
    artwork?.addEventListener('pointerdown', (event) => {
      drag = { x: event.clientX - state.panX, y: event.clientY - state.panY };
      artwork.setPointerCapture(event.pointerId);
      artwork.style.cursor = 'grabbing';
    });
    artwork?.addEventListener('pointermove', (event) => {
      if (!drag) return;
      state.panX = Math.max(-300, Math.min(300, event.clientX - drag.x));
      state.panY = Math.max(-200, Math.min(200, event.clientY - drag.y));
      artwork.style.transform = `translate(${state.panX}px,${state.panY}px)`;
    });
    artwork?.addEventListener('pointerup', () => { drag = null; artwork.style.cursor = 'grab'; });
  }

  function paintGraphs() {
    const palette = [[11, 17, 29], [42, 35, 82], [90, 43, 126], [164, 46, 135], [226, 83, 100], [249, 153, 71], [255, 237, 169]];
    document.querySelectorAll('.spectral-canvas').forEach((canvas) => {
      const width = 480;
      const height = 140;
      canvas.width = width;
      canvas.height = height;
      const context = canvas.getContext('2d');
      const pixels = context.createImageData(width, height);
      const offset = state.range === 'live' ? state.time / 15 : 0;
      for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
          const t = x / width * 22 + offset;
          const f = (height - y) / height;
          const noise = Math.sin(x * 12.73 + y * 3.27) * Math.sin(x * .53 - y * 2.67);
          const envelope = .52 + .18 * Math.sin(t * 1.4) + .15 * Math.sin(t * 4.7);
          const bands = Math.pow(Math.max(0, Math.sin((f * 19 + .18 * Math.sin(t * .7)) * Math.PI)), 10);
          const transient = Math.pow(Math.max(0, Math.sin(t * 3.5)), 26) * .26;
          const intensity = Math.min(.999, Math.max(0, (.72 - f * .49 + bands * .3 + noise * .14 + transient) * envelope * 1.5));
          const p = intensity * (palette.length - 1);
          const lower = Math.floor(p);
          const mix = p - lower;
          const index = (y * width + x) * 4;
          for (let channel = 0; channel < 3; channel++) pixels.data[index + channel] = palette[lower][channel] * (1 - mix) + palette[Math.min(lower + 1, palette.length - 1)][channel] * mix;
          pixels.data[index + 3] = 255;
        }
      }
      context.putImageData(pixels, 0, 0);
    });
  }

  document.querySelectorAll('[data-preset]').forEach((button) => button.addEventListener('click', () => setMedia(button.dataset.preset === 'info' ? 'video' : button.dataset.preset, button.dataset.preset === 'info' ? 'info' : 'preview')));
  document.querySelectorAll('[data-file]').forEach((button) => button.addEventListener('click', () => setMedia(button.dataset.file)));
  $('#scenario').addEventListener('change', (event) => { state.scenario = event.target.value; render(); });
  $('#theme-toggle').addEventListener('click', () => { state.theme = state.theme === 'dark' ? 'light' : 'dark'; render(); });
  $('#split-toggle').addEventListener('click', () => { state.split = !state.split; render(); });
  function showReferences(show) {
    $('#workbench').hidden = show;
    $('#references').hidden = !show;
    $('#show-workbench').classList.toggle('active', !show);
    $('#show-references').classList.toggle('active', show);
    $('#show-workbench').setAttribute('aria-pressed', String(!show));
    $('#show-references').setAttribute('aria-pressed', String(show));
  }
  $('#show-workbench').addEventListener('click', () => showReferences(false));
  $('#show-references').addEventListener('click', () => showReferences(true));
  $('#editor').addEventListener('keydown', (event) => {
    if (event.target.closest('button,input,select,textarea,a') || state.media === 'image') return;
    if (event.code === 'Space') { event.preventDefault(); togglePlay(); }
    if (event.code === 'ArrowRight') { event.preventDefault(); seek(state.time + 5); }
    if (event.code === 'ArrowLeft') { event.preventDefault(); seek(state.time - 5); }
    if (event.code === 'Escape' && $('#media-options')) $('#media-options').hidden = true;
  });
  new ResizeObserver(() => { paintGraphs(); updateClock(); }).observe($('#editor'));
  setInterval(() => {
    if (!state.playing || $('#workbench').hidden || state.media === 'image') return;
    seek(state.time + .25 * state.speed);
    if (state.time >= files[state.media].duration) {
      if (state.media === 'animation' && state.repeat) seek(0);
      else togglePlay();
    }
  }, 250);
  render();
  if (params.get('board') === 'references') showReferences(true);
})();
