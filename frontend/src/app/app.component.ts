import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule, HttpClient } from '@angular/common/http';
import { GameScreenComponent } from './components/game-screen/game-screen.component';
import { PlayerService } from './services/player.service';
import { QuizService } from './services/quiz.service';
import { ViewChild, ElementRef } from '@angular/core';

declare global {
  interface Window {
    google?: any;
  }
}

interface QuizInfo {
  id: number;
  title: string;
  start: string;
  end: string;
  owner: string;
  winner: string;
  winnerScore: number;
  status: string;
}

type AppScreen = 'landing' | 'auth' | 'player' | 'admin';
type AuthMode = 'login' | 'register';
type UserRole = 'player' | 'admin';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    GameScreenComponent,
    HttpClientModule
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  title = 'quiz-app';
  screen: AppScreen = 'auth';
  authMode: AuthMode = 'login';
  selectedRole: UserRole = 'player';
  authName = '';
  authEmail = '';
  authPassword = '';
  adminVerificationCode = '';
  authMessage = '';
  isAdminAuthenticated = false;
  adminActionSelected: 'list' | 'create' | null = null;

  googleClientId = '485828484387-krrhkpr3jttsal7ktuu4pmcqoqfvr6s8.apps.googleusercontent.com';
  private readonly adminAccessCode = 'QUIZ-ADMIN-2026';

  currentUser: { name: string; email: string; role: UserRole; photoUrl: string } | null = null;
  readonly defaultAvatar = '/assets/avatar1.png';
  playerName = '';
  quizCode = '';
  playerMessage = '';
  isJoined = false;
  playerData: any = null;
  selectedQuizId = 0;
  adminEmail = '';
  adminName = '';
  adminSigned = false;
  uploadChoice: 'replace' | 'add' = 'replace';
  uploadedFileName = 'לא נבחר קובץ';
  selectedFile: File | null = null;
  selectedQuiz: QuizInfo | null = null;
  message = '';
  isLoadingQuizzes = false;

  // ללא id — ה-DB מייצר אוטומטית
  newQuiz = { title: '', start: '', end: '' };

  quizzes: QuizInfo[] = [];

  constructor(
    private playerService: PlayerService,
    private http: HttpClient,
    private quizService: QuizService
  ) {}

  ngOnInit() {
    this.loadGoogleIdentityScript();
  }

  handleCredentialResponse = (response: any) => {
    this.processGoogleCredential(response);
  }

  private processGoogleCredential(credentialResponse: any) {
    const jwt = credentialResponse?.credential;
    if (!jwt) {
      this.authMessage = 'תקלה בקריאת אישור Google. נסה שנית.';
      return;
    }
    if (!this.validateAdminVerification()) return;

    const payload = this.decodeJwt(jwt);
    const email = payload.email || '';
    const name = payload.name || email.split('@')[0] || 'משתמש';
    const photoUrl = payload.picture || this.defaultAvatar;

    this.currentUser = { name, email, role: this.selectedRole, photoUrl };
    this.authMessage = `התחברת עם Google כ${this.selectedRole === 'admin' ? 'מנהל' : 'שחקן'}.`;
    this.applyUserRole();
  }

  private decodeJwt(token: string) {
    try {
      let payload = token.split('.')[1];
      payload = payload.replace(/-/g, '+').replace(/_/g, '/');
      while (payload.length % 4 !== 0) {
        payload += '=';
      }
      const decoded = atob(payload);
      return JSON.parse(decodeURIComponent(
        decoded.split('').map((c) => `%${('00' + c.charCodeAt(0).toString(16)).slice(-2)}`).join('')
      ));
    } catch {
      return {};
    }
  }

  private validateAdminVerification() {
    if (this.selectedRole !== 'admin' || this.authMode !== 'register') return true;
    if (this.adminVerificationCode.trim() === this.adminAccessCode) return true;
    this.authMessage = 'קוד מנהל שגוי. יש להזין קוד אימות מנהלי בהרשמה ראשונה.';
    return false;
  }

  private loadGoogleIdentityScript() {
    if (window.google?.accounts?.id) {
      this.initializeGoogleId();
      return;
    }
    const existing = document.querySelector<HTMLScriptElement>('script[src="https://accounts.google.com/gsi/client"]');
    if (existing) {
      existing.addEventListener('load', () => this.initializeGoogleId());
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.onload = () => this.initializeGoogleId();
    document.head.appendChild(script);
  }

  private initializeGoogleId() {
    if (window.google?.accounts?.id) {
      (window as any).handleCredentialResponse = this.handleCredentialResponse;
      window.google.accounts.id.initialize({
        client_id: this.googleClientId,
        callback: this.handleCredentialResponse.bind(this),
        auto_select: false,
        use_fedcm_for_prompt: false
      });
      const container = document.getElementById('g_id_signin_container');
      if (container) {
        window.google.accounts.id.renderButton(container, { theme: 'outline', size: 'large', width: '100%' });
      }
    }
  }

  openAuth() {
    this.screen = 'auth';
    this.authMode = 'login';
    this.selectedRole = 'player';
    this.authMessage = '';
  }

  switchAuthMode(mode: AuthMode) {
    this.authMode = mode;
    this.authMessage = '';
  }

  selectRole(role: UserRole) {
    this.selectedRole = role;
    this.authMessage = '';
    if (role !== 'admin') this.adminVerificationCode = '';
  }

  handleLocalAuth() {
    if (!this.authEmail.trim() || !this.authPassword.trim()) {
      this.authMessage = 'אנא מלא אימייל וסיסמה.';
      return;
    }
    if (this.authMode === 'register' && !this.authName.trim()) {
      this.authMessage = 'אנא הזן שם מלא להרשמה.';
      return;
    }
    if (!this.validateAdminVerification()) return;

    const name = this.authMode === 'register'
      ? this.authName
      : this.authName || this.authEmail.split('@')[0];

    this.currentUser = { name, email: this.authEmail, role: this.selectedRole, photoUrl: this.defaultAvatar };
    this.authMessage = this.authMode === 'register'
      ? `נרשמת בהצלחה כ${this.selectedRole === 'admin' ? 'מנהל' : 'שחקן'}.`
      : `התחברת בהצלחה כ${this.selectedRole === 'admin' ? 'מנהל' : 'שחקן'}.`;
    this.applyUserRole();
  }

  triggerGoogleSignIn() {
    if (!window.google?.accounts?.id) {
      this.authMessage = 'ספריית Google לא טעונה. רענן את הדף.';
      return;
    }
    window.google.accounts.id.prompt((notification: any) => {
      console.log('Google prompt notification:', notification);
    });
  }

  private applyUserRole() {
    if (!this.currentUser) return;

    if (this.currentUser.role === 'admin') {
      this.adminEmail = this.currentUser.email;
      this.adminName = this.currentUser.name;
      this.adminSigned = true;
      this.message = `ברוך הבא, ${this.adminName}.`;
      this.screen = 'admin';
      this.adminActionSelected = null;
      // טעינת החידונים מה-DB מיד בכניסה
      this.loadQuizzesFromDB();
    } else {
      this.playerName = this.currentUser.name;
      this.playerMessage = 'שלום ' + this.playerName + ', בחר קוד חידון כדי להתחיל.';
      this.screen = 'player';
    }

    this.isJoined = false;
    this.selectedQuiz = null;
    this.quizCode = '';
  }

  loadQuizzesFromDB() {
    this.isLoadingQuizzes = true;
    this.quizService.getQuizzesByEmail(this.adminEmail).subscribe({
      next: (quizzes) => {
        this.isLoadingQuizzes = false;
        this.quizzes = quizzes.map(q => ({
          id: q.id,
          title: q.name,
          start: q.startTime,
          end: q.endTime,
          owner: q.creatorEmail,
          winner: q.winnerName || '-',
          winnerScore: q.winnerScore || 0,
          status: new Date(q.endTime) > new Date() ? 'פתוח' : 'סגור'
        }));
      },
      error: () => {
        this.isLoadingQuizzes = false;
        this.message = 'שגיאה בטעינת החידונים. בדוק שהשרת פועל.';
      }
    });
  }

  resetRole() {
    this.screen = 'auth';
    this.adminActionSelected = null;
    this.isAdminAuthenticated = false;
    this.authMode = 'login';
    this.selectedRole = 'player';
    this.authEmail = '';
    this.authPassword = '';
    this.adminVerificationCode = '';
    this.authName = '';
    this.authMessage = '';
    this.currentUser = null;
    this.playerName = '';
    this.quizCode = '';
    this.playerMessage = '';
    this.isJoined = false;
    this.playerData = null;
    this.selectedQuizId = 0;
    this.adminEmail = '';
    this.adminName = '';
    this.adminSigned = false;
    this.uploadChoice = 'replace';
    this.uploadedFileName = 'לא נבחר קובץ';
    this.selectedQuiz = null;
    this.message = '';
    this.quizzes = [];
  }

  onPlayerJoin() {
    const quizId = Number(this.quizCode);
    if (!quizId) {
      this.playerMessage = 'יש להזין קוד חידון תקין.';
      return;
    }
    if (!this.playerName.trim()) {
      this.playerMessage = 'אנא הזן שם תצוגה לפני ההצטרפות.';
      return;
    }

    this.selectedQuizId = quizId;
    this.playerMessage = 'מצטרף לחידון...';

    const avatarUrl = this.currentUser?.photoUrl || this.defaultAvatar;
    this.playerService.joinQuiz(quizId, this.playerName, avatarUrl).subscribe({
      next: (player) => {
        this.playerData = {
          ...player,
          playerName: this.playerName,
          avatar: avatarUrl
        };
        this.isJoined = true;
        this.playerMessage = '';
      },
      error: (err) => {
        if (err.status === 403) {
          this.playerMessage = 'החידון סגור או שפג זמן ההצטרפות. נסה קוד אחר.';
        } else if (err.status === 0) {
          this.playerMessage = 'השרת לא זמין. בדוק שהשרת פועל.';
        } else {
          this.playerMessage = 'שגיאה בהצטרפות. בדוק את הקוד ונסה שוב.';
        }
      }
    });
  }

  selectQuiz(quiz: QuizInfo) {
    this.selectedQuiz = { ...quiz };
  }

  saveQuizUpdate() {
    if (!this.selectedQuiz) {
      this.message = 'בחר חידון מהרשימה כדי לערוך אותו.';
      return;
    }

    this.quizService.updateQuiz(this.selectedQuiz.id, {
      name: this.selectedQuiz.title,
      startTime: this.selectedQuiz.start,
      endTime: this.selectedQuiz.end,
      creatorEmail: this.selectedQuiz.owner
    }).subscribe({
      next: () => {
        this.message = `החידון "${this.selectedQuiz!.title}" עודכן בהצלחה.`;
        this.loadQuizzesFromDB();
      },
      error: () => {
        this.message = 'שגיאה בעדכון החידון. בדוק שהשרת פועל.';
      }
    });
  }

  createNewQuiz() {
    if (!this.newQuiz.title.trim() || !this.newQuiz.start || !this.newQuiz.end) {
      this.message = 'אנא מלא שם ותאריכים ליצירת חידון.';
      return;
    }
    if (new Date(this.newQuiz.end) <= new Date(this.newQuiz.start)) {
      this.message = 'זמן הסגירה חייב להיות אחרי זמן הפתיחה.';
      return;
    }

    this.quizService.createQuiz({
      name: this.newQuiz.title,
      startTime: this.newQuiz.start,
      endTime: this.newQuiz.end,
      creatorEmail: this.adminEmail
    }).subscribe({
      next: (created) => {
        if (this.selectedFile) {
          this.quizService.uploadQuizQuestions(created.id, this.selectedFile, this.uploadChoice === 'replace').subscribe({
            next: () => {
              this.message = `חידון "${created.name}" נוצר והקובץ הועלה בהצלחה! קוד: ${created.id}`;
              this.resetCreateQuizForm();
              this.loadQuizzesFromDB();
            },
            error: () => {
              this.message = `החידון נוצר, אך קרתה שגיאה בהעלאת קובץ השאלות.`;
              this.resetCreateQuizForm();
              this.loadQuizzesFromDB();
            }
          });
        } else {
          this.message = `חידון "${created.name}" נוצר בהצלחה! קוד: ${created.id}`;
          this.resetCreateQuizForm();
          this.loadQuizzesFromDB();
        }
      },
      error: () => {
        this.message = 'שגיאה ביצירת החידון. בדוק שהשרת פועל.';
      }
    });
  }

  handleFileInput(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.selectedFile = input.files[0];
      this.uploadedFileName = this.selectedFile.name;
      this.message = `הקובץ ${this.uploadedFileName} נטען.`;
    }
  }

  private resetCreateQuizForm() {
    this.newQuiz = { title: '', start: '', end: '' };
    this.uploadChoice = 'replace';
    this.selectedFile = null;
    this.uploadedFileName = 'לא נבחר קובץ';
  }
}