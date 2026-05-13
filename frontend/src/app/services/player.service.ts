import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { Client, Message } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

@Injectable({
  providedIn: 'root'
})
export class PlayerService {
  private apiUrl = 'http://localhost:8080/api/player';
  private stompClient: Client | null = null;

  // Subject שמחזיק תמיד את הרשימה האחרונה
  private leaderboardSubject = new BehaviorSubject<any[]>([]);

  constructor(private http: HttpClient) {}

  // חיבור WebSocket לחידון ספציפי — נקרא פעם אחת כשהשחקן נכנס
  connectToQuiz(quizId: number): void {
    if (this.stompClient?.connected) return;

    this.stompClient = new Client({
      // FIX: שימוש ב-SockJS כ-factory במקום brokerURL ישיר
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      reconnectDelay: 5000,

      onConnect: () => {
        console.log('WebSocket connected');

        // FIX: הנתיב הנכון שהשרת שולח אליו — /topic/quiz/{quizId}
        this.stompClient!.subscribe(`/topic/quiz/${quizId}`, (message: Message) => {
          try {
            const players = JSON.parse(message.body);
            this.leaderboardSubject.next(players);
          } catch (e) {
            console.error('Failed to parse leaderboard message', e);
          }
        });
      },

      onDisconnect: () => console.log('WebSocket disconnected'),
      onStompError: (frame) => console.error('STOMP error', frame)
    });

    this.stompClient.activate();
  }

  // ניתוק מהחידון — נקרא כשהשחקן עוזב
  disconnectFromQuiz(): void {
    if (this.stompClient?.connected) {
      this.stompClient.deactivate();
      this.stompClient = null;
    }
  }

  joinQuiz(quizId: number, name: string, image: string): Observable<any> {
    const params = new HttpParams()
      .set('name', name)
      .set('image', image);
    return this.http.post<any>(`${this.apiUrl}/${quizId}/join`, {}, { params });
  }

  // עזיבת חידון — שולח לשרת ומנתק WebSocket
  leaveQuiz(quizId: number, playerId: string): void {
    this.http.post(`${this.apiUrl}/${quizId}/leave`, null, {
      params: new HttpParams().set('playerId', playerId)
    }).subscribe({
      next: () => console.log('Left quiz successfully'),
      error: () => {} // לא קריטי אם נכשל
    });
    this.disconnectFromQuiz();
  }

  // קבלת עדכוני לוח מובילים — מחזיר Observable שמתעדכן דרך WebSocket
  getLeaderboardUpdates(quizId: number): Observable<any[]> {
    // טעינה ראשונית מיידית דרך HTTP (לפני שה-WebSocket מתחבר)
    this.http.get<any[]>(`${this.apiUrl}/leaderboard/${quizId}`).subscribe({
      next: (players) => this.leaderboardSubject.next(players),
      error: (err) => console.error('Initial leaderboard fetch failed', err)
    });

    return this.leaderboardSubject.asObservable();
  }

  getSyncQuestion(quizId: number, playerId: string): Observable<any> {
    const params = new HttpParams().set('playerId', playerId);
    return this.http.get<any>(`${this.apiUrl}/question/${quizId}`, {
      params,
      observe: 'response'
    });
  }

  submitAnswer(quizId: number, playerId: string, answer: string): Observable<any> {
    const params = new HttpParams()
      .set('quizId', quizId.toString())
      .set('playerId', playerId)
      .set('answer', answer);
    return this.http.post<any>(`${this.apiUrl}/submit`, null, { params });
  }
}