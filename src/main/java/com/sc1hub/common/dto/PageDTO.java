package com.sc1hub.common.dto;

import lombok.Data;

@Data
public class PageDTO {

    private int pageBeginPoint;
    private int postEndPoint;
    private int postBeginPoint;
    private int pageEndPoint;
    private int prevPageSetPoint;
    private int nextPageSetPoint;
    private int totalPage;
    private int totalPostCount;
    private int recentPage;
    private String searchType; // 검색 결과도 페이징이 필요하기에, 검색단어 전달 포함
    private String keyword;
    private int postNum; // 댓글 목록때문에 넣음
    private int displayPostLimit; // MySQL문으로 전환해서 추가

}
