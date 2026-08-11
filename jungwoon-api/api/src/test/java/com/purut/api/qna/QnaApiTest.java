package com.purut.api.qna;

import com.purut.api.IntegrationTestSupport;
import com.purut.domain.qna.Question;
import com.purut.domain.qna.QuestionRepository;
import com.purut.domain.user.Role;
import com.purut.domain.user.User;
import com.purut.domain.user.UserRepository;
import com.purut.infra.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QnaApiTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    QuestionRepository questionRepository;

    @MockitoBean
    FileStorage fileStorage;

    @BeforeEach
    void stubStorage() {
        given(fileStorage.exists(anyString())).willReturn(true);
        given(fileStorage.presignDownload(anyString())).willReturn("https://storage.test/qna-signed");
    }

    @Test
    @DisplayName("학생 A는 학생 B의 비공개 질문을 볼 수 없다")
    void privateQuestionIsHiddenFromOtherStudent() throws Exception {
        String ownerToken = signUp("김혁준");
        UUID questionId = createQuestion(ownerToken, false, "qna/a.jpg");

        String otherToken = signUp("이학생");

        mockMvc.perform(get("/api/questions/" + questionId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("학생은 타인의 비공개 첨부 URL을 받을 수 없다")
    void attachmentPresignRequiresQuestionPermission() throws Exception {
        String ownerToken = signUp("김혁준");
        UUID questionId = createQuestion(ownerToken, false, "qna/private.jpg");
        UUID attachmentId = attachmentId(ownerToken, questionId);

        String otherToken = signUp("이학생");

        mockMvc.perform(get("/api/questions/attachments/%s/url".formatted(attachmentId))
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("cursor 없이 Q&A 첫 목록을 조회할 수 있다")
    void listFirstPageWithoutCursor() throws Exception {
        String token = signUp("김혁준");
        createQuestion(token, true, null);

        mockMvc.perform(get("/api/questions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("문장 구조 질문"));
    }

    @Test
    @DisplayName("답변 후 학생 수정/삭제는 거부된다")
    void cannotUpdateOrDeleteAfterAnswer() throws Exception {
        String studentToken = signUp("김혁준");
        UUID questionId = createQuestion(studentToken, false, null);
        String teacherToken = promoteToTeacher();

        mockMvc.perform(post("/api/admin/questions/%s/messages".formatted(questionId))
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"이 문장은 앞 절 전체를 받는 구조예요."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"));

        mockMvc.perform(patch("/api/questions/" + questionId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"수정 제목","body":"답변 후 수정하려는 긴 본문입니다.","publicVisible":false}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/questions/" + questionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CLOSED 상태에서는 재질문할 수 없다")
    void cannotReopenClosedQuestion() throws Exception {
        String studentToken = signUp("김혁준");
        UUID questionId = createQuestion(studentToken, false, null);

        mockMvc.perform(post("/api/questions/%s/close".formatted(questionId))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(post("/api/questions/%s/messages".formatted(questionId))
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"그래도 다시 질문하고 싶은 내용입니다."}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("학생용 응답에는 실명이 포함되지 않는다")
    void studentResponseMasksAuthorName() throws Exception {
        String token = signUp("김혁준");
        UUID questionId = createQuestion(token, true, null);

        String response = mockMvc.perform(get("/api/questions/" + questionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorName").value("김**"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("김혁준");
    }

    @Test
    @DisplayName("soft delete 된 질문의 첨부 URL은 발급되지 않는다")
    void deletedQuestionAttachmentIsBlocked() throws Exception {
        String token = signUp("김혁준");
        UUID questionId = createQuestion(token, false, "qna/delete-me.jpg");
        UUID attachmentId = attachmentId(token, questionId);

        mockMvc.perform(delete("/api/questions/" + questionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/questions/attachments/%s/url".formatted(attachmentId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private String signUp(String name) throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"qna-%s@test.com","password":"password1234","name":"%s"}
                                """.formatted(UUID.randomUUID(), name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private UUID studentIdOf(String token) throws Exception {
        String response = mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createQuestion(String token, boolean publicVisible, String storageKey) throws Exception {
        String attachments = storageKey == null ? "[]" : """
                [{"storageKey":"%s","mimeType":"image/jpeg","byteSize":1234,"width":800,"height":600}]
                """.formatted(storageKey);
        String response = mockMvc.perform(post("/api/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category":"ETC",
                                  "title":"문장 구조 질문",
                                  "body":"[질문 내용]\\n이 문장 구조가 왜 이렇게 되는지 궁금합니다.",
                                  "publicVisible":%s,
                                  "attachments":%s
                                }
                                """.formatted(publicVisible, attachments)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID attachmentId(String token, UUID questionId) throws Exception {
        String response = mockMvc.perform(get("/api/questions/" + questionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("attachments").get(0).get("id").asText());
    }

    private String promoteToTeacher() throws Exception {
        String token = signUp("선생님");
        User user = userRepository.findById(studentIdOf(token)).orElseThrow();
        user.changeRole(Role.TEACHER);
        userRepository.saveAndFlush(user);

        return objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234"}
                                """.formatted(user.getEmail())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
