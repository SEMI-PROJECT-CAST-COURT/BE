package com.verdict.castcourt.global.infra.log.ai;

import com.verdict.castcourt.domain.category.entity.Category;
import com.verdict.castcourt.domain.evidencecard.dto.CardDraftRequestDTO;
import com.verdict.castcourt.domain.evidencecard.service.CardDraftService;
import com.verdict.castcourt.domain.penaltytier.dto.PenaltyDraftRequestDTO;
import com.verdict.castcourt.domain.penaltytier.service.PenaltyDraftService;
import com.verdict.castcourt.domain.speaking.ai.model.JudgeAi;
import com.verdict.castcourt.domain.speaking.ai.model.OpenAiTopicAi;
import com.verdict.castcourt.domain.speaking.ai.model.OpponentAi;
import com.verdict.castcourt.domain.speaking.ai.model.VerdictAi;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialTopic;
import com.verdict.castcourt.domain.work.dto.WorkSummaryDraftRequest;
import com.verdict.castcourt.domain.work.entity.Work;
import com.verdict.castcourt.domain.work.repository.WorkRepository;
import com.verdict.castcourt.domain.work.service.WorkSummaryDraftService;
import com.verdict.castcourt.domain.workcase.ai.model.OpenAiCaseDraftAi;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.AiUsage;
import com.verdict.castcourt.global.ai.OpenAiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/* AI 모델 비용 · 속도 비교 (실제 OpenAI 호출 · 실행할 때마다 토큰 비용이 든다)
 *  같은 입력으로 모든 AI 기능을 모델마다 ROUNDS 번씩 불러 토큰 · 걸린 시간을 표로 찍는다. 품질은 보지 않는다.
 *  실수로 돌지 않게 두 환경 변수가 모두 있어야 실행된다. (평소 ./gradlew test 에서는 건너뛴다)
 *      OPENAI_API_KEY=...  AI_BENCHMARK=true
 *  선택
 *      AI_BENCH_MODELS=gpt-6-luna,gpt-6-sol   비교할 모델 (기본값)
 *      AI_BENCH_ROUNDS=3                      기능마다 부를 횟수 (기본값)
 *  실행 : AI_BENCHMARK=true ./gradlew test --tests "*AiModelBenchmarkTest"
 *  reasoning-effort 는 application.yaml 과 같은 none 으로 고정한다. (모델끼리 조건을 맞춘다)
 * */
@Tag("ai-benchmark")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AI_BENCHMARK", matches = "true")
class AiModelBenchmarkTest {

    private static final List<String> MODELS = Arrays.stream(
                    System.getenv().getOrDefault("AI_BENCH_MODELS", "gpt-6-luna,gpt-6-sol").split(","))
            .map(String::strip).filter(model -> !model.isEmpty()).toList();
    private static final int ROUNDS = Integer.parseInt(System.getenv().getOrDefault("AI_BENCH_ROUNDS", "3"));

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /* ===== 같은 입력 (트루먼 쇼 사건) ===== */

    private static final Work WORK = new Work(new Category("영화", 1), "트루먼 쇼", "피터 위어", LocalDate.of(1998, 6, 5),
            "자신의 삶이 24시간 생중계되는 리얼리티 쇼라는 사실을 모른 채 살아온 남자의 이야기", "poster.png");

    private static final String QUESTION = "보호와 양육을 제공했더라도, 당사자 몰래 일상을 방송한 책임을 물을 수 있는가?";
    private static final String SUSPECT = "크리스토프";
    private static final String VICTIM = "트루먼 버뱅크";
    private static final String SUMMARY = "크리스토프는 트루먼이 방송 출연 사실을 모르는 상태에서 그의 일상을 촬영하고 공개한다. "
            + "생활 기반을 제공했다는 사정이 사생활 공개와 상업적 이용을 정당화할 수 있는지가 쟁점이다.";
    private static final String PROGRESS = "트루먼은 출생 이후 방송의 중심인물로 성장하지만, 촬영과 방송 사실을 알지 못한다. "
            + "가족과 친구 역할의 배우들이 그의 주변에서 생활하고, 숨겨진 카메라가 일상을 기록한다. "
            + "방송에는 상품 광고가 결합되며, 트루먼의 생활이 상업적 콘텐츠로 이용된다. "
            + "크리스토프는 자신이 만든 세계가 트루먼을 보호한다는 취지로 방송을 옹호한다.";

    /* 사건 등록 최소 조건과 같게 진영마다 3장 (EvidenceCardService : 진영별 3장 이상) */
    private static final CaseSnapshot SNAPSHOT = new CaseSnapshot(1, 1, WORK.getTitle(), QUESTION, SUSPECT, VICTIM, SUMMARY, PROGRESS,
            List.of(
                    new CaseSnapshot.Card(11, Side.PROSECUTION, "숨겨진 카메라",
                            "트루먼의 집과 직장 곳곳에 카메라가 설치되어 동의 없이 일상이 기록되었다.", 12),
                    new CaseSnapshot.Card(12, Side.PROSECUTION, "광고 수익",
                            "방송 중 배우들이 상품을 노출하며 트루먼의 생활을 광고 수단으로 이용했다.", 10),
                    new CaseSnapshot.Card(13, Side.PROSECUTION, "탈출 방해",
                            "트루먼이 섬을 떠나려 할 때마다 제작진이 사고와 공포를 연출해 이동을 막았다.", 14),
                    new CaseSnapshot.Card(21, Side.DEFENSE, "안전한 생활 기반",
                            "트루먼은 범죄와 빈곤 없는 환경에서 자랐고, 제작진은 그의 안전을 최우선으로 관리했다.", 8),
                    new CaseSnapshot.Card(22, Side.DEFENSE, "입양 계약",
                            "방송사는 법적 절차를 거쳐 트루먼을 입양했고, 출연은 그 계약에 따른 것이었다.", 9),
                    new CaseSnapshot.Card(23, Side.DEFENSE, "자유로운 선택",
                            "트루먼은 결국 스스로 문을 열고 나갈 수 있었고, 제작진은 마지막에 그를 막지 않았다.", 7)
            ),
            List.of(
                    new CaseSnapshot.Penalty(1, "무죄 · 방송 지속 허용"), new CaseSnapshot.Penalty(2, "무죄 · 시청자 사과문 게시"),
                    new CaseSnapshot.Penalty(3, "무죄 · 트루먼에게 방송 사실 고지"), new CaseSnapshot.Penalty(4, "유죄 · 방송 중단"),
                    new CaseSnapshot.Penalty(5, "유죄 · 수익 전액 배상"), new CaseSnapshot.Penalty(6, "유죄 · 제작 영구 금지")
            ));

    private static final String USER_SPEECH = "피고인은 트루먼의 동의 없이 집 안까지 카메라를 설치했습니다. "
            + "보호라는 명분이 있어도 사생활을 광고 수익에 이용한 책임은 사라지지 않습니다.";

    /* ===== 실행 ===== */

    @Test
    @DisplayName("모델별 · 기능별 토큰과 응답 시간을 비교한다")
    void compareCostAndSpeed() {

        Map<String, RecordingClient> clients = new LinkedHashMap<>();
        for (String model : MODELS) {
            RecordingClient client = new RecordingClient(model);
            for (int round = 1; round <= ROUNDS; round++) {
                runAllFeatures(client);
            }
            clients.put(model, client);
        }
        print(clients);
    }

    // 기능 하나가 실패해도 나머지는 계속 잰다 (실패 수는 표에 남는다)
    private void runAllFeatures(RecordingClient client) {

        WorkRepository workRepository = mock(WorkRepository.class);
        when(workRepository.findActiveById(any())).thenReturn(Optional.of(WORK));

        run(client, AiFeature.WORK_SUMMARY_DRAFT, () ->
                new WorkSummaryDraftService(client, MAPPER).generate(new WorkSummaryDraftRequest(WORK.getTitle(), WORK.getAuthor())));

        run(client, AiFeature.CASE_DRAFT, () ->
                new OpenAiCaseDraftAi(client).candidates(WORK, List.of()));

        run(client, AiFeature.EVIDENCE_CARD_DRAFT, () ->
                new CardDraftService(client, workRepository).generate(new CardDraftRequestDTO(
                        1, "PROSECUTION", QUESTION, SUSPECT, VICTIM, SUMMARY, PROGRESS, List.of(), null)));

        run(client, AiFeature.PENALTY_TIER_DRAFT, () ->
                new PenaltyDraftService(client, workRepository).generate(new PenaltyDraftRequestDTO(
                        1, QUESTION, SUSPECT, VICTIM, SUMMARY, PROGRESS, List.of())));

        run(client, AiFeature.TRIAL_TOPIC, () ->
                new OpenAiTopicAi(client).generate(SNAPSHOT, DRAWN_CARD_IDS));

        // 사용자가 변호인이면 첫 차례가 AI 검사
        run(client, AiFeature.TRIAL_OPPONENT, () ->
                new OpponentAi(client).speak(session(Side.DEFENSE), delta -> {
                }));

        // 사용자가 검사면 첫 차례가 사용자 발언 → 판정
        run(client, AiFeature.TRIAL_JUDGE, () ->
                new JudgeAi(client).judge(session(Side.PROSECUTION), USER_SPEECH));

        run(client, AiFeature.TRIAL_VERDICT, () ->
                new VerdictAi(client).summarize(finishedSession()));
    }

    private void run(RecordingClient client, AiFeature feature, Runnable call) {

        client.feature = feature;

        try {
            call.run();
        } catch (RuntimeException e) {
            client.failures.merge(feature, 1, Integer::sum);
            System.out.println("[benchmark] " + client.model + " " + feature + " 실패 : " + e.getMessage());
        }
    }

    /* 재판 시작과 같게 검사 카드 3장을 뽑고 (TrialService.drawCards · MAX_CARD_COUNT), 공방마다 대응 변호인 카드를 붙인다 */
    private static final List<Integer> DRAWN_CARD_IDS = List.of(11, 12, 13);
    private static final Map<Integer, TrialTopic> TOPICS = Map.of(
            11, new TrialTopic("동의 없는 촬영", 21),
            12, new TrialTopic("사생활의 상업적 이용", 22),
            13, new TrialTopic("이동의 자유 침해", 23)
    );

    private static TrialSession session(Side role) {

        return new TrialSession(
                "benchmark", 1, 1,
                role, TrialMode.CHICKEN, SNAPSHOT, DRAWN_CARD_IDS, TOPICS);
    }

    // 총평용 : 모든 차례를 같은 발언으로 채운 판
    private static TrialSession finishedSession() {

        TrialSession session = session(Side.PROSECUTION);

        while (!session.isFinished()) {
            session.record(USER_SPEECH, true, true, true, true, 10);
        }

        return session;
    }

    /* ===== 결과 표 ===== */

    private void print(Map<String, RecordingClient> clients) {

        System.out.println("\n===== AI 모델 비용 · 속도 비교 (기능마다 " + ROUNDS + "번 · 실패한 호출은 평균에서 빠짐) =====");
        System.out.printf("%-20s %-12s %5s %5s %8s %7s %8s %8s %8s%n",
                "feature", "model", "호출", "실패", "입력", "출력", "평균ms", "p50ms", "p95ms");

        for (AiFeature feature : AiFeature.values()) {
            for (RecordingClient client : clients.values()) {

                List<AiUsage> usages = client.usages.getOrDefault(feature, List.of());

                int failures = client.failures.getOrDefault(feature, 0);

                if (usages.isEmpty()) {
                    System.out.printf("%-20s %-12s %5d %5d %8s %7s %8s %8s %8s%n",
                            feature, client.model, 0, failures, "-", "-", "-", "-", "-");
                    continue;
                }

                List<Long> times = usages.stream().map(AiUsage::elapsedMs).sorted().toList();
                System.out.printf("%-20s %-12s %5d %5d %8.0f %7.0f %8.0f %8d %8d%n",
                        feature, client.model, usages.size(), failures,
                        usages.stream().mapToInt(AiUsage::inputTokens).average().orElse(0),
                        usages.stream().mapToInt(AiUsage::outputTokens).average().orElse(0),
                        times.stream().mapToLong(Long::longValue).average().orElse(0),
                        percentile(times, 50), percentile(times, 95));
            }
        }

        System.out.println("\n----- 모델별 합계 (" + ROUNDS + "번 전체) -----");
        for (RecordingClient client : clients.values()) {

            List<AiUsage> all = client.usages.values().stream().flatMap(List::stream).toList();

            System.out.printf("%-12s 호출 %d · 입력 %d · 출력 %d · 걸린 시간 %dms%n", client.model, all.size(),
                    all.stream().mapToInt(AiUsage::inputTokens).sum(),
                    all.stream().mapToInt(AiUsage::outputTokens).sum(),
                    all.stream().mapToLong(AiUsage::elapsedMs).sum());
        }
        System.out.println("비용 = 입력 토큰 × 입력 단가 + 출력 토큰 × 출력 단가 (단가는 OpenAI 가격표 기준)\n");
    }

    private static long percentile(List<Long> sorted, int p) {
        int index = Math.max(0, Math.min(sorted.size() - 1, (int) Math.round(p / 100.0 * sorted.size()) - 1));
        return sorted.get(index);
    }

    /* 진짜 OpenAiClient 를 그대로 쓰되, 호출마다 사용량을 지금 기능 이름으로 모아 둔다
     *  논점 생성처럼 사용량을 돌려주지 않는 기능도 여기서 잡힌다. 재시도하면 호출이 여러 번 잡힌다. (실제 비용 그대로)
     * */
    private static class RecordingClient extends OpenAiClient {

        private final String model;
        private final Map<AiFeature, List<AiUsage>> usages = new LinkedHashMap<>();
        private final Map<AiFeature, Integer> failures = new LinkedHashMap<>();
        private AiFeature feature;

        RecordingClient(String model) {

            super(
                    MAPPER, System.getenv("OPENAI_API_KEY"), "https://api.openai.com/v1", model, "none"
            );
            this.model = model;
        }

        @Override
        public AiResult<JsonNode> createJson(String instructions, String input, int maxOutputTokens,
                                             String schemaName, Map<String, Object> schema) {

            return recorded(
                    super.createJson(instructions, input, maxOutputTokens, schemaName, schema)
            );
        }

        @Override
        public AiResult<String> streamText(String instructions, String input, int maxOutputTokens,
                                           Consumer<String> onDelta) {

            return recorded(
                    super.streamText(instructions, input, maxOutputTokens, onDelta)
            );
        }

        private <T> AiResult<T> recorded(AiResult<T> result) {

            usages.computeIfAbsent(
                    feature,
                    f -> new ArrayList<>()).add(result.usage()
            );

            return result;
        }
    }
}
