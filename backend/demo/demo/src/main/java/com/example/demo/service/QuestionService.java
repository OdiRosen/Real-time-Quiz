package com.example.demo.service;

import com.example.demo.model.Question;
import com.example.demo.model.Quiz;
import com.example.demo.repository.QuestionRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class QuestionService {

    @Autowired
    private QuestionRepository questionRepository;

    public void importQuestionsFromExcel(InputStream is, Quiz quiz, boolean deleteExisting) throws IOException {
        if (deleteExisting) {
            questionRepository.deleteByQuizId(quiz.getId());
        }

        Workbook workbook = new XSSFWorkbook(is);
        Sheet sheet = workbook.getSheetAt(0);
        DataFormatter formatter = new DataFormatter(); // FIX: שימוש ב-DataFormatter לטיפול בטוח בכל סוגי התאים

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;

            // FIX: דילוג על שורות ריקות
            if (row.getCell(0) == null || formatter.formatCellValue(row.getCell(0)).isBlank()) continue;

            Question q = new Question();
            q.setQuiz(quiz);
            q.setQuestionText(formatter.formatCellValue(row.getCell(0)));
            q.setAnswer1(formatter.formatCellValue(row.getCell(1)));
            q.setAnswer2(formatter.formatCellValue(row.getCell(2)));
            q.setAnswer3(formatter.formatCellValue(row.getCell(3)));
            q.setAnswer4(formatter.formatCellValue(row.getCell(4)));

            // FIX: בדיקה שתא הנקודות אינו null לפני קריאה
            if (row.getCell(5) != null && row.getCell(5).getCellType() == CellType.NUMERIC) {
                q.setPoints((int) row.getCell(5).getNumericCellValue());
            } else {
                q.setPoints(10); // ברירת מחדל
            }

            questionRepository.save(q);
        }
        workbook.close();
    }
}