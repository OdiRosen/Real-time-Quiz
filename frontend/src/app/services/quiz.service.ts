import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Quiz {
  id: number;
  name: string;
  startTime: string;
  endTime: string;
  creatorEmail: string;
  winnerName?: string;
  winnerScore?: number;
}

@Injectable({
  providedIn: 'root'
})
export class QuizService {
  private apiUrl = 'http://localhost:8080/api/admin/quiz';

  constructor(private http: HttpClient) {}

  getQuizzesByEmail(email: string): Observable<Quiz[]> {
    const params = new HttpParams().set('email', email);
    return this.http.get<Quiz[]>(this.apiUrl, { params });
  }

  createQuiz(quiz: Partial<Quiz>): Observable<Quiz> {
    return this.http.post<Quiz>(this.apiUrl, quiz);
  }

  uploadQuizQuestions(quizId: number, file: File, deleteExisting: boolean): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('deleteExisting', String(deleteExisting));
    return this.http.post(`${this.apiUrl}/${quizId}/upload`, formData);
  }

  updateQuiz(id: number, quiz: Partial<Quiz>): Observable<Quiz> {
    return this.http.put<Quiz>(`${this.apiUrl}/${id}`, quiz);
  }
}