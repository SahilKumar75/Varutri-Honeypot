import 'package:flutter/material.dart';

import 'app_state.dart';
import 'models.dart';

class VarutriConsumerApp extends StatelessWidget {
  const VarutriConsumerApp({super.key, required this.state});

  final ConsumerAppState state;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Varutri Consumer',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF00695C),
          brightness: Brightness.light,
        ),
        useMaterial3: true,
      ),
      home: ConsumerHomePage(state: state),
    );
  }
}

class ConsumerHomePage extends StatefulWidget {
  const ConsumerHomePage({super.key, required this.state});

  final ConsumerAppState state;

  @override
  State<ConsumerHomePage> createState() => _ConsumerHomePageState();
}

class _ConsumerHomePageState extends State<ConsumerHomePage> {
  late final TextEditingController _baseUrlController;
  late final TextEditingController _appIdController;
  late final TextEditingController _deviceIdController;
  late final TextEditingController _appVersionController;
  late final TextEditingController _messageController;
  late final TextEditingController _senderController;
  late final TextEditingController _urlController;

  int _tabIndex = 0;

  @override
  void initState() {
    super.initState();
    _baseUrlController = TextEditingController(text: widget.state.baseUrl);
    _appIdController = TextEditingController(text: widget.state.appId);
    _deviceIdController = TextEditingController(text: widget.state.deviceId);
    _appVersionController = TextEditingController(text: widget.state.appVersion);
    _messageController = TextEditingController();
    _senderController = TextEditingController();
    _urlController = TextEditingController();
  }

  @override
  void dispose() {
    _baseUrlController.dispose();
    _appIdController.dispose();
    _deviceIdController.dispose();
    _appVersionController.dispose();
    _messageController.dispose();
    _senderController.dispose();
    _urlController.dispose();
    super.dispose();
  }

  void _syncConnectionSettings() {
    widget.state.updateConnection(
      nextBaseUrl: _baseUrlController.text.trim(),
      nextAppId: _appIdController.text.trim(),
      nextDeviceId: _deviceIdController.text.trim(),
      nextAppVersion: _appVersionController.text.trim(),
      nextPlatform: widget.state.platform,
    );
  }

  Future<void> _issueToken() async {
    _syncConnectionSettings();
    await widget.state.issueToken();
  }

  Future<void> _analyze() async {
    _syncConnectionSettings();
    await widget.state.analyze(
      text: _messageController.text,
      senderId: _senderController.text,
      url: _urlController.text,
    );
  }

  Future<void> _loadHistory({bool forceRefresh = false}) async {
    await widget.state.loadHistory(forceRefresh: forceRefresh);
  }

  Future<void> _loadCapabilities({bool forceRefresh = false}) async {
    _syncConnectionSettings();
    await widget.state.loadCapabilities(forceRefresh: forceRefresh);
  }

  Future<void> _openHistoryDetail(ConsumerHistoryItem item) async {
    await widget.state.loadHistoryDetail(item.sessionId);
    final detail = widget.state.selectedHistoryDetail;
    if (!mounted || detail == null || detail.sessionId != item.sessionId) {
      return;
    }

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (ctx) => _HistoryDetailSheet(detail: detail),
    );
    widget.state.clearHistoryDetail();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.state,
      builder: (context, _) {
        final state = widget.state;
        final presence = _presenceTheme(state.riskPresence);

        return Scaffold(
          appBar: AppBar(
            title: const Text('Varutri Consumer Operations'),
            actions: [
              Padding(
                padding: const EdgeInsets.only(right: 12),
                child: Chip(
                  backgroundColor: presence.background,
                  label: Text(
                    presence.title,
                    style: TextStyle(color: presence.foreground),
                  ),
                ),
              ),
            ],
          ),
          body: SafeArea(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildPresenceBanner(state, presence),
                _buildConnectionCard(state),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Text(
                    'Status: ${state.status}',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ),
                const SizedBox(height: 8),
                Expanded(
                  child: IndexedStack(
                    index: _tabIndex,
                    children: [
                      _buildAnalyzeFlow(state),
                      _buildHistoryFlow(state),
                      _buildCapabilitiesFlow(state),
                    ],
                  ),
                ),
              ],
            ),
          ),
          bottomNavigationBar: NavigationBar(
            selectedIndex: _tabIndex,
            onDestinationSelected: (index) {
              setState(() {
                _tabIndex = index;
              });
            },
            destinations: const [
              NavigationDestination(
                icon: Icon(Icons.analytics_outlined),
                selectedIcon: Icon(Icons.analytics),
                label: 'Analyze',
              ),
              NavigationDestination(
                icon: Icon(Icons.history_outlined),
                selectedIcon: Icon(Icons.history),
                label: 'History',
              ),
              NavigationDestination(
                icon: Icon(Icons.map_outlined),
                selectedIcon: Icon(Icons.map),
                label: 'Capabilities',
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildPresenceBanner(ConsumerAppState state, _PresenceTheme presence) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: presence.background,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.shield_outlined, color: presence.foreground),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'On-screen Presence: ${presence.title}',
                  style: TextStyle(
                    color: presence.foreground,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  presence.subtitle,
                  style: TextStyle(color: presence.foreground),
                ),
                if (state.latestAnalysis != null)
                  Text(
                    'Latest: ${state.latestAnalysis!.channel} ${state.latestAnalysis!.verdict} (${state.latestAnalysis!.threatLevel} ${state.latestAnalysis!.threatScore.toStringAsFixed(2)})',
                    style: TextStyle(color: presence.foreground),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildConnectionCard(ConsumerAppState state) {
    return Card(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      child: ExpansionTile(
        title: const Text('Server Handling and Token Boundary'),
        subtitle: Text('Token: ${state.tokenPreview}'),
        childrenPadding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
        children: [
          _textField(_baseUrlController, 'Base URL'),
          _textField(_appIdController, 'App ID'),
          _textField(_deviceIdController, 'Device ID'),
          _textField(_appVersionController, 'App Version'),
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: DropdownButtonFormField<String>(
              value: state.platform,
              decoration: const InputDecoration(
                labelText: 'Platform',
                border: OutlineInputBorder(),
              ),
              items: const [
                DropdownMenuItem(value: 'ANDROID', child: Text('ANDROID')),
                DropdownMenuItem(value: 'IOS', child: Text('IOS')),
                DropdownMenuItem(value: 'WEB', child: Text('WEB')),
                DropdownMenuItem(value: 'EXTENSION', child: Text('EXTENSION')),
              ],
              onChanged: (value) {
                if (value == null) {
                  return;
                }
                widget.state.updateConnection(
                  nextBaseUrl: _baseUrlController.text.trim(),
                  nextAppId: _appIdController.text.trim(),
                  nextDeviceId: _deviceIdController.text.trim(),
                  nextAppVersion: _appVersionController.text.trim(),
                  nextPlatform: value,
                );
              },
            ),
          ),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              FilledButton.icon(
                onPressed: state.issuingToken ? null : _issueToken,
                icon: const Icon(Icons.key),
                label: const Text('Issue Token'),
              ),
              OutlinedButton.icon(
                onPressed: state.hasToken ? () => _loadCapabilities(forceRefresh: true) : null,
                icon: const Icon(Icons.refresh),
                label: const Text('Refresh Capabilities'),
              ),
              OutlinedButton.icon(
                onPressed: state.hasToken ? () => _loadHistory(forceRefresh: true) : null,
                icon: const Icon(Icons.sync),
                label: const Text('Refresh History'),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text('Token Expires: ${state.tokenExpiryText}'),
        ],
      ),
    );
  }

  Widget _buildAnalyzeFlow(ConsumerAppState state) {
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _sectionTitle('Flow 1: Capture and Analyze'),
          DropdownButtonFormField<String>(
            value: state.channel,
            decoration: const InputDecoration(
              labelText: 'Channel',
              border: OutlineInputBorder(),
            ),
            items: const [
              DropdownMenuItem(value: 'SMS', child: Text('SMS')),
              DropdownMenuItem(value: 'CALL', child: Text('CALL')),
              DropdownMenuItem(value: 'WHATSAPP', child: Text('WHATSAPP')),
              DropdownMenuItem(value: 'EMAIL', child: Text('EMAIL')),
              DropdownMenuItem(value: 'BROWSER', child: Text('BROWSER')),
              DropdownMenuItem(value: 'MANUAL', child: Text('MANUAL')),
              DropdownMenuItem(value: 'PROMPT', child: Text('PROMPT')),
            ],
            onChanged: (value) {
              if (value != null) {
                state.setChannel(value);
              }
            },
          ),
          const SizedBox(height: 8),
          _textField(_messageController, 'Suspicious content', maxLines: 5),
          _textField(_senderController, 'Sender ID (optional)'),
          _textField(_urlController, 'URL (optional)'),
          const SizedBox(height: 8),
          FilledButton.icon(
            onPressed: state.analyzing ? null : _analyze,
            icon: const Icon(Icons.search),
            label: const Text('Run Threat Analysis'),
          ),
          const SizedBox(height: 16),
          if (state.latestAnalysis != null) _buildAnalysisCard(state.latestAnalysis!),
        ],
      ),
    );
  }

  Widget _buildHistoryFlow(ConsumerAppState state) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _sectionTitle('Flow 2: Evidence Timeline'),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              FilledButton.tonalIcon(
                onPressed: state.loadingHistory ? null : () => _loadHistory(forceRefresh: false),
                icon: const Icon(Icons.download),
                label: const Text('Load History'),
              ),
              OutlinedButton.icon(
                onPressed: state.loadingHistory ? null : () => _loadHistory(forceRefresh: true),
                icon: const Icon(Icons.refresh),
                label: const Text('Force Refresh'),
              ),
            ],
          ),
          const SizedBox(height: 12),
          if (state.history.isEmpty)
            const Expanded(
              child: Center(
                child: Text('No sessions yet. Analyze a suspicious signal to start history.'),
              ),
            )
          else
            Expanded(
              child: ListView.separated(
                itemCount: state.history.length,
                separatorBuilder: (_, __) => const Divider(height: 1),
                itemBuilder: (context, index) {
                  final item = state.history[index];
                  return ListTile(
                    title: Text('${item.channel} ${item.verdict}'),
                    subtitle: Text('${item.sessionId}\n${item.lastUpdated}'),
                    trailing: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text(item.threatLevel),
                        Text(item.threatScore.toStringAsFixed(2)),
                      ],
                    ),
                    isThreeLine: true,
                    onTap: () => _openHistoryDetail(item),
                  );
                },
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildCapabilitiesFlow(ConsumerAppState state) {
    final capabilities = state.capabilities;

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _sectionTitle('Flow 3: Platform Capability Matrix'),
          Text('Active Platform: ${state.platform}'),
          const SizedBox(height: 8),
          FilledButton.tonalIcon(
            onPressed: state.loadingCapabilities ? null : () => _loadCapabilities(forceRefresh: true),
            icon: const Icon(Icons.map_outlined),
            label: const Text('Load Capability Matrix'),
          ),
          const SizedBox(height: 12),
          if (capabilities == null)
            const Text('No capabilities loaded. Issue token and load matrix.')
          else ...[
            Text('Generated At: ${capabilities.generatedAt}'),
            const SizedBox(height: 8),
            ...capabilities.channels.map(_capabilityCard),
            const SizedBox(height: 8),
            _stringCard('Global Limitations', capabilities.globalLimitations),
            const SizedBox(height: 8),
            _stringCard('Operational Notes', capabilities.operationalNotes),
          ],
        ],
      ),
    );
  }

  Widget _buildAnalysisCard(ConsumerAnalysis analysis) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Latest Analysis Result',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            Text('Session: ${analysis.sessionId}'),
            Text('Verdict: ${analysis.verdict}'),
            Text('Threat: ${analysis.threatLevel} (${analysis.threatScore.toStringAsFixed(2)})'),
            const SizedBox(height: 8),
            _inlineList('Recommended Actions', analysis.recommendedActions),
            if (analysis.platformNotes.isNotEmpty) ...[
              const SizedBox(height: 8),
              _inlineList('Platform Notes', analysis.platformNotes),
            ],
          ],
        ),
      ),
    );
  }

  Widget _capabilityCard(ChannelCapability capability) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '${capability.channel} (${capability.automationLevel})',
              style: Theme.of(context).textTheme.titleSmall,
            ),
            const SizedBox(height: 4),
            Text(
              'Passive Detection: ${capability.passiveDetection ? 'Yes' : 'No'} | User Share Required: ${capability.userShareRequired ? 'Yes' : 'No'}',
            ),
            const SizedBox(height: 8),
            _inlineList('Limitations', capability.limitations),
            const SizedBox(height: 8),
            _inlineList('Recommended Flows', capability.recommendedFlows),
          ],
        ),
      ),
    );
  }

  Widget _stringCard(String title, List<String> values) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: _inlineList(title, values),
      ),
    );
  }

  Widget _inlineList(String title, List<String> values) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.titleSmall,
        ),
        const SizedBox(height: 4),
        if (values.isEmpty)
          const Text('None')
        else
          ...values.map((value) => Text('- $value')),
      ],
    );
  }

  Widget _sectionTitle(String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(
        value,
        style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
      ),
    );
  }

  Widget _textField(TextEditingController controller, String label, {int maxLines = 1}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: TextField(
        controller: controller,
        maxLines: maxLines,
        decoration: InputDecoration(
          labelText: label,
          border: const OutlineInputBorder(),
        ),
      ),
    );
  }

  _PresenceTheme _presenceTheme(ConsumerRiskPresence presence) {
    return switch (presence) {
      ConsumerRiskPresence.danger => _PresenceTheme(
          title: 'High Risk Active',
          subtitle: 'Danger signals detected. Prioritize evidence capture and user warning.',
          background: const Color(0xFFFEE2E2),
          foreground: const Color(0xFF7F1D1D),
        ),
      ConsumerRiskPresence.watch => _PresenceTheme(
          title: 'Watch Mode',
          subtitle: 'Suspicious signals present. Keep monitoring and verify every next action.',
          background: const Color(0xFFFEF3C7),
          foreground: const Color(0xFF78350F),
        ),
      ConsumerRiskPresence.safe => _PresenceTheme(
          title: 'Stable',
          subtitle: 'No severe indicators in latest analysis. Continue safe-by-default flow.',
          background: const Color(0xFFD1FAE5),
          foreground: const Color(0xFF064E3B),
        ),
      ConsumerRiskPresence.idle => _PresenceTheme(
          title: 'Idle',
          subtitle: 'Awaiting analysis. Capture suspicious signals to activate protection.',
          background: const Color(0xFFE0E7FF),
          foreground: const Color(0xFF1E1B4B),
        ),
    };
  }
}

class _HistoryDetailSheet extends StatelessWidget {
  const _HistoryDetailSheet({required this.detail});

  final ConsumerHistoryDetail detail;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Session Detail',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            Text('Session: ${detail.sessionId}'),
            Text('Channel: ${detail.channel}'),
            Text('Verdict: ${detail.verdict}'),
            Text('Threat: ${detail.threatLevel} (${detail.threatScore.toStringAsFixed(2)})'),
            Text('First Seen: ${detail.firstSeen}'),
            Text('Last Updated: ${detail.lastUpdated}'),
            const SizedBox(height: 12),
            Text(
              'Conversation',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 6),
            if (detail.conversation.isEmpty)
              const Text('No conversation entries')
            else
              ...detail.conversation.map(
                (entry) => Card(
                  child: Padding(
                    padding: const EdgeInsets.all(8),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('${entry.sender} @ ${entry.timestamp}'),
                        const SizedBox(height: 4),
                        Text(entry.text),
                      ],
                    ),
                  ),
                ),
              ),
            const SizedBox(height: 12),
            Text(
              'Extracted Indicators',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 6),
            _detailList(context, 'UPI IDs', detail.extractedInfo.upiIds),
            _detailList(context, 'Bank Accounts', detail.extractedInfo.bankAccountNumbers),
            _detailList(context, 'IFSC Codes', detail.extractedInfo.ifscCodes),
            _detailList(context, 'Phone Numbers', detail.extractedInfo.phoneNumbers),
            _detailList(context, 'URLs', detail.extractedInfo.urls),
            _detailList(context, 'Emails', detail.extractedInfo.emails),
            _detailList(context, 'Keywords', detail.extractedInfo.suspiciousKeywords),
          ],
        ),
      ),
    );
  }

  Widget _detailList(BuildContext context, String title, List<String> values) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: Theme.of(context).textTheme.titleSmall,
          ),
          const SizedBox(height: 2),
          if (values.isEmpty)
            const Text('None')
          else
            ...values.map((v) => Text('- $v')),
        ],
      ),
    );
  }
}

class _PresenceTheme {
  const _PresenceTheme({
    required this.title,
    required this.subtitle,
    required this.background,
    required this.foreground,
  });

  final String title;
  final String subtitle;
  final Color background;
  final Color foreground;
}
