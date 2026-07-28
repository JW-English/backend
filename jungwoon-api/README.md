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

## 쿼리 관찰 (개발)

`local` 프로파일은 p6spy 를 거쳐 DB 에 붙는다. 요청이 끝나면 쿼리 수가 한 줄로 찍힌다.

```
GET /api/listening/exams → 3 queries, 4ms
GET /api/homework/assignments → 27 queries, 41ms  ⚠️ N+1 의심
```

**쿼리 수가 데이터 양에 따라 늘어나면 N+1 이다.** 화면을 추가한 뒤 한 번씩 확인한다.
p6spy 는 `compileOnly + developmentOnly` 라 운영 jar 에 들어가지 않는다.

## 미디어 스토리지 (MinIO → R2)

로컬은 MinIO, 운영은 Cloudflare R2 다. 코드는 AWS SDK 하나로 같고 **설정만 바뀐다.**

```bash
STORAGE_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
STORAGE_REGION=auto            # R2 는 리전 개념이 없다
STORAGE_BUCKET=jungwoon-media
STORAGE_ACCESS_KEY=...         # R2 API 토큰의 Access Key ID
STORAGE_SECRET_KEY=...         # Secret Access Key
STORAGE_PATH_STYLE=true
```

R2 를 고른 이유는 **egress(전송)가 무료**이기 때문이다. 듣기 음원은 반복 재생되는
콘텐츠라 전송량이 저장량보다 훨씬 크다. S3 는 GB당 $0.09 가 붙는다.

주의: 브라우저(관리자 웹)에서 미디어를 직접 재생하려면 **R2 버킷에 CORS 규칙**이 필요하다.
네이티브 앱은 CORS 대상이 아니라 설정 없이도 동작한다.

## 다음 작업 (P1 — 인증)

- [ ] `JwtTokenProvider` / `JwtAuthenticationFilter` → `SecurityConfig` 에 등록
- [ ] `OAuth2UserInfoClient` 인터페이스 + KAKAO 구현체 1개 (나머지는 복제)
- [ ] Refresh Token Redis 저장 + Rotation + 재사용 감지
- [ ] 반 코드 입력 온보딩
- [ ] 학생 A → 학생 B 리소스 접근 403 통합 테스트
