package com.sc1hub.common.controller;

import com.sc1hub.board.service.BoardService;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import com.sc1hub.board.support.BoardTitleNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
public class MigrationController {

    private final BoardService boardService;
    private final BoardMapper boardMapper;

    public MigrationController(BoardService boardService, BoardMapper boardMapper) {
        this.boardService = boardService;
        this.boardMapper = boardMapper;
    }

    @PostMapping("/migrate/comments")
    public String migrateComments() {
        return migrateCommentTables(
                boardMapper::addCommentColumns,
                "Migrated table: {}",
                "Failed or already exists for {}",
                "Comments migration completed. Check logs."
        );
    }

    @PostMapping("/migrate/id-nullable")
    public String migrateId() {
        return migrateCommentTables(
                boardMapper::modifyIdColumn,
                "Modified table ID column: {}",
                "Failed to modify ID for {}",
                "ID nullable migration completed. Check logs."
        );
    }

    private String migrateCommentTables(TableMigrationAction migrationAction,
                                        String successMessage,
                                        String failureMessage,
                                        String completionMessage) {
        try {
            List<BoardListDTO> boards = boardService.getBoardList();
            for (BoardListDTO board : boards) {
                String tableName = BoardTitleNormalizer.requireValid(board.getBoardTitle()) + "_comment";
                try {
                    migrationAction.execute(tableName);
                    log.info(successMessage, tableName);
                } catch (Exception e) {
                    log.warn(failureMessage, tableName, e);
                }
            }
            return completionMessage;
        } catch (Exception e) {
            log.error("Migration failed", e);
            return "Migration failed: " + e.getMessage();
        }
    }

    @FunctionalInterface
    private interface TableMigrationAction {
        void execute(String tableName) throws Exception;
    }
}
