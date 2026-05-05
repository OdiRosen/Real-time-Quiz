package com.example.demo.service;

import com.example.demo.entity.Quiz;
import com.example.demo.repository.QuizRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class QuizService {

    @Autowired
    private QuizRepository quizRepository;

    public Quiz addQuiz(Quiz quiz){
        return quizRepository.save(quiz);
    }

    public List<Quiz> getQuizzesByEmail(String email){
        return quizRepository.findByCreatorEmail(email);
    }

    public Quiz updateQuiz(Long id, Quiz updatedQuiz){
        Quiz existingQuiz = quizRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Quiz not found😶"));

        if (LocalDateTime.now().isAfter(existingQuiz.getEndTime())){
            throw new RuntimeException("Cannot update a closed quiz😉");
        }

        existingQuiz.setName(updatedQuiz.getName());
        existingQuiz.setStartTime(updatedQuiz.getStartTime());
        existingQuiz.setEndTime(updatedQuiz.getEndTime());

        return quizRepository.save(existingQuiz);
    }
}
