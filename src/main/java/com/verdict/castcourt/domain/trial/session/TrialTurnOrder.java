package com.verdict.castcourt.domain.trial.session;

import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.Side;

import java.util.List;

/* 발언 순서 규칙 (기획서 '재판 진행' 그대로)
 *  모두 진술 2 → 공방 카드마다 6 (검사 공격 · 변호인 반론 3번) → 최후 변론 2
 *  카드 2장이면 16개, 3장이면 22개 · 모든 단계에서 검사가 먼저 말한다
 * */
public final class TrialTurnOrder {

    public static final int OPENING_COUNT = 2;
    public static final int SPEAKINGS_PER_CARD = 6;
    public static final int CLOSING_COUNT = 2;

    public static final int BASE_CARD_COUNT = 2;
    public static final int MAX_CARD_COUNT = 3;

    // 카드 2장 공방이 끝나는 발언 (seq 14) · 이 시점 게이지로 3번째 카드를 정한다
    public static final int THIRD_CARD_DECISION_SEQ = OPENING_COUNT + SPEAKINGS_PER_CARD * BASE_CARD_COUNT;
    public static final int THIRD_CARD_MIN_SCORE = 47;
    public static final int THIRD_CARD_MAX_SCORE = 53;

    private TrialTurnOrder() {
    }

    /* 전체 발언 수 : 카드 2장 16 · 3장 22 */
    public static int totalCount(int cardCount) {
        return OPENING_COUNT + SPEAKINGS_PER_CARD * cardCount + CLOSING_COUNT;
    }

    /* seq 번째 발언 차례 · usedCardIds 는 실제로 쓰는 카드 (2~3장, 뽑힌 순서) */
    public static TrialTurn turnAt(int seq, Side role, List<Integer> usedCardIds) {

        int total = totalCount(usedCardIds.size());
        if (seq < 1 || seq > total) {
            throw new IllegalArgumentException("발언 순서 범위 밖 : " + seq);
        }

        if (seq <= OPENING_COUNT) {
            return turn(seq, Stage.OPENING, sideAt(seq - 1), role, null);
        }

        int closingStart = total - CLOSING_COUNT + 1;
        if (seq >= closingStart) {
            return turn(seq, Stage.CLOSING, sideAt(seq - closingStart), role, null);
        }

        // 공방 안에서 0부터 센 위치 → 몇 번째 카드인지, 카드 안에서 몇 번째인지
        int offset = cardOffset(seq);
        int cardIndex = offset / SPEAKINGS_PER_CARD;
        return turn(seq, Stage.CARD, sideAt(offset % SPEAKINGS_PER_CARD), role, usedCardIds.get(cardIndex));
    }

    /* 공방 단계에서 몇 번째 카드인지 1~3 · 공방이 아니면 null (재판 상태 cardNo) */
    public static Integer cardNo(TrialTurn turn) {
        if (turn.stage() != Stage.CARD) {
            return null;
        }
        return cardOffset(turn.seq()) / SPEAKINGS_PER_CARD + 1;
    }

    /* 카드 안에서 몇 번째 공방(검사 공격 + 변호인 반론)인지 1~3 · 공방이 아니면 null (재판 상태 exchangeNo) */
    public static Integer exchangeNo(TrialTurn turn) {
        if (turn.stage() != Stage.CARD) {
            return null;
        }
        return cardOffset(turn.seq()) % SPEAKINGS_PER_CARD / 2 + 1;
    }

    /* 3번째 카드 조건 : 카드 2장 공방 뒤 게이지가 47~53 (승부가 아직 팽팽함) */
    public static boolean needsThirdCard(int score) {
        return score >= THIRD_CARD_MIN_SCORE && score <= THIRD_CARD_MAX_SCORE;
    }

    // 공방 안에서 0부터 센 위치 (seq 3 → 0, seq 9 → 6)
    private static int cardOffset(int seq) {
        return seq - OPENING_COUNT - 1;
    }

    // 단계 안에서 0 · 2 · 4번째는 검사, 1 · 3 · 5번째는 변호인
    private static Side sideAt(int indexInStage) {
        return indexInStage % 2 == 0 ? Side.PROSECUTION : Side.DEFENSE;
    }

    // 발언하는 쪽이 사용자 역할과 같으면 사용자 차례, 아니면 AI 차례
    private static TrialTurn turn(int seq, Stage stage, Side side, Side role, Integer cardId) {
        Speaker speaker = side == role ? Speaker.USER : Speaker.AI;
        return new TrialTurn(seq, stage, side, speaker, cardId);
    }
}