package com.gamehub;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameHubIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateRoomJoinAndStartMonopolyGame() throws Exception {
        String hostToken = tokenForGuest("host-player");
        String guestToken = tokenForGuest("guest-player");

        String roomResponse = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gameType": "MONOPOLY",
                                  "roomType": "ONLINE",
                                  "visibility": "PUBLIC",
                                  "maxPlayers": 4
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameType").value("MONOPOLY"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode roomJson = objectMapper.readTree(roomResponse);
        String roomId = roomJson.get("id").asText();

        mockMvc.perform(post("/api/rooms/{roomId}/join", roomId)
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk());

        String sessionResponse = mockMvc.perform(post("/api/games/start")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomId": "%s"
                                }
                                """.formatted(roomId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameType").value("MONOPOLY"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = objectMapper.readTree(sessionResponse).get("sessionId").asText();

        mockMvc.perform(get("/api/games/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.state.phase").value("WAITING_FOR_ROLL"));
    }

    private String tokenForGuest(String username) throws Exception {
        String response = mockMvc.perform(post("/api/auth/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
