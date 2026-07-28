package com.jungwoon.api.vocabulary;

import com.jungwoon.domain.vocabulary.QuestionType;
import com.jungwoon.domain.vocabulary.QuizAnswer;
import com.jungwoon.domain.vocabulary.QuizAttempt;
import com.jungwoon.domain.vocabulary.Word;
import com.jungwoon.domain.vocabulary.WordRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 4지선다 출제.
 *
 * 오답 보기는 <b>같은 DAY 안의 다른 단어 뜻</b>에서 먼저 뽑는다.
 * 무작위 단어에서 뽑으면 뜻이 너무 달라 찍기가 쉬워진다.
 */
@Service
public class QuizGenerationService {

    private static final int CHOICE_COUNT = 4;
    /** 뜻이 이만큼 겹치면 "정답이 두 개"가 되므로 보기에서 뺀다. */
    private static final double SIMILARITY_THRESHOLD = 0.6;

    private final WordRepository wordRepository;

    public QuizGenerationService(WordRepository wordRepository) {
        this.wordRepository = wordRepository;
    }

    /**
     * @param pool        출제 후보 (DAY 의 전체 단어)
     * @param questionCount 문항 수
     */
    public List<QuizAnswer> generate(QuizAttempt attempt, List<Word> pool, int questionCount,
                                     QuestionType questionType) {
        List<Word> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);

        List<Word> questions = shuffled.subList(0, Math.min(questionCount, shuffled.size()));

        List<QuizAnswer> answers = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Word target = questions.get(i);
            List<String> choices = buildChoices(target, pool, questionType);

            // 정답 위치를 무작위로 섞는다. 항상 같은 자리면 위치만 외운다
            int correctIndex = ThreadLocalRandom.current().nextInt(choices.size() + 1);
            choices.add(correctIndex, answerTextOf(target, questionType));

            answers.add(new QuizAnswer(attempt, target, questionType, choices, correctIndex, i));
        }
        return answers;
    }

    /** 오답 3개. 같은 DAY → 같은 난이도 풀 순으로 채운다. */
    private List<String> buildChoices(Word target, List<Word> pool, QuestionType questionType) {
        String answerText = answerTextOf(target, questionType);

        List<String> distractors = pool.stream()
                .filter(word -> !word.getId().equals(target.getId()))
                .map(word -> answerTextOf(word, questionType))
                .filter(text -> !isTooSimilar(text, answerText))
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(distractors);

        int needed = CHOICE_COUNT - 1;
        if (distractors.size() < needed) {
            // DAY 안에서 부족하면 같은 난이도 풀에서 보충한다
            Set<String> already = new java.util.HashSet<>(distractors);
            already.add(answerText);

            List<Long> excludeIds = pool.stream().map(Word::getId).toList();
            wordRepository.findRandomExcluding(excludeIds, target.getLevel(), needed * 3).stream()
                    .map(word -> answerTextOf(word, questionType))
                    .filter(text -> !already.contains(text) && !isTooSimilar(text, answerText))
                    .forEach(text -> {
                        if (distractors.size() < needed) {
                            distractors.add(text);
                            already.add(text);
                        }
                    });
        }

        return new ArrayList<>(distractors.subList(0, Math.min(needed, distractors.size())));
    }

    private String answerTextOf(Word word, QuestionType questionType) {
        return questionType == QuestionType.EN_TO_KO ? word.getMeaningKo() : word.getHeadword();
    }

    /**
     * 자카드 유사도(문자 기준). "포기하다" vs "포기시키다" 같은 보기를 걸러낸다.
     * 형태소 분석까지는 과하고, 이 정도로도 명백한 중복 정답은 막힌다.
     */
    private boolean isTooSimilar(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        Set<Character> setA = a.toLowerCase(Locale.KOREAN).chars()
                .filter(Character::isLetterOrDigit)
                .mapToObj(c -> (char) c).collect(Collectors.toSet());
        Set<Character> setB = b.toLowerCase(Locale.KOREAN).chars()
                .filter(Character::isLetterOrDigit)
                .mapToObj(c -> (char) c).collect(Collectors.toSet());

        if (setA.isEmpty() || setB.isEmpty()) {
            return false;
        }

        Set<Character> intersection = new java.util.HashSet<>(setA);
        intersection.retainAll(setB);
        Set<Character> union = new java.util.HashSet<>(setA);
        union.addAll(setB);

        return (double) intersection.size() / union.size() >= SIMILARITY_THRESHOLD;
    }
}
