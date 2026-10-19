package com.verdict.castcourt.domain.trial.session;

import com.verdict.castcourt.domain.speaking.entity.Grade;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.rule.ScoreRule;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/* 진행 중인 재판 한 판 (서버 메모리에만 있고 DB 에는 없다)
 *  판결 때 TrialEntity · SpeakingEntity 로 바뀌어 저장된다. (7장)
 *  여러 요청이 동시에 고치지 못하게 막는 것은 6장에서 다룬다.
 * */
@Getter
public class TrialSession {

    private final String trialId;               // 진행 중 재판 id (UUID 문자열)
    private final int userId;
    private final int workCaseId;
    private final Side role;                    // 사용자가 맡은 역할
    private final TrialMode mode;
    private final CaseSnapshot snapshot;        // 재판 시작 때의 사건 복사본
    private final List<Integer> drawnCardIds;   // 시작 때 뽑은 카드 (2~3장, 뽑힌 순서)

    private final List<TrialSessionSpeaking> speakings = new ArrayList<>();
    private int cardCount = TrialTurnOrder.BASE_CARD_COUNT;    // 실제로 쓰는 카드 수
    private int score = ScoreRule.START_SCORE;

    public TrialSession(String trialId, int userId, int workCaseId, Side role, TrialMode mode,
                        CaseSnapshot snapshot, List<Integer> drawnCardIds) {

        if (drawnCardIds.size() < TrialTurnOrder.BASE_CARD_COUNT
                || drawnCardIds.size() > TrialTurnOrder.MAX_CARD_COUNT) {
            throw new IllegalArgumentException("뽑은 카드는 2~3장 : " + drawnCardIds.size());
        }

        this.trialId = trialId;
        this.userId = userId;
        this.workCaseId = workCaseId;
        this.role = role;
        this.mode = mode;
        this.snapshot = snapshot;
        this.drawnCardIds = List.copyOf(drawnCardIds);
    }

    /* 실제로 공방에 쓰는 카드 (뽑은 카드 앞에서부터 cardCount 장) */
    public List<Integer> usedCardIds() {
        return drawnCardIds.subList(0, cardCount);
    }

    /* 발언이 전부 끝났는지 (끝나면 판결 받기 가능 · 7장) */
    public boolean isFinished() {
        return speakings.size() == TrialTurnOrder.totalCount(cardCount);
    }

    /* 다음 발언 차례 · 끝났으면 null */
    public TrialTurn currentTurn() {
        if (isFinished()) {
            return null;
        }
        return TrialTurnOrder.turnAt(speakings.size() + 1, role, usedCardIds());
    }

    /* 지금 차례의 발언 하나를 판정과 함께 기록하고 게이지를 움직인다
     *  weight : 공방은 카드 가중치, 모두 진술 · 최후 변론은 판사 AI 가 정한 가중치
     * */
    public TrialSessionSpeaking record(String content, boolean issue, boolean evidence, Boolean rebut,
                                       boolean novel, int weight) {

        TrialTurn turn = currentTurn();
        if (turn == null) {
            throw new IllegalStateException("이미 끝난 재판 : " + trialId);
        }

        Grade grade = Grade.of(issue, evidence, rebut, novel);
        int delta = ScoreRule.delta(weight, grade, turn.side());
        score = ScoreRule.applyDelta(score, delta);

        TrialSessionSpeaking speaking = new TrialSessionSpeaking(
                turn.seq(), turn.stage(), turn.side(), turn.speaker(), content,
                issue, evidence, rebut, novel, grade, delta, score
        );
        speakings.add(speaking);

        decideThirdCard();
        return speaking;
    }

    /* 밖에서 목록을 고치지 못하게 읽기 전용으로 준다 */
    public List<TrialSessionSpeaking> getSpeakings() {
        return Collections.unmodifiableList(speakings);
    }

    // 카드 2장 공방이 끝난 순간에만 3번째 카드를 정한다 (그 뒤 게이지가 바뀌어도 다시 정하지 않음)
    private void decideThirdCard() {
        if (speakings.size() == TrialTurnOrder.THIRD_CARD_DECISION_SEQ
                && drawnCardIds.size() == TrialTurnOrder.MAX_CARD_COUNT
                && TrialTurnOrder.needsThirdCard(score)) {
            cardCount = TrialTurnOrder.MAX_CARD_COUNT;
        }
    }
}