class ConsumerAuthToken {
  ConsumerAuthToken({
    required this.tokenType,
    required this.accessToken,
    required this.expiresAtEpochMs,
    required this.expiresInSeconds,
    required this.appId,
    required this.deviceId,
    required this.platform,
  });

  final String tokenType;
  final String accessToken;
  final int expiresAtEpochMs;
  final int expiresInSeconds;
  final String appId;
  final String deviceId;
  final String platform;

  bool get isExpired => DateTime.now().millisecondsSinceEpoch >= expiresAtEpochMs;

  factory ConsumerAuthToken.fromApiResponse(Map<String, dynamic> json) {
    final data = (json['data'] as Map<String, dynamic>? ?? <String, dynamic>{});
    return ConsumerAuthToken(
      tokenType: (data['tokenType'] ?? '').toString(),
      accessToken: (data['accessToken'] ?? '').toString(),
      expiresAtEpochMs: ((data['expiresAtEpochMs'] ?? 0) as num).toInt(),
      expiresInSeconds: ((data['expiresInSeconds'] ?? 0) as num).toInt(),
      appId: (data['appId'] ?? '').toString(),
      deviceId: (data['deviceId'] ?? '').toString(),
      platform: (data['platform'] ?? '').toString(),
    );
  }
}

class ConsumerAnalysis {
  ConsumerAnalysis({
    required this.sessionId,
    required this.channel,
    required this.verdict,
    required this.threatLevel,
    required this.threatScore,
    required this.recommendedActions,
    required this.platformNotes,
  });

  final String sessionId;
  final String channel;
  final String verdict;
  final String threatLevel;
  final double threatScore;
  final List<String> recommendedActions;
  final List<String> platformNotes;

  factory ConsumerAnalysis.fromApiResponse(Map<String, dynamic> json) {
    final data = (json['data'] as Map<String, dynamic>? ?? <String, dynamic>{});
    final threat = (data['threatAssessment'] as Map<String, dynamic>? ?? <String, dynamic>{});

    return ConsumerAnalysis(
      sessionId: (data['sessionId'] ?? '').toString(),
      channel: (data['channel'] ?? '').toString(),
      verdict: (data['verdict'] ?? '').toString(),
      threatLevel: (threat['threatLevel'] ?? '').toString(),
      threatScore: ((threat['threatScore'] ?? 0) as num).toDouble(),
      recommendedActions: ((data['recommendedActions'] as List<dynamic>? ?? const <dynamic>[])
          .map((e) => e.toString())
          .toList()),
      platformNotes: ((data['platformNotes'] as List<dynamic>? ?? const <dynamic>[])
          .map((e) => e.toString())
          .toList()),
    );
  }
}

class ConsumerHistoryItem {
  ConsumerHistoryItem({
    required this.sessionId,
    required this.channel,
    required this.verdict,
    required this.threatLevel,
    required this.threatScore,
    required this.lastUpdated,
  });

  final String sessionId;
  final String channel;
  final String verdict;
  final String threatLevel;
  final double threatScore;
  final String lastUpdated;

  factory ConsumerHistoryItem.fromJson(Map<String, dynamic> json) {
    return ConsumerHistoryItem(
      sessionId: (json['sessionId'] ?? '').toString(),
      channel: (json['channel'] ?? '').toString(),
      verdict: (json['verdict'] ?? '').toString(),
      threatLevel: (json['threatLevel'] ?? '').toString(),
      threatScore: ((json['threatScore'] ?? 0) as num).toDouble(),
      lastUpdated: (json['lastUpdated'] ?? '').toString(),
    );
  }
}

class ConsumerHistoryDetail {
  ConsumerHistoryDetail({
    required this.sessionId,
    required this.channel,
    required this.verdict,
    required this.threatLevel,
    required this.threatScore,
    required this.firstSeen,
    required this.lastUpdated,
    required this.extractedInfo,
    required this.conversation,
  });

  final String sessionId;
  final String channel;
  final String verdict;
  final String threatLevel;
  final double threatScore;
  final String firstSeen;
  final String lastUpdated;
  final ConsumerExtractedInfo extractedInfo;
  final List<ConsumerConversationEntry> conversation;

  factory ConsumerHistoryDetail.fromApiResponse(Map<String, dynamic> json) {
    final data = (json['data'] as Map<String, dynamic>? ?? <String, dynamic>{});
    return ConsumerHistoryDetail(
      sessionId: (data['sessionId'] ?? '').toString(),
      channel: (data['channel'] ?? '').toString(),
      verdict: (data['verdict'] ?? '').toString(),
      threatLevel: (data['threatLevel'] ?? '').toString(),
      threatScore: ((data['threatScore'] ?? 0) as num).toDouble(),
      firstSeen: (data['firstSeen'] ?? '').toString(),
      lastUpdated: (data['lastUpdated'] ?? '').toString(),
      extractedInfo: ConsumerExtractedInfo.fromJson(
        data['extractedInfo'] as Map<String, dynamic>? ?? <String, dynamic>{},
      ),
      conversation: ((data['conversation'] as List<dynamic>? ?? const <dynamic>[])
          .map((e) => ConsumerConversationEntry.fromJson(e as Map<String, dynamic>))
          .toList()),
    );
  }
}

class ConsumerExtractedInfo {
  ConsumerExtractedInfo({
    required this.upiIds,
    required this.bankAccountNumbers,
    required this.ifscCodes,
    required this.phoneNumbers,
    required this.urls,
    required this.emails,
    required this.suspiciousKeywords,
  });

  final List<String> upiIds;
  final List<String> bankAccountNumbers;
  final List<String> ifscCodes;
  final List<String> phoneNumbers;
  final List<String> urls;
  final List<String> emails;
  final List<String> suspiciousKeywords;

  bool get hasIndicators =>
      upiIds.isNotEmpty ||
      bankAccountNumbers.isNotEmpty ||
      ifscCodes.isNotEmpty ||
      phoneNumbers.isNotEmpty ||
      urls.isNotEmpty ||
      emails.isNotEmpty ||
      suspiciousKeywords.isNotEmpty;

  factory ConsumerExtractedInfo.fromJson(Map<String, dynamic> json) {
    return ConsumerExtractedInfo(
      upiIds: _list(json['upiIds']),
      bankAccountNumbers: _list(json['bankAccountNumbers']),
      ifscCodes: _list(json['ifscCodes']),
      phoneNumbers: _list(json['phoneNumbers']),
      urls: _list(json['urls']),
      emails: _list(json['emails']),
      suspiciousKeywords: _list(json['suspiciousKeywords']),
    );
  }

  static List<String> _list(dynamic value) {
    return (value as List<dynamic>? ?? const <dynamic>[]).map((e) => e.toString()).toList();
  }
}

class ConsumerConversationEntry {
  ConsumerConversationEntry({
    required this.sender,
    required this.text,
    required this.timestamp,
  });

  final String sender;
  final String text;
  final int timestamp;

  factory ConsumerConversationEntry.fromJson(Map<String, dynamic> json) {
    return ConsumerConversationEntry(
      sender: (json['sender'] ?? '').toString(),
      text: (json['text'] ?? '').toString(),
      timestamp: ((json['timestamp'] ?? 0) as num).toInt(),
    );
  }
}

class ConsumerCapabilities {
  ConsumerCapabilities({
    required this.platform,
    required this.generatedAt,
    required this.channels,
    required this.globalLimitations,
    required this.operationalNotes,
  });

  final String platform;
  final String generatedAt;
  final List<ChannelCapability> channels;
  final List<String> globalLimitations;
  final List<String> operationalNotes;

  factory ConsumerCapabilities.fromApiResponse(Map<String, dynamic> json) {
    final data = (json['data'] as Map<String, dynamic>? ?? <String, dynamic>{});
    final channels = ((data['channels'] as List<dynamic>? ?? const <dynamic>[])
            .map((item) => ChannelCapability.fromJson(item as Map<String, dynamic>))
            .toList());

    return ConsumerCapabilities(
      platform: (data['platform'] ?? '').toString(),
      generatedAt: (data['generatedAt'] ?? '').toString(),
      channels: channels,
      globalLimitations: ((data['globalLimitations'] as List<dynamic>? ?? const <dynamic>[])
          .map((e) => e.toString())
          .toList()),
      operationalNotes: ((data['operationalNotes'] as List<dynamic>? ?? const <dynamic>[])
          .map((e) => e.toString())
          .toList()),
    );
  }
}

class ChannelCapability {
  ChannelCapability({
    required this.channel,
    required this.automationLevel,
    required this.passiveDetection,
    required this.userShareRequired,
    required this.limitations,
    required this.recommendedFlows,
  });

  final String channel;
  final String automationLevel;
  final bool passiveDetection;
  final bool userShareRequired;
  final List<String> limitations;
  final List<String> recommendedFlows;

  factory ChannelCapability.fromJson(Map<String, dynamic> json) {
    return ChannelCapability(
      channel: (json['channel'] ?? '').toString(),
      automationLevel: (json['automationLevel'] ?? '').toString(),
      passiveDetection: (json['passiveDetection'] ?? false) == true,
      userShareRequired: (json['userShareRequired'] ?? false) == true,
      limitations: ((json['limitations'] as List<dynamic>? ?? const <dynamic>[])
          .map((e) => e.toString())
          .toList()),
      recommendedFlows: ((json['recommendedFlows'] as List<dynamic>? ?? const <dynamic>[])
          .map((e) => e.toString())
          .toList()),
    );
  }
}
