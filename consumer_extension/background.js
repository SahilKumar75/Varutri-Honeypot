const DEFAULT_CONFIG = {
  baseUrl: 'http://localhost:8080',
  appId: 'varutri-extension',
  deviceId: 'chrome-local-001',
  appVersion: '0.1.0',
  platform: 'EXTENSION',
};

const STORAGE_KEYS = {
  config: 'varutriConfig',
  auth: 'varutriAuth',
  lastAnalysis: 'varutriLastAnalysis',
};

chrome.runtime.onInstalled.addListener(async () => {
  const state = await getStoredState();
  if (!state.config) {
    await chrome.storage.local.set({
      [STORAGE_KEYS.config]: DEFAULT_CONFIG,
    });
  }
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  handleMessage(message)
    .then((result) => sendResponse({ ok: true, result }))
    .catch((error) => sendResponse({ ok: false, error: error.message }));
  return true;
});

async function handleMessage(message) {
  switch (message.type) {
    case 'GET_CONFIG':
      return getConfig();
    case 'SAVE_CONFIG':
      await saveConfig(message.payload || {});
      return getConfig();
    case 'ISSUE_TOKEN':
      return issueToken();
    case 'GET_AUTH':
      return getAuth();
    case 'ANALYZE_ACTIVE_TAB':
      return analyzeActiveTab();
    case 'GET_CAPABILITIES':
      return getCapabilities();
    case 'GET_HISTORY':
      return getHistory();
    case 'GET_LAST_ANALYSIS':
      return getLastAnalysis();
    default:
      throw new Error(`Unsupported message type: ${message.type}`);
  }
}

async function getStoredState() {
  const state = await chrome.storage.local.get([
    STORAGE_KEYS.config,
    STORAGE_KEYS.auth,
    STORAGE_KEYS.lastAnalysis,
  ]);
  return {
    config: state[STORAGE_KEYS.config] || null,
    auth: state[STORAGE_KEYS.auth] || null,
    lastAnalysis: state[STORAGE_KEYS.lastAnalysis] || null,
  };
}

async function getConfig() {
  const state = await getStoredState();
  return state.config || DEFAULT_CONFIG;
}

async function saveConfig(patch) {
  const current = await getConfig();
  const next = {
    ...current,
    ...patch,
  };
  await chrome.storage.local.set({
    [STORAGE_KEYS.config]: next,
  });
}

async function getAuth() {
  const state = await getStoredState();
  return state.auth;
}

async function issueToken() {
  const config = await getConfig();
  const response = await fetch(`${config.baseUrl}/api/consumer/auth/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      appId: config.appId,
      deviceId: config.deviceId,
      platform: config.platform,
      appVersion: config.appVersion,
    }),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Token request failed (${response.status}): ${body}`);
  }

  const payload = await response.json();
  const data = payload.data || {};
  if (!data.accessToken) {
    throw new Error('Token missing in response');
  }

  const auth = {
    tokenType: data.tokenType || 'Bearer',
    accessToken: data.accessToken,
    expiresAtEpochMs: data.expiresAtEpochMs || 0,
    expiresInSeconds: data.expiresInSeconds || 0,
    appId: data.appId || config.appId,
    deviceId: data.deviceId || config.deviceId,
    platform: data.platform || config.platform,
  };

  await chrome.storage.local.set({
    [STORAGE_KEYS.auth]: auth,
  });

  return auth;
}

async function analyzeActiveTab() {
  const config = await getConfig();
  const auth = await ensureAuth();

  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab || !tab.id) {
    throw new Error('No active tab found');
  }

  const context = await getTabContext(tab.id);
  const text = (context.selectedText || '').trim();
  const url = context.pageUrl || tab.url || '';

  if (!text && !url) {
    throw new Error('Nothing to analyze. Select text or open a page with a URL.');
  }

  const response = await fetch(`${config.baseUrl}/api/consumer/analyze`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `${auth.tokenType || 'Bearer'} ${auth.accessToken}`,
    },
    body: JSON.stringify({
      channel: 'BROWSER',
      payload: {
        text: text || `Analyze this page URL for phishing risk: ${url}`,
        url,
        additionalContext: context.pageTitle || '',
      },
      metadata: {
        platform: config.platform,
        sourceApp: 'varutri-extension',
        locale: navigator.language || 'en-US',
      },
    }),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Analyze failed (${response.status}): ${body}`);
  }

  const payload = await response.json();
  const data = payload.data || {};

  await chrome.storage.local.set({
    [STORAGE_KEYS.lastAnalysis]: data,
  });

  const verdict = `${data.verdict || 'UNKNOWN'} / ${data.threatLevel || 'UNKNOWN'}`;
  chrome.tabs.sendMessage(tab.id, {
    type: 'SHOW_RISK_BANNER',
    payload: {
      verdict,
      sessionId: data.sessionId || '',
    },
  });

  return data;
}

async function getCapabilities() {
  const config = await getConfig();
  const auth = await ensureAuth();

  const response = await fetch(
    `${config.baseUrl}/api/consumer/capabilities?platform=${encodeURIComponent(config.platform)}`,
    {
      method: 'GET',
      headers: {
        Authorization: `${auth.tokenType || 'Bearer'} ${auth.accessToken}`,
      },
    }
  );

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Capabilities failed (${response.status}): ${body}`);
  }

  const payload = await response.json();
  return payload.data || {};
}

async function getHistory() {
  const config = await getConfig();
  const auth = await ensureAuth();

  const response = await fetch(`${config.baseUrl}/api/consumer/history?limit=10`, {
    method: 'GET',
    headers: {
      Authorization: `${auth.tokenType || 'Bearer'} ${auth.accessToken}`,
    },
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`History failed (${response.status}): ${body}`);
  }

  const payload = await response.json();
  return payload.data || [];
}

async function getLastAnalysis() {
  const state = await getStoredState();
  return state.lastAnalysis;
}

async function ensureAuth() {
  const auth = await getAuth();
  if (!auth || !auth.accessToken) {
    throw new Error('No token found. Use Connect first.');
  }
  if (auth.expiresAtEpochMs && Date.now() >= auth.expiresAtEpochMs) {
    throw new Error('Token expired. Connect again.');
  }
  return auth;
}

function getTabContext(tabId) {
  return new Promise((resolve) => {
    chrome.tabs.sendMessage(tabId, { type: 'GET_PAGE_CONTEXT' }, (response) => {
      if (chrome.runtime.lastError || !response) {
        resolve({ selectedText: '', pageUrl: '', pageTitle: '' });
        return;
      }
      resolve(response);
    });
  });
}
