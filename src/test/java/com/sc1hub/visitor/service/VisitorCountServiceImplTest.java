package com.sc1hub.visitor.service;

import com.sc1hub.visitor.dto.VisitorCountDTO;
import com.sc1hub.visitor.mapper.VisitorCountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitorCountServiceImplTest {

    @Mock
    private VisitorCountMapper visitorCountMapper;

    @InjectMocks
    private VisitorCountServiceImpl visitorCountService;

    @Test
    void incrementVisitorCount_incrementsExistingDailyRecord() {
        when(visitorCountMapper.findByDate(any(LocalDate.class))).thenReturn(new VisitorCountDTO());
        when(visitorCountMapper.getTotalCount()).thenReturn(10);

        visitorCountService.incrementVisitorCount();

        verify(visitorCountMapper).incrementDailyCount(any(LocalDate.class));
        verify(visitorCountMapper, never()).insertNewRecord(any(LocalDate.class), anyInt());
        verify(visitorCountMapper).incrementTotalCount();
    }

    @Test
    void incrementVisitorCount_insertsNewRecord_whenMissing() {
        when(visitorCountMapper.findByDate(any(LocalDate.class))).thenReturn(null);
        when(visitorCountMapper.getTotalCount()).thenReturn(null);

        visitorCountService.incrementVisitorCount();

        verify(visitorCountMapper).insertNewRecord(any(LocalDate.class), eq(0));
        verify(visitorCountMapper, never()).incrementDailyCount(any(LocalDate.class));
        verify(visitorCountMapper).incrementTotalCount();
    }
}
