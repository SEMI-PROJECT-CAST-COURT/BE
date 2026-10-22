package com.verdict.castcourt.domain.trial.session;

import com.verdict.castcourt.domain.speaking.entity.Grade;
import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.Side;

/* 진행 중 재판의 발언 하나 (메모리 보관용)
 *  컬럼은 speaking 테이블과 같다. 판결 때 SpeakingEntity 로 바뀌어 저장된다.
 * */
public record TrialSessionSpeaking(
        int seq,
        Stage stage,
        Side side,
        Speaker speaker,
        String content,
        boolean issue,      // ① 쟁점
        boolean evidence,   // ② 근거
        Boolean rebut,      // ③ 반박 (첫 발언이면 null)
        boolean novel,      // ④ 새로움
        Grade grade,
        int delta,          // 게이지 변화량 (검사 +, 변호인 -)
        int scoreAfter      // 이 발언 뒤 게이지
) {
}
