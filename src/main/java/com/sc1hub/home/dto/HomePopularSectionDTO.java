package com.sc1hub.home.dto;

import lombok.Data;

import java.util.List;

@Data
public class HomePopularSectionDTO {
    private String title;
    private String cssClass;
    private String legendColor;
    private List<HomePopularBoardDTO> boards;
}
