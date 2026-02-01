// Configuration
const API_URL = 'http://localhost:8080';
const API_KEY = 'varutri_shield_2026';

// State
let currentSessionId = null;
let activeSessions = [];
let recentSessions = [];

// Initialize
window.onload = () => {
    updateTimestamp();
    setInterval(updateTimestamp, 1000);
    loadSessions();
    loadStats();

    // Refresh sessions every 5 seconds
    setInterval(loadSessions, 5000);

    // Enable Enter key to send
    document.getElementById('messageInput').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') sendMessage();
    });
};

// Update timestamp
function updateTimestamp() {
    const now = new Date();
    const timestamp = now.toLocaleString('en-US', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
    });
    document.getElementById('timestamp').textContent = `Last Sync: ${timestamp}`;
}

// Load sessions
async function loadSessions() {
    try {
        // In production, this would fetch from API
        // For now, create demo sessions
        activeSessions = [
            {
                id: 'session-' + Date.now(),
                channel: 'WHATSAPP',
                scamType: 'LOTTERY_SCAM',
                threatLevel: 0.85,
                messages: 12,
                aiResponses: 6,
                startTime: new Date(Date.now() - 300000),
                status: 'active'
            }
        ];

        recentSessions = [
            {
                id: 'session-' + (Date.now() - 1000000),
                channel: 'WHATSAPP',
                scamType: 'BANK_SCAM',
                threatLevel: 0.72,
                messages: 18,
                aiResponses: 9,
                startTime: new Date(Date.now() - 3600000),
                status: 'completed'
            },
            {
                id: 'session-' + (Date.now() - 2000000),
                channel: 'TELEGRAM',
                scamType: 'JOB_SCAM',
                threatLevel: 0.45,
                messages: 8,
                aiResponses: 4,
                startTime: new Date(Date.now() - 7200000),
                status: 'completed'
            }
        ];

        renderSessions();

    } catch (error) {
        console.error('Error loading sessions:', error);
    }
}

// Render sessions
function renderSessions() {
    // Active sessions
    const activeContainer = document.getElementById('activeSessions');
    document.getElementById('activeCount').textContent = activeSessions.length;

    if (activeSessions.length === 0) {
        activeContainer.innerHTML = '<div class="empty-state">No active sessions</div>';
    } else {
        activeContainer.innerHTML = activeSessions.map(session => createSessionCard(session)).join('');
    }

    // Recent sessions
    const recentContainer = document.getElementById('recentSessions');
    document.getElementById('recentCount').textContent = recentSessions.length;

    if (recentSessions.length === 0) {
        recentContainer.innerHTML = '<div class="empty-state">No recent sessions</div>';
    } else {
        recentContainer.innerHTML = recentSessions.map(session => createSessionCard(session)).join('');
    }
}

// Create session card HTML
function createSessionCard(session) {
    const threatPercent = Math.round(session.threatLevel * 100);
    const threatClass = threatPercent < 40 ? 'low' : threatPercent < 70 ? 'medium' : 'high';
    const duration = Math.round((Date.now() - session.startTime.getTime()) / 60000);

    return `
        <div class="session-card" onclick="viewSession('${session.id}')">
            <div class="session-card-header">
                <span class="session-id">${session.id.substring(0, 16)}...</span>
                <span class="session-channel">${session.channel}</span>
            </div>
            <div class="session-card-body">
                <div class="session-info-row">
                    <span class="session-info-label">SCAM TYPE:</span>
                    <span class="session-info-value">${session.scamType}</span>
                </div>
                <div class="session-info-row">
                    <span class="session-info-label">MESSAGES:</span>
                    <span class="session-info-value">${session.messages} (${session.aiResponses} AI)</span>
                </div>
                <div class="session-info-row">
                    <span class="session-info-label">DURATION:</span>
                    <span class="session-info-value">${duration}m</span>
                </div>
                <div class="session-threat">
                    <div class="session-threat-bar">
                        <div class="session-threat-fill" style="width: ${threatPercent}%"></div>
                    </div>
                    <span class="session-threat-value ${threatClass}">${threatPercent}%</span>
                </div>
            </div>
        </div>
    `;
}

// View session
async function viewSession(sessionId) {
    currentSessionId = sessionId;

    // Hide sessions view, show conversation view
    document.getElementById('sessionsView').classList.add('hidden');
    document.getElementById('conversationView').classList.remove('hidden');

    // Update header
    document.getElementById('currentSessionId').textContent = sessionId.substring(0, 20) + '...';

    // Find session
    const session = [...activeSessions, ...recentSessions].find(s => s.id === sessionId);
    if (session) {
        document.getElementById('currentChannel').textContent = session.channel;
    }

    // Load conversation
    await loadConversation(sessionId);
}

// Show sessions list
function showSessionsList() {
    document.getElementById('conversationView').classList.add('hidden');
    document.getElementById('sessionsView').classList.remove('hidden');
    currentSessionId = null;
}

// Load conversation
async function loadConversation(sessionId) {
    const feed = document.getElementById('conversationFeed');
    feed.innerHTML = '';

    try {
        // Try to fetch from API
        const response = await fetch(`${API_URL}/api/evidence/${sessionId}`, {
            headers: { 'x-api-key': API_KEY }
        });

        const evidence = await response.json();

        // Add AI entry indicator
        addAIEntryIndicator('AI VARUTRI INITIATED CONVERSATION');

        // Render conversation
        if (evidence.conversation && evidence.conversation.length > 0) {
            evidence.conversation.forEach((turn, index) => {
                if (index === 0) {
                    // First message - show when AI entered
                    addAIEntryIndicator('AI VARUTRI ENTERED CONVERSATION');
                }

                addMessageToFeed('scammer', turn.userMessage, turn.timestamp);
                addMessageToFeed('ai', turn.aiResponse, turn.timestamp);
            });
        }

        // Update threat panel
        updateThreatPanel(evidence);

    } catch (error) {
        console.error('Error loading conversation:', error);

        // Show demo conversation
        addAIEntryIndicator('AI VARUTRI INITIATED CONVERSATION');
        addMessageToFeed('scammer', 'Hello! You won 10 lakh rupees in lottery!', new Date());
        addMessageToFeed('ai', 'Arrey beta! Really? How to claim this prize?', new Date());
        addMessageToFeed('scammer', 'Send Rs 500 to 9876543210@paytm for processing fee', new Date());
        addMessageToFeed('ai', 'What is this paytm? I am old person, not understanding...', new Date());
    }
}

// Add AI entry indicator
function addAIEntryIndicator(text) {
    const feed = document.getElementById('conversationFeed');
    const indicator = document.createElement('div');
    indicator.className = 'ai-entry';
    indicator.textContent = text;
    feed.appendChild(indicator);
}

// Add message to feed
function addMessageToFeed(type, text, timestamp) {
    const feed = document.getElementById('conversationFeed');
    const msgDiv = document.createElement('div');
    msgDiv.className = `${type}-msg`;

    const time = timestamp ? new Date(timestamp).toLocaleTimeString('en-US', { hour12: false }) :
        new Date().toLocaleTimeString('en-US', { hour12: false });

    const sender = type === 'scammer' ? 'SCAMMER' : type === 'ai' ? 'VARUTRI' : 'SYSTEM';

    msgDiv.innerHTML = `
        <span class="msg-timestamp">[${time}]</span>
        <span class="msg-sender">${sender}:</span>
        <span class="msg-content">${text}</span>
    `;

    feed.appendChild(msgDiv);
    feed.scrollTop = feed.scrollHeight;
}

// Send message (for testing)
async function sendMessage() {
    if (!currentSessionId) {
        alert('Please select a session first');
        return;
    }

    const input = document.getElementById('messageInput');
    const message = input.value.trim();

    if (!message) return;

    input.value = '';

    // Add scammer message
    addMessageToFeed('scammer', message, new Date());

    try {
        // Call API
        const response = await fetch(`${API_URL}/api/chat`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'x-api-key': API_KEY
            },
            body: JSON.stringify({
                sessionId: currentSessionId,
                message: {
                    sender: 'scammer',
                    text: message,
                    timestamp: new Date().toISOString()
                }
            })
        });

        const data = await response.json();

        // Add AI response
        addMessageToFeed('ai', data.reply, new Date());

        // Update stats
        updateSessionStats();

        // Update threat panel
        await loadConversationData();

    } catch (error) {
        console.error('Error:', error);
        addMessageToFeed('system', `[ERROR] API connection failed: ${error.message}`, new Date());
    }
}

// Update session stats
function updateSessionStats() {
    const scammerMsgs = document.querySelectorAll('.scammer-msg').length;
    const aiMsgs = document.querySelectorAll('.ai-msg').length;

    document.getElementById('totalMessages').textContent = scammerMsgs + aiMsgs;
    document.getElementById('aiResponses').textContent = aiMsgs;
    document.getElementById('scammerMessages').textContent = scammerMsgs;
}

// Load conversation data and update panel
async function loadConversationData() {
    if (!currentSessionId) return;

    try {
        const response = await fetch(`${API_URL}/api/evidence/${currentSessionId}`, {
            headers: { 'x-api-key': API_KEY }
        });

        const evidence = await response.json();
        updateThreatPanel(evidence);

    } catch (error) {
        console.error('Error loading conversation data:', error);
    }
}

// Update threat panel
function updateThreatPanel(evidence) {
    // Update threat level
    const threatLevel = evidence.threatLevel || 0;
    const threatPercent = Math.round(threatLevel * 100);

    document.getElementById('threatFill').style.width = `${threatPercent}%`;
    document.getElementById('threatPercent').textContent = `${threatPercent}%`;

    // Update threat classification
    const classEl = document.getElementById('threatClass');
    classEl.className = 'threat-classification';

    if (threatLevel < 0.4) {
        classEl.textContent = 'SAFE';
        classEl.classList.add('safe');
    } else if (threatLevel < 0.7) {
        classEl.textContent = 'MEDIUM THREAT';
        classEl.classList.add('medium');
    } else {
        classEl.textContent = 'HIGH THREAT';
        classEl.classList.add('high');
    }

    // Update scam type
    document.getElementById('scamType').textContent = evidence.scamType || 'UNKNOWN';
    document.getElementById('confidence').textContent = threatPercent + '%';

    // Update intelligence
    const intel = evidence.extractedInfo || {};
    const intelContainer = document.getElementById('intelContainer');
    intelContainer.innerHTML = '';

    let count = 0;

    if (intel.upiIds && intel.upiIds.length > 0) {
        intel.upiIds.forEach(upi => {
            addIntelItem('[CRITICAL] UPI ID: ' + upi, 'critical');
            count++;
        });
    }

    if (intel.bankAccountNumbers && intel.bankAccountNumbers.length > 0) {
        intel.bankAccountNumbers.forEach(acc => {
            addIntelItem('[CRITICAL] BANK ACC: ' + acc, 'critical');
            count++;
        });
    }

    if (intel.phoneNumbers && intel.phoneNumbers.length > 0) {
        intel.phoneNumbers.forEach(phone => {
            addIntelItem('[INFO] PHONE: ' + phone, 'info');
            count++;
        });
    }

    if (intel.urls && intel.urls.length > 0) {
        intel.urls.forEach(url => {
            addIntelItem('[INFO] URL: ' + url, 'info');
            count++;
        });
    }

    if (intel.suspiciousKeywords && intel.suspiciousKeywords.length > 0) {
        const keywords = intel.suspiciousKeywords.slice(0, 5).join(', ');
        addIntelItem('[WARN] KEYWORDS: ' + keywords, 'warning');
    }

    if (count === 0) {
        intelContainer.innerHTML = '<div class="intel-empty">No intelligence gathered</div>';
    }

    // Update stats
    updateSessionStats();
}

// Add intelligence item
function addIntelItem(text, type) {
    const container = document.getElementById('intelContainer');
    const item = document.createElement('div');
    item.className = `intel-item ${type}`;
    item.textContent = text;
    container.appendChild(item);
}

// Load global stats
async function loadStats() {
    try {
        const response = await fetch(`${API_URL}/api/report/stats`, {
            headers: { 'x-api-key': API_KEY }
        });

        const stats = await response.json();
        document.getElementById('totalReports').textContent = stats.totalReports || 0;

    } catch (error) {
        console.error('Error loading stats:', error);
        document.getElementById('totalReports').textContent = '0';
    }
}

// Run tests
async function runTests() {
    if (!confirm('Run diagnostic scenarios? This will test the AI honeypot system.')) {
        return;
    }

    try {
        const response = await fetch(`${API_URL}/api/test/run-all`, {
            method: 'POST',
            headers: { 'x-api-key': API_KEY }
        });

        const results = await response.json();

        let message = 'DIAGNOSTIC RESULTS:\n\n';
        results.forEach(result => {
            const status = result.passed ? '[PASS]' : '[FAIL]';
            message += `${status}: ${result.scenarioName}\n`;
        });

        const passed = results.filter(r => r.passed).length;
        message += `\nTotal: ${passed}/${results.length} passed`;

        alert(message);

    } catch (error) {
        alert(`[ERROR] Diagnostic failed: ${error.message}`);
    }
}

// Generate report
async function generateReport() {
    if (!currentSessionId) {
        alert('Please select a session first');
        return;
    }

    if (!confirm('Generate classified report for this session?')) {
        return;
    }

    try {
        const response = await fetch(`${API_URL}/api/report/manual`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'x-api-key': API_KEY
            },
            body: JSON.stringify({ sessionId: currentSessionId })
        });

        const result = await response.json();

        if (result.status === 'success') {
            alert(`[SUCCESS] Report ${result.reportId} generated\n\nThreat: ${(result.threatLevel * 100).toFixed(0)}%\nIntelligence: ${result.intelligenceCount} items`);
            loadStats();
        } else {
            alert(`[ERROR] Report generation failed: ${result.message}`);
        }

    } catch (error) {
        alert(`[ERROR] Report generation failed: ${error.message}`);
    }
}
