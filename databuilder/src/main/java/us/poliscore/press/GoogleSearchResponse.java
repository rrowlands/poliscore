package us.poliscore.press;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * A simplified representation of the Google search response,
 * focusing on all known properties within the "items" field
 * and ignoring everything else.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleSearchResponse {

    private List<Item> items;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String kind;
        private String title;
        private String htmlTitle;
        private String link;
        private String displayLink;
        private String snippet;
        private String htmlSnippet;
        private String formattedUrl;
        private String htmlFormattedUrl;
        private PageMap pagemap;
        private String mime;
        private String fileFormat;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageMap {

        @JsonProperty("cse_thumbnail")
        private List<CseThumbnail> cseThumbnail;

        @JsonProperty("metatags")
        private List<Metatag> metatags;

        @JsonProperty("cse_image")
        private List<CseImage> cseImage;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CseThumbnail {
        private String src;
        private String width;
        private String height;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CseImage {
        private String src;
    }

    /**
     * We know 'metatags' is an array of objects with many possible fields.
     * We don't care about them all, but we must acknowledge this class to avoid errors.
     * We'll ignore unknown fields so no need to specify all.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metatag {
        // You can add specific known fields if you like, or leave it empty.
    }
}