package cms.schedule.service;

import cms.schedule.dto.ScheduleDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleService {
    /**
     * 날짜 범위로 스케줄 목록 조회
     */
    List<ScheduleDto> getSchedulesByDateRange(LocalDate dateFrom, LocalDate dateTo);

    /**
     * 연월로 스케줄 목록 조회
     */
    List<ScheduleDto> getSchedulesByYearMonth(int year, int month);

    /**
     * 검색 조건으로 스케줄 목록 조회
     */
    Page<ScheduleDto> searchSchedules(String title, String displayYn, Pageable pageable);

    /**
     * 스케줄 상세 조회
     */
    ScheduleDto getSchedule(Long scheduleId);

    /**
     * 스케줄 등록
     */
    ScheduleDto createSchedule(ScheduleDto scheduleDto, String createdBy, String createdIp);

    /**
     * 스케줄 수정
     */
    ScheduleDto updateSchedule(Long scheduleId, ScheduleDto scheduleDto, String updatedBy, String updatedIp);

    /**
     * 스케줄 삭제
     */
    void deleteSchedule(Long scheduleId);
} 