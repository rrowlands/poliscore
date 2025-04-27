package us.poliscore.press;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;
import us.poliscore.model.InterpretationOrigin;

public class RedditFetcher {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    
    public static void main(String[] args) {
    	var origin = new InterpretationOrigin("https://www.reddit.com/r/NeutralPolitics/comments/1jawsml/what_are_the_pros_and_cons_of_voting_for_hr1968", "Reddit");
		System.out.println(RedditFetcher.fetch(origin));
	}

    @SneakyThrows
    public static String fetch(InterpretationOrigin origin) {
        return fetch(origin, 100, 100);
    }

    @SneakyThrows
    public static String fetch(InterpretationOrigin origin, int numComments, int maxDepth) {
    	String redditUrl = origin.getUrl();
        String jsonUrl = redditUrl.endsWith("/") ? redditUrl + ".json" : redditUrl + "/.json";

        URI uri = URI.create(jsonUrl);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; PoliScore/1.0)");

        try (InputStream is = conn.getInputStream(); Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {
            String json = scanner.useDelimiter("\\A").next();

            JsonNode root = MAPPER.readTree(json);
            JsonNode postData = root.get(0).get("data").get("children").get(0).get("data");
            JsonNode commentsData = root.get(1).get("data").get("children");

            StringBuilder output = new StringBuilder();
            output.append("The following is a conversation thread scraped from Reddit. The right arrows at the start of each comment '>' indicate how nested the comment is within the current comment chain. If the comment does not start with any right arrows then it indicates the start of a comment chain.\n\n");

//            output.append("Thread Title: " + origin.getTitle() + "\n\n");
            
            // Add OP
            output.append(formatPost(postData));

            // Add Top N Comment Chains
            List<JsonNode> topComments = new ArrayList<>();
            commentsData.forEach(node -> {
                if (node.get("kind").asText().equals("t1")) {
                    topComments.add(node.get("data"));
                }
            });

            topComments.stream()
                    .sorted(Comparator.comparingInt((JsonNode c) -> c.path("score").asInt()).reversed())
                    .limit(numComments)
                    .forEach(comment -> output.append(formatTopCommentChain(comment, maxDepth)));

            return output.toString();
        }
    }

    private static String formatPost(JsonNode postData) {
        String author = postData.path("author").asText("[deleted]");
        long createdUtc = postData.path("created_utc").asLong(0);
        String timestamp = FORMATTER.format(Instant.ofEpochSecond(createdUtc));
        String selftext = postData.path("selftext").asText("").trim();
        String title = postData.path("title").asText("").trim();

        return String.format("(OP) %s (%s)\n%s\n%s\n\n", author, timestamp, title, selftext);
    }

    private static String formatTopCommentChain(JsonNode commentData, int maxDepth) {
        StringBuilder sb = new StringBuilder();

        JsonNode current = commentData;
        int depth = 0;

        while (current != null && depth < maxDepth) {
            String author = current.path("author").asText("[deleted]");
            long createdUtc = current.path("created_utc").asLong(0);
            String timestamp = FORMATTER.format(Instant.ofEpochSecond(createdUtc));
            String body = current.path("body").asText("").trim();

            // Add depth arrows
            String arrows = ">".repeat(depth);

            sb.append(String.format("%s%s (%s)\n%s\n\n", arrows.isEmpty() ? "" : arrows + " ", author, timestamp, body));

            JsonNode replies = current.path("replies");
            if (replies.isMissingNode() || replies.isNull()) {
                break;
            }

            JsonNode children = replies.path("data").path("children");
            if (!children.isArray() || children.isEmpty()) {
                break;
            }

            JsonNode bestReply = null;
            int bestScore = Integer.MIN_VALUE;
            for (JsonNode child : children) {
                if (child.path("kind").asText().equals("t1")) {
                    JsonNode childData = child.path("data");
                    int score = childData.path("score").asInt(0);
                    if (score > bestScore) {
                        bestScore = score;
                        bestReply = childData;
                    }
                }
            }

            current = bestReply;
            depth++;
        }

        return sb.toString();
    }

}

