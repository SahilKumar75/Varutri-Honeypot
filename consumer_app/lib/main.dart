import 'package:flutter/material.dart';

import 'src/app_state.dart';
import 'src/consumer_app.dart';

void main() {
  final state = ConsumerAppState(baseUrl: 'http://10.0.2.2:8080');
  runApp(VarutriConsumerApp(state: state));
}
