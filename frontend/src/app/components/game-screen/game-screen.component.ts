import { Component, OnDestroy, Input, inject, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { PlayerService } from '../../services/player.service';
import { ChangeDetectorRef } from '@angular/core';
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
  timerInterval: any;

  private leaderboardSub: Subscription | null = null;
  private playerService = inject(PlayerService);

  ngOnInit() {
    // לא עושים כלום כאן — מחכים ל-ngOnChanges שיגיע עם הערכים האמיתיים
  }

  // FIX: ngOnChanges מופעל בכל פעם שה-@Input משתנה — כולל הפעם הראשונה
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
    if (this.timerInterval) clearInterval(this.timerInterval);
  }

  calculateOffset(): number {
    const circleLength = 283;
    return circleLength - (this.timeLeft / this.maxTime) * circleLength;
  }

  loadQuestionFromServer() {
    if (this.isFinished) return;

    this.isAnswering = false;
    this.playerService.getSyncQuestion(this.quizId).subscribe({
      next: (response: any) => {
        if (response.status === 204 || !response.body) {
          this.currentQuestion = null;
          this.isFinished = true;
          this.statusMessage = 'החידון הסתיים! תודה שהשתתפת 🎉';
          if (this.timerInterval) clearInterval(this.timerInterval);
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
        this.startTimer();
      },
      error: (err: any) => {
        if (err.status === 204 || err.status === 0) {
          this.isFinished = true;
          this.statusMessage = 'החידון הסתיים! תודה שהשתתפת 🎉';
        } else {
          this.statusMessage = 'שגיאה בטעינת השאלה. מנסה שוב...';
          setTimeout(() => this.loadQuestionFromServer(), 2000);
        }
        if (this.timerInterval) clearInterval(this.timerInterval);
      }
    });
  }

  startTimer() {
    this.timeLeft = this.maxTime;
    if (this.timerInterval) clearInterval(this.timerInterval);

    this.timerInterval = setInterval(() => {
      if (this.timeLeft > 0) {
        this.timeLeft--;
      } else {
        this.handleTimeout();
      }
    }, 1000);
  }

  handleTimeout() {
    clearInterval(this.timerInterval);
    this.statusMessage = 'נגמר הזמן! ⏰';
    setTimeout(() => this.loadQuestionFromServer(), 1500);
  }

  onAnswer(selectedAnswer: string) {
    if (!this.currentQuestion || this.isFinished || this.isAnswering) return;
    this.isAnswering = true;
    clearInterval(this.timerInterval);

    if (!this.playerId) {
      this.statusMessage = 'שגיאה: מזהה שחקן חסר.';
      return;
    }

    this.playerService.submitAnswer(this.quizId, this.playerId, selectedAnswer).subscribe({
      next: (res: any) => {
        if (res.status === 'finished') {
          this.isFinished = true;
          this.currentQuestion = null;
          this.statusMessage = 'החידון הסתיים! תודה שהשתתפת 🎉';
          return;
        }
        if (res.status === 'success') {
          this.statusMessage = res.correct ? 'כל הכבוד! תשובה נכונה ✨' : 'טעות... לא נורא 😕';
        } else if (res.status === 'timeout') {
          this.statusMessage = 'איחרת את המועד! ⏰';
        }
        setTimeout(() => this.loadQuestionFromServer(), 2000);
      },
      error: (err: any) => {
        this.statusMessage = 'שגיאה בשליחת התשובה.';
        this.isAnswering = false;
        console.error(err);
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
              const avatar = item.image || '/assets/avatar1.png';
              const score = item.score ?? 0;
              const key = playerName.toLowerCase();

              const existing = uniquePlayers.get(key);
              if (!existing || score > existing.score) {
                uniquePlayers.set(key, { playerName, avatar, score });
              }
            });
            this.players = Array.from(uniquePlayers.values());
          }
        },
        error: (err: any) => console.error('Leaderboard update failed', err)
      });
  }
}