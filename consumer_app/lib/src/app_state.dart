import 'package:flutter/foundation.dart';

import 'consumer_api_service.dart';
import 'models.dart';

class ConsumerAppState extends ChangeNotifier {
  ConsumerAppState({
    required this.baseUrl,
    this.appId = 'varutri-mobile',
    this.deviceId = 'android-local-test-001',
    this.appVersion = '0.1.0',
    this.platform = 'ANDROID',
    this.channel = 'SMS',
  });

  static const Duration _historyTtl = Duration(seconds: 30);
  static const Duration _detailTtl = Duration(minutes: 2);
  static const Duration _capabilitiesTtl = Duration(minutes: 5);

  String baseUrl;
  String appId;
  String deviceId;
  String appVersion;
  String platform;
  String channel;

  ConsumerAuthToken? authToken;
  ConsumerAnalysis? latestAnalysis;
  ConsumerCapabilities? capabilities;
  ConsumerHistoryDetail? selectedHistoryDetail;

  List<ConsumerHistoryItem> history = const <ConsumerHistoryItem>[];

  String status = 'Ready';

  bool _issuingToken = false;
  bool _analyzing = false;
  bool _loadingHistory = false;
  bool _loadingCapabilities = false;
  bool _loadingHistoryDetail = false;

  _TimedCacheEntry<List<ConsumerHistoryItem>>? _historyCache;
  final Map<String, _TimedCacheEntry<ConsumerHistoryDetail>> _historyDetailCache =
      <String, _TimedCacheEntry<ConsumerHistoryDetail>>{};
  final Map<String, _TimedCacheEntry<ConsumerCapabilities>> _capabilitiesCache =
      <String, _TimedCacheEntry<ConsumerCapabilities>>{};

  ConsumerApiService get _api => ConsumerApiService(baseUrl: baseUrl.trim());

  bool get issuingToken => _issuingToken;
  bool get analyzing => _analyzing;
  bool get loadingHistory => _loadingHistory;
  bool get loadingCapabilities => _loadingCapabilities;
  bool get loadingHistoryDetail => _loadingHistoryDetail;
  bool get isBusy =>
      _issuingToken || _analyzing || _loadingHistory || _loadingCapabilities || _loadingHistoryDetail;

  bool get hasToken => authToken != null && !authToken!.isExpired && authToken!.accessToken.isNotEmpty;

  String get tokenPreview {
    if (!hasToken) {
      return 'Not issued';
    }
    final token = authToken!.accessToken;
    if (token.length <= 14) {
      return token;
    }
    return '${token.substring(0, 8)}...${token.substring(token.length - 4)}';
  }

  String get tokenExpiryText {
    if (authToken == null) {
      return 'No token';
    }
    final ms = authToken!.expiresAtEpochMs;
    if (ms <= 0) {
      return 'Unknown expiry';
    }
    final date = DateTime.fromMillisecondsSinceEpoch(ms);
    return date.toIso8601String();
  }

  ConsumerRiskPresence get riskPresence {
    final level = (latestAnalysis?.threatLevel ?? '').toUpperCase();
    if (level == 'CRITICAL' || level == 'HIGH') {
      return ConsumerRiskPresence.danger;
    }
    if (level == 'MEDIUM' || level == 'LOW') {
      return ConsumerRiskPresence.watch;
    }
    if (level == 'SAFE') {
      return ConsumerRiskPresence.safe;
    }
    return ConsumerRiskPresence.idle;
  }

  void updateConnection({
    required String nextBaseUrl,
    required String nextAppId,
    required String nextDeviceId,
    required String nextAppVersion,
    required String nextPlatform,
  }) {
    bool changed = false;

    if (baseUrl != nextBaseUrl) {
      baseUrl = nextBaseUrl;
      changed = true;
    }
    if (appId != nextAppId) {
      appId = nextAppId;
      changed = true;
    }
    if (deviceId != nextDeviceId) {
      deviceId = nextDeviceId;
      changed = true;
    }
    if (appVersion != nextAppVersion) {
      appVersion = nextAppVersion;
      changed = true;
    }
    if (platform != nextPlatform) {
      platform = nextPlatform;
      capabilities = null;
      selectedHistoryDetail = null;
      changed = true;
    }

    if (changed) {
      status = 'Connection settings updated';
      notifyListeners();
    }
  }

  void setChannel(String nextChannel) {
    if (channel == nextChannel) {
      return;
    }
    channel = nextChannel;
    notifyListeners();
  }

  Future<void> issueToken() async {
    _issuingToken = true;
    status = 'Requesting token...';
    notifyListeners();

    try {
      final token = await _api.issueToken(
        appId: appId,
        deviceId: deviceId,
        platform: platform,
        appVersion: appVersion,
      );
      authToken = token;
      status = 'Token ready';
      await loadCapabilities(forceRefresh: true);
    } catch (error) {
      status = 'Token error: $error';
      authToken = null;
    } finally {
      _issuingToken = false;
      notifyListeners();
    }
  }

  Future<void> analyze({
    required String text,
    String senderId = '',
    String url = '',
  }) async {
    if (!hasToken) {
      status = 'Get token first';
      notifyListeners();
      return;
    }
    if (text.trim().isEmpty) {
      status = 'Enter suspicious content first';
      notifyListeners();
      return;
    }

    _analyzing = true;
    status = 'Analyzing signal...';
    notifyListeners();

    try {
      final analysis = await _api.analyze(
        token: authToken!.accessToken,
        channel: channel,
        platform: platform,
        text: text.trim(),
        senderId: senderId.trim(),
        url: url.trim(),
      );

      latestAnalysis = analysis;
      status = 'Analysis complete';
      _historyCache = null;
      _historyDetailCache.remove(analysis.sessionId);
      await loadHistory(forceRefresh: true);
    } catch (error) {
      status = 'Analyze error: $error';
    } finally {
      _analyzing = false;
      notifyListeners();
    }
  }

  Future<void> loadHistory({
    bool forceRefresh = false,
    int limit = 10,
  }) async {
    if (!hasToken) {
      status = 'Get token first';
      notifyListeners();
      return;
    }

    final now = DateTime.now();
    if (!forceRefresh && _historyCache != null && _historyCache!.isValidAt(now)) {
      history = _historyCache!.value;
      status = 'History loaded from cache';
      notifyListeners();
      return;
    }

    _loadingHistory = true;
    status = 'Loading history...';
    notifyListeners();

    try {
      final items = await _api.history(token: authToken!.accessToken, limit: limit);
      history = items;
      _historyCache = _TimedCacheEntry<List<ConsumerHistoryItem>>(
        value: items,
        expiresAt: now.add(_historyTtl),
      );
      status = 'History loaded';
    } catch (error) {
      status = 'History error: $error';
    } finally {
      _loadingHistory = false;
      notifyListeners();
    }
  }

  Future<void> loadHistoryDetail(String sessionId, {bool forceRefresh = false}) async {
    if (!hasToken) {
      status = 'Get token first';
      notifyListeners();
      return;
    }

    final key = sessionId.trim();
    final now = DateTime.now();
    final cached = _historyDetailCache[key];

    if (!forceRefresh && cached != null && cached.isValidAt(now)) {
      selectedHistoryDetail = cached.value;
      status = 'History detail loaded from cache';
      notifyListeners();
      return;
    }

    _loadingHistoryDetail = true;
    status = 'Loading session detail...';
    notifyListeners();

    try {
      final detail = await _api.historyDetail(token: authToken!.accessToken, sessionId: key);
      selectedHistoryDetail = detail;
      _historyDetailCache[key] = _TimedCacheEntry<ConsumerHistoryDetail>(
        value: detail,
        expiresAt: now.add(_detailTtl),
      );
      status = 'Session detail ready';
    } catch (error) {
      status = 'History detail error: $error';
    } finally {
      _loadingHistoryDetail = false;
      notifyListeners();
    }
  }

  void clearHistoryDetail() {
    if (selectedHistoryDetail == null) {
      return;
    }
    selectedHistoryDetail = null;
    notifyListeners();
  }

  Future<void> loadCapabilities({bool forceRefresh = false}) async {
    if (!hasToken) {
      status = 'Get token first';
      notifyListeners();
      return;
    }

    final key = platform.toUpperCase();
    final now = DateTime.now();
    final cached = _capabilitiesCache[key];

    if (!forceRefresh && cached != null && cached.isValidAt(now)) {
      capabilities = cached.value;
      status = 'Capabilities loaded from cache';
      notifyListeners();
      return;
    }

    _loadingCapabilities = true;
    status = 'Loading capabilities...';
    notifyListeners();

    try {
      final response = await _api.capabilities(token: authToken!.accessToken, platform: key);
      capabilities = response;
      _capabilitiesCache[key] = _TimedCacheEntry<ConsumerCapabilities>(
        value: response,
        expiresAt: now.add(_capabilitiesTtl),
      );
      status = 'Capabilities loaded';
    } catch (error) {
      status = 'Capabilities error: $error';
    } finally {
      _loadingCapabilities = false;
      notifyListeners();
    }
  }
}

class _TimedCacheEntry<T> {
  _TimedCacheEntry({required this.value, required this.expiresAt});

  final T value;
  final DateTime expiresAt;

  bool isValidAt(DateTime now) => now.isBefore(expiresAt);
}

enum ConsumerRiskPresence {
  idle,
  safe,
  watch,
  danger,
}
