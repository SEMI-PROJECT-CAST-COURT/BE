package com.verdict.castcourt.domain.trial.dto.response;

import com.verdict.castcourt.domain.speaking.entity.Grade;
import com.verdict.castcourt.domain.speaking.entity.Speaker;
import com.verdict.castcourt.domain.speaking.entity.SpeakingEntity;
import com.verdict.castcourt.domain.speaking.entity.Stage;
import com.verdict.castcourt.domain.trial.entity.Side;
import com.verdict.castcourt.domain.trial.entity.TrialEntity;
import com.verdict.castcourt.domain.trial.entity.TrialMode;
import com.verdict.castcourt.domain.trial.rule.ScoreRule;

import java.util.Comparator;
import java.util.List;

/* 재판 기록 응답 (TRL-05) · 판결이 난 판이라 모드와 상관없이 판정 · 게이지를 모두 공개한다
 *  title · sentence 는 판결 받기 응답과 같은 값이다.
 * */
public record TrialRecordResponseDTO(
        Side role,
        TrialMode mode,
        int score,                      // 최종 게이지 0~100
        int penaltyTier,                // 형벌 단계 1~6
        String title,                   // 단계 이름 "무죄 1단계" ~ "유죄 3단계"
        String sentence,                // 처분 문장 (재판 당시 형벌표)
        String judgeSummary,
        List<SpeakingRecord> speakings, // 발언 전체 (seq 순)
        List<Integer> keyMoments        // 결정적 순간 : 게이지를 가장 크게 움직인 발언의 seq 3개
) {

    // 결정적 순간 개수 (명세 v6 : delta 절댓값 상위 3개)
    static final int KEY_MOMENT_COUNT = 3;

    /* 발언 하나 */
    public record SpeakingRecord(
            int seq,
            Stage stage,
            Side side,
            Speaker speaker,
            String content,
            Checks checks,
            Grade grade,
            int delta,                  // 게이지 변화량 (검사 +, 변호인 -)
            int scoreAfter              // 이 발언 뒤 게이지
    ) {

        static SpeakingRecord from(SpeakingEntity speaking) {
            return new SpeakingRecord(
                    speaking.getSeq(),
                    speaking.getStage(),
                    speaking.getSide(),
                    speaking.getSpeaker(),
                    speaking.getContent(),
                    new Checks(speaking.isIssue(), speaking.isEvidence(), speaking.getRebut(), speaking.isNovel()),
                    speaking.getGrade(),
                    speaking.getDelta(),
                    speaking.getScoreAfter()
            );
        }
    }

    /* 판사 판정 4항목 · rebut 은 반박할 상대 발언이 없던 첫 발언이면 null */
    public record Checks(
            boolean issue,
            boolean evidence,
            Boolean rebut,
            boolean novel
    ) {
    }

    /* speakings 는 seq 순으로 받는다 (SpeakingRepository.findByTrialIdOrderBySeqAsc) */
    public static TrialRecordResponseDTO of(TrialEntity trial, List<SpeakingEntity> speakings) {
        return new TrialRecordResponseDTO(
                trial.getRole(),
                trial.getMode(),
                trial.getScore(),
                trial.getPenaltyTier(),
                ScoreRule.tierTitle(trial.getPenaltyTier()),
                trial.getCaseSnapshot().sentenceOf(trial.getPenaltyTier()),
                trial.getJudgeSummary(),
                speakings.stream().map(SpeakingRecord::from).toList(),
                keyMoments(speakings)
        );
    }

    /* 결정적 순간 : delta 절댓값이 큰 순 → 같으면 앞 발언(seq 작은 쪽) 먼저 → 3개의 seq
     *  화면은 이 seq 로 speakings 에서 발언을 찾아 강조한다.
     * */
    static List<Integer> keyMoments(List<SpeakingEntity> speakings) {
        return speakings.stream()
                .sorted(Comparator.comparingInt((SpeakingEntity speaking) -> Math.abs(speaking.getDelta()))
                        .reversed()
                        .thenComparingInt(SpeakingEntity::getSeq))
                .limit(KEY_MOMENT_COUNT)
                .map(SpeakingEntity::getSeq)
                .toList();
    }
}
