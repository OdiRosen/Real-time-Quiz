package com.example.demo.controller;

import com.example.demo.entity.Player;
import com.example.demo.entity.Question;
import com.example.demo.service.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/player")
@CrossOrigin(origins = "http://localhost:4200", allowedHeaders = "*", allowCredentials = "true")
public class PlayerController {

    @Autowired
    private PlayerService playerService;

    @PostMapping("/{quizId}/join")
    public ResponseEntity<Player> join(@PathVariable Long quizId,
                                       @RequestParam String name,
                                       @RequestParam String image){
        try {
            Player player = playerService.joinQuiz(quizId, name, image);
            return ResponseEntity.ok(player);
        } catch (RuntimeException e){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
    }

    @GetMapping("/{quizId}/question/{playerId}")
    public ResponseEntity<Question> getNextQuestion(@PathVariable Long quizId,
                                                    @PathVariable String playerId){
        Question question = playerService.getNextQuestion(quizId, playerId);
        return ResponseEntity.ok(question);
    }

    @PostMapping("/{quizId}/submit")
    public ResponseEntity<Boolean> submitAnswer(@PathVariable Long quizId,
                                                @RequestParam String playerId,
                                                @RequestParam String answer,
                                                @RequestParam long timeTaken){
        boolean isCorrect = playerService.submitAnswer(quizId, playerId, answer, timeTaken);
        return ResponseEntity.ok(isCorrect);
    }

    @GetMapping("/{quizId}/players")
    public ResponseEntity<List<Player>> getPlayers(@PathVariable Long quizId) {
        return ResponseEntity.ok(playerService.getActivePlayers(quizId));
    }
}
