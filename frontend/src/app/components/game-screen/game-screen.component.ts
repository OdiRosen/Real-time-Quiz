import { Component, OnDestroy, Input, inject, OnInit, OnChanges, SimpleChanges, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { PlayerService } from '../../services/player.service';
import { QuizService } from '../../services/quiz.service';

@Component({
  selector: 'app-game-screen',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './game-screen.component.html',
  styleUrls: ['./game-screen.component.css']
})
export class GameScreenComponent implements OnInit, OnChanges, OnDestroy {
  @Input() player: any;
  @Input() quizId = 0;

  players: any[] = [];
  shuffledAnswers: string[] = [];
  currentQuestion: any = null;
  playerId = '';
  statusMessage = 'טוען שאלה...';
  isFinished = false;
  isAnswering = false;
  private initialized = false;

  timeLeft = 10;
  maxTime = 10;
  timerInterval: any = null;

  private leaderboardSub: Subscription | null = null;
  private playerService = inject(PlayerService);
  private quizService = inject(QuizService);
  private cdr = inject(ChangeDetectorRef);

  ngOnInit() {}

  ngOnChanges(changes: SimpleChanges) {
    const playerReady = this.player && (this.player.playerId || this.player.id);
    const quizReady = this.quizId && this.quizId > 0;

    if (playerReady && quizReady && !this.initialized) {
      this.initialized = true;
      this.playerId = this.player.playerId || this.player.id || '';
      this.loadQuestionFromServer();
      this.subscribeLeaderboard();
    }
  }

  ngOnDestroy() {
    this.leaderboardSub?.unsubscribe();
    this.clearTimer();
  }

  private clearTimer() {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = null;
    }
  }

  loadQuestionFromServer() {
    if (this.isFinished) return;

    this.clearTimer();
    this.isAnswering = false;
    this.currentQuestion = null;
    this.statusMessage = 'טוען שאלה...';
    this.cdr.detectChanges();

    this.playerService.getSyncQuestion(this.quizId, this.playerId).subscribe({
      next: (response: any) => {
        if (response.status === 204 || !response.body) {
          this.isFinished = true;
          this.currentQuestion = null;
          this.statusMessage = 'החידון הסתיים! תודה שהשתתפת 🎉';
          this.saveFinalWinner();
          this.cdr.detectChanges();
          return;
        }

        const question = response.body;
        this.currentQuestion = question;
        this.shuffledAnswers = [
          question.answer1,
          question.answer2,
          question.answer3,
          question.answer4
        ].filter((a: any) => a != null && a.trim() !== '')
         .sort(() => Math.random() - 0.5);

        this.statusMessage = '';
        this.cdr.detectChanges();
        this.startTimer();
      },
      error: (err: any) => {
        if (err.status === 204) {
          this.isFinished = true;
          this.statusMessage = 'החידון הסתיים! תודה שהשתתפת 🎉';
          this.saveFinalWinner();
        } else {
          this.statusMessage = 'שגיאה בטעינת השאלה. מנסה שוב...';
          setTimeout(() => this.loadQuestionFromServer(), 1500);
        }
        this.cdr.detectChanges();
      }
    });
  }

  startTimer() {
    this.clearTimer();
    this.timeLeft = this.maxTime;
    this.cdr.detectChanges();

    this.timerInterval = setInterval(() => {
      if (this.timeLeft > 0) {
        this.timeLeft--;
        this.cdr.detectChanges();
      } else {
        this.clearTimer();
        this.handleTimeout();
      }
    }, 1000);
  }

  handleTimeout() {
    this.statusMessage = 'נגמר הזמן! ⏰';
    this.isAnswering = true;
    this.cdr.detectChanges();

    this.playerService.submitAnswer(this.quizId, this.playerId, 'TIMEOUT').subscribe({
      next: (res: any) => {
        if (res.status === 'finished') {
          this.isFinished = true;
          this.currentQuestion = null;
          this.statusMessage = 'החידון הסתיים! תודה שהשתתפת 🎉';
          this.saveFinalWinner();
          this.cdr.detectChanges();
          return;
        }
        setTimeout(() => this.loadQuestionFromServer(), 1500);
      },
      error: () => {
        this.isAnswering = false;
        setTimeout(() => this.loadQuestionFromServer(), 1500);
      }
    });
  }

  onAnswer(selectedAnswer: string) {
    if (!this.currentQuestion || this.isFinished || this.isAnswering) return;

    this.clearTimer();
    this.isAnswering = true;
    this.cdr.detectChanges();

    this.playerService.submitAnswer(this.quizId, this.playerId, selectedAnswer).subscribe({
      next: (res: any) => {
        if (res.status === 'finished') {
          this.isFinished = true;
          this.currentQuestion = null;
          this.statusMessage = 'החידון הסתיים! תודה שהשתתפת 🎉';
          this.saveFinalWinner();
          this.cdr.detectChanges();
          return;
        }

        if (res.status === 'timeout') {
          this.statusMessage = 'איחרת את המועד! ⏰';
        } else {
          this.statusMessage = res.correct === true ? 'כל הכבוד! ✨' : 'טעות... 😕';
        }

        this.cdr.detectChanges();
        setTimeout(() => this.loadQuestionFromServer(), 2000);
      },
      error: () => {
        this.statusMessage = 'שגיאה בשליחת התשובה.';
        this.isAnswering = false;
        this.cdr.detectChanges();
      }
    });
  }

  subscribeLeaderboard() {
    this.leaderboardSub = this.playerService.getLeaderboardUpdates(this.quizId)
      .subscribe({
        next: (data: any[]) => {
          if (Array.isArray(data)) {
            const uniquePlayers = new Map<string, any>();
            data.forEach((item: any) => {
              const playerName = (item.displayName || item.name || 'שחקן').trim();
              const key = playerName.toLowerCase();
              const score = item.score ?? 0;
              const existing = uniquePlayers.get(key);
              if (!existing || score > existing.score) {
                uniquePlayers.set(key, {
                  playerName,
                  avatar: item.image || 'assets/avatar1.png',
                  score
                });
              }
            });
            this.players = Array.from(uniquePlayers.values());
            this.cdr.detectChanges();
          }
        },
        error: (err: any) => console.error('Leaderboard update failed', err)
      });
  }

  // FIX: שימוש ב-saveWinner במקום updateQuiz — לא מוגבל בזמן
  saveFinalWinner() {
    if (this.players.length > 0) {
      const winner = this.players[0];
      this.quizService.saveWinner(this.quizId, winner.playerName, winner.score).subscribe({
        next: () => console.log('הזוכה נשמר בהצלחה!'),
        error: (err) => console.error('שגיאה בשמירת הזוכה:', err)
      });
    }
  }

  calculateOffset(): number {
    return 283 - (this.timeLeft / this.maxTime) * 283;
  }
}