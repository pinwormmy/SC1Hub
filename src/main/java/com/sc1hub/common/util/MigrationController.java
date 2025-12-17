package com.sc1hub.common.util;

import com.sc1hub.board.BoardService;
import com.sc1hub.board.BoardListDTO;
import com.sc1hub.mapper.BoardMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MigrationController {

    @Autowired
    BoardService boardService;

    @Autowired
    BoardMapper boardMapper;

    @GetMapping("/migrate/comments")
    public String migrateComments() {
        try {
            List<BoardListDTO> boards = boardService.getBoardList();
            for (BoardListDTO board : boards) {
                String tableName = board.getBoardTitle() + "_comment";
                try {
                    boardMapper.addCommentColumns(tableName);
                    System.out.println("Migrated table: " + tableName);
                } catch (Exception e) {
                    System.out.println("Failed or already exists for: " + tableName + " - " + e.getMessage());
                }
            }
            return "Migration completed check logs";
        } catch (Exception e) {
            return "Migration failed: " + e.getMessage();
        }
    }

    @GetMapping("/migrate/id-nullable")
    public String migrateId() {
        try {
            List<BoardListDTO> boards = boardService.getBoardList();
            for (BoardListDTO board : boards) {
                String tableName = board.getBoardTitle() + "_comment";
                try {
                    boardMapper.modifyIdColumn(tableName);
                    System.out.println("Modified table ID column: " + tableName);
                } catch (Exception e) {
                    System.out.println("Failed to modify ID for: " + tableName + " - " + e.getMessage());
                }
            }
            return "ID Migration completed check logs";
        } catch (Exception e) {
            return "Migration failed: " + e.getMessage();
        }
    }
}
