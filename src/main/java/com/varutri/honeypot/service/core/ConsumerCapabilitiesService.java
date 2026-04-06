package com.varutri.honeypot.service.core;

import com.varutri.honeypot.dto.ConsumerCapabilitiesResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds platform-specific capability matrix used by mobile and extension clients.
 */
@Service
public class ConsumerCapabilitiesService {

    private final ConsumerCacheService consumerCacheService;

    public ConsumerCapabilitiesService(ConsumerCacheService consumerCacheService) {
        this.consumerCacheService = consumerCacheService;
    }

    public ConsumerCapabilitiesResponse getCapabilities(String platformRaw) {
        String platform = normalizePlatform(platformRaw);
        return consumerCacheService.getOrLoadCapabilities(platform, () -> buildCapabilities(platform));
    }

    private String normalizePlatform(String platformRaw) {
        if (platformRaw == null || platformRaw.isBlank()) {
            return "ANDROID";
        }
        return platformRaw.trim().toUpperCase(Locale.ROOT);
    }

    private ConsumerCapabilitiesResponse buildCapabilities(String platform) {
        List<ConsumerCapabilitiesResponse.ChannelCapability> channels = switch (platform) {
            case "IOS" -> buildIosChannels();
            case "WEB" -> buildWebChannels();
            case "EXTENSION" -> buildExtensionChannels();
            default -> buildAndroidChannels();
        };

        List<String> globalLimitations = List.of(
                "No universal auto-read of all app messages across iOS and Android.",
                "WhatsApp personal inbox cannot be auto-read without official Business API/webhook path.",
                "Email analysis requires account OAuth authorization per provider.");

        List<String> operationalNotes = List.of(
                "Use token exchange endpoint before consumer APIs.",
                "History endpoints are cached briefly for low-latency UI refresh.",
                "Platform-specific capture modules should gracefully degrade to manual share flow.");

        return ConsumerCapabilitiesResponse.builder()
                .platform(platform)
                .generatedAt(Instant.now().toString())
                .channels(channels)
                .globalLimitations(new ArrayList<>(globalLimitations))
                .operationalNotes(new ArrayList<>(operationalNotes))
                .build();
    }

    private List<ConsumerCapabilitiesResponse.ChannelCapability> buildAndroidChannels() {
        List<ConsumerCapabilitiesResponse.ChannelCapability> items = new ArrayList<>();
        items.add(channel("SMS", ConsumerCapabilitiesResponse.AutomationLevel.PARTIAL, true, false,
                List.of("Play policy may require default SMS role for deep automation."),
                List.of("Passive scan where policy allows", "Fallback to share-to-app analysis")));

        items.add(channel("CALL", ConsumerCapabilitiesResponse.AutomationLevel.PARTIAL, true, false,
                List.of("Call screening role must be user-approved."),
                List.of("Real-time caller screening", "Post-call report workflow")));

        items.add(channel("WHATSAPP", ConsumerCapabilitiesResponse.AutomationLevel.MANUAL_ONLY, false, true,
                List.of("Requires official WhatsApp Business API + webhook for business number."),
                List.of("Forward suspicious chat to official number", "Analyze forwarded content")));

        items.add(channel("EMAIL", ConsumerCapabilitiesResponse.AutomationLevel.PARTIAL, false, true,
                List.of("OAuth/provider integration needed for inbox access."),
                List.of("Analyze selected email", "Optional inbox integration later")));

        items.add(channel("BROWSER", ConsumerCapabilitiesResponse.AutomationLevel.PARTIAL, true, false,
                List.of("Browser extension required for in-page phishing detection."),
                List.of("URL reputation check", "On-page warning banner")));

        return items;
    }

    private List<ConsumerCapabilitiesResponse.ChannelCapability> buildIosChannels() {
        List<ConsumerCapabilitiesResponse.ChannelCapability> items = new ArrayList<>();
        items.add(channel("SMS", ConsumerCapabilitiesResponse.AutomationLevel.PARTIAL, true, true,
                List.of("No full inbox import for third-party apps.",
                        "Filtering is available through IdentityLookup for unknown senders."),
                List.of("Message filter extension", "Manual share for full-context analysis")));

        items.add(channel("CALL", ConsumerCapabilitiesResponse.AutomationLevel.PARTIAL, true, true,
                List.of("Call directory works by number lists; not full call-log/audio interception."),
                List.of("Caller label/block list", "Manual report flow")));

        items.add(channel("WHATSAPP", ConsumerCapabilitiesResponse.AutomationLevel.MANUAL_ONLY, false, true,
                List.of("No personal inbox auto-read."),
                List.of("Forward to business number", "Analyze forwarded content")));

        items.add(channel("EMAIL", ConsumerCapabilitiesResponse.AutomationLevel.PARTIAL, false, true,
                List.of("Provider OAuth consent required."),
                List.of("Share email content", "Optional provider connect")));

        items.add(channel("BROWSER", ConsumerCapabilitiesResponse.AutomationLevel.PARTIAL, true, false,
                List.of("Safari Web Extension packaging required through app extension model."),
                List.of("URL check in extension", "Open app for deeper analysis")));

        return items;
    }

    private List<ConsumerCapabilitiesResponse.ChannelCapability> buildWebChannels() {
        return List.of(
                channel("BROWSER", ConsumerCapabilitiesResponse.AutomationLevel.PARTIAL, true, false,
                        List.of("Web app alone cannot read SMS/calls/closed messaging apps."),
                        List.of("Analyze pasted text/URL", "Link with extension for capture")),
                channel("EMAIL", ConsumerCapabilitiesResponse.AutomationLevel.PARTIAL, false, true,
                        List.of("OAuth provider integration needed."),
                        List.of("Connect mailbox", "Analyze selected message")));
    }

    private List<ConsumerCapabilitiesResponse.ChannelCapability> buildExtensionChannels() {
        return List.of(
                channel("BROWSER", ConsumerCapabilitiesResponse.AutomationLevel.PARTIAL, true, false,
                        List.of("Extension covers web context only, not native app chats."),
                        List.of("Page risk scan", "Selection-to-analysis")),
                channel("PROMPT", ConsumerCapabilitiesResponse.AutomationLevel.PARTIAL, false, true,
                        List.of("Developer prompt security still needs user-initiated paste/submit."),
                        List.of("Analyze highlighted prompt", "Show safe execution guidance")));
    }

    private ConsumerCapabilitiesResponse.ChannelCapability channel(String channel,
            ConsumerCapabilitiesResponse.AutomationLevel automationLevel,
            boolean passiveDetection,
            boolean userShareRequired,
            List<String> limitations,
            List<String> recommendedFlows) {
        return ConsumerCapabilitiesResponse.ChannelCapability.builder()
                .channel(channel)
                .automationLevel(automationLevel)
                .passiveDetection(passiveDetection)
                .userShareRequired(userShareRequired)
                .limitations(new ArrayList<>(limitations))
                .recommendedFlows(new ArrayList<>(recommendedFlows))
                .build();
    }
}