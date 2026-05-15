import { Component, OnDestroy, Input, inject, OnInit, OnChanges, SimpleChanges, ChangeDetectorRef, HostListener } from '@angular/core';
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
  /** שלושת המנצחים הקבועים — לא מתעדכן כשמישהו עוזב */
  finalWinners: { playerName: string; avatar: string; score: number }[] = [];
  private podiumLocked = false;
  shuffledAnswers: string[] = [];
  currentQuestion: any = null;
  playerId = '';
  statusMessage = 'טוען שאלה...';
  isFinished = false;
  isAnswering = false;
  private initialized = false;

  // הודעות כניסה/יציאה
  notifications: { playerName: string; avatar: string; type: 'join' | 'leave' }[] = [];
  private previousPlayerNames = new Set<string>();

  timeLeft = 10;
  maxTime = 10;
  timerInterval: any = null;

  private leaderboardSub: Subscription | null = null;
  private playerService = inject(PlayerService);
  private quizService = inject(QuizService);
  private cdr = inject(ChangeDetectorRef);

  // FIX: כשסוגרים טאב/דפדפן — מודיעים לשרת
  @HostListener('window:beforeunload')
  onBeforeUnload() {
    if (this.playerId && this.quizId) {
      this.playerService.leaveQuiz(this.quizId, this.playerId);
    }
  }

  ngOnInit() {}

  ngOnChanges(changes: SimpleChanges) {
    const playerReady = this.player && (this.player.playerId || this.player.id);
    const quizReady = this.quizId && this.quizId > 0;

    if (playerReady && quizReady && !this.initialized) {
      this.initialized = true;
      this.playerId = this.player.playerId || this.player.id || '';

      // FIX: מתחברים ל-WebSocket לפני הכל
      this.playerService.connectToQuiz(this.quizId);

      this.loadQuestionFromServer();
      this.subscribeLeaderboard();
    }
  }

  ngOnDestroy() {
    this.leaderboardSub?.unsubscribe();
    this.clearTimer();
    // ניתוק WebSocket בעת השמדת הקומפוננט
    if (this.playerId && this.quizId) {
      this.playerService.leaveQuiz(this.quizId, this.playerId);
    }
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
          question.answer1, question.answer2,
          question.answer3, question.answer4
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

        this.statusMessage = res.status === 'timeout' ? 'איחרת את המועד! ⏰'
          : res.correct === true ? 'כל הכבוד! ✨' : 'טעות... 😕';

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
          if (!Array.isArray(data)) return;

          const newNames = new Set(data.map(p =>
            (p.displayName || p.name || 'שחקן').trim().toLowerCase()
          ));

          // זיהוי שחקנים שנכנסו — שם חדש שלא היה קודם
          data.forEach(item => {
            const name = (item.displayName || item.name || 'שחקן').trim();
            const key = name.toLowerCase();
            if (!this.previousPlayerNames.has(key)) {
              this.previousPlayerNames.add(key);
              // לא מציגים toast לשחקן הראשון (זה אני)
              if (this.previousPlayerNames.size > 1) {
                this.showNotification(name, item.image || '', 'join');
              }
            }
          });

          // זיהוי שחקנים שיצאו — שם שהיה ועכשיו נעלם
          this.previousPlayerNames.forEach(key => {
            if (!newNames.has(key)) {
              // מוצאים את השם המקורי (לא lowercase)
              const leftPlayer = this.players.find(p =>
                p.playerName.toLowerCase() === key
              );
              if (leftPlayer) {
                this.showNotification(leftPlayer.playerName, leftPlayer.avatar, 'leave');
              }
              this.previousPlayerNames.delete(key);
            }
          });

          if (!this.isFinished) {
            this.players = data.map(p => ({
              playerName: (p.displayName || p.name || 'שחקן').trim(),
              avatar: p.image || '',
              score: p.score ?? 0,
              hasAnswered: p.hasAnswered || false,
              answerStatus: p.lastAnswerStatus || 'none'
            })).sort((a, b) => b.score - a.score);
          }

          this.cdr.detectChanges();
        },
        error: (err: any) => console.error('Leaderboard update failed', err)
      });
  }

  showNotification(playerName: string, avatar: string, type: 'join' | 'leave') {
    const note = { playerName, avatar, type };
    this.notifications.push(note);
    this.cdr.detectChanges();

    setTimeout(() => {
      const idx = this.notifications.indexOf(note);
      if (idx !== -1) this.notifications.splice(idx, 1);
      this.cdr.detectChanges();
    }, 3000);
  }

  getAvatarColor(name: string | undefined): string {
    if (!name) return '#7b1fa2';
    const colors = ['#f44336','#e91e63','#9c27b0','#673ab7',
                    '#3f51b5','#2196f3','#00bcd4','#009688','#4caf50','#ff9800'];
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
      hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    return colors[Math.abs(hash % colors.length)];
  }

  private lockPodiumFromLiveLeaderboard() {
    if (this.podiumLocked) return;
    this.podiumLocked = true;

    const top3 = this.players.slice(0, 3).map(p => ({
      playerName: p.playerName,
      avatar: p.avatar || '',
      score: p.score ?? 0
    }));

    if (top3.length === 0) {
      this.loadPodiumFromServer();
      return;
    }

    this.finalWinners = top3;
    this.persistTopWinners(top3);
    this.cdr.detectChanges();
  }

  private loadPodiumFromServer() {
    this.quizService.getTopWinners(this.quizId).subscribe({
      next: (rows) => {
        if (rows?.length) {
          this.finalWinners = rows.map(r => ({
            playerName: r.playerName,
            avatar: r.image || '',
            score: r.score ?? 0
          }));
          this.podiumLocked = true;
          this.cdr.detectChanges();
        }
      },
      error: () => {}
    });
  }

  private persistTopWinners(
    top3: { playerName: string; avatar: string; score: number }[]
  ) {
    this.quizService.saveTopWinners(
      this.quizId,
      top3.map(w => ({
        playerName: w.playerName,
        score: w.score,
        image: w.avatar
      }))
    ).subscribe({
      next: () => console.log('שלושת המנצחים נשמרו ב-DB'),
      error: (err) => console.error('שגיאה בשמירת המנצחים:', err)
    });
  }

  saveFinalWinner() {
    this.lockPodiumFromLiveLeaderboard();
  }

  calculateOffset(): number {
    return 283 - (this.timeLeft / this.maxTime) * 283;
  }
}