package com.sc1hub.assistant.search;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantSearchTermsIndexServiceTest {

    @Mock
    private BoardMapper boardMapper;

    @Mock
    private AssistantSearchTermsService searchTermsService;

    private AssistantSearchTermsIndexService indexService;

    @BeforeEach
    void setUp() {
        indexService = new AssistantSearchTermsIndexService(boardMapper, searchTermsService);
    }

    @Test
    void reindexAll_updatesWhenNewTermsNeedToClearExistingValue() throws Exception {
        BoardListDTO board = new BoardListDTO();
        board.setBoardTitle("testboard");

        BoardDTO post = new BoardDTO();
        post.setPostNum(1);
        post.setTitle("title");
        post.setContent("content");
        post.setSearchTerms("legacy terms");

        when(boardMapper.getBoardList()).thenReturn(Collections.singletonList(board));
        when(boardMapper.selectPostsForSearchTerms("testboard", 0, 200)).thenReturn(Collections.singletonList(post));
        when(boardMapper.selectPostsForSearchTerms("testboard", 1, 200)).thenReturn(Collections.emptyList());
        when(searchTermsService.buildSearchTerms("title", "content")).thenReturn("");

        AssistantSearchTermsIndexService.ReindexResult result = indexService.reindexAllDefault();

        verify(boardMapper).updateSearchTerms("testboard", 1, "");
        assertThat(result.getUpdatedPosts()).isEqualTo(1);
    }

    @Test
    void reindexAll_skipsUpdateWhenBothExistingAndNewTermsAreEmpty() throws Exception {
        BoardListDTO board = new BoardListDTO();
        board.setBoardTitle("testboard");

        BoardDTO post = new BoardDTO();
        post.setPostNum(1);
        post.setTitle("title");
        post.setContent("content");
        post.setSearchTerms(null);

        when(boardMapper.getBoardList()).thenReturn(Collections.singletonList(board));
        when(boardMapper.selectPostsForSearchTerms("testboard", 0, 200)).thenReturn(Collections.singletonList(post));
        when(boardMapper.selectPostsForSearchTerms("testboard", 1, 200)).thenReturn(Collections.emptyList());
        when(searchTermsService.buildSearchTerms("title", "content")).thenReturn("");

        AssistantSearchTermsIndexService.ReindexResult result = indexService.reindexAllDefault();

        verify(boardMapper, never()).updateSearchTerms("testboard", 1, "");
        assertThat(result.getUpdatedPosts()).isEqualTo(0);
    }
}
