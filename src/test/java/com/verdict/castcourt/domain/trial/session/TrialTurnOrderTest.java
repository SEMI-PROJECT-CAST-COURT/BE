package com.verdict.castcourt.domain.trial.session;

import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialTurnOrderTest {

    private static final List<Integer> TWO_CARDS = List.of(11, 22);
    private static final List<Integer> THREE_CARDS = List.of(11, 22, 33);

    @Test
    @DisplayName("전체 발언 수는 카드 2장이면 16, 3장이면 22")
    void totalCount() {
        assertEquals(16, TrialTurnOrder.totalCount(2));
        assertEquals(22, TrialTurnOrder.totalCount(3));
    }

    @ParameterizedTest(name = "seq {0} = {1} {2} 카드 {3}")
    @DisplayName("카드 2장 순서 : 모두 진술 2 → 카드마다 검사 · 변호인 3번 → 최후 변론 2")
    @CsvSource({
            "1,  OPENING, PROSECUTION, ",
            "2,  OPENING, DEFENSE, ",
            "3,  CARD,    PROSECUTION, 11",
            "8,  CARD,    DEFENSE,     11",
            "9,  CARD,    PROSECUTION, 22",
            "14, CARD,    DEFENSE,     22",
            "15, CLOSING, PROSECUTION, ",
            "16, CLOSING, DEFENSE, "
    })
    void twoCardOrder(int seq, Stage stage, Side side, Integer cardId) {
        TrialTurn turn = TrialTurnOrder.turnAt(seq, Side.PROSECUTION, TWO_CARDS);

        assertEquals(stage, turn.stage());
        assertEquals(side, turn.side());
        assertEquals(cardId, turn.cardId());
    }

    @Test
    @DisplayName("카드 3장이면 15~20 이 3번째 카드, 21 · 22 가 최후 변론")
    void threeCardOrder() {
        assertEquals(33, TrialTurnOrder.turnAt(15, Side.PROSECUTION, THREE_CARDS).cardId());
        assertEquals(33, TrialTurnOrder.turnAt(20, Side.PROSECUTION, THREE_CARDS).cardId());
        assertEquals(Stage.CLOSING, TrialTurnOrder.turnAt(21, Side.PROSECUTION, THREE_CARDS).stage());
        assertNull(TrialTurnOrder.turnAt(22, Side.PROSECUTION, THREE_CARDS).cardId());
    }

    @Test
    @DisplayName("발언하는 쪽이 내 역할이면 USER, 아니면 AI")
    void speakerByRole() {
        assertEquals(Speaker.USER, TrialTurnOrder.turnAt(1, Side.PROSECUTION, TWO_CARDS).speaker());
        assertEquals(Speaker.AI, TrialTurnOrder.turnAt(1, Side.DEFENSE, TWO_CARDS).speaker());
        assertEquals(Speaker.USER, TrialTurnOrder.turnAt(2, Side.DEFENSE, TWO_CARDS).speaker());
    }

    @Test
    @DisplayName("범위 밖 seq 는 예외")
    void outOfRange() {
        assertThrows(IllegalArgumentException.class, () -> TrialTurnOrder.turnAt(0, Side.PROSECUTION, TWO_CARDS));
        assertThrows(IllegalArgumentException.class, () -> TrialTurnOrder.turnAt(17, Side.PROSECUTION, TWO_CARDS));
    }

    @Test
    @DisplayName("3번째 카드는 게이지 47~53 일 때만")
    void thirdCardCondition() {
        assertFalse(TrialTurnOrder.needsThirdCard(46));
        assertTrue(TrialTurnOrder.needsThirdCard(47));
        assertTrue(TrialTurnOrder.needsThirdCard(53));
        assertFalse(TrialTurnOrder.needsThirdCard(54));
    }

    @ParameterizedTest(name = "seq {0} = 카드 {1} · 공방 {2}")
    @DisplayName("공방 단계의 카드 번호 · 공방 번호 (공방이 아니면 null)")
    @CsvSource({
            "2,  ,  ",
            "3,  1, 1",
            "4,  1, 1",
            "5,  1, 2",
            "8,  1, 3",
            "9,  2, 1",
            "14, 2, 3",
            "15, ,  "
    })
    void cardAndExchangeNo(int seq, Integer cardNo, Integer exchangeNo) {
        TrialTurn turn = TrialTurnOrder.turnAt(seq, Side.PROSECUTION, TWO_CARDS);

        assertEquals(cardNo, TrialTurnOrder.cardNo(turn));
        assertEquals(exchangeNo, TrialTurnOrder.exchangeNo(turn));
    }
}