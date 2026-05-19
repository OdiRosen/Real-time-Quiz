import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { Client, Message } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export interface PodiumEntry {
  rank: number;
  playerName: string;
  score: number;
  image?: string;
}

@Injectable({
  providedIn: 'root'
})
export class PlayerService {
  private apiUrl = 'https://real-time-quiz-5q2e.onrender.com/api/player';
  private stompClient: Client | null = null;
  private connectedQuizId: number | null = null;

  private leaderboardSubject = new BehaviorSubject<any[]>([]);
  private podiumSubject = new BehaviorSubject<PodiumEntry[]>([]);

  constructor(private http: HttpClient) {}

  connectToQuiz(quizId: number): void {
    if (this.stompClient?.connected && this.connectedQuizId === quizId) return;

    if (this.stompClient) {
      this.stompClient.deactivate();
    }

    this.connectedQuizId = quizId;

    this.stompClient = new Client({
      webSocketFactory: () => new SockJS('https://real-time-quiz-5q2e.onrender.com/ws'),
      reconnectDelay: 5000,

      onConnect: () => {
        this.stompClient!.subscribe(`/topic/quiz/${quizId}`, (message: Message) => {
          try {
            const parsed = JSON.parse(message.body);
            if (Array.isArray(parsed)) {
              this.leaderboardSubject.next(parsed);
              return;
            }
            if (parsed?.active) {
              this.leaderboardSubject.next(parsed.active);
            }
            if (parsed?.podium) {
              this.podiumSubject.next(this.mapPodium(parsed.podium));
            }
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

  disconnectFromQuiz(): void {
    if (this.stompClient?.connected) {
      this.stompClient.deactivate();
    }
    this.stompClient = null;
    this.connectedQuizId = null;
  }

  joinQuiz(quizId: number, name: string, image: string, playerId?: string): Observable<any> {
    let params = new HttpParams()
      .set('name', name)
      .set('image', image);
    if (playerId) {
      params = params.set('playerId', playerId);
    }
    return this.http.post<any>(`${this.apiUrl}/${quizId}/join`, {}, { params });
  }

  leaveQuiz(quizId: number, playerId: string): void {
    this.http.post(`${this.apiUrl}/${quizId}/leave`, null, {
      params: new HttpParams().set('playerId', playerId)
    }).subscribe({
      next: () => console.log('Left quiz successfully'),
      error: () => {}
    });
    this.disconnectFromQuiz();
  }

  getLeaderboardUpdates(quizId: number): Observable<any[]> {
    this.http.get<any[]>(`${this.apiUrl}/leaderboard/${quizId}`).subscribe({
      next: (players) => this.leaderboardSubject.next(players),
      error: (err) => console.error('Initial leaderboard fetch failed', err)
    });

    this.http.get<PodiumEntry[]>(`${this.apiUrl}/${quizId}/podium`).subscribe({
      next: (podium) => this.podiumSubject.next(this.mapPodium(podium)),
      error: () => {}
    });

    return this.leaderboardSubject.asObservable();
  }

  getPodiumUpdates(): Observable<PodiumEntry[]> {
    return this.podiumSubject.asObservable();
  }

  getPodium(quizId: number): Observable<PodiumEntry[]> {
    return this.http.get<PodiumEntry[]>(`${this.apiUrl}/${quizId}/podium`);
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

  private mapPodium(rows: any[]): PodiumEntry[] {
    if (!Array.isArray(rows)) return [];
    return rows.map(r => ({
      rank: r.rank,
      playerName: r.playerName,
      score: r.score ?? 0,
      image: r.image || ''
    }));
  }
}
