package com.verdict.castcourt.domain.trial.rule;

import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.session.TrialTurn;
import com.verdict.castcourt.domain.trial.session.TrialTurnOrder;

/* 판사 판정 규칙 (기획서 '판정 방식' 표)
 *  판사 AI 의 대답 중 어떤 값을 쓰고 어떤 값을 버릴지 서버가 정한다.
 *  가짜 · 실제 판사 AI 가 같은 규칙을 쓰도록 한 곳에 모았다.
 * */
public final class JudgeRule {

    // 모두 진술 · 최후 변론 가중치 범위 (카드 가중치와 같은 1~20 으로 정함 · 팀 확인 필요)
    public static final int MIN_WEIGHT = 1;
    public static final int MAX_WEIGHT = 20;

    private JudgeRule() {
    }

    /* ③ 반박을 판정하는 발언인지
     *  직전 상대 발언이 없으면 제외한다 → 모두 진술, 각 카드의 첫 검사 공격
     *  공방에서는 검사 · 변호인이 번갈아 말하므로 첫 공격만 빼면 항상 직전 상대 발언이 있다.
     * */
    public static boolean needsRebut(TrialTurn turn) {

        if (turn.stage() == Stage.OPENING) {
            return false;
        }
        // 카드 안 1번째 공방의 검사 공격 = 그 카드의 첫 발언
        boolean firstAttack = turn.stage() == Stage.CARD
                && turn.side() == Side.PROSECUTION
                && TrialTurnOrder.exchangeNo(turn) == 1;
        return !firstAttack;
    }

    /* 가중치를 판사 AI 가 정하는 발언인지 (공방은 카드 가중치를 쓴다) */
    public static boolean needsAiWeight(TrialTurn turn) {
        return turn.stage() != Stage.CARD;
    }

    /* AI 가 범위 밖 숫자를 줘도 1~20 안으로 자른다 */
    public static int clampWeight(int weight) {
        return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, weight));
    }
}