import 'dart:convert';

import 'package:http/http.dart' as http;

import 'models.dart';

class ConsumerApiService {
  ConsumerApiService({required this.baseUrl});

  final String baseUrl;

  Future<ConsumerAuthToken> issueToken({
    required String appId,
    required String deviceId,
    required String platform,
    required String appVersion,
  }) async {
    final uri = Uri.parse('$baseUrl/api/consumer/auth/token');
    final response = await http.post(
      uri,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'appId': appId,
        'deviceId': deviceId,
        'platform': platform,
        'appVersion': appVersion,
      }),
    );

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('Token request failed: ${response.statusCode} ${response.body}');
    }

    final payload = jsonDecode(response.body) as Map<String, dynamic>;
    final authToken = ConsumerAuthToken.fromApiResponse(payload);
    if (authToken.accessToken.isEmpty) {
      throw Exception('Token missing in response');
    }
    return authToken;
  }

  Future<ConsumerAnalysis> analyze({
    required String token,
    required String channel,
    required String platform,
    required String text,
    String senderId = '',
    String url = '',
  }) async {
    final uri = Uri.parse('$baseUrl/api/consumer/analyze');
    final response = await http.post(
      uri,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $token',
      },
      body: jsonEncode({
        'channel': channel,
        'payload': {
          'text': text,
          if (senderId.isNotEmpty) 'senderId': senderId,
          if (url.isNotEmpty) 'url': url,
        },
        'metadata': {
          'platform': platform,
          'sourceApp': 'consumer_app_shell',
          'locale': 'en_IN',
        },
      }),
    );

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('Analyze failed: ${response.statusCode} ${response.body}');
    }

    final payload = jsonDecode(response.body) as Map<String, dynamic>;
    return ConsumerAnalysis.fromApiResponse(payload);
  }

  Future<List<ConsumerHistoryItem>> history({
    required String token,
    int limit = 10,
  }) async {
    final uri = Uri.parse('$baseUrl/api/consumer/history?limit=$limit');
    final response = await http.get(
      uri,
      headers: {
        'Authorization': 'Bearer $token',
      },
    );

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('History failed: ${response.statusCode} ${response.body}');
    }

    final payload = jsonDecode(response.body) as Map<String, dynamic>;
    final data = payload['data'] as List<dynamic>? ?? const <dynamic>[];
    return data
        .map((item) => ConsumerHistoryItem.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<ConsumerHistoryDetail> historyDetail({
    required String token,
    required String sessionId,
  }) async {
    final uri = Uri.parse('$baseUrl/api/consumer/history/$sessionId');
    final response = await http.get(
      uri,
      headers: {
        'Authorization': 'Bearer $token',
      },
    );

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('History detail failed: ${response.statusCode} ${response.body}');
    }

    final payload = jsonDecode(response.body) as Map<String, dynamic>;
    return ConsumerHistoryDetail.fromApiResponse(payload);
  }

  Future<ConsumerCapabilities> capabilities({
    required String token,
    required String platform,
  }) async {
    final uri = Uri.parse('$baseUrl/api/consumer/capabilities?platform=$platform');
    final response = await http.get(
      uri,
      headers: {
        'Authorization': 'Bearer $token',
      },
    );

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('Capabilities failed: ${response.statusCode} ${response.body}');
    }

    final payload = jsonDecode(response.body) as Map<String, dynamic>;
    return ConsumerCapabilities.fromApiResponse(payload);
  }
}
