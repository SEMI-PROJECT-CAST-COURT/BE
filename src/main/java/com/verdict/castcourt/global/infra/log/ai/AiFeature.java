package com.verdict.castcourt.global.infra.log.ai;

import com.verdict.castcourt.domain.evidencecard.service.CardDraftService;
import com.verdict.castcourt.domain.penaltytier.service.PenaltyDraftService;
import com.verdict.castcourt.domain.speaking.ai.model.JudgeAi;
import com.verdict.castcourt.domain.speaking.ai.model.OpenAiTopicAi;
import com.verdict.castcourt.domain.speaking.ai.model.OpponentAi;
import com.verdict.castcourt.domain.speaking.ai.model.VerdictAi;
import com.verdict.castcourt.domain.work.service.WorkSummaryDraftService;
import com.verdict.castcourt.domain.workcase.ai.model.OpenAiCaseDraftAi;

import java.util.Arrays;
import java.util.Optional;

/* AI 를 부르는 기능 = OpenAiClient 를 직접 부르는 클래스
 *  클래스를 직접 참조해서, 이름이 바뀌면 여기서 컴파일 오류가 난다. (문자열로 두면 조용히 UNKNOWN 이 된다)
 *  새 기능이 OpenAiClient 를 부르면 여기에 한 줄 추가한다.
 * */
public enum AiFeature {

    CASE_DRAFT(OpenAiCaseDraftAi.class),                // 사건 초안
    EVIDENCE_CARD_DRAFT(CardDraftService.class),        // 근거 카드 초안 · 추천
    PENALTY_TIER_DRAFT(PenaltyDraftService.class),      // 형벌표 초안
    WORK_SUMMARY_DRAFT(WorkSummaryDraftService.class),  // 작품 소개 초안
    TRIAL_TOPIC(OpenAiTopicAi.class),                   // 재판 시작 : 논점 생성
    TRIAL_OPPONENT(OpponentAi.class),                   // 재판 : 상대 AI 발언 (검사 또는 변호인)
    TRIAL_JUDGE(JudgeAi.class),                         // 재판 : 판사 발언 판정
    TRIAL_VERDICT(VerdictAi.class);                     // 재판 : 판사 총평

    private final Class<?> caller;

    AiFeature(Class<?> caller) {
        this.caller = caller;
    }

    static Optional<AiFeature> of(Class<?> type) {

        return Arrays.stream(values())
                .filter(feature -> feature.caller == type)
                .findFirst();
    }
}
