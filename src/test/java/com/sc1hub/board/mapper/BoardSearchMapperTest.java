package com.sc1hub.board.mapper;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.common.dto.PageDTO;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoardSearchMapperTest {

    private SqlSessionFactory sessions;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                create table funboard (
                    post_num int primary key, title varchar(100), content varchar(200),
                    search_terms varchar(200), writer varchar(50), reg_date timestamp,
                    views int default 0, comment_count int default 0,
                    recommend_count int default 0, notice int
                )
                """);
        jdbc.update("""
                insert into funboard(post_num, title, content, search_terms, notice, reg_date) values
                (1, 'needle', 'body', '', 0, CURRENT_TIMESTAMP),
                (2, 'other', 'needle', '', 0, CURRENT_TIMESTAMP),
                (3, 'other', 'body', 'needle', 0, CURRENT_TIMESTAMP),
                (4, 'needle', 'needle', 'needle', 1, CURRENT_TIMESTAMP),
                (5, 'unrelated', 'body', '', 0, CURRENT_TIMESTAMP)
                """);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mapper/BoardMapper.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        sessions = factory.getObject();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "TITLE", " ", "title' OR 1=1 --"})
    void invalidSearchTypesFallBackToTitleWithoutSqlErrors(String searchType) throws Exception {
        assertSearch(searchType, List.of(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "content", "titleAndContent"})
    void searchMatchesOnlyNonNoticePostsAndCountAgreesWithList(String searchType) throws Exception {
        List<Integer> expected = switch (searchType) {
            case "title" -> List.of(1);
            case "content" -> List.of(2, 3);
            default -> List.of(1, 2, 3);
        };
        assertSearch(searchType, expected);
    }

    private void assertSearch(String searchType, List<Integer> expected) throws Exception {
        PageDTO page = new PageDTO();
        page.setSearchType(searchType);
        page.setKeyword("needle");
        page.setDisplayPostLimit(15);
        try (SqlSession session = sessions.openSession()) {
            BoardMapper mapper = session.getMapper(BoardMapper.class);
            assertEquals(expected.size(), mapper.countTotalPost("funboard", page));
            assertEquals(expected, mapper.showPostList("funboard", page).stream()
                    .map(BoardDTO::getPostNum).sorted().toList());
        }
    }
}
