package com.verdict.castcourt.domain.speaking.service;

import com.verdict.castcourt.domain.speaking.ai.JudgeAi;
import com.verdict.castcourt.domain.speaking.ai.OpponentAi;
import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialSessionStore;
import com.verdict.castcourt.domain.trial.session.TrialTurn;
import com.verdict.castcourt.global.exception.BusinessException;
import com.verdict.castcourt.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/* 발언 제출 (SPK-01) : 내 발언 판정 → AI 상대 발언 → 상대 발언 판정 → 단계 알림을 SSE 로 흘려보낸다
 *  - 요청 스레드 : 검사(400 · 403 · 404)만 하고 SseEmitter 를 돌려준다. 여기서 난 오류는 평소처럼 JSON 오류 응답이 된다.
 *  - 작업 스레드 : AI 를 부르며 이벤트를 보낸다. 여기서 난 오류는 error 이벤트로 알린다. (이미 응답 중이라 상태 코드를 못 바꾼다)
 *  흐름은 예제 RecommendationService 와 같다.
 * */
@Service
public class SpeakingService {

    public static final int MAX_CONTENT_LENGTH = 300;

    private final TrialSessionStore trialSessionStore;
    private final JudgeAi judgeAi;              // 가짜 / 실제 중 설정값으로 정해진 하나 (5장) · 6-3 부터 쓴다
    private final OpponentAi opponentAi;
    private final TaskExecutor taskExecutor;

    public SpeakingService(TrialSessionStore trialSessionStore, JudgeAi judgeAi, OpponentAi opponentAi,
                           // 스프링 부트가 만들어 둔 비동기 작업용 실행기 (가상 스레드를 켜면 가상 스레드로 실행)
                           @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.trialSessionStore = trialSessionStore;
        this.judgeAi = judgeAi;
        this.opponentAi = opponentAi;
        this.taskExecutor = taskExecutor;
    }

    /* SSE 를 열기 전 검사 : 404 → 403 → 내 차례 → 글자 수 */
    TrialSession checkSubmittable(int userId, String trialId, String content) {

        TrialSession session = trialSessionStore.findByTrialId(trialId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIAL_NOT_FOUND));

        if (session.getUserId() != userId) {
            throw new BusinessException(ErrorCode.TRIAL_ACCESS_DENIED);
        }

        // 발언이 다 끝났거나(null) AI 차례면 사용자가 말할 수 없다
        TrialTurn turn = session.currentTurn();
        if (turn == null || turn.speaker() != Speaker.USER) {
            throw new BusinessException(ErrorCode.SPEAKING_NOT_YOUR_TURN);
        }

        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.SPEAKING_CONTENT_INVALID);
        }
        return session;
    }
}