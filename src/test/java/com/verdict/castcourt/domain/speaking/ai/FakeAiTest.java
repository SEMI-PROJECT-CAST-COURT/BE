package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.ai.AiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeAiTest {

    private final FakeJudgeAi judgeAi = new FakeJudgeAi();
    private final FakeOpponentAi opponentAi = new FakeOpponentAi(0);     // 테스트는 기다리지 않는다
    private final FakeVerdictAi verdictAi = new FakeVerdictAi();

    @Test
    @DisplayName("가짜 상대 AI : 글자 조각을 이어 붙이면 돌려준 문장과 같다")
    void opponentStreams() {
        TrialSession session = SpeakingAiFixture.session(Side.DEFENSE);     // 첫 차례가 AI 검사
        List<String> deltas = new ArrayList<>();

        AiResult<String> result = opponentAi.speak(session, deltas::add);

        assertEquals(result.output(), String.join("", deltas));
        assertEquals(result.output().length(), deltas.size());
        assertTrue(result.output().startsWith("재판장님, 검사 측"));
        assertEquals("fake", result.usage().model());
    }

    @Test
    @DisplayName("가짜 판사 · 상대 AI 만으로 한 판을 끝까지 돌리고 총평을 받는다")
    void playToEnd() {
        TrialSession session = SpeakingAiFixture.session(Side.PROSECUTION);

        while (!session.isFinished()) {
            String content = opponentAi.speak(session, delta -> { }).output();
            SpeakingAiFixture.record(session, judgeAi.judge(session, content).output());
        }

        assertEquals(16, session.getSpeakings().size());
        assertTrue(verdictAi.summarize(session).output().contains("최종 게이지 " + session.getScore()));
    }
}