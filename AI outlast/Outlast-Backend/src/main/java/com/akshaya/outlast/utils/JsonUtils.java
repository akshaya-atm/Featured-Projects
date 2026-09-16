package com.akshaya.outlast.utils;

public class JsonUtils {

    /**
     * Extracts the outermost JSON object substring (from the first '{' to the last '}') 
     * from the raw LLM response. If no matching braces are found, returns the trimmed raw response.
     */
    public static String extractJson(String response) {
        if (response == null) {
            return "";
        }
        // First, strip any <think>...</think> blocks to avoid processing template examples inside thoughts
        String withoutThinking = response.replaceAll("(?s)<think>.*?</think>", "").trim();

        int firstCurly = withoutThinking.indexOf('{');
        int lastCurly = withoutThinking.lastIndexOf('}');
        int firstSquare = withoutThinking.indexOf('[');
        int lastSquare = withoutThinking.lastIndexOf(']');

        boolean hasCurly = firstCurly != -1 && lastCurly != -1 && lastCurly > firstCurly;
        boolean hasSquare = firstSquare != -1 && lastSquare != -1 && lastSquare > firstSquare;

        String extracted = withoutThinking.trim();
        if (hasCurly && hasSquare) {
            if (firstSquare < firstCurly) {
                extracted = withoutThinking.substring(firstSquare, lastSquare + 1);
            } else {
                extracted = withoutThinking.substring(firstCurly, lastCurly + 1);
            }
        } else if (hasSquare) {
            extracted = withoutThinking.substring(firstSquare, lastSquare + 1);
        } else if (hasCurly) {
            extracted = withoutThinking.substring(firstCurly, lastCurly + 1);
        }
        return repairJson(extracted);
    }

    /**
     * Sanitizes common LLM JSON syntax errors like single-line/multi-line comments 
     * and trailing commas before closing braces/brackets.
     */
    public static String repairJson(String jsonStr) {
        if (jsonStr == null) return "";
        // Remove single-line comments: // ...
        String cleaned = jsonStr.replaceAll("(?m)//.*$", "");
        // Remove multi-line comments: /* ... */
        cleaned = cleaned.replaceAll("(?s)/\\*.*?\\*/", "");
        // Remove trailing commas before } or ]
        cleaned = cleaned.replaceAll(",(\\s*[\\}\\]])", "$1");
        return cleaned.trim();
    }
}
