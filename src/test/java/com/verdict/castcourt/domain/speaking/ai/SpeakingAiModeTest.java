package com.verdict.castcourt.domain.speaking.ai;

import com.verdict.castcourt.global.ai.OpenAiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/* 설정값 castcourt.ai.mode 하나로 가짜 / 실제 AI 중 한 벌만 빈으로 뜨는지 확인한다
 *  ApplicationContextRunner : 서버 전체를 띄우지 않고 지정한 클래스만으로 작은 스프링 컨테이너를 만든다.
 * */
class SpeakingAiModeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    FakeJudgeAi.class, FakeOpponentAi.class, FakeVerdictAi.class,
                    OpenAiJudgeAi.class, OpenAiOpponentAi.class, OpenAiVerdictAi.class)
            .withBean(OpenAiClient.class, () -> mock(OpenAiClient.class));

    @Test
    @DisplayName("설정이 없으면 가짜 AI")
    void defaultFake() {
        runner.run(context -> {
            assertThat(context.getBean(JudgeAi.class)).isInstanceOf(FakeJudgeAi.class);
            assertThat(context.getBean(OpponentAi.class)).isInstanceOf(FakeOpponentAi.class);
            assertThat(context.getBean(VerdictAi.class)).isInstanceOf(FakeVerdictAi.class);
        });
    }

    @Test
    @DisplayName("castcourt.ai.mode=openai 면 실제 AI")
    void openai() {
        runner.withPropertyValues("castcourt.ai.mode=openai").run(context -> {
            assertThat(context.getBean(JudgeAi.class)).isInstanceOf(OpenAiJudgeAi.class);
            assertThat(context.getBean(OpponentAi.class)).isInstanceOf(OpenAiOpponentAi.class);
            assertThat(context.getBean(VerdictAi.class)).isInstanceOf(OpenAiVerdictAi.class);
        });
    }
}