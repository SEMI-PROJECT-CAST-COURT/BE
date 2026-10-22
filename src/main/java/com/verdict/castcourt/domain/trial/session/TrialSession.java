package com.verdict.castcourt.domain.trial.session;

import com.verdict.castcourt.domain.speaking.entity.Grade;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.CaseSnapshot;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.rule.ScoreRule;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Duration;
import java.time.Instant;

/* 진행 중인 재판 한 판 (서버 메모리에만 있고 DB 에는 없다)
 *  판결 때 TrialEntity · SpeakingEntity 로 바뀌어 저장된다.
 *  발언 처리는 복사본(copy)에서 하고, 성공했을 때만 applyFrom 으로 반영한다.
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

    // 재판 시작 때 한 번 만든 중립 논점. 복사본과 원본이 같은 불변 맵을 사용한다.
    private final Map<Integer, TrialTopic> topics;

    /* 아래 세 값은 발언 처리 스레드가 바꾸고 재판 상태 조회 스레드가 읽는다.
     *  volatile : 한 스레드가 바꾼 값을 다른 스레드가 바로 보게 한다.
     *  speakings 는 final 을 빼고, 반영할 때 목록을 통째로 바꿔 끼운다. (applyFrom)
     * */
    private volatile List<TrialSessionSpeaking> speakings = new ArrayList<>();
    private volatile int cardCount = TrialTurnOrder.BASE_CARD_COUNT;    // 실제로 쓰는 카드 수
    private volatile int score = ScoreRule.START_SCORE;

    private int failCount;          // AI 실패 연속 횟수 (성공하면 0)

    // 마지막 활동 시각 · 재판 시작(생성) · 발언 제출 때 바뀐다 (발언 스레드가 바꾸고 조회 스레드가 읽어서 volatile)
    private volatile Instant lastActiveAt = Instant.now();
    // 발언 처리 중인지 · 같은 재판에 발언이 동시에 두 번 들어오지 못하게 막는다 (밖에서 직접 못 만지게 @Getter 제외)
    @Getter(AccessLevel.NONE)
    private final AtomicBoolean processing = new AtomicBoolean(false);

    public TrialSession(String trialId, int userId, int workCaseId, Side role, TrialMode mode,
                        CaseSnapshot snapshot, List<Integer> drawnCardIds) {
        this(trialId, userId, workCaseId, role, mode, snapshot, drawnCardIds, Map.of());
    }

    public TrialSession(String trialId, int userId, int workCaseId, Side role, TrialMode mode,
                        CaseSnapshot snapshot, List<Integer> drawnCardIds, Map<Integer, TrialTopic> topics) {

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
        this.topics = Map.copyOf(topics);
    }

    /* 실제로 공방에 쓰는 카드 (뽑은 카드 앞에서부터 cardCount 장) */
    public List<Integer> usedCardIds() {
        return drawnCardIds.subList(0, cardCount);
    }

    /* 발언이 전부 끝났는지 (끝나면 판결 받기 가능) */
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

    /* 공방에서만 현재 논점을 공개한다. 미래 카드의 논점은 응답에 넣지 않는다. */
    public String currentTopic() {
        TrialTurn turn = currentTurn();
        TrialTopic topic = turn == null || turn.cardId() == null ? null : topics.get(turn.cardId());
        return topic == null ? null : topic.topic();
    }

    /* 공방 기준은 검사 카드, 각 진영의 근거는 시작 때 선정한 카드로 고정한다. */
    public CaseSnapshot.Card currentCard(Side side) {
        TrialTurn turn = currentTurn();
        if (turn == null || turn.cardId() == null) {
            return null;
        }
        return roundCard(turn.cardId(), side);
    }

    /* 화면에 보여 줄 카드 : 공방은 현재 카드, 모두 진술은 첫 공방 카드, 최후 변론 · 종료 후는 마지막 공방 카드 */
    public CaseSnapshot.Card shownCard(Side side) {
        TrialTurn turn = currentTurn();
        if (turn != null && turn.stage() == Stage.CARD) {
            return currentCard(side);
        }
        List<Integer> usedCardIds = usedCardIds();
        Integer roundCardId = turn != null && turn.stage() == Stage.OPENING
                ? usedCardIds.get(0) : usedCardIds.get(usedCardIds.size() - 1);
        return roundCard(roundCardId, side);
    }

    /* 공방 검사 카드(roundCardId) 기준으로 해당 진영의 카드를 찾는다 (대응 변호인 카드가 없으면 null) */
    private CaseSnapshot.Card roundCard(Integer roundCardId, Side side) {
        TrialTopic topic = topics.get(roundCardId);
        Integer cardId = side == Side.PROSECUTION ? roundCardId
                : topic == null ? null : topic.defenseCardId();
        if (cardId == null) {
            return null;
        }
        return snapshot.cards().stream()
                .filter(card -> card.cardId().equals(cardId) && card.side() == side)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("현재 진영에 없는 카드 : " + cardId));
    }

    /* 지금 차례의 발언 하나를 판정과 함께 기록하고 게이지를 움직인다
     *  weight : 공방은 카드 가중치, 모두 진술 · 최후 변론은 판사 AI 가 정한 가중치
     * */
    public TrialSessionSpeaking record(String content, boolean issue, boolean evidence, Boolean rebut,
                                       boolean novel, boolean roleViolation, int weight) {

        TrialTurn turn = currentTurn();
        if (turn == null) {
            throw new IllegalStateException("이미 끝난 재판 : " + trialId);
        }

        Grade grade = Grade.of(issue, evidence, rebut, novel);
        int delta = ScoreRule.delta(weight, grade, turn.side(), roleViolation);
        score = ScoreRule.applyDelta(score, delta);

        TrialSessionSpeaking speaking = new TrialSessionSpeaking(
                turn.seq(), turn.stage(), turn.side(), turn.speaker(), content,
                issue, evidence, rebut, novel, roleViolation, grade, delta, score
        );
        speakings.add(speaking);

        decideThirdCard();
        return speaking;
    }

    /* 발언 처리용 복사본 : 판정 · 상대 발언이 모두 성공해야 원본에 반영한다
     *  실패하면 복사본만 버리면 되므로 원본의 발언 · 게이지는 그대로 남는다. (B-1 실패 시 저장 안 함 · 게이지 변화 없음)
     * */
    public TrialSession copy() {

        TrialSession draft = new TrialSession(trialId, userId, workCaseId, role, mode, snapshot, drawnCardIds, topics);
        draft.speakings = new ArrayList<>(speakings);
        draft.cardCount = cardCount;
        draft.score = score;
        return draft;
    }

    /* 성공한 복사본의 결과를 원본에 반영한다
     *  목록을 고치지 않고 통째로 바꿔 끼워서, 재판 상태 조회가 읽는 도중 목록이 바뀌는 일이 없다.
     * */
    public void applyFrom(TrialSession draft) {
        this.speakings = draft.speakings;
        this.cardCount = draft.cardCount;
        this.score = draft.score;
    }

    /* AI 실패 한 번 더 · 늘어난 횟수를 돌려준다 (error 이벤트 failCount) */
    public int increaseFailCount() {
        return ++failCount;
    }

    public void resetFailCount() {
        failCount = 0;
    }

    /* 발언 처리 시작 : 이미 처리 중이면 false (compareAndSet 은 확인과 변경을 한 번에 해서 두 요청이 함께 통과하지 못한다) */
    public boolean tryStartTurn() {
        return processing.compareAndSet(false, true);
    }

    /* 발언 처리 끝 (여러 번 불러도 안전) */
    public void endTurn() {
        processing.set(false);
    }

    /* 활동 기록 : 마지막 활동 시각을 now 로 바꾼다 (부르는 쪽이 Instant.now() 를 넘긴다) */
    public void touch(Instant now) {
        lastActiveAt = now;
    }

    /* 버려진 판인지 : 마지막 활동 뒤 idleTimeout 이 지났으면 true */
    public boolean isIdle(Instant now, Duration idleTimeout) {
        return lastActiveAt.plus(idleTimeout).isBefore(now);
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
