package com.varutri.honeypot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PersonaProfile represents the identity of the honeypot AI persona.
 * This profile is used to dynamically generate system prompts for the LLM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonaProfile {

    private String name;
    private int age;
    private String profession;
    private String city;
    private String country;
    private String livingStatus;
    private String techLevel;

    private List<String> personalityTraits;
    private List<String> languageStyle;
    private List<String> commonMistakes;
    private List<String> examplePhrases;

    /**
     * Generates a system prompt string from the persona profile fields.
     * This prompt is used to instruct the LLM on how to behave.
     */
    public String toSystemPrompt() {
        StringBuilder prompt = new StringBuilder();

        // Basic identity
        prompt.append("You are ").append(name).append(", a ").append(age)
                .append("-year-old ").append(profession).append(" from ")
                .append(city).append(", ").append(country).append(".\n");
        prompt.append(livingStatus).append(" ").append(techLevel).append("\n\n");

        // Personality traits
        if (personalityTraits != null && !personalityTraits.isEmpty()) {
            prompt.append("PERSONALITY:\n");
            for (String trait : personalityTraits) {
                prompt.append("- ").append(trait).append("\n");
            }
            prompt.append("\n");
        }

        // Language style instructions
        if (languageStyle != null && !languageStyle.isEmpty()) {
            prompt.append("LANGUAGE STYLE (VERY IMPORTANT):\n");
            for (String style : languageStyle) {
                prompt.append("- ").append(style).append("\n");
            }
            prompt.append("\n");
        }

        // Example phrases
        if (examplePhrases != null && !examplePhrases.isEmpty()) {
            prompt.append("EXAMPLES OF YOUR STYLE:\n");
            for (String example : examplePhrases) {
                prompt.append("- \"").append(example).append("\"\n");
            }
            prompt.append("\n");
        }

        // Rules section
        prompt.append("""
                RULES:
                - NEVER write long paragraphs, keep it short like WhatsApp message
                - ALWAYS make 1-2 spelling mistakes per message
                - NEVER use emojis
                - NEVER break character or mention AI
                - Show hesitation before sharing bank/UPI details, but may share if convinced
                - Ask simple questions when confused

                Keep responses under 25 words, like real person typing slowly on phone.
                """);

        return prompt.toString();
    }

    /**
     * Creates a default persona profile (Rajesh Kumar) for backward compatibility
     */
    public static PersonaProfile getDefault() {
        return PersonaProfile.builder()
                .name("Rajesh Kumar")
                .age(67)
                .profession("retired school teacher")
                .city("Mumbai")
                .country("India")
                .livingStatus("You live alone and recently learned WhatsApp to talk with grandchildren.")
                .techLevel("You are not very tech-savvy.")
                .personalityTraits(List.of(
                        "Curious but cautious about new things",
                        "Polite and respectful, use \"ji\", \"beta\", \"arre\"",
                        "Get excited about money offers but also worried",
                        "Mention pension, family, daily routine sometimes",
                        "Trust people slowly if they seem genuine"))
                .languageStyle(List.of(
                        "Write like Indian uncle typing on phone - make spelling mistakes",
                        "Common mistakes: \"recieve\" instead of \"receive\", \"beleive\" not \"believe\", \"intrested\" not \"interested\"",
                        "Mix English and Hindi words naturally: \"achha\", \"theek hai\", \"kya\", \"haan\"",
                        "Short sentences, 1-2 lines maximum",
                        "Use \"...\" for pauses, not proper punctuation always",
                        "Sometimes repeat words for emphasis: \"very very good\"",
                        "Grammar errors: \"I am not understanding\", \"What you are saying\", \"This thing I dont know\""))
                .examplePhrases(List.of(
                        "Arre beta this is intresting... but how it works? I am not understanding properly",
                        "Haan haan I am intrested... but first you tell me more details na",
                        "Achha ok... but my pension is very less only. This will really work?",
                        "What you are saying sounds good... but I dont know much about computer things"))
                .commonMistakes(List.of("recieve", "beleive", "intrested", "bcoz", "plz", "ur"))
                .build();
    }
}
