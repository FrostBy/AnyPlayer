async () => {
  const checks = [];
  const $ = (selector) => document.querySelector(selector);
  const settle = () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  const assert = (condition, message) => { if (!condition) throw new Error(message); checks.push(message); };
  const click = async (selector) => {
    const element = $(selector);
    if (!element || element.disabled) throw new Error(`Cannot click ${selector}`);
    element.click();
    await settle();
  };
  const scenario = async (value) => { $('#scenario').value = value; $('#scenario').dispatchEvent(new Event('change', { bubbles: true })); await settle(); };
  const fitControls = (selector) => {
    const root = $(selector);
    const box = root.getBoundingClientRect();
    return [...root.querySelectorAll('button,input,select')].filter((element) => element.getClientRects().length).every((element) => {
      const bounds = element.getBoundingClientRect();
      return bounds.left >= box.left - 1 && bounds.right <= box.right + 1 && bounds.top >= box.top - 1 && bounds.bottom <= box.bottom + 1;
    });
  };

  await click('#show-workbench');
  if (document.body.dataset.theme === 'light') await click('#theme-toggle');
  if ($('#ide-frame').classList.contains('split')) await click('#split-toggle');
  await click('[data-preset="video"]');
  assert($('#play-toggle').getAttribute('aria-label') === 'Play', 'Video opens paused');
  await click('#play-toggle');
  assert($('#play-toggle').getAttribute('aria-label') === 'Pause', 'Play enters playing presentation');
  await click('#play-toggle');
  assert($('#play-toggle').getAttribute('aria-label') === 'Play', 'Pause restores paused presentation');
  const initial = Number($('#seek').value);
  await click('#seek-forward');
  assert(Number($('#seek').value) === initial + 10, 'Forward seek updates the source position');
  await click('#drawer-toggle');
  assert($('.analysis-drawer').classList.contains('collapsed'), 'Analysis drawer collapses');
  await click('#drawer-toggle');
  await click('[data-view="spectrogram"]');
  assert(Boolean($('.audio-content canvas')) && !$('.media-viewport'), 'Full spectrogram takes the main content area');
  await click('[data-range="file"]');
  assert($('[data-range="file"]').getAttribute('aria-pressed') === 'true' && $('.time-axis').textContent.includes('04:18'), 'File overview exposes the full duration');
  await click('[data-range="live"]');
  assert($('[data-range="live"]').getAttribute('aria-pressed') === 'true', 'Live mode can be selected');
  await click('[data-view="info"]');
  assert($('.property-table').textContent.includes('MPEG-4') && Boolean($('#copy-report')), 'MediaInfo presents properties and report action');
  assert(Boolean($('#play-toggle')) && Boolean($('#seek')), 'Transport remains available while inspecting MediaInfo');
  await scenario('native');
  assert(Boolean($('.property-table')), 'MediaInfo remains accessible during playback-engine failure');
  await click('[data-view="preview"]');
  assert($('.state-card').textContent.includes('Media engine could not start') && $('#play-toggle').disabled, 'Native failure disables playback and identifies the failure');
  assert([...document.querySelectorAll('.transport button,.transport input')].every((element) => element.disabled), 'Native failure disables all unavailable transport actions');
  await click('#retry-media');
  assert(!$('#play-toggle').disabled && Boolean($('.media-viewport img')), 'Retry returns to the ready design state');
  await scenario('analysis-error');
  assert(Boolean($('.analysis-failure')) && !$('#play-toggle').disabled, 'Analysis failure leaves transport enabled');
  await click('#retry-analysis');
  await scenario('no-audio');
  assert($('[data-view="spectrogram"]').disabled && $('.empty-analysis').textContent.includes('no audio'), 'Video without audio disables spectrum access');
  await scenario('loading');
  assert(Boolean($('[role="progressbar"]')) && $('#play-toggle').disabled, 'Loading shows progress and disabled transport');
  await click('#cancel-opening');
  await scenario('unsupported');
  assert($('.state-card').textContent.includes('Cannot play this file'), 'Unsupported media has an explicit error state');

  await click('[data-preset="image"]');
  assert(!$('#seek') && $('[data-view="spectrogram"]').disabled, 'Still images have no playback timeline or spectrum');
  await click('#zoom-original');
  assert($('.zoom-value').textContent === '100%' && $('#image-artwork').style.width === '1600px', 'Original-size image action uses source dimensions');
  await click('#zoom-in');
  assert($('.zoom-value').textContent === '125%', 'Image zoom changes the displayed scale');
  await click('#zoom-fit');
  assert(parseInt($('.zoom-value').textContent) <= 100, 'Fit returns the image to the available viewport');

  await click('[data-preset="animation"]');
  assert(Boolean($('#seek')) && !$('#mute-toggle') && !$('#track-options') && $('[data-view="spectrogram"]').disabled, 'Animation exposes silent playback and hides audio analysis');
  const frame = $('.transport-status').textContent;
  const sourceTime = Number($('#seek').value);
  await click('#next-frame');
  assert($('.transport-status').textContent !== frame && Number($('#seek').value) > sourceTime && $('#play-toggle').getAttribute('aria-label') === 'Play', 'Animation frame stepping updates time while paused');
  await click('#previous-frame');
  assert($('.transport-status').textContent === frame, 'Previous frame returns to the preceding composed-frame presentation');
  $('#seek').value = '0.35';
  $('#seek').dispatchEvent(new Event('input', { bubbles: true }));
  await settle();
  assert($('.transport-status').textContent === 'Frame 4 / 12' && $('.current-time').textContent === '00:00.350', 'Animation seek respects unequal frame durations');
  await click('#repeat-toggle');
  assert($('#repeat-toggle').getAttribute('aria-pressed') === 'false', 'Animation repeat can be disabled');

  await click('[data-preset="audio"]');
  assert(Boolean($('.audio-content canvas')) && !$('.media-viewport'), 'Audio gives the main editor area to analysis');
  $('#editor').focus();
  $('#editor').dispatchEvent(new KeyboardEvent('keydown', { code: 'Space', bubbles: true, cancelable: true }));
  assert($('#play-toggle').getAttribute('aria-label') === 'Pause', 'Focused media editor handles Space');
  $('#play-toggle').click();
  $('#scenario').dispatchEvent(new KeyboardEvent('keydown', { code: 'Space', bubbles: true, cancelable: true }));
  assert($('#play-toggle').getAttribute('aria-label') === 'Play', 'A control outside the editor does not toggle playback');
  await click('#theme-toggle');
  assert(document.body.dataset.theme === 'light', 'Light theme applies');
  await click('#split-toggle');
  assert($('#ide-frame').classList.contains('split'), 'Split editor presentation applies');
  assert(fitControls('.view-toolbar') && fitControls('.transport'), 'Primary controls fit the split editor');
  $('#editor').style.flex = '0 0 480px';
  for (const preset of ['video', 'audio', 'image', 'animation', 'info']) {
    await click(`[data-preset="${preset}"]`);
    const bottomControls = $('.transport') ? '.transport' : $('.image-footer') ? '.image-footer' : null;
    assert(fitControls('.view-toolbar') && (!bottomControls || fitControls(bottomControls)), `${preset} controls fit a 480-pixel editor in light theme`);
  }
  $('#editor').style.flex = '';
  assert(document.documentElement.scrollWidth <= innerWidth, 'The study has no horizontal page overflow');

  await click('#show-references');
  assert(!$('#references').hidden && $('#workbench').hidden && document.querySelectorAll('.reference-grid figure').length === 4, 'Annotated moodboard is reachable with four references');
  await click('#show-workbench');
  await click('#split-toggle');
  await click('#theme-toggle');
  await click('[data-preset="video"]');
  return { passed: checks.length, checks };
}
