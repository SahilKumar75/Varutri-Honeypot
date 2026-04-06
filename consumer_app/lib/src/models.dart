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
