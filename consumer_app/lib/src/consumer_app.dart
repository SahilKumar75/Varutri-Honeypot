import 'package:flutter/material.dart';

import 'consumer_api_service.dart';
import 'models.dart';

class VarutriConsumerApp extends StatelessWidget {
  const VarutriConsumerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Varutri Consumer',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.teal),
        useMaterial3: true,
      ),
      home: const ConsumerHomePage(),
    );
  }
}

class ConsumerHomePage extends StatefulWidget {
  const ConsumerHomePage({super.key});

  @override
  State<ConsumerHomePage> createState() => _ConsumerHomePageState();
}

class _ConsumerHomePageState extends State<ConsumerHomePage> {
  final TextEditingController _baseUrlController =
      TextEditingController(text: 'http://10.0.2.2:8080');
  final TextEditingController _appIdController =
      TextEditingController(text: 'varutri-mobile');
  final TextEditingController _deviceIdController =
      TextEditingController(text: 'android-local-test-001');
  final TextEditingController _appVersionController =
      TextEditingController(text: '0.1.0');
  final TextEditingController _messageController = TextEditingController();
  final TextEditingController _senderController = TextEditingController();
  final TextEditingController _urlController = TextEditingController();

  String _channel = 'SMS';
  String _token = '';
  String _status = 'Ready';
  ConsumerAnalysis? _analysis;
  List<ConsumerHistoryItem> _history = const <ConsumerHistoryItem>[];

  ConsumerApiService get _api => ConsumerApiService(baseUrl: _baseUrlController.text.trim());

  Future<void> _issueToken() async {
    setState(() => _status = 'Requesting token...');
    try {
      final token = await _api.issueToken(
        appId: _appIdController.text.trim(),
        deviceId: _deviceIdController.text.trim(),
        platform: 'ANDROID',
        appVersion: _appVersionController.text.trim(),
      );
      setState(() {
        _token = token;
        _status = 'Token ready';
      });
    } catch (error) {
      setState(() => _status = 'Token error: $error');
    }
  }

  Future<void> _analyze() async {
    if (_token.isEmpty) {
      setState(() => _status = 'Get token first');
      return;
    }
    if (_messageController.text.trim().isEmpty) {
      setState(() => _status = 'Enter suspicious content first');
      return;
    }

    setState(() => _status = 'Analyzing...');
    try {
      final analysis = await _api.analyze(
        token: _token,
        channel: _channel,
        text: _messageController.text.trim(),
        senderId: _senderController.text.trim(),
        url: _urlController.text.trim(),
      );
      setState(() {
        _analysis = analysis;
        _status = 'Analysis complete';
      });
    } catch (error) {
      setState(() => _status = 'Analyze error: $error');
    }
  }

  Future<void> _loadHistory() async {
    if (_token.isEmpty) {
      setState(() => _status = 'Get token first');
      return;
    }

    setState(() => _status = 'Loading history...');
    try {
      final history = await _api.history(token: _token, limit: 10);
      setState(() {
        _history = history;
        _status = 'History loaded';
      });
    } catch (error) {
      setState(() => _status = 'History error: $error');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Varutri Consumer Shell'),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _sectionTitle('Connection'),
              _textField(_baseUrlController, 'Base URL'),
              _textField(_appIdController, 'App ID'),
              _textField(_deviceIdController, 'Device ID'),
              _textField(_appVersionController, 'App Version'),
              const SizedBox(height: 8),
              FilledButton(onPressed: _issueToken, child: const Text('Issue Token')),
              const SizedBox(height: 16),
              _sectionTitle('Analyze Suspicious Signal'),
              DropdownButtonFormField<String>(
                value: _channel,
                items: const [
                  DropdownMenuItem(value: 'SMS', child: Text('SMS')),
                  DropdownMenuItem(value: 'CALL', child: Text('CALL')),
                  DropdownMenuItem(value: 'WHATSAPP', child: Text('WHATSAPP')),
                  DropdownMenuItem(value: 'EMAIL', child: Text('EMAIL')),
                  DropdownMenuItem(value: 'BROWSER', child: Text('BROWSER')),
                  DropdownMenuItem(value: 'MANUAL', child: Text('MANUAL')),
                  DropdownMenuItem(value: 'PROMPT', child: Text('PROMPT')),
                ],
                onChanged: (value) => setState(() => _channel = value ?? 'SMS'),
                decoration: const InputDecoration(labelText: 'Channel'),
              ),
              _textField(_messageController, 'Suspicious message/content', maxLines: 4),
              _textField(_senderController, 'Sender ID (optional)'),
              _textField(_urlController, 'URL (optional)'),
              const SizedBox(height: 8),
              FilledButton(onPressed: _analyze, child: const Text('Analyze')),
              const SizedBox(height: 16),
              FilledButton.tonal(onPressed: _loadHistory, child: const Text('Load History')),
              const SizedBox(height: 16),
              Text('Status: $_status'),
              const SizedBox(height: 12),
              if (_analysis != null) ...[
                _sectionTitle('Latest Analysis'),
                Text('Session: ${_analysis!.sessionId}'),
                Text('Verdict: ${_analysis!.verdict}'),
                Text('Threat: ${_analysis!.threatLevel} (${_analysis!.threatScore})'),
                const SizedBox(height: 8),
                const Text('Recommended Actions:'),
                ..._analysis!.recommendedActions.map((action) => Text('- $action')),
                if (_analysis!.platformNotes.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  const Text('Platform Notes:'),
                  ..._analysis!.platformNotes.map((note) => Text('- $note')),
                ],
              ],
              if (_history.isNotEmpty) ...[
                const SizedBox(height: 16),
                _sectionTitle('Recent History'),
                ..._history.map(
                  (item) => ListTile(
                    dense: true,
                    title: Text('${item.channel} - ${item.verdict}'),
                    subtitle: Text('${item.sessionId}\n${item.lastUpdated}'),
                    trailing: Text(item.threatLevel),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
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
}
