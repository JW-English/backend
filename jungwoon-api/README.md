# jungwoon-api

정운영어 백엔드. Spring Boot 4.1 / Java 21 / PostgreSQL 16 / Redis.

## 모듈 구조

| 모듈 | 역할 |
|---|---|
| `api` | Controller, DTO, 보안 설정, Flyway 마이그레이션 (실행 모듈) |
| `domain` | Entity, Repository, 도메인 서비스 |
| `infra` | R2(S3 호환) 스토리지, Redis, 소셜 로그인 API, Expo Push |
| `common` | 예외, 에러 코드, 유틸 (프레임워크 의존 최소) |

## 로컬 실행

```bash
docker compose up -d          # Postgres 5433, Redis 6380
./gradlew :api:bootRun        # 기본 프로파일 local
```

호스트 포트가 5433/6380 인 이유는 5432/6379 를 다른 프로젝트가 점유하고 있어서다.
바꾸려면 `POSTGRES_PORT` / `REDIS_PORT` 환경변수를 준다.

확인:

```bash
curl localhost:8080/actuator/health     # {"status":"UP"}
curl localhost:8080/api/public/ping     # 앱 관통 확인용
open http://localhost:8080/swagger-ui.html
```

## 테스트

```bash
./gradlew build     # Testcontainers 로 실제 Postgres/Redis 를 띄운다 (Docker 필요)
```

통합 테스트는 Flyway 마이그레이션을 적용한 실제 DB에 대해 Hibernate `validate` 를 수행한다.
**스키마와 엔티티가 어긋나면 테스트가 깨진다** — 이게 `ddl-auto: validate` 를 쓰는 이유다.

## 스키마 변경 규칙

1. `api/src/main/resources/db/migration/V{n}__{설명}.sql` 추가 (기존 파일 수정 금지)
2. 엔티티 수정
3. `./gradlew :api:test` 로 검증

`ddl-auto` 는 영구히 `validate` 다. `update` 로 바꾸지 않는다.

## 설정 / 비밀 값

- 로컬 기본값은 `application.yml` 에 있고, 운영 값은 전부 환경변수로 주입한다 (`.env.example` 참고).
- JWT 시크릿·소셜 Client Secret·DB 비밀번호는 절대 커밋하지 않는다.

## 다음 작업 (P1 — 인증)

- [ ] `JwtTokenProvider` / `JwtAuthenticationFilter` → `SecurityConfig` 에 등록
- [ ] `OAuth2UserInfoClient` 인터페이스 + KAKAO 구현체 1개 (나머지는 복제)
- [ ] Refresh Token Redis 저장 + Rotation + 재사용 감지
- [ ] 반 코드 입력 온보딩
- [ ] 학생 A → 학생 B 리소스 접근 403 통합 테스트
