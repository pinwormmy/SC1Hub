package com.sc1hub.board.mapper;

import com.sc1hub.assistant.rag.AssistantRagBoardSnapshot;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.dto.CommentDTO;
import com.sc1hub.board.dto.LatestPostDTO;
import com.sc1hub.board.dto.RecommendDTO;
import com.sc1hub.common.dto.PageDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface BoardMapper {

        List<BoardDTO> showPostList(@Param("boardTitle") String boardTitle, @Param("page") PageDTO page)
                        throws Exception;

        void submitPost(@Param("boardTitle") String boardTitle, @Param("board") BoardDTO board) throws Exception;

        int submitModifyPost(@Param("boardTitle") String boardTitle, @Param("board") BoardDTO board) throws Exception;

        BoardDTO readPost(@Param("boardTitle") String boardTitle, @Param("postNum") int postNum) throws Exception;

        int deletePost(@Param("boardTitle") String boardTitle, @Param("postNum") int postNum) throws Exception;

        int countTotalPost(@Param("boardTitle") String boardTitle, @Param("page") PageDTO page) throws Exception;

        /** 해당 닉네임으로 작성된 회원 게시글 수(funboard 는 비회원 글 제외). 닉네임 재사용 차단용. */
        int countMemberPostsByWriter(@Param("boardTitle") String boardTitle, @Param("writer") String writer);

        void addComment(@Param("boardTitle") String boardTitle, @Param("comment") CommentDTO comment) throws Exception;

        List<CommentDTO> showCommentList(@Param("boardTitle") String boardTitle, @Param("page") PageDTO page)
                        throws Exception;

        CommentDTO readCommentForUpdate(@Param("boardTitle") String boardTitle,
                        @Param("commentNum") int commentNum) throws Exception;

        int deleteComment(@Param("boardTitle") String boardTitle, @Param("commentNum") int commentNum)
                        throws Exception;

        void updateCommentCount(@Param("boardTitle") String boardTitle, @Param("postNum") int postNum) throws Exception;

        void updateViews(@Param("boardTitle") String boardTitle, @Param("postNum") int postNum) throws Exception;

        int checkViewUserIp(@Param("boardTitle") String boardTitle, @Param("postNum") int postNum,
                        @Param("ip") String ip)
                        throws Exception;

        void saveViewUserIp(@Param("boardTitle") String boardTitle, @Param("postNum") int postNum,
                        @Param("ip") String ip)
                        throws Exception;

        List<BoardDTO> showSelfNoticeList(@Param("boardTitle") String boardTitle) throws Exception;

        int countTotalComment(@Param("boardTitle") String boardTitle, @Param("page") PageDTO page) throws Exception;

        int checkRecommendation(@Param("boardTitle") String boardTitle,
                        @Param("recommendDTO") RecommendDTO recommendDTO);

        void insertRecommendation(@Param("boardTitle") String boardTitle,
                        @Param("recommendDTO") RecommendDTO recommendDTO);

        void deleteRecommendation(@Param("boardTitle") String boardTitle,
                        @Param("recommendDTO") RecommendDTO recommendDTO);

        void updateTotalRecommendCount(@Param("boardTitle") String boardTitle, @Param("postNum") int postNum);

        /** 회원이 추천한 게시글 번호 목록. 회원 삭제 전 추천 기록 정리용. */
        List<Integer> selectRecommendedPostNumsByUser(@Param("boardTitle") String boardTitle,
                        @Param("userId") String userId);

        /** 회원의 추천 기록을 모두 지우고 지운 행 수를 돌려준다. */
        int deleteRecommendationsByUser(@Param("boardTitle") String boardTitle, @Param("userId") String userId);

        int getRecommendCount(@Param("boardTitle") String boardTitle, @Param("postNum") int postNum);

        int getActualRecommendCount(@Param("boardTitle") String boardTitle, @Param("postNum") int postNum);

        List<BoardListDTO> getBoardList();

        String getKoreanTitle(String boardTitle);

        List<LatestPostDTO> showLatestPosts();

        List<BoardDTO> selectPopularPosts(@Param("boardTitle") String boardTitle,
                        @Param("limit") int limit) throws Exception;

        List<BoardDTO> selectRecentPosts(@Param("boardTitle") String boardTitle,
                        @Param("limit") int limit) throws Exception;

        List<BoardDTO> selectSitemapPosts(@Param("boardTitle") String boardTitle) throws Exception;

        List<BoardDTO> selectRecentPostsForBot(@Param("boardTitle") String boardTitle,
                        @Param("limit") int limit) throws Exception;

        List<CommentDTO> selectRecentCommentsForBot(@Param("boardTitle") String boardTitle,
                        @Param("postNum") Integer postNum,
                        @Param("limit") int limit) throws Exception;

        List<BoardDTO> searchPostsByKeywords(@Param("boardTitle") String boardTitle,
                        @Param("keywords") List<String> keywords,
                        @Param("limit") int limit) throws Exception;

        List<BoardDTO> selectPostsForSearchTerms(@Param("boardTitle") String boardTitle,
                        @Param("sincePostNum") int sincePostNum,
                        @Param("limit") int limit) throws Exception;

        void updateSearchTerms(@Param("boardTitle") String boardTitle,
                        @Param("postNum") int postNum,
                        @Param("searchTerms") String searchTerms) throws Exception;

        List<BoardDTO> selectPostsForRag(@Param("boardTitle") String boardTitle,
                        @Param("limit") int limit) throws Exception;

        List<BoardDTO> selectNewPostsForRag(@Param("boardTitle") String boardTitle,
                        @Param("sincePostNum") int sincePostNum,
                        @Param("limit") int limit) throws Exception;

        List<BoardDTO> selectUpdatedPostsForRag(@Param("boardTitle") String boardTitle,
                        @Param("sinceRegDate") Date sinceRegDate,
                        @Param("limit") int limit) throws Exception;

        AssistantRagBoardSnapshot selectBoardRagStats(@Param("boardTitle") String boardTitle) throws Exception;

        // Migration
        void addCommentColumns(String tableName);

        void modifyIdColumn(String tableName);
}
