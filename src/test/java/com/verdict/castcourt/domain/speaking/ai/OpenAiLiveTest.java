package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.OpenAiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/* 실제 OpenAI 를 한 번씩 불러 보는 확인용 테스트 (커밋하지 않는다)
 *  환경 변수 OPENAI_API_KEY 가 있을 때만 실행되고, 없으면 건너뛴다. 실행할 때마다 토큰 비용이 든다.
 * */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiLiveTest {

    private final OpenAiClient client = new OpenAiClient(JsonMapper.builder().build(),
            System.getenv("OPENAI_API_KEY"), "https://api.openai.com/v1",
            System.getenv().getOrDefault("OPENAI_MODEL", "gpt-6-luna"), "none");

    @Test
    @DisplayName("판사 판정 → 상대 발언 → 판사 총평을 실제로 불러 결과와 토큰 수를 찍는다")
    void callRealAi() {
        TrialSession session = session();

        // 1. 내 발언(검사 모두 진술) 판정
        String mine = "재판장님, 피고인은 계약서를 위조해 피해자에게 3천만 원의 손해를 입혔습니다.";
        AiResult<JudgeResult> judged = new OpenAiJudgeAi(client).judge(session, mine);
        System.out.println("판정 = " + judged.output() + " / 토큰 = " + judged.usage());

        JudgeResult r = judged.output();
        session.record(mine, r.issue(), r.evidence(), r.rebut(), r.novel(), r.weight());

        // 2. 상대(AI 변호인) 모두 진술 : 조각이 올 때마다 바로 찍는다
        System.out.print("상대 발언 = ");
        AiResult<String> spoken = new OpenAiOpponentAi(client).speak(session, System.out::print);
        System.out.println("\n토큰 = " + spoken.usage());

        // 3. 판사 총평
        AiResult<String> verdict = new OpenAiVerdictAi(client).summarize(session);
        System.out.println("총평 = " + verdict.output() + "\n토큰 = " + verdict.usage());
    }

    private TrialSession session() {
        CaseSnapshot snapshot = new CaseSnapshot(
                1, 1, "흥부전", "놀부의 박 사건은 사기인가", "놀부", "흥부",
                "놀부가 흥부의 박씨를 훔쳐 심었다는 의혹", "박을 타자 도깨비가 나왔다",
                List.of(new CaseSnapshot.Card(11, Side.PROSECUTION, "목격자 진술", "이웃이 박씨를 가져가는 놀부를 봤다", 12),
                        new CaseSnapshot.Card(12, Side.PROSECUTION, "제비 다리", "놀부가 제비 다리를 일부러 부러뜨렸다", 8),
                        new CaseSnapshot.Card(21, Side.DEFENSE, "구매 영수증", "놀부는 박씨를 장터에서 샀다", 10)),
                List.of());     // 형벌표는 판결(7장)에서만 쓴다
        return new TrialSession("live-1", 4, 1, Side.PROSECUTION, TrialMode.CHICKEN, snapshot, List.of(11, 12));
    }
}
