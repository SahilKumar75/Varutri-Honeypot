const fields = {
  baseUrl: document.getElementById('baseUrl'),
  appId: document.getElementById('appId'),
  deviceId: document.getElementById('deviceId'),
  appVersion: document.getElementById('appVersion'),
};

const outputEl = document.getElementById('output');
const presenceCard = document.getElementById('presenceCard');
const presenceValue = document.getElementById('presenceValue');

const buttons = {
  saveConfig: document.getElementById('saveConfig'),
  connect: document.getElementById('connectBtn'),
  analyze: document.getElementById('analyzeBtn'),
  capabilities: document.getElementById('capabilitiesBtn'),
  history: document.getElementById('historyBtn'),
};

initialize().catch((error) => showError(error));

buttons.saveConfig.addEventListener('click', () => runAction(saveConfig));
buttons.connect.addEventListener('click', () => runAction(connect));
buttons.analyze.addEventListener('click', () => runAction(analyze));
buttons.capabilities.addEventListener('click', () => runAction(loadCapabilities));
buttons.history.addEventListener('click', () => runAction(loadHistory));

async function initialize() {
  const config = await sendMessage('GET_CONFIG');
  writeConfigToForm(config);

  const auth = await sendMessage('GET_AUTH');
  if (auth && auth.accessToken) {
    printOutput({ token: summarizeToken(auth.accessToken), expiresAt: auth.expiresAtEpochMs });
  }

  const last = await sendMessage('GET_LAST_ANALYSIS');
  if (last) {
    updatePresence(last);
  }
}

async function saveConfig() {
  const payload = readConfigFromForm();
  const config = await sendMessage('SAVE_CONFIG', payload);
  printOutput({ message: 'Config saved', config });
}

async function connect() {
  await saveConfig();
  const auth = await sendMessage('ISSUE_TOKEN');
  printOutput({
    message: 'Token issued',
    token: summarizeToken(auth.accessToken || ''),
    expiresAt: auth.expiresAtEpochMs,
  });
}

async function analyze() {
  const result = await sendMessage('ANALYZE_ACTIVE_TAB');
  updatePresence(result);
  printOutput({
    sessionId: result.sessionId,
    verdict: result.verdict,
    threatLevel: result.threatLevel,
    threatScore: result.threatAssessment?.threatScore,
    recommendedActions: result.recommendedActions || [],
  });
}

async function loadCapabilities() {
  const result = await sendMessage('GET_CAPABILITIES');
  printOutput(result);
}

async function loadHistory() {
  const result = await sendMessage('GET_HISTORY');
  printOutput(result);
}

function updatePresence(analysis) {
  const verdict = String(analysis?.verdict || 'UNKNOWN').toUpperCase();
  const threat = String(analysis?.threatLevel || 'UNKNOWN').toUpperCase();
  const label = `${verdict} / ${threat}`;
  presenceValue.textContent = label;

  if (threat === 'HIGH' || threat === 'CRITICAL' || verdict === 'DANGER') {
    presenceCard.style.background = 'var(--danger)';
    presenceCard.style.color = 'var(--danger-text)';
    return;
  }

  if (threat === 'MEDIUM' || verdict === 'SUSPICIOUS') {
    presenceCard.style.background = 'var(--watch)';
    presenceCard.style.color = 'var(--watch-text)';
    return;
  }

  if (threat === 'SAFE' || verdict === 'SAFE') {
    presenceCard.style.background = 'var(--safe)';
    presenceCard.style.color = 'var(--safe-text)';
    return;
  }

  presenceCard.style.background = '#ffffff';
  presenceCard.style.color = '#14322e';
}

function writeConfigToForm(config) {
  fields.baseUrl.value = config.baseUrl || '';
  fields.appId.value = config.appId || '';
  fields.deviceId.value = config.deviceId || '';
  fields.appVersion.value = config.appVersion || '';
}

function readConfigFromForm() {
  return {
    baseUrl: fields.baseUrl.value.trim(),
    appId: fields.appId.value.trim(),
    deviceId: fields.deviceId.value.trim(),
    appVersion: fields.appVersion.value.trim(),
    platform: 'EXTENSION',
  };
}

async function runAction(action) {
  toggleButtons(true);
  try {
    await action();
  } catch (error) {
    showError(error);
  } finally {
    toggleButtons(false);
  }
}

function toggleButtons(disabled) {
  Object.values(buttons).forEach((btn) => {
    btn.disabled = disabled;
  });
}

function printOutput(payload) {
  outputEl.textContent = JSON.stringify(payload, null, 2);
}

function showError(error) {
  outputEl.textContent = `Error: ${error?.message || String(error)}`;
}

function summarizeToken(value) {
  if (!value || value.length < 16) {
    return value;
  }
  return `${value.slice(0, 10)}...${value.slice(-4)}`;
}

function sendMessage(type, payload = null) {
  return new Promise((resolve, reject) => {
    chrome.runtime.sendMessage({ type, payload }, (response) => {
      if (chrome.runtime.lastError) {
        reject(new Error(chrome.runtime.lastError.message));
        return;
      }
      if (!response || response.ok !== true) {
        reject(new Error(response?.error || 'Unknown extension error'));
        return;
      }
      resolve(response.result);
    });
  });
}
