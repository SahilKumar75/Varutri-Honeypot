const BANNER_ID = 'varutri-risk-banner';

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'GET_PAGE_CONTEXT') {
    sendResponse({
      selectedText: window.getSelection().toString(),
      pageUrl: window.location.href,
      pageTitle: document.title,
    });
    return;
  }

  if (message.type === 'SHOW_RISK_BANNER') {
    showRiskBanner(message.payload || {});
    sendResponse({ ok: true });
  }
});

function showRiskBanner(payload) {
  let banner = document.getElementById(BANNER_ID);
  if (!banner) {
    banner = document.createElement('div');
    banner.id = BANNER_ID;
    banner.style.position = 'fixed';
    banner.style.top = '12px';
    banner.style.right = '12px';
    banner.style.zIndex = '2147483647';
    banner.style.maxWidth = '360px';
    banner.style.padding = '10px 12px';
    banner.style.borderRadius = '10px';
    banner.style.boxShadow = '0 4px 20px rgba(0,0,0,0.2)';
    banner.style.fontFamily = 'ui-sans-serif, system-ui, -apple-system, Segoe UI, sans-serif';
    banner.style.fontSize = '13px';
    banner.style.lineHeight = '1.4';
    document.body.appendChild(banner);
  }

  const verdict = String(payload.verdict || 'UNKNOWN').toUpperCase();
  const theme = verdict.includes('DANGER') || verdict.includes('HIGH') || verdict.includes('CRITICAL')
    ? { bg: '#fee2e2', fg: '#7f1d1d' }
    : verdict.includes('SUSPICIOUS') || verdict.includes('MEDIUM')
      ? { bg: '#fef3c7', fg: '#78350f' }
      : { bg: '#dcfce7', fg: '#14532d' };

  banner.style.background = theme.bg;
  banner.style.color = theme.fg;
  banner.innerHTML = [
    '<strong>Varutri Scam Shield</strong>',
    `<div>Verdict: ${escapeHtml(verdict)}</div>`,
    payload.sessionId ? `<div>Session: ${escapeHtml(payload.sessionId)}</div>` : '',
  ].join('');

  clearTimeout(banner._hideTimer);
  banner._hideTimer = setTimeout(() => {
    if (banner && banner.parentNode) {
      banner.parentNode.removeChild(banner);
    }
  }, 9000);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');
}
