package com.verdict.castcourt.domain.speaking.ai.dto;

import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.rule.JudgeRule;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialTurn;

/* 판사 판정 결과 : TrialSession.record() 에 그대로 넘기는 값 묶음 */
public record JudgeResult(
        boolean issue,      // ① 쟁점
        boolean evidence,   // ② 근거
        Boolean rebut,      // ③ 반박 (판정하지 않는 발언이면 null)
        boolean novel,      // ④ 새로움
        int weight          // 게이지에 곱할 가중치 (공방 = 카드 가중치, 그 밖 = AI 가중치)
) {

    /* AI 대답 → 판정 결과
     *  판사 AI 는 이 메서드로만 판정 결과를 만든다. 그래서 ③ 제외와 가중치 규칙이 한 곳에서만 적용된다.
     *  session 은 아직 이번 발언을 기록하기 전이라 currentTurn() 이 '판정할 발언'의 차례다.
     * */
    public static JudgeResult of(TrialSession session, boolean issue, boolean evidence, boolean rebut,
                                 boolean novel, int aiWeight) {

        TrialTurn turn = session.currentTurn();

        // ③ : 직전 상대 발언이 없는 발언이면 AI 가 뭐라고 답했든 버린다
        Boolean judgedRebut = JudgeRule.needsRebut(turn) ? rebut : null;

        // 가중치 : 공방이면 지금 카드의 가중치, 아니면 AI 가 준 값을 1~20 으로 자른 값
        int weight = JudgeRule.needsAiWeight(turn)
                ? JudgeRule.clampWeight(aiWeight)
                : cardWeight(session.getSnapshot(), turn.cardId());

        return new JudgeResult(issue, evidence, judgedRebut, novel, weight);
    }

    // 스냅샷의 카드 목록에서 id 로 가중치를 찾는다 (뽑은 카드는 스냅샷에서 나왔으므로 항상 있다)
    private static int cardWeight(CaseSnapshot snapshot, Integer cardId) {
        return snapshot.cards().stream()
                .filter(card -> card.cardId().equals(cardId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("스냅샷에 없는 카드 : " + cardId))
                .weight();
    }
}
