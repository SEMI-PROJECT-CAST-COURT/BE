package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.AiUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/* 가짜 판사 AI : OpenAI 를 부르지 않고 무작위로 판정한다
 *  키 없이도 한 판을 끝까지 돌려 볼 수 있게 한다. (6장 SSE · 7장 판결 확인용)
 *  castcourt.ai.mode 가 없거나 fake 이면 이 빈이 뜬다. (기본값)
 * */
@Component
@ConditionalOnProperty(name = "castcourt.ai.mode", havingValue = "fake", matchIfMissing = true)
public class FakeJudgeAi implements JudgeAi {

    @Override
    public AiResult<JudgeResult> judge(TrialSession session, String content) {

        LocalDateTime calledAt = LocalDateTime.now();
        // 여러 스레드(요청)가 함께 써도 안전한 난수
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // ① 은 대부분 충족, 나머지는 70% 확률로 충족 → 등급이 고루 나온다
        JudgeResult result = JudgeResult.of(
                session,
                random.nextInt(100) < 90,
                random.nextInt(100) < 70,
                random.nextInt(100) < 70,
                random.nextInt(100) < 70,
                random.nextInt(5, 16)           // 모두 진술 · 최후 변론 가중치 5~15
        );
        return new AiResult<>(result, AiUsage.fake(calledAt, 0));
    }
}