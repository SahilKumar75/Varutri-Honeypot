const STORAGE_KEY = 'varutri.consumer.web.config.v1';

const DEFAULT_CONFIG = {
    baseUrl: 'http://localhost:8080',
    appId: 'varutri-web',
    deviceId: `web-${Math.random().toString(36).slice(2, 10)}`,
    appVersion: '0.2.0',
    platform: 'WEB',
};

const CACHE_TTL_MS = {
    history: 30_000,
    detail: 120_000,
    capabilities: 300_000,
};

const state = {
    config: { ...DEFAULT_CONFIG },
    auth: null,
    channel: 'SMS',
    status: 'Ready',
    statusLevel: 'info',
    latestAnalysis: null,
    history: [],
    selectedDetail: null,
    capabilities: null,
    loading: {
        token: false,
        analyze: false,
        history: false,
        detail: false,
        capabilities: false,
    },
    cache: {
        history: null,
        capabilitiesByPlatform: new Map(),
        detailsBySession: new Map(),
    },
};

const dom = {};

window.addEventListener('DOMContentLoaded', bootstrap);

function bootstrap() {
    cacheDom();
    hydrateConfig();
    bindEvents();
    tickClock();
    setInterval(tickClock, 1000);
    renderAll();
}

function cacheDom() {
    dom.connectionForm = document.getElementById('connectionForm');
    dom.analyzeForm = document.getElementById('analyzeForm');
    dom.baseUrlInput = document.getElementById('baseUrlInput');
    dom.appIdInput = document.getElementById('appIdInput');
    dom.deviceIdInput = document.getElementById('deviceIdInput');
    dom.appVersionInput = document.getElementById('appVersionInput');
    dom.platformSelect = document.getElementById('platformSelect');
    dom.channelSelect = document.getElementById('channelSelect');
    dom.contentInput = document.getElementById('contentInput');
    dom.senderInput = document.getElementById('senderInput');
    dom.urlInput = document.getElementById('urlInput');

    dom.issueTokenBtn = document.getElementById('issueTokenBtn');
    dom.loadCapabilitiesBtn = document.getElementById('loadCapabilitiesBtn');
    dom.analyzeBtn = document.getElementById('analyzeBtn');
    dom.refreshHistoryBtn = document.getElementById('refreshHistoryBtn');

    dom.presenceChip = document.getElementById('presenceChip');
    dom.tokenChip = document.getElementById('tokenChip');
    dom.clockChip = document.getElementById('clockChip');
    dom.tokenExpiryText = document.getElementById('tokenExpiryText');
    dom.presencePanel = document.getElementById('presencePanel');
    dom.presenceTitle = document.getElementById('presenceTitle');
    dom.presenceNote = document.getElementById('presenceNote');
    dom.latestAnalysisSummary = document.getElementById('latestAnalysisSummary');

    dom.analysisContent = document.getElementById('analysisContent');
    dom.historyList = document.getElementById('historyList');
    dom.capabilitiesSummary = document.getElementById('capabilitiesSummary');
    dom.capabilitiesList = document.getElementById('capabilitiesList');
    dom.detailContainer = document.getElementById('detailContainer');

    dom.statusText = document.getElementById('statusText');
    dom.cacheStatus = document.getElementById('cacheStatus');
}

function bindEvents() {
    dom.connectionForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        syncConfigFromForm();
        await issueToken();
    });

    dom.loadCapabilitiesBtn.addEventListener('click', async () => {
        syncConfigFromForm();
        await loadCapabilities({ forceRefresh: true });
    });

    dom.refreshHistoryBtn.addEventListener('click', async () => {
        await loadHistory({ forceRefresh: true });
    });

    dom.analyzeForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        syncConfigFromForm();
        state.channel = dom.channelSelect.value;
        await runAnalysis();
    });

    dom.historyList.addEventListener('click', async (event) => {
        const item = event.target.closest('[data-session-id]');
        if (!item) {
            return;
        }
        await loadHistoryDetail(item.dataset.sessionId, { forceRefresh: false });
    });

    dom.platformSelect.addEventListener('change', () => {
        state.capabilities = null;
        renderCapabilities();
    });
}

function hydrateConfig() {
    try {
        const stored = localStorage.getItem(STORAGE_KEY);
        if (stored) {
            const parsed = JSON.parse(stored);
            state.config = { ...DEFAULT_CONFIG, ...parsed };
        }
    } catch (error) {
        setStatus(`Config restore failed: ${error.message}`, 'warning');
    }

    dom.baseUrlInput.value = state.config.baseUrl;
    dom.appIdInput.value = state.config.appId;
    dom.deviceIdInput.value = state.config.deviceId;
    dom.appVersionInput.value = state.config.appVersion;
    dom.platformSelect.value = state.config.platform;
    dom.channelSelect.value = state.channel;
}

function syncConfigFromForm() {
    state.config = {
        baseUrl: dom.baseUrlInput.value.trim(),
        appId: dom.appIdInput.value.trim(),
        deviceId: dom.deviceIdInput.value.trim(),
        appVersion: dom.appVersionInput.value.trim(),
        platform: dom.platformSelect.value,
    };

    localStorage.setItem(STORAGE_KEY, JSON.stringify(state.config));
}

async function issueToken() {
    setLoading('token', true);
    setStatus('Requesting token...');

    try {
        const response = await requestJson('/api/consumer/auth/token', {
            method: 'POST',
            body: {
                appId: state.config.appId,
                deviceId: state.config.deviceId,
                platform: state.config.platform,
                appVersion: state.config.appVersion,
            },
            auth: false,
        });

        state.auth = response.data || null;
        setStatus('Token issued successfully');
        updateCacheStatus('token refresh');

        await Promise.all([
            loadCapabilities({ forceRefresh: true, silent: true }),
            loadHistory({ forceRefresh: true, silent: true }),
        ]);
    } catch (error) {
        state.auth = null;
        setStatus(`Token request failed: ${error.message}`, 'danger');
    } finally {
        setLoading('token', false);
        renderAll();
    }
}

async function runAnalysis() {
    const content = dom.contentInput.value.trim();
    if (!content) {
        setStatus('Provide suspicious content before analysis.', 'warning');
        return;
    }

    setLoading('analyze', true);
    setStatus('Analyzing suspicious signal...');

    try {
        const response = await requestJson('/api/consumer/analyze', {
            method: 'POST',
            body: {
                channel: state.channel,
                payload: {
                    text: content,
                    senderId: dom.senderInput.value.trim(),
                    url: dom.urlInput.value.trim(),
                },
                metadata: {
                    platform: state.config.platform,
                    sourceApp: 'consumer_web_console',
                    locale: navigator.language || 'en_US',
                },
            },
        });

        state.latestAnalysis = response.data || null;
        state.cache.history = null;
        state.cache.detailsBySession.clear();

        setStatus('Analysis complete');
        updateCacheStatus('history cache invalidated');
        renderPresence();
        renderLatestAnalysis();

        await loadHistory({ forceRefresh: true, silent: true });
        if (state.latestAnalysis?.sessionId) {
            await loadHistoryDetail(state.latestAnalysis.sessionId, { forceRefresh: true, silent: true });
        }
    } catch (error) {
        setStatus(`Analysis failed: ${error.message}`, 'danger');
    } finally {
        setLoading('analyze', false);
        renderButtons();
    }
}

async function loadHistory({ forceRefresh = false, silent = false } = {}) {
    if (!ensureToken(silent)) {
        return;
    }

    const now = Date.now();
    if (!forceRefresh && state.cache.history && state.cache.history.expiresAt > now) {
        state.history = state.cache.history.value;
        if (!silent) {
            setStatus('History loaded from cache');
        }
        updateCacheStatus('history cache hit');
        renderHistory();
        return;
    }

    setLoading('history', true);
    if (!silent) {
        setStatus('Loading timeline...');
    }

    try {
        const response = await requestJson('/api/consumer/history?limit=20');
        state.history = Array.isArray(response.data) ? response.data : [];
        state.cache.history = {
            value: state.history,
            expiresAt: now + CACHE_TTL_MS.history,
        };
        updateCacheStatus('history refreshed');
        if (!silent) {
            setStatus('History loaded');
        }
        renderHistory();
    } catch (error) {
        if (!silent) {
            setStatus(`History load failed: ${error.message}`, 'danger');
        }
    } finally {
        setLoading('history', false);
        renderButtons();
    }
}

async function loadHistoryDetail(sessionId, { forceRefresh = false, silent = false } = {}) {
    if (!ensureToken(silent)) {
        return;
    }

    const key = String(sessionId || '').trim();
    if (!key) {
        return;
    }

    const now = Date.now();
    const cached = state.cache.detailsBySession.get(key);
    if (!forceRefresh && cached && cached.expiresAt > now) {
        state.selectedDetail = cached.value;
        updateCacheStatus('detail cache hit');
        renderDetail();
        if (!silent) {
            setStatus(`Loaded detail for ${key}`);
        }
        return;
    }

    setLoading('detail', true);
    if (!silent) {
        setStatus('Loading session detail...');
    }

    try {
        const response = await requestJson(`/api/consumer/history/${encodeURIComponent(key)}`);
        state.selectedDetail = response.data || null;
        state.cache.detailsBySession.set(key, {
            value: state.selectedDetail,
            expiresAt: now + CACHE_TTL_MS.detail,
        });
        updateCacheStatus('detail refreshed');
        if (!silent) {
            setStatus(`Session detail ready for ${key}`);
        }
        renderDetail();
    } catch (error) {
        if (!silent) {
            setStatus(`Detail load failed: ${error.message}`, 'danger');
        }
    } finally {
        setLoading('detail', false);
        renderButtons();
    }
}

async function loadCapabilities({ forceRefresh = false, silent = false } = {}) {
    if (!ensureToken(silent)) {
        return;
    }

    const platformKey = state.config.platform.toUpperCase();
    const now = Date.now();
    const cached = state.cache.capabilitiesByPlatform.get(platformKey);
    if (!forceRefresh && cached && cached.expiresAt > now) {
        state.capabilities = cached.value;
        updateCacheStatus('capability cache hit');
        renderCapabilities();
        if (!silent) {
            setStatus(`Capabilities loaded from cache for ${platformKey}`);
        }
        return;
    }

    setLoading('capabilities', true);
    if (!silent) {
        setStatus('Loading capability matrix...');
    }

    try {
        const response = await requestJson(
            `/api/consumer/capabilities?platform=${encodeURIComponent(platformKey)}`
        );
        state.capabilities = response.data || null;
        state.cache.capabilitiesByPlatform.set(platformKey, {
            value: state.capabilities,
            expiresAt: now + CACHE_TTL_MS.capabilities,
        });
        updateCacheStatus('capabilities refreshed');
        if (!silent) {
            setStatus(`Capabilities loaded for ${platformKey}`);
        }
        renderCapabilities();
    } catch (error) {
        if (!silent) {
            setStatus(`Capabilities load failed: ${error.message}`, 'danger');
        }
    } finally {
        setLoading('capabilities', false);
        renderButtons();
    }
}

function ensureToken(silent) {
    if (!state.auth?.accessToken) {
        if (!silent) {
            setStatus('Issue a token first.', 'warning');
        }
        return false;
    }

    if (state.auth.expiresAtEpochMs && Date.now() >= state.auth.expiresAtEpochMs) {
        if (!silent) {
            setStatus('Token expired. Re-issue token.', 'warning');
        }
        return false;
    }

    return true;
}

async function requestJson(path, { method = 'GET', body = null, auth = true } = {}) {
    if (auth && !ensureToken(false)) {
        throw new Error('Missing or expired token');
    }

    const url = `${state.config.baseUrl}${path}`;
    const headers = {
        'Content-Type': 'application/json',
    };

    if (auth) {
        headers.Authorization = `Bearer ${state.auth.accessToken}`;
    }

    const response = await fetch(url, {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined,
    });

    const text = await response.text();
    let payload = {};

    if (text) {
        try {
            payload = JSON.parse(text);
        } catch (error) {
            throw new Error(`Non-JSON response from server (${response.status})`);
        }
    }

    if (!response.ok) {
        const message = payload.message || payload.error || `HTTP ${response.status}`;
        throw new Error(message);
    }

    return payload;
}

function setLoading(key, value) {
    state.loading[key] = value;
    renderButtons();
}

function setStatus(message, level = 'info') {
    state.status = message;
    state.statusLevel = level;
    dom.statusText.textContent = message;
    dom.statusText.style.color = statusColor(level);
}

function statusColor(level) {
    if (level === 'danger') {
        return '#b42318';
    }
    if (level === 'warning') {
        return '#b45309';
    }
    return '#0f766e';
}

function updateCacheStatus(message) {
    dom.cacheStatus.textContent = `Cache: ${message}`;
}

function tickClock() {
    const now = new Date();
    dom.clockChip.textContent = now.toLocaleTimeString('en-GB', { hour12: false });
}

function renderAll() {
    renderButtons();
    renderStatusChips();
    renderPresence();
    renderLatestAnalysis();
    renderHistory();
    renderCapabilities();
    renderDetail();
}

function renderButtons() {
    dom.issueTokenBtn.disabled = state.loading.token;
    dom.loadCapabilitiesBtn.disabled = state.loading.capabilities || state.loading.token;
    dom.analyzeBtn.disabled = state.loading.analyze;
    dom.refreshHistoryBtn.disabled = state.loading.history;
}

function renderStatusChips() {
    const tokenPreview = state.auth?.accessToken
        ? `${state.auth.accessToken.slice(0, 8)}...${state.auth.accessToken.slice(-4)}`
        : 'Not issued';

    dom.tokenChip.textContent = `Token: ${tokenPreview}`;
    dom.tokenExpiryText.textContent = state.auth?.expiresAtEpochMs
        ? new Date(state.auth.expiresAtEpochMs).toISOString()
        : 'No token';
}

function renderPresence() {
    const presence = derivePresence(state.latestAnalysis);
    dom.presenceChip.textContent = `Presence: ${presence.label}`;
    dom.presencePanel.classList.remove('presence-idle', 'presence-safe', 'presence-watch', 'presence-danger');
    dom.presencePanel.classList.add(presence.className);
    dom.presenceTitle.textContent = presence.title;
    dom.presenceNote.textContent = presence.note;

    if (state.latestAnalysis) {
        dom.latestAnalysisSummary.textContent = `${state.latestAnalysis.channel} ${state.latestAnalysis.verdict} | ${state.latestAnalysis.threatLevel} ${Number(state.latestAnalysis.threatScore || 0).toFixed(2)}`;
    } else {
        dom.latestAnalysisSummary.textContent = 'No analysis yet.';
    }
}

function derivePresence(analysis) {
    if (!analysis) {
        return {
            label: 'IDLE',
            className: 'presence-idle',
            title: 'Idle Monitoring',
            note: 'Awaiting first signal. Ingest suspicious content to activate risk posture.',
        };
    }

    const level = String(analysis.threatLevel || '').toUpperCase();
    if (level === 'CRITICAL' || level === 'HIGH') {
        return {
            label: 'DANGER',
            className: 'presence-danger',
            title: 'High-Risk Defense Active',
            note: 'Prioritize evidence preservation and immediate user escalation steps.',
        };
    }
    if (level === 'MEDIUM' || level === 'LOW') {
        return {
            label: 'WATCH',
            className: 'presence-watch',
            title: 'Watch Mode',
            note: 'Suspicious indicators present. Verify each requested action through official channels.',
        };
    }

    return {
        label: 'SAFE',
        className: 'presence-safe',
        title: 'Stable',
        note: 'No high-severity signal in latest analysis. Continue caution baseline.',
    };
}

function renderLatestAnalysis() {
    const analysis = state.latestAnalysis;
    if (!analysis) {
        dom.analysisContent.innerHTML = '<div class="empty-state">No analysis data yet.</div>';
        return;
    }

    const actions = listHtml(analysis.recommendedActions || []);
    const notes = listHtml(analysis.platformNotes || []);

    dom.analysisContent.innerHTML = `
        <div class="detail-card">
            <h4>Session ${escapeHtml(analysis.sessionId || '-')}</h4>
            <ul class="mini-list">
                <li>Verdict: <strong>${escapeHtml(analysis.verdict || 'UNKNOWN')}</strong></li>
                <li>Threat: <strong>${escapeHtml(analysis.threatLevel || 'UNKNOWN')}</strong> (${Number(analysis.threatScore || 0).toFixed(2)})</li>
                <li>Channel: <strong>${escapeHtml(analysis.channel || '-')}</strong></li>
            </ul>
        </div>
        <div class="detail-card">
            <h4>Recommended Actions</h4>
            ${actions}
        </div>
        <div class="detail-card">
            <h4>Platform Notes</h4>
            ${notes}
        </div>
    `;
}

function renderHistory() {
    if (!Array.isArray(state.history) || state.history.length === 0) {
        dom.historyList.innerHTML = '<div class="empty-state">No sessions loaded yet.</div>';
        return;
    }

    dom.historyList.innerHTML = state.history.map((item) => {
        const tone = toneFromThreat(item.threatLevel, item.verdict);
        return `
            <article class="history-item" data-session-id="${escapeHtml(item.sessionId || '')}">
                <div class="history-head">
                    <strong>${escapeHtml(item.channel || 'UNKNOWN')} | ${escapeHtml(item.verdict || 'UNKNOWN')}</strong>
                    <span class="badge ${tone}">${escapeHtml(item.threatLevel || 'UNKNOWN')}</span>
                </div>
                <ul class="history-meta">
                    <li class="mono">${escapeHtml(item.sessionId || '-')}</li>
                    <li>Score: ${Number(item.threatScore || 0).toFixed(2)}</li>
                    <li>Updated: ${escapeHtml(item.lastUpdated || '-')}</li>
                </ul>
            </article>
        `;
    }).join('');
}

function renderCapabilities() {
    const capabilities = state.capabilities;
    if (!capabilities) {
        dom.capabilitiesSummary.textContent = 'No capability data yet.';
        dom.capabilitiesList.innerHTML = '<div class="empty-state">Issue token and load capabilities for the active platform.</div>';
        return;
    }

    dom.capabilitiesSummary.textContent = `${capabilities.platform || state.config.platform} | Generated: ${capabilities.generatedAt || '-'}`;

    const channelCards = (capabilities.channels || []).map((channel) => `
        <article class="capability-item">
            <h4>${escapeHtml(channel.channel || 'UNKNOWN')} (${escapeHtml(channel.automationLevel || 'UNKNOWN')})</h4>
            <ul class="mini-list">
                <li>Passive Detection: ${channel.passiveDetection ? 'Yes' : 'No'}</li>
                <li>User Share Required: ${channel.userShareRequired ? 'Yes' : 'No'}</li>
            </ul>
            <p class="mono">Limitations</p>
            ${listHtml(channel.limitations || [])}
            <p class="mono">Recommended Flows</p>
            ${listHtml(channel.recommendedFlows || [])}
        </article>
    `).join('');

    const globalBlock = `
        <article class="capability-item">
            <h4>Global Limitations</h4>
            ${listHtml(capabilities.globalLimitations || [])}
        </article>
        <article class="capability-item">
            <h4>Operational Notes</h4>
            ${listHtml(capabilities.operationalNotes || [])}
        </article>
    `;

    dom.capabilitiesList.innerHTML = `${channelCards}${globalBlock}`;
}

function renderDetail() {
    const detail = state.selectedDetail;
    if (!detail) {
        dom.detailContainer.innerHTML = '<div class="empty-state">Select a timeline session to inspect detail.</div>';
        return;
    }

    const conversation = listHtml((detail.conversation || []).map((entry) => {
        return `${entry.sender || 'unknown'} @ ${entry.timestamp || '-'}: ${entry.text || ''}`;
    }));

    const indicators = flattenIndicators(detail.extractedInfo || {});

    dom.detailContainer.innerHTML = `
        <article class="detail-card">
            <h4>${escapeHtml(detail.sessionId || '-')}</h4>
            <ul class="mini-list">
                <li>Channel: ${escapeHtml(detail.channel || 'UNKNOWN')}</li>
                <li>Verdict: ${escapeHtml(detail.verdict || 'UNKNOWN')}</li>
                <li>Threat: ${escapeHtml(detail.threatLevel || 'UNKNOWN')} (${Number(detail.threatScore || 0).toFixed(2)})</li>
            </ul>
        </article>
        <article class="detail-card">
            <h4>Conversation</h4>
            <div class="conversation-list-wrap">${conversation}</div>
        </article>
        <article class="detail-card">
            <h4>Extracted Indicators</h4>
            <div class="indicator-list-wrap">${listHtml(indicators)}</div>
        </article>
    `;
}

function toneFromThreat(threatLevel, verdict) {
    const level = String(threatLevel || '').toUpperCase();
    const verdictLabel = String(verdict || '').toUpperCase();

    if (level === 'HIGH' || level === 'CRITICAL' || verdictLabel === 'DANGER') {
        return 'danger';
    }
    if (level === 'MEDIUM' || level === 'LOW' || verdictLabel === 'SUSPICIOUS') {
        return 'watch';
    }
    return 'safe';
}

function flattenIndicators(extracted) {
    const entries = [];
    const map = {
        upiIds: 'UPI',
        bankAccountNumbers: 'Bank Account',
        ifscCodes: 'IFSC',
        phoneNumbers: 'Phone',
        urls: 'URL',
        emails: 'Email',
        suspiciousKeywords: 'Keyword',
    };

    Object.entries(map).forEach(([key, label]) => {
        const values = Array.isArray(extracted[key]) ? extracted[key] : [];
        values.forEach((value) => entries.push(`${label}: ${value}`));
    });

    return entries;
}

function listHtml(values) {
    if (!values || values.length === 0) {
        return '<div class="empty-state">None</div>';
    }

    return `<ul class="mini-list">${values
        .map((value) => `<li>${escapeHtml(String(value))}</li>`)
        .join('')}</ul>`;
}

function escapeHtml(value) {
    return String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}
