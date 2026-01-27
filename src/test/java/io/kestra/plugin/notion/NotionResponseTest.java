package io.kestra.plugin.notion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class NotionResponseTest {

    @Test
    void testResultsMappingToChildren() throws Exception {
        String json = "{" +
            "\"results\":[{" +
                "\"id\":\"block-1\"," +
                "\"type\":\"paragraph\"," +
                "\"paragraph\":{\"rich_text\":[{\"plain_text\":\"Hello\"}]}" +
            "}]," +
            "\"has_more\":false," +
            "\"next_cursor\":null" +
            "}";

        ObjectMapper mapper = new ObjectMapper();
        NotionResponse response = mapper.readValue(json, NotionResponse.class);

        assertThat(response.getChildren(), notNullValue());
        assertThat(response.getChildren(), hasSize(1));
        assertThat(response.getChildren().getFirst().get("id"), equalTo("block-1"));
    }
}
