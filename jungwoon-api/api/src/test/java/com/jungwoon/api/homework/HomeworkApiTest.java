package com.jungwoon.api.homework;

import com.jungwoon.api.IntegrationTestSupport;
import com.jungwoon.domain.homework.Assignment;
import com.jungwoon.domain.homework.AssignmentRepository;
import com.jungwoon.domain.user.Role;
import com.jungwoon.domain.user.User;
import com.jungwoon.domain.user.UserRepository;
import com.jungwoon.infra.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HomeworkApiTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AssignmentRepository assignmentRepository;

    /** 스토리지는 이 테스트의 관심사가 아니다 — 업로드 여부만 통과시킨다. */
    @MockitoBean
    FileStorage fileStorage;

    @BeforeEach
    void stubStorage() {
        given(fileStorage.exists(anyString())).willReturn(true);
        given(fileStorage.presignDownload(anyString())).willReturn("https://storage.test/signed");
    }

    private String signUp() throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"hw-%s@test.com","password":"password1234","name":"김학생"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private UUID studentIdOf(String token) throws Exception {
        String response = mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private Assignment createAssignment(UUID studentId, LocalDate dueDate) {
        User student = userRepository.findById(studentId).orElseThrow();
        // 부여일은 마감일보다 뒤일 수 없다 (DB 제약). 지난 마감을 만들 때는 부여일도 당겨준다
        LocalDate assignedDate = dueDate.isBefore(LocalDate.now()) ? dueDate.minusDays(2) : LocalDate.now();
        return assignmentRepository.save(Assignment.builder()
                .student(student)
                .title("Day 1 단어 외우기")
                .assignedDate(assignedDate)
                .dueDate(dueDate)
                .build());
    }

    @Test
    @DisplayName("제출하면 상태가 SUBMITTED 로 바뀌고 사진이 함께 내려온다")
    void submit() throws Exception {
        String token = signUp();
        Assignment assignment = createAssignment(studentIdOf(token), LocalDate.now().plusDays(3));

        mockMvc.perform(post("/api/homework/assignments/%s/submission".formatted(assignment.getId()))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"images":[{"storageKey":"homework/a.jpg","width":1600,"height":1200}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submission.images.length()").value(1))
                .andExpect(jsonPath("$.submission.images[0].url").value("https://storage.test/signed"));
    }

    @Test
    @DisplayName("재제출하면 이전 사진이 교체된다")
    void resubmitReplacesImages() throws Exception {
        String token = signUp();
        Assignment assignment = createAssignment(studentIdOf(token), LocalDate.now().plusDays(3));
        String url = "/api/homework/assignments/%s/submission".formatted(assignment.getId());

        mockMvc.perform(post(url).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"images":[{"storageKey":"homework/a.jpg"},{"storageKey":"homework/b.jpg"}]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post(url).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"images":[{"storageKey":"homework/c.jpg"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.images.length()").value(1));
    }

    @Test
    @DisplayName("마감된 숙제는 서버가 제출을 거부한다")
    void submitAfterDueDate() throws Exception {
        String token = signUp();
        Assignment assignment = createAssignment(studentIdOf(token), LocalDate.now().minusDays(1));

        mockMvc.perform(post("/api/homework/assignments/%s/submission".formatted(assignment.getId()))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"images":[{"storageKey":"homework/a.jpg"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_CLOSED"));
    }

    @Test
    @DisplayName("다른 학생의 숙제는 조회도 제출도 할 수 없다 (IDOR)")
    void cannotAccessOthersAssignment() throws Exception {
        String victimToken = signUp();
        Assignment victimAssignment = createAssignment(studentIdOf(victimToken), LocalDate.now().plusDays(3));

        String attackerToken = signUp();

        mockMvc.perform(get("/api/homework/assignments/" + victimAssignment.getId())
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/homework/assignments/%s/submission".formatted(victimAssignment.getId()))
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"images":[{"storageKey":"homework/evil.jpg"}]}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("목록에는 내 숙제와 전체 대상 숙제만 나온다")
    void listShowsOnlyMine() throws Exception {
        String otherToken = signUp();
        createAssignment(studentIdOf(otherToken), LocalDate.now().plusDays(1));

        String myToken = signUp();
        Assignment mine = createAssignment(studentIdOf(myToken), LocalDate.now().plusDays(1));

        String response = mockMvc.perform(get("/api/homework/assignments")
                        .header("Authorization", "Bearer " + myToken)
                        .param("from", LocalDate.now().minusDays(7).toString())
                        .param("to", LocalDate.now().plusDays(7).toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode items = objectMapper.readTree(response);
        for (JsonNode item : items) {
            if (!item.get("id").asText().equals(mine.getId().toString())) {
                throw new AssertionError("남의 숙제가 목록에 보인다: " + item);
            }
        }
    }

    @Test
    @DisplayName("학생은 선생님 API 에 접근할 수 없다")
    void studentCannotUseAdminApi() throws Exception {
        String token = signUp();

        mockMvc.perform(get("/api/admin/homework/submissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/homework/assignments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"내가 만든 숙제","dueDate":"2030-01-01"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("선생님은 제출물을 보고 첨삭할 수 있고, 학생 화면에 코멘트가 보인다")
    void teacherReviewFlow() throws Exception {
        String studentToken = signUp();
        UUID studentId = studentIdOf(studentToken);
        Assignment assignment = createAssignment(studentId, LocalDate.now().plusDays(3));

        mockMvc.perform(post("/api/homework/assignments/%s/submission".formatted(assignment.getId()))
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"images":[{"storageKey":"homework/a.jpg"}]}
                                """))
                .andExpect(status().isOk());

        String teacherToken = promoteToTeacher();

        String submissions = mockMvc.perform(get("/api/admin/homework/submissions")
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UUID submissionId = UUID.fromString(objectMapper.readTree(submissions).get(0).get("id").asText());

        mockMvc.perform(post("/api/admin/homework/submissions/%s/comments".formatted(submissionId))
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"3번 문제 다시 확인해 보세요","requestResubmit":false}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/homework/assignments/" + assignment.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"))
                .andExpect(jsonPath("$.submission.comments[0].body").value("3번 문제 다시 확인해 보세요"))
                .andExpect(jsonPath("$.submission.comments[0].fromTeacher").value(true));
    }

    /** 선생님 승급은 API 로 열지 않는다 — 테스트에서는 DB 를 직접 바꾼다. */
    private String promoteToTeacher() throws Exception {
        String token = signUp();
        User user = userRepository.findById(studentIdOf(token)).orElseThrow();
        user.changeRole(Role.TEACHER);
        userRepository.saveAndFlush(user);

        // 역할은 토큰 클레임에 들어가므로 승급 후 발급된 토큰이어야 한다
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
