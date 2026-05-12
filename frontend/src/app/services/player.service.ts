import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, interval, switchMap, map, shareReplay, BehaviorSubject } from 'rxjs';
import { Client, Message } from '@stomp/stompjs'; // וודאי שהתקנת את החבילה הזו

@Injectable({
  providedIn: 'root'
})
export class PlayerService {
  private apiUrl = 'http://localhost:8080/api/player';
  private socketUrl = 'ws://localhost:8080/ws'; // כתובת ה-WebSocket בשרת
  private stompClient: Client | null = null;
  
  // כאן נשמור את רשימת השחקנים העדכנית
  private leaderboardSubject = new BehaviorSubject<any[]>([]);

  constructor(private http: HttpClient) {
    this.initWebSocket();
  }

  // 1. חיבור ראשוני ל-WebSocket
  private initWebSocket() {
    this.stompClient = new Client({
      brokerURL: this.socketUrl,
      reconnectDelay: 5000,
      onConnect: () => {
        console.log('Connected to WebSocket');
      }
    });
    this.stompClient.activate();
  }

  // 2. הצטרפות לחידון (דרך WebSocket כדי שהשרת יזהה ניתוק)
joinQuiz(quizId: number, name: string, image: string): Observable<any> {
    const params = new HttpParams()
      .set('name', name)
      .set('image', image);
    return this.http.post<any>(`${this.apiUrl}/${quizId}/join`, {}, { params });
  }
  
  // 3. האזנה לעדכוני טבלה (במקום interval כל 3 שניות)
getLeaderboardUpdates(quizId: number): Observable<any[]> {
    return interval(2000).pipe(
      switchMap(() => this.http.get<any[]>(`${this.apiUrl}/leaderboard/${quizId}`)),
      shareReplay(1) // מונע קריאות כפולות אם כמה רכיבים מקשיבים
    );
  }  // --- שאר הפונקציות נשארות HTTP כי הן פעולות חד פעמיות ---

getSyncQuestion(quizId: number, playerId: string): Observable<any> {
    const params = new HttpParams().set('playerId', playerId);
    return this.http.get<any>(`${this.apiUrl}/question/${quizId}`, { params, observe: 'response' });
  }
  
submitAnswer(quizId: number, playerId: string, answer: string): Observable<any> {
    const params = new HttpParams()
      .set('quizId', quizId.toString())
      .set('playerId', playerId)
      .set('answer', answer);
    return this.http.post<any>(`${this.apiUrl}/submit`, null, { params });
  }
}