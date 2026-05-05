package com.example.demo.service;

import com.example.demo.entity.Question;
import com.example.demo.entity.Quiz;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.QuizRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class QuestionService {
    @Autowired
    private QuestionRepository questionRepository;

    public void importQuestionsFromExcel(InputStream is, Quiz quiz, boolean deleteExisting) throws IOException{
        if (deleteExisting){
            questionRepository.deleteByQuizId(quiz.getId());
        }

        Workbook workbook = new XSSFWorkbook(is);
        Sheet sheet = workbook.getSheetAt(0);

        for (Row row : sheet){
            if (row.getRowNum() == 0) continue;

            Question q = new Question();
            q.setQuiz(quiz);
            q.setQuestionText(row.getCell(0).getStringCellValue());
            q.setAnswer1(row.getCell(1).getStringCellValue());
            q.setAnswer2(row.getCell(2).getStringCellValue());
            q.setAnswer3(row.getCell(3).getStringCellValue());
            q.setAnswer4(row.getCell(4).getStringCellValue());
            q.setPoints((int) row.getCell(5).getNumericCellValue());

            questionRepository.save(q);
        }
        workbook.close();
    }
}
