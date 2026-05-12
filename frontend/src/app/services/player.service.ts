import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, interval, EMPTY } from 'rxjs';
import { switchMap, catchError } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class PlayerService {
  private apiUrl = 'http://localhost:8080/api/player';

  constructor(private http: HttpClient) {}

  /**
   * הצטרפות לחידון
   * FIX: שם הפרמטר תוקן מ-displayName ל-name (תואם את ה-Java Controller)
   */
  joinQuiz(quizId: number, name: string, image: string): Observable<any> {
    const params = new HttpParams()
      .set('name', name)   // FIX: היה 'displayName', ה-Java מצפה ל-'name'
      .set('image', image);
    return this.http.post<any>(`${this.apiUrl}/${quizId}/join`, {}, { params });
  }

  /**
   * משיכת השאלה הנוכחית
   * FIX: הוספת observe: 'response' כדי לזהות 204 No Content (סיום חידון)
   */
  getSyncQuestion(quizId: number, playerId: string): Observable<any> {
    const params = new HttpParams().set('playerId', playerId); // מוסיף את ה-ID לכתובת
    return this.http.get<any>(`${this.apiUrl}/question/${quizId}`, {
      params,
      observe: 'response'  // FIX: מקבלים את כל ה-response כדי לבדוק status 204
    });
  }

  /**
   * שליחת תשובה
   */
  submitAnswer(quizId: number, playerId: string, answer: string): Observable<any> {
    const params = new HttpParams()
      .set('quizId', quizId.toString())
      .set('playerId', playerId)
      .set('answer', answer);
    return this.http.post<any>(`${this.apiUrl}/submit`, null, { params });
  }

  /**
   * עדכון טבלת מובילים כל 3 שניות
   * FIX: הוספת catchError כדי שכשל ב-poll אחד לא יהרוס את כל ה-Observable
   */
  getLeaderboardUpdates(quizId: number): Observable<any[]> {
    return interval(3000).pipe(
      switchMap(() =>
        this.http.get<any[]>(`${this.apiUrl}/leaderboard/${quizId}`).pipe(
          catchError(err => {
            console.warn('Leaderboard fetch failed, skipping tick:', err);
            return EMPTY;
          })
        )
      )
    );
  }
  finishGame(quizId: number, winner: any) {
  const updateData = {
    winnerName: winner.name,
    winnerScore: winner.score
  };
  
  this.http.put(`/api/admin/quiz/${quizId}`, updateData).subscribe(
    response => console.log('הזוכה נשמר בהצלחה!'),
    error => console.error('שגיאה בשמירת הזוכה', error)
  );
}
}