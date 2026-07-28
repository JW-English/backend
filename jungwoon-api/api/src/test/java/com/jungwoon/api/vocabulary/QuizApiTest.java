package com.jungwoon.api.vocabulary;

import com.jungwoon.api.IntegrationTestSupport;
import com.jungwoon.domain.vocabulary.Word;
import com.jungwoon.domain.vocabulary.WordDay;
import com.jungwoon.domain.vocabulary.WordDayItem;
import com.jungwoon.domain.vocabulary.WordDayItemRepository;
import com.jungwoon.domain.vocabulary.WordDayRepository;
import com.jungwoon.domain.vocabulary.WordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuizApiTest extends IntegrationTestSupport {

    private static final AtomicInteger DAY_SEQ = new AtomicInteger(1000);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    WordRepository wordRepository;

    @Autowired
    WordDayRepository dayRepository;

    @Autowired
    WordDayItemRepository dayItemRepository;

    private String signUpAndOnboard(int grade) throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"quiz-%s@test.com","password":"password1234","name":"김학생"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(put("/api/me/onboarding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"김학생","grade":%d}
                                """.formatted(grade)))
                .andExpect(status().isOk());

        return token;
    }

    private WordDay createDay(int grade, LocalDate scheduledDate, int wordCount) {
        WordDay day = dayRepository.save(WordDay.builder()
                .grade(grade)
                .dayNo(DAY_SEQ.incrementAndGet())
                .scheduledDate(scheduledDate)
                .title("테스트 DAY")
                .build());

        List<WordDayItem> items = new ArrayList<>();
        for (int i = 0; i < wordCount; i++) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Word word = wordRepository.save(Word.builder()
                    .headword("word-" + suffix)
                    .meaningKo("뜻-" + suffix)
                    .exampleEn("This is %s.".formatted(suffix))
                    .level(1)
                    .build());
            items.add(new WordDayItem(day, word, i));
        }
        dayItemRepository.saveAll(items);
        return day;
    }

    private JsonNode startQuiz(String token, UUID dayId, int questionCount) throws Exception {
        String response = mockMvc.perform(post("/api/quiz/attempts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayId":"%s","questionCount":%d}
                                """.formatted(dayId, questionCount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    @Test
    @DisplayName("출제 응답에는 정답이 들어있지 않다")
    void questionsHideAnswer() throws Exception {
        String token = signUpAndOnboard(2);
        WordDay day = createDay(2, LocalDate.now().minusDays(1), 10);

        JsonNode attempt = startQuiz(token, day.getId(), 5);

        assertThat(attempt.get("questions")).hasSize(5);
        // 정답 인덱스가 어떤 형태로도 섞여 나가면 안 된다
        assertThat(attempt.toString()).doesNotContain("correctIndex");

        for (JsonNode question : attempt.get("questions")) {
            assertThat(question.get("choices")).hasSize(4);
            assertThat(question.has("prompt")).isTrue();
        }
    }

    @Test
    @DisplayName("서버가 채점한다 — 정답을 모른 채 찍으면 만점이 나오지 않는다")
    void serverGrades() throws Exception {
        String token = signUpAndOnboard(2);
        WordDay day = createDay(2, LocalDate.now().minusDays(1), 10);

        JsonNode attempt = startQuiz(token, day.getId(), 5);
        UUID attemptId = UUID.fromString(attempt.get("attemptId").asText());

        // 전부 0번으로 찍는다
        for (JsonNode question : attempt.get("questions")) {
            mockMvc.perform(post("/api/quiz/attempts/%s/answers".formatted(attemptId))
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"wordId":%d,"selectedIndex":0}
                                    """.formatted(question.get("wordId").asLong())))
                    .andExpect(status().isNoContent());
        }

        String result = mockMvc.perform(post("/api/quiz/attempts/%s/submit".formatted(attemptId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(5))
                .andReturn().getResponse().getContentAsString();

        JsonNode graded = objectMapper.readTree(result);

        // 채점 후에는 정답이 공개된다. 공개된 정답과 대조해 점수가 맞는지 확인한다
        long expected = 0;
        for (JsonNode review : graded.get("reviews")) {
            assertThat(review.has("correctIndex")).isTrue();
            if (review.get("selectedIndex").asInt() == review.get("correctIndex").asInt()) {
                expected++;
                assertThat(review.get("correct").asBoolean()).isTrue();
            } else {
                assertThat(review.get("correct").asBoolean()).isFalse();
            }
        }
        assertThat(graded.get("correctCount").asInt()).isEqualTo((int) expected);
        // "찍으면 만점이 안 나온다"는 단언은 넣지 않는다 — 1/1024 확률로 실패하는 플래키 테스트가 된다

        // score 는 DB 생성 컬럼이다. 채점 직후 응답에 낡은 값(0)이 실리는 사고가 있었다
        assertThat(graded.get("score").asDouble())
                .isEqualTo(expected * 100.0 / graded.get("totalCount").asInt());
    }

    @Test
    @DisplayName("이미 제출한 시험은 다시 제출할 수 없다")
    void cannotSubmitTwice() throws Exception {
        String token = signUpAndOnboard(2);
        WordDay day = createDay(2, LocalDate.now().minusDays(1), 10);
        UUID attemptId = UUID.fromString(startQuiz(token, day.getId(), 4).get("attemptId").asText());

        mockMvc.perform(post("/api/quiz/attempts/%s/submit".formatted(attemptId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/quiz/attempts/%s/submit".formatted(attemptId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_ALREADY_FINISHED"));
    }

    @Test
    @DisplayName("아직 열리지 않은 DAY 는 조회도 응시도 막힌다")
    void unopenedDayIsBlocked() throws Exception {
        String token = signUpAndOnboard(2);
        WordDay future = createDay(2, LocalDate.now().plusDays(7), 10);

        mockMvc.perform(get("/api/vocabulary/days/" + future.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DAY_NOT_OPENED"));

        mockMvc.perform(post("/api/quiz/attempts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayId":"%s"}
                                """.formatted(future.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("남의 응시 기록은 조회도 제출도 할 수 없다 (IDOR)")
    void cannotTouchOthersAttempt() throws Exception {
        String victim = signUpAndOnboard(2);
        WordDay day = createDay(2, LocalDate.now().minusDays(1), 10);
        UUID attemptId = UUID.fromString(startQuiz(victim, day.getId(), 4).get("attemptId").asText());

        String attacker = signUpAndOnboard(2);

        mockMvc.perform(get("/api/quiz/attempts/" + attemptId)
                        .header("Authorization", "Bearer " + attacker))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/quiz/attempts/%s/submit".formatted(attemptId))
                        .header("Authorization", "Bearer " + attacker))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("틀린 단어는 오답노트에 쌓이고, 맞힌 단어는 들어가지 않는다")
    void wrongNotesAccumulate() throws Exception {
        String token = signUpAndOnboard(2);
        WordDay day = createDay(2, LocalDate.now().minusDays(1), 10);

        JsonNode attempt = startQuiz(token, day.getId(), 5);
        UUID attemptId = UUID.fromString(attempt.get("attemptId").asText());

        // 답을 하나도 고르지 않고 제출하면 전부 오답 처리된다
        mockMvc.perform(post("/api/quiz/attempts/%s/submit".formatted(attemptId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctCount").value(0));

        String notes = mockMvc.perform(get("/api/quiz/wrong-notes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(notes)).hasSize(5);
    }

    @Test
    @DisplayName("학년 설정 전에는 DAY 목록을 볼 수 없다")
    void requiresGrade() throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nograde-%s@test.com","password":"password1234","name":"김학생"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(get("/api/vocabulary/days").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
