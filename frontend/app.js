// Configuration
const API_URL = 'http://localhost:8080';
const API_KEY = 'varutri_shield_2026';

// State
let currentSessionId = 'session-' + Date.now();
let messageCount = 0;
let turnCount = 0;
let intelCount = 0;
let isUserInControl = false;

// Initialize
window.onload = () => {
    updateTimestamp();
    setInterval(updateTimestamp, 1000);
    loadStats();
    updateSessionDisplay();

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

// Update session display
function updateSessionDisplay() {
    document.getElementById('sessionId').textContent = currentSessionId;
    document.getElementById('messageCount').textContent = messageCount;
    document.getElementById('turnCount').textContent = turnCount;
    document.getElementById('intelCount').textContent = intelCount;
}

// Take control
function takeControl() {
    isUserInControl = true;

    // Update UI
    document.getElementById('controlStatus').textContent = 'USER IN CONTROL';
    document.getElementById('controlStatus').style.borderColor = '#60a5fa';
    document.getElementById('controlStatus').style.color = '#60a5fa';
    document.getElementById('controlStatus').style.background = 'rgba(96, 165, 250, 0.1)';

    document.getElementById('modeStatus').textContent = 'MANUAL';
    document.getElementById('inputLabel').textContent = 'YOUR MESSAGE TO SCAMMER';

    // Update buttons
    document.getElementById('takeControlBtn').classList.add('disabled');
    document.getElementById('takeControlBtn').disabled = true;
    document.getElementById('giveControlBtn').classList.remove('disabled');
    document.getElementById('giveControlBtn').disabled = false;
    document.getElementById('giveControlBtn').classList.add('active');

    addMessageToFeed('system', 'Control transferred to operator. You are now responding to the scammer.');
}

// Give control back to AI
function giveControl() {
    isUserInControl = false;

    // Update UI
    document.getElementById('controlStatus').textContent = 'AI IN CONTROL';
    document.getElementById('controlStatus').style.borderColor = '#4ade80';
    document.getElementById('controlStatus').style.color = '#4ade80';
    document.getElementById('controlStatus').style.background = 'rgba(74, 222, 128, 0.1)';

    document.getElementById('modeStatus').textContent = 'AUTONOMOUS';
    document.getElementById('inputLabel').textContent = 'SCAMMER INPUT (SIMULATION)';

    // Update buttons
    document.getElementById('takeControlBtn').classList.remove('disabled');
    document.getElementById('takeControlBtn').disabled = false;
    document.getElementById('giveControlBtn').classList.add('disabled');
    document.getElementById('giveControlBtn').disabled = true;
    document.getElementById('giveControlBtn').classList.remove('active');

    addMessageToFeed('system', 'Control returned to AI. Varutri will resume autonomous operation.');
}

// Send message
async function sendMessage() {
    const input = document.getElementById('messageInput');
    const message = input.value.trim();

    if (!message) return;

    // Clear input
    input.value = '';

    if (isUserInControl) {
        // User is sending message to scammer
        addMessageToFeed('user', message);
        messageCount++;
        updateSessionDisplay();

        // In real implementation, this would send to actual scammer
        addMessageToFeed('system', 'Message sent to scammer. Awaiting response...');

    } else {
        // Simulating scammer message
        addMessageToFeed('scammer', message);
        messageCount++;

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
                    },
                    conversationHistory: [],
                    metadata: {}
                })
            });

            const data = await response.json();

            // Add AI response
            addMessageToFeed('ai', data.reply);
            messageCount++;
            turnCount++;

            // Update displays
            updateSessionDisplay();
            await updateThreatPanel();

        } catch (error) {
            console.error('Error:', error);
            addMessageToFeed('system', `[ERROR] API connection failed: ${error.message}`);
            document.getElementById('apiStatus').textContent = 'DISCONNECTED';
            document.getElementById('apiStatus').style.color = '#ff4444';
        }
    }
}

// Add message to feed
function addMessageToFeed(type, text) {
    const feed = document.getElementById('conversationFeed');
    const msgDiv = document.createElement('div');
    msgDiv.className = `${type}-msg`;

    const timestamp = new Date().toLocaleTimeString('en-US', { hour12: false });
    const label = {
        'scammer': 'SCAMMER',
        'ai': 'VARUTRI',
        'user': 'OPERATOR',
        'system': 'SYSTEM'
    }[type];

    msgDiv.innerHTML = `
        <span class="msg-timestamp">[${timestamp}] ${label}:</span>
        <span class="msg-content">${text}</span>
    `;

    feed.appendChild(msgDiv);
    feed.scrollTop = feed.scrollHeight;
}

// Update threat panel
async function updateThreatPanel() {
    try {
        const response = await fetch(`${API_URL}/api/evidence/${currentSessionId}`, {
            headers: { 'x-api-key': API_KEY }
        });

        const evidence = await response.json();

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

        intelCount = count;
        updateSessionDisplay();

    } catch (error) {
        console.error('Error updating threat panel:', error);
    }
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

        document.getElementById('apiStatus').textContent = 'CONNECTED';
        document.getElementById('apiStatus').style.color = '#4ade80';

    } catch (error) {
        console.error('Error loading stats:', error);
        document.getElementById('apiStatus').textContent = 'DISCONNECTED';
        document.getElementById('apiStatus').style.color = '#ff4444';
    }
}

// Run tests
async function runTests() {
    addMessageToFeed('system', 'Running diagnostic scenarios...');

    try {
        const response = await fetch(`${API_URL}/api/test/run-all`, {
            method: 'POST',
            headers: { 'x-api-key': API_KEY }
        });

        const results = await response.json();

        results.forEach(result => {
            const status = result.passed ? '[PASS]' : '[FAIL]';
            addMessageToFeed('system', `${status}: ${result.scenarioName} (${result.totalMessages} messages)`);
        });

        const passed = results.filter(r => r.passed).length;
        addMessageToFeed('system', `Diagnostics complete: ${passed}/${results.length} scenarios passed`);

    } catch (error) {
        addMessageToFeed('system', `[ERROR] Diagnostic failed: ${error.message}`);
    }
}

// Generate report
async function generateReport() {
    if (turnCount === 0) {
        addMessageToFeed('system', '[ERROR] No conversation data to report. Initiate contact first.');
        return;
    }

    addMessageToFeed('system', 'Generating classified report...');

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
            addMessageToFeed('system', `[SUCCESS] Report ${result.reportId} generated`);
            addMessageToFeed('system', `Threat: ${(result.threatLevel * 100).toFixed(0)}% | Intelligence: ${result.intelligenceCount} items`);
            loadStats();
        } else {
            addMessageToFeed('system', `[ERROR] Report generation failed: ${result.message}`);
        }

    } catch (error) {
        addMessageToFeed('system', `[ERROR] Report generation failed: ${error.message}`);
    }
}

// Reset session
function resetSession() {
    if (!confirm('Terminate current session and start new operation?')) {
        return;
    }

    currentSessionId = 'session-' + Date.now();
    messageCount = 0;
    turnCount = 0;
    intelCount = 0;

    // Reset control to AI
    if (isUserInControl) {
        giveControl();
    }

    // Clear conversation
    document.getElementById('conversationFeed').innerHTML = `
        <div class="system-msg">
            <span class="msg-timestamp">[SYSTEM]</span>
            <span class="msg-content">Session terminated. New session initiated: ${currentSessionId}</span>
        </div>
    `;

    // Reset threat panel
    document.getElementById('threatFill').style.width = '0%';
    document.getElementById('threatPercent').textContent = '0%';
    document.getElementById('threatClass').textContent = 'SAFE';
    document.getElementById('threatClass').className = 'threat-classification safe';
    document.getElementById('scamType').textContent = 'UNKNOWN';
    document.getElementById('confidence').textContent = 'N/A';
    document.getElementById('intelContainer').innerHTML = '<div class="intel-empty">No intelligence gathered</div>';

    updateSessionDisplay();
}
