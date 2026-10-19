package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.session.TrialSession;
import com.verdict.castcourt.domain.trial.session.TrialTurn;
import com.verdict.castcourt.global.ai.AiResult;
import com.verdict.castcourt.global.ai.AiUsage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/* 가짜 상대 AI : 정해진 문장을 글자 단위로 흘려보낸다
 *  실제 AI 처럼 글자가 조금씩 나오므로 6장 SSE 화면을 키 없이 확인할 수 있다.
 * */
@Component
@ConditionalOnProperty(name = "castcourt.ai.mode", havingValue = "fake", matchIfMissing = true)
public class FakeOpponentAi implements OpponentAi {

    // 글자 하나를 보낸 뒤 쉬는 시간 (테스트에서는 0 으로 만들어 바로 끝낸다)
    private final Duration delay;

    // castcourt.ai.fake-delay-ms 가 없으면 30ms (공용 yaml 을 고치지 않으려고 기본값을 둔다)
    public FakeOpponentAi(@Value("${castcourt.ai.fake-delay-ms:30}") long delayMs) {
        this.delay = Duration.ofMillis(delayMs);
    }

    @Override
    public AiResult<String> speak(TrialSession session, Consumer<String> onDelta) {

        LocalDateTime calledAt = LocalDateTime.now();
        long start = System.currentTimeMillis();

        String text = sentence(session.currentTurn());

        // 한 글자씩 넘긴다. codePoints() 로 나눠 이모지 같은 2칸 문자도 깨지지 않게 한다.
        text.codePoints().forEach(codePoint -> {
            onDelta.accept(Character.toString(codePoint));
            pause();
        });

        return new AiResult<>(text, AiUsage.fake(calledAt, System.currentTimeMillis() - start));
    }

    // 단계 · 쪽마다 다른 문장을 써서 화면에서 구분되게 한다
    private String sentence(TrialTurn turn) {

        String side = turn.side() == Side.PROSECUTION ? "검사" : "변호인";
        return switch (turn.stage()) {
            case OPENING -> "재판장님, " + side + " 측 모두 진술입니다. 사건 기록을 근거로 저희 입장을 밝히겠습니다.";
            case CARD -> "재판장님, " + side + " 측은 이 근거 카드가 저희 주장을 뒷받침한다고 봅니다. (" + turn.seq() + "번째 발언)";
            case CLOSING -> "재판장님, " + side + " 측 최후 변론입니다. 지금까지의 공방을 고려해 현명한 판단을 부탁드립니다.";
        };
    }

    private void pause() {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            // 브라우저 연결이 끊겨 작업이 중단되면 인터럽트 표시를 되살리고 멈춘다
            Thread.currentThread().interrupt();
            throw new IllegalStateException("가짜 상대 AI 중단", e);
        }
    }
}